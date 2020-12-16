package com.xiaodabao.common.timer;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.concurrent.atomic.AtomicLong;

/**
 * dubbo时间轮的具体实现
 * 需规划好tickDuration, 1. 任务执行的精度, 2. 避免大量无谓的空轮询
 * dubbo调用中基本都是异步调用, 时间轮的用处很大
 */
public class HashedWheelTimer implements Timer {

    /**
     *
     */
    private static final AtomicInteger INSTANCE_COUNTER = new AtomicInteger();
    private static final AtomicBoolean WARNED_TOO_MANY_INSTANCES = new AtomicBoolean();
    private static final int INSTANCE_COUNT_LIMIT = 64;
    private static final AtomicIntegerFieldUpdater<HashedWheelTimer> WORKER_STATE_UPDATER =
            AtomicIntegerFieldUpdater.newUpdater(HashedWheelTimer.class, "workerState");

    private final Worker worker = new Worker();
    private final Thread workerThread;

    private static final int WORKER_STATE_INIT = 0;
    private static final int WORKER_STATE_STARTED = 1;
    private static final int WORKER_STATE_SHUTDOWN = 2;

    /**
     * 0 - init, 1 - started, 2 - shut down
     */
    @SuppressWarnings({"unused", "FieldMayBeFinal"})
    private volatile int workerState;

    private final long tickDuration;
    private final HashedWheelBucket[] wheel;
    private final int mask;
    private final CountDownLatch startTimeInitialized = new CountDownLatch(1);
    private final Queue<HashedWheelTimeout> timeouts = new LinkedBlockingQueue<>();
    private final Queue<HashedWheelTimeout> cancelledTimeouts = new LinkedBlockingQueue<>();
    private final AtomicLong pendingTimeouts = new AtomicLong(0);
    private final long maxPendingTimeouts;

    private volatile long startTime;

    /**
     * 以下为Constructor， tickDuration的默认值为100ms， ticksPerWheel默认值为512
     */
    public HashedWheelTimer() {
        this(Executors.defaultThreadFactory());
    }

    public HashedWheelTimer(long tickDuration, TimeUnit unit) {
        this(Executors.defaultThreadFactory(), tickDuration, unit);
    }

    public HashedWheelTimer(long tickDuration, TimeUnit unit, int ticksPerWheel) {
        this(Executors.defaultThreadFactory(), tickDuration, unit, ticksPerWheel);
    }

    public HashedWheelTimer(ThreadFactory threadFactory) {
        this(threadFactory, 100, TimeUnit.MILLISECONDS);
    }

    public HashedWheelTimer(
            ThreadFactory threadFactory, long tickDuration, TimeUnit unit) {
        this(threadFactory, tickDuration, unit, 512);
    }

    public HashedWheelTimer(
            ThreadFactory threadFactory,
            long tickDuration, TimeUnit unit, int ticksPerWheel) {
        this(threadFactory, tickDuration, unit, ticksPerWheel, -1);
    }

    /**
     * 创建基于Hash算法的时间轮
     */
    public HashedWheelTimer(ThreadFactory threadFactory, long tickDuration, TimeUnit unit, int ticksPerWheel, long maxPendingTimeouts) {

        if (threadFactory == null) {
            throw new NullPointerException("threadFactory");
        }
        if (unit == null) {
            throw new NullPointerException("unit");
        }
        if (tickDuration <= 0) {
            throw new IllegalArgumentException("tickDuration must be greater than 0: " + tickDuration);
        }
        if (ticksPerWheel <= 0) {
            throw new IllegalArgumentException("ticksPerWheel must be greater than 0: " + ticksPerWheel);
        }

        // 将ticksPerWheel规范为2的幂次方, 这样分配bucket时使用位运算较求余性能更好, 大部分的hash操作都是这样做
        wheel = createWheel(ticksPerWheel);
        mask = wheel.length - 1;

        // 以纳秒计算, 更精确
        this.tickDuration = unit.toNanos(tickDuration);

        // Prevent overflow.
        if (this.tickDuration >= Long.MAX_VALUE / wheel.length) {
            throw new IllegalArgumentException(String.format(
                    "tickDuration: %d (expected: 0 < tickDuration in nanos < %d",
                    tickDuration, Long.MAX_VALUE / wheel.length));
        }
        workerThread = threadFactory.newThread(worker);

        this.maxPendingTimeouts = maxPendingTimeouts;

        // 限制进程中时间轮的数量, 当超过限制时告警
        if (INSTANCE_COUNTER.incrementAndGet() > INSTANCE_COUNT_LIMIT &&
                WARNED_TOO_MANY_INSTANCES.compareAndSet(false, true)) {
            reportTooManyInstances();
        }
    }

    /**
     * 创建HashedWheelBucket数组并初始化数组项
     * @param ticksPerWheel
     * @return
     */
    private static HashedWheelBucket[] createWheel(int ticksPerWheel) {
        if (ticksPerWheel > 1073741824) {
            throw new IllegalArgumentException(
                    "ticksPerWheel may not be greater than 2^30: " + ticksPerWheel);
        }

        ticksPerWheel = normalizeTicksPerWheel(ticksPerWheel);
        // 创建数组, 初始化每一项
        HashedWheelBucket[] wheel = new HashedWheelBucket[ticksPerWheel];
        for (int i = 0; i < wheel.length; i++) {
            wheel[i] = new HashedWheelBucket();
        }
        return wheel;
    }

    /**
     * 通过移位操作计算出最合适的2的幂次方  刚好大于等于ticksPerWheel
     * @param ticksPerWheel
     * @return
     */
    private static int normalizeTicksPerWheel(int ticksPerWheel) {
        int normalizedTicksPerWheel = ticksPerWheel - 1;
        normalizedTicksPerWheel |= normalizedTicksPerWheel >>> 1;
        normalizedTicksPerWheel |= normalizedTicksPerWheel >>> 2;
        normalizedTicksPerWheel |= normalizedTicksPerWheel >>> 4;
        normalizedTicksPerWheel |= normalizedTicksPerWheel >>> 8;
        normalizedTicksPerWheel |= normalizedTicksPerWheel >>> 16;
        return normalizedTicksPerWheel + 1;
    }


    @Override
    protected void finalize() throws Throwable {
        try {
            super.finalize();
        } finally {
            // 时间轮销毁时，更新程序中时间轮的数量
            if (WORKER_STATE_UPDATER.getAndSet(this, WORKER_STATE_SHUTDOWN) != WORKER_STATE_SHUTDOWN) {
                INSTANCE_COUNTER.decrementAndGet();
            }
        }
    }

    @Override
    public Timeout newTimeout(TimerTask task, long delay, TimeUnit unit) {
        if (task == null) {
            throw new NullPointerException("task");
        }
        if (unit == null) {
            throw new NullPointerException("unit");
        }

        // 维护时间轮中待执行任务的数量
        long pendingTimeoutsCount = pendingTimeouts.incrementAndGet();

        // 若有最大待执行任务数量的限制，超出限制后会拒绝执行新增任务
        if (maxPendingTimeouts > 0 && pendingTimeoutsCount > maxPendingTimeouts) {
            pendingTimeouts.decrementAndGet();
            throw new RejectedExecutionException("Number of pending timeouts ("
                    + pendingTimeoutsCount + ") is greater than or equal to maximum allowed pending "
                    + "timeouts (" + maxPendingTimeouts + ")");
        }

        // 1. 惰性启动  2. 时间轮状态判断
        start();

        // System.nanoTime() + unit.toNanos(delay) 计算出任务的绝对执行时间, 减掉startTime 得到相对执行时间, 所以deadline是基于startTime的相对时间
        long deadline = System.nanoTime() + unit.toNanos(delay) - startTime;

        // 防御性编程, 一般不会出现这种情况
        if (delay > 0 && deadline < 0) {
            deadline = Long.MAX_VALUE;
        }
        // 这里 将timeout、timer、timerTask 三者关联了
        HashedWheelTimeout timeout = new HashedWheelTimeout(this, task, deadline);
        // 这里将新任务添加到timeouts中
        timeouts.add(timeout);
        return timeout;
    }

    /**
     * 启动时间轮
     */
    public void start() {
        // 这里的workerState的获取方式, 个人认为只是get, 有volatile保证可见性已足够，可以直接使用this.workerState
        switch (WORKER_STATE_UPDATER.get(this)) {
            case WORKER_STATE_INIT:
                if (WORKER_STATE_UPDATER.compareAndSet(this, WORKER_STATE_INIT, WORKER_STATE_STARTED)) {
                    // 正式开启Worker.run
                    workerThread.start();
                }
                break;
            case WORKER_STATE_STARTED:
                break;
            case WORKER_STATE_SHUTDOWN:
                // 在newTimeout中调用, 每次新增一个任务时都会判断时间轮的当前状态, 一旦关闭, 则抛出异常
                throw new IllegalStateException("cannot be started once stopped");
            default:
                throw new Error("Invalid WorkerState");
        }

        // 通过CountDownLatch的功能等待startTime的初始化完成, startTime的初始化是在Worker.run中进行的
        while (startTime == 0) {
            try {
                startTimeInitialized.await();
            } catch (InterruptedException ignore) {
                // Ignore - it will be ready very soon.
            }
        }
    }

    /**
     * 停止时间轮
     * @return
     */
    @Override
    public Set<Timeout> stop() {
        // 时间轮不能自己停止, 即不能向时间轮添加一个任务调用timer的stop方法
        if (Thread.currentThread() == workerThread) {
            throw new IllegalStateException(
                    HashedWheelTimer.class.getSimpleName() +
                            ".stop() cannot be called from " +
                            TimerTask.class.getSimpleName());
        }

        // 1. 更新workerState的值, 时间轮已关闭
        if (!WORKER_STATE_UPDATER.compareAndSet(this, WORKER_STATE_STARTED, WORKER_STATE_SHUTDOWN)) {
            // workerState can be 0 or 2 at this moment - let it always be 2.
            // 减少实例数量, 如果已经为SHUTDOWN, 说明被其他线程更新过了
            if (WORKER_STATE_UPDATER.getAndSet(this, WORKER_STATE_SHUTDOWN) != WORKER_STATE_SHUTDOWN) {
                INSTANCE_COUNTER.decrementAndGet();
            }

            return Collections.emptySet();
        }

        try {
            boolean interrupted = false;
            while (workerThread.isAlive()) {
                workerThread.interrupt();
                try {
                    workerThread.join(100);
                } catch (InterruptedException ignored) {
                    interrupted = true;
                }
            }

            if (interrupted) {
                Thread.currentThread().interrupt();
            }
        } finally {
            // 减少实例数量
            INSTANCE_COUNTER.decrementAndGet();
        }
        // 2. 返回未执行的任务
        return worker.unprocessedTimeouts();
    }

    @Override
    public boolean isStop() {
        // 同理, 使用this.workerState更优
        return WORKER_STATE_SHUTDOWN == WORKER_STATE_UPDATER.get(this);
    }

    /**
     * 返回时间轮中尚未执行的任务数
     */
    public long pendingTimeouts() {
        return pendingTimeouts.get();
    }

    private static void reportTooManyInstances() {
        // 可以自己实现通知逻辑
    }

    /**
     * 任务执行，核心任务调度逻辑
     */
    private final class Worker implements Runnable {

        private final Set<Timeout> unprocessedTimeouts = new HashSet<Timeout>();

        // 时间轮全靠tick进行推进, 即使溢出之后为负也没关系, 会计算tick&mask
        private long tick;

        @Override
        public void run() {
            // 初始化startTime
            startTime = System.nanoTime();
            if (startTime == 0) {
                // 0 是用来做为时间轮未启动标记的，初始化时startTime不能为0
                startTime = 1;
            }

            // 这里使用CountDownLatch通知等待的线程
            startTimeInitialized.countDown();

            // 一直循环执行， 直到时间轮被关闭
            do {
                final long deadline = waitForNextTick();
                if (deadline > 0) {
                    int idx = (int) (tick & mask);
                    // 优先将已取消的任务剔除
                    processCancelledTasks();
                    // 随着tick的不断递增, wheel[] 中的bucket中的任务依次执行
                    HashedWheelBucket bucket = wheel[idx];
                    // 将timeouts中的任务分派到正确的bucket中
                    transferTimeoutsToBuckets();
                    // 真正执行到期的任务
                    bucket.expireTimeouts(deadline);
                    tick++;
                }
            } while (WORKER_STATE_UPDATER.get(HashedWheelTimer.this) == WORKER_STATE_STARTED);

            // 一旦时间轮关闭, 将bucket中未执行的任务都放在unprocessedTimeouts中
            for (HashedWheelBucket bucket : wheel) {
                bucket.clearTimeouts(unprocessedTimeouts);
            }
            for (; ; ) {
                // timeouts中未分配的任务也一并放入unprocessedTimeouts中
                HashedWheelTimeout timeout = timeouts.poll();
                if (timeout == null) {
                    break;
                }
                if (!timeout.isCancelled()) {
                    unprocessedTimeouts.add(timeout);
                }
            }
            processCancelledTasks();
        }

        private void transferTimeoutsToBuckets() {
            // 每次最多分派10万任务
            for (int i = 0; i < 100000; i++) {
                HashedWheelTimeout timeout = timeouts.poll();
                if (timeout == null) {
                    // all processed
                    break;
                }
                if (timeout.state() == HashedWheelTimeout.ST_CANCELLED) {
                    // Was cancelled in the meantime.
                    continue;
                }

                // calculated 和 remainingRounds 的计算是关键, deadline是相对startTime的时间, 除以tickDuration得到相对于index为0的一个偏移量
                long calculated = timeout.deadline / tickDuration;
                // calculated - tick 得到距离当前tick的一个偏移量, 除以wheel.length即得到了还需要**轮才能执行此timeout
                // 这里的计算可能通过 delay / (wheel.length * tickDuration) 更容易理解, 即 延迟时间/ 时间轮的时间跨度
                // 每经过一个时间跨度, remainingRounds减1, 为0时表示任务需要执行了
                timeout.remainingRounds = (calculated - tick) / wheel.length;

                // 如果过期了，保证该任务可以立刻执行
                final long ticks = Math.max(calculated, tick);
                // 使用 & mask 代替了求余
                int stopIndex = (int) (ticks & mask);

                HashedWheelBucket bucket = wheel[stopIndex];
                bucket.addTimeout(timeout);
            }
        }

        private void processCancelledTasks() {
            for (; ; ) {
                HashedWheelTimeout timeout = cancelledTimeouts.poll();
                if (timeout == null) {
                    // all processed
                    break;
                }
                try {
                    timeout.remove();
                } catch (Throwable t) {
                    System.out.println("An exception was thrown while process a cancellation task");
                }
            }
        }

        /**
         * calculate goal nanoTime from startTime and current tick number,
         * then wait until that goal has been reached.
         *
         * @return Long.MIN_VALUE if received a shutdown request,
         * current time otherwise (with Long.MIN_VALUE changed by +1)
         */
        private long waitForNextTick() {
            // deadline为本次的截止时间, 所以是tick + 1
            long deadline = tickDuration * (tick + 1);

            for (; ; ) {
                final long currentTime = System.nanoTime() - startTime;
                long sleepTimeMs = (deadline - currentTime + 999999) / 1000000;

                if (sleepTimeMs <= 0) {
                    if (currentTime == Long.MIN_VALUE) {
                        return -Long.MAX_VALUE;
                    } else {
                        // 休眠之后, 最终都会返回这个currentTime > 0
                        return currentTime;
                    }
                }
                if (isWindows()) {
                    sleepTimeMs = sleepTimeMs / 10 * 10;
                }

                // 通过sleep来控制时间轮的正常推进
                try {
                    Thread.sleep(sleepTimeMs);
                } catch (InterruptedException ignored) {
                    if (WORKER_STATE_UPDATER.get(HashedWheelTimer.this) == WORKER_STATE_SHUTDOWN) {
                        return Long.MIN_VALUE;
                    }
                }
            }
        }

        Set<Timeout> unprocessedTimeouts() {
            return Collections.unmodifiableSet(unprocessedTimeouts);
        }
    }


    private static final class HashedWheelTimeout implements Timeout {

        private static final int ST_INIT = 0;
        private static final int ST_CANCELLED = 1;
        private static final int ST_EXPIRED = 2;
        private static final AtomicIntegerFieldUpdater<HashedWheelTimeout> STATE_UPDATER =
                AtomicIntegerFieldUpdater.newUpdater(HashedWheelTimeout.class, "state");

        private final HashedWheelTimer timer;
        private final TimerTask task;
        private final long deadline;

        @SuppressWarnings({"unused", "FieldMayBeFinal", "RedundantFieldInitialization"})
        private volatile int state = ST_INIT;

        /**
         * Worker.transferTimeoutsToBuckets() 计算得来, 初始值已进行过分析
         * 主要用来表示当前timeout处于时间轮的哪个层级
         */
        long remainingRounds;

        /**
         * 双向链表 指针 只有WorkerThread调用（单线程），不存在线程安全问题
         */
        HashedWheelTimeout next;
        HashedWheelTimeout prev;

        /**
         * timeout所属的bucket
         */
        HashedWheelBucket bucket;

        /**
         * 初始化时将三者关联
         * @param timer
         * @param task
         * @param deadline
         */
        HashedWheelTimeout(HashedWheelTimer timer, TimerTask task, long deadline) {
            this.timer = timer;
            this.task = task;
            this.deadline = deadline;
        }

        @Override
        public Timer timer() {
            return this.timer;
        }

        @Override
        public TimerTask task() {
            return this.task;
        }

        @Override
        public boolean isExpired() {
            return state() == ST_EXPIRED;
        }

        @Override
        public boolean isCancelled() {
            return state() == ST_CANCELLED;
        }

        @Override
        public boolean cancel() {
            // only update the state it will be removed from HashedWheelBucket on next tick.
            if (!compareAndSetState(ST_INIT, ST_CANCELLED)) {
                return false;
            }
            // If a task should be canceled we put this to another queue which will be processed on each tick.
            // So this means that we will have a GC latency of max. 1 tick duration which is good enough. This way
            // we can make again use of our MpscLinkedQueue and so minimize the locking / overhead as much as possible.
            timer.cancelledTimeouts.add(this);
            return true;
        }

        /**
         * 真正执行任务
         */
        public void expire() {
            // 确保任务只被执行一次，只有初始化状态的任务才会被执行
            if (!compareAndSetState(ST_INIT, ST_EXPIRED)) {
                return;
            }

            try {
                task.run(this);
            } catch (Throwable t) {

            }
        }

        public boolean compareAndSetState(int expected, int state) {
            return STATE_UPDATER.compareAndSet(this, expected, state);
        }

        void remove() {
            HashedWheelBucket bucket = this.bucket;
            if (bucket != null) {
                bucket.remove(this);
            } else {
                timer.pendingTimeouts.decrementAndGet();
            }
        }

        public int state() {
            return state;
        }
    }


    private static final class HashedWheelBucket {

        /**
         * 采用了双向链表的数据结构
         */
        private HashedWheelTimeout head;
        private HashedWheelTimeout tail;

        /**
         * 向bucket中新增一个timeout
         * 1. bucket为空(head==null) 初始化head/tail
         * 2. bucket不为空, 将timeout添加到末尾, 更新链表指针、tail
         */
        void addTimeout(HashedWheelTimeout timeout) {
            assert timeout.bucket == null;
            timeout.bucket = this;
            if (head == null) {
                head = tail = timeout;
            } else {
                tail.next = timeout;
                timeout.prev = tail;
                tail = timeout;
            }
        }

        /**
         * 根据指定的deadline运行bucket中已到期的任务
         */
        void expireTimeouts(long deadline) {
            HashedWheelTimeout timeout = head;

            // process all timeouts
            while (timeout != null) {
                HashedWheelTimeout next = timeout.next;
                if (timeout.remainingRounds <= 0) {
                    // 这里没必要重新对next进行重新赋值, 在remove方法里返回的也是timeout.next，重新赋值让人迷惑
                    next = remove(timeout);
                    if (timeout.deadline <= deadline) {
                        // 真正执行任务, 见timeout.expire
                        timeout.expire();
                    } else {
                        // The timeout was placed into a wrong slot. This should never happen.
                        throw new IllegalStateException(String.format(
                                "timeout.deadline (%d) > deadline (%d)", timeout.deadline, deadline));
                    }
                } else if (timeout.isCancelled()) {
                    // 这里没必要重新对next进行重新赋值, 在remove方法里返回的也是timeout.next，重新赋值让人迷惑
                    next = remove(timeout);
                } else {
                    // 这里类似对时间轮进行降级, 距离此任务被执行又近了一步, tickDuration * ticksPerWheel 的时间
                    timeout.remainingRounds--;
                }
                timeout = next;
            }
        }

        /**
         * 从bucket中移除timeout 在为timeout重新分配bucket时使用，任务被执行时、取消时使用
         * 1. 被删除的timeout是中间节点
         * 2. 被删除的timeout是头结点
         * 3. 被删除的timeout是尾结点
         * 4. 既是头结点也是尾结点，删除后bucket数据为空
         * @param timeout
         * @return
         */
        public HashedWheelTimeout remove(HashedWheelTimeout timeout) {
            HashedWheelTimeout next = timeout.next;

            if (timeout.prev != null) {
                timeout.prev.next = next;
            }
            if (timeout.next != null) {
                timeout.next.prev = timeout.prev;
            }

            if (timeout == head) {
                if (timeout == tail) {
                    tail = null;
                    head = null;
                } else {
                    head = next;
                }
            } else if (timeout == tail) {
                tail = timeout.prev;
            }

            timeout.prev = null;
            timeout.next = null;
            timeout.bucket = null;
            // 这里移除了timeout之后整个timer待执行任务减少了
            timeout.timer.pendingTimeouts.decrementAndGet();
            return next;
        }

        /**
         * 清除bucket中所有未执行的任务
         */
        void clearTimeouts(Set<Timeout> set) {
            for (; ; ) {
                HashedWheelTimeout timeout = pollTimeout();
                if (timeout == null) {
                    return;
                }
                if (timeout.isExpired() || timeout.isCancelled()) {
                    continue;
                }
                set.add(timeout);
            }
        }

        /**
         * 取出第一个任务head, 维护队列指针
         * @return
         */
        private HashedWheelTimeout pollTimeout() {
            HashedWheelTimeout head = this.head;
            if (head == null) {
                return null;
            }
            HashedWheelTimeout next = head.next;
            if (next == null) {
                tail = this.head = null;
            } else {
                this.head = next;
                next.prev = null;
            }

            // null out prev and next to allow for GC.
            head.next = null;
            head.prev = null;
            head.bucket = null;
            return head;
        }
    }

    /**
     * 可以将此配置放在类初始化中，这个调用是十分频繁的，时间轮每次推进都会进行调用
     * 但操作系统类型在程序运行期间是不会变化的
     * @return
     */
    private boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.US).contains("win");
    }
}
