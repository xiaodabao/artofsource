package com.xiaodabao.buffer;

import com.xiaodabao.common.util.IllegalReferenceCountException;

public abstract class AbstractByteBuf extends ByteBuf {

    private static final boolean checkBounds;

    static final boolean checkAccessible;

    static {
        checkBounds = true;
        checkAccessible = true;
    }

    /**
     * 定义读写指针
     */
    int readerIndex;
    int writerIndex;

    /**
     * 定义标记指针, 可以重置, 恢复原来的index, 重新进行buffer的读写
     */
    private int markedReaderIndex;
    private int markedWriterIndex;

    private int maxCapacity;

    protected AbstractByteBuf(int maxCapacity) {
        checkPositiveOrZero(maxCapacity, "maxCapacity");
        this.maxCapacity = maxCapacity;
    }

    /**
     * 判断i必须是正整数
     * @param i
     * @param name
     * @return
     */
    public static int checkPositiveOrZero(int i, String name) {
        if (i < 0) {
            throw new IllegalArgumentException(name + ":" + "(expected: >=0");
        }
        return i;
    }

    @Override
    public boolean isReadOnly() {
        // 可读可写
        return false;
    }

    @Override
    public ByteBuf asReadOnly() {
        if (isReadOnly()) {
            return this;
        }
        return Unpooled.unmodifiableBuffer(this);
    }

    @Override
    public int maxCapacity() {
        return maxCapacity;
    }

    protected final void maxCapacity(int maxCapacity) {
        this.maxCapacity = maxCapacity;
    }

    /**
     * 检查readerIndex/writerIndex/capacity三者的关系
     * @param readerIndex
     * @param writerIndex
     * @param capacity
     */
    private static void checkIndexBounds(final int readerIndex, final int writerIndex, final int capacity) {
        if (readerIndex < 0 || readerIndex > writerIndex || writerIndex > capacity) {
            throw new IndexOutOfBoundsException(String.format(
                    "readerIndex: %d, writerIndex: %d (expected: 0 <= readerIndex <= writerIndex <= capacity(%d))",
                    readerIndex, writerIndex, capacity));
        }
    }

    /**
     * 获取/设置 readerIndex/writerIndex
     * @return
     */
    @Override
    public int readerIndex() {
        return readerIndex;
    }

    @Override
    public ByteBuf readerIndex(int readerIndex) {
        if (checkBounds) {
            checkIndexBounds(readerIndex, writerIndex, capacity());
        }
        this.readerIndex = readerIndex;
        return this;
    }

    @Override
    public int writerIndex() {
        return writerIndex;
    }

    @Override
    public ByteBuf writerIndex(int writerIndex) {
        if (checkBounds) {
            checkIndexBounds(readerIndex, writerIndex, capacity());
        }
        this.writerIndex = writerIndex;
        return this;
    }

    @Override
    public ByteBuf setIndex(int readerIndex, int writerIndex) {
        if (checkBounds) {
            checkIndexBounds(readerIndex, writerIndex, capacity());
        }
        setIndex0(readerIndex, writerIndex);
        return this;
    }

    final void setIndex0(int readerIndex, int writerIndex) {
        this.readerIndex = readerIndex;
        this.writerIndex = writerIndex;
    }

    @Override
    public ByteBuf clear() {
        // 清空buf, 只是将读写指针设置为0，并不真正的清除数据
        readerIndex = writerIndex = 0;
        return this;
    }

    @Override
    public boolean isReadable() {
        // 可读 意味着写的数据比已读的数据多
        return writerIndex > readerIndex;
    }

    @Override
    public boolean isReadable(int numBytes) {
        // 想要读出numBytes个字节, 写指针比读指针多numBytes个字节
        return writerIndex - readerIndex >= numBytes;
    }

    @Override
    public boolean isWritable() {
        // 想要可写, 当前容量必须大于写指针
        return capacity() > writerIndex;
    }

    @Override
    public boolean isWritable(int numBytes) {
        return capacity() - writerIndex >= numBytes;
    }

    @Override
    public int readableBytes() {
        // 可读的字节数 [readerIndex, writerIndex) 为可读区间
        return writerIndex - readerIndex;
    }

    @Override
    public int writableBytes() {
        // 可写的字节数 [writerIndex, capacity) 为可写区间
        return capacity() - writerIndex;
    }

    @Override
    public int maxWritableBytes() {
        // 最大可写字节数 需要由capacity扩容至maxCapacity才行
        return maxCapacity() - writerIndex;
    }

    @Override
    public ByteBuf markReaderIndex() {
        // 标记当前的readerIndex
        markedReaderIndex = readerIndex;
        return this;
    }

    @Override
    public ByteBuf resetReaderIndex() {
        // 将readerIndex 恢复为之前标记的markedReaderIndex
        readerIndex(markedReaderIndex);
        return this;
    }

    /**
     * markedWrite 同 markedRead
     * @return
     */
    @Override
    public ByteBuf markWriterIndex() {
        markedWriterIndex = writerIndex;
        return this;
    }

    @Override
    public ByteBuf resetWriterIndex() {
        writerIndex(markedWriterIndex);
        return this;
    }

    /**
     * 将已读取的数据清除, 通过复制未读的数据至 [0, writerIndex - readerIndex]实现
     * 这样释放了空间, 可写的空间变大了
     * @return
     */
    @Override
    public ByteBuf discardReadBytes() {
        if (readerIndex == 0) {
            ensureAccessible();
            return this;
        }

        if (readerIndex != writerIndex) {
            setBytes(0, this, readerIndex, writerIndex - readerIndex);
            writerIndex -= readerIndex;
            adjustMarkers(readerIndex);
            readerIndex = 0;
        } else {
            ensureAccessible();
            adjustMarkers(readerIndex);
            writerIndex = readerIndex = 0;
        }
        return this;
    }

    /**
     * 清除已读数据的时候必须满足两个条件
     * 1. 全部数据已读
     * 2. readerIndex大于等于容量的一半
     * @return
     */
    @Override
    public ByteBuf discardSomeReadBytes() {
        if (readerIndex > 0) {
            if (readerIndex == writerIndex) {
                ensureAccessible();
                adjustMarkers(readerIndex);
                writerIndex = readerIndex = 0;
                return this;
            }

            if (readerIndex >= capacity() >>> 1) {
                setBytes(0, this, readerIndex, writerIndex - readerIndex);
                writerIndex -= readerIndex;
                adjustMarkers(readerIndex);
                readerIndex = 0;
                return this;
            }
        }
        ensureAccessible();
        return this;
    }


    protected final void ensureAccessible() {
        if (checkAccessible && !isAccessible()) {
            throw new IllegalReferenceCountException(0);
        }
    }

    /**
     * 调整markedReaderIndex和markedWriterIndex
     * 逻辑虽然写的没错, 但是判断复杂, 最根本的思想其实是在 0 和 markedIndex - decrement 之间选一个大值
     * 改写一下此方法, 见adjustMarkers2
     * @param decrement
     */
    protected final void adjustMarkers(int decrement) {
        int markedReaderIndex = this.markedReaderIndex;
        if (markedReaderIndex <= decrement) {
            this.markedReaderIndex = 0;
            int markedWriterIndex = this.markedWriterIndex;
            if (markedWriterIndex <= decrement) {
                this.markedWriterIndex = 0;
            } else {
                this.markedWriterIndex = markedWriterIndex - decrement;
            }
        } else {
            this.markedReaderIndex = markedReaderIndex - decrement;
            this.markedWriterIndex -= decrement;
        }
    }

    /**
     * 逻辑清晰, 目的就是调整时别让markedIndex成为非法的值
     * @param decrement
     */
    protected final void adjustMarkers2(int decrement) {
        this.markedReaderIndex = Math.max(0, this.markedReaderIndex - decrement);
        this.markedWriterIndex = Math.max(0, this.markedWriterIndex - decrement);
    }

    @Override
    public ByteBuf ensureWritable(int minWritableBytes) {
        ensureWritable0(checkPositiveOrZero(minWritableBytes, "minWritableBytes"));
        return this;
    }

    final void ensureWritable0(int minWritableBytes) {
        final int writerIndex = writerIndex();
        final int targetCapacity = writerIndex + minWritableBytes;
        // 不超过capacity的话, 什么事也不会发生, 满足写入minWritableBytes的条件
        if (targetCapacity >= 0 && targetCapacity <= capacity()) {
            ensureAccessible();
            return;
        }
        // 超出maxCapacity的话, 直接抛异常就行了, 扩容都放不下
        if (checkBounds && (targetCapacity < 0 || targetCapacity > maxCapacity)) {
            ensureAccessible();
            throw new IndexOutOfBoundsException(String.format(
                    "writerIndex(%d) + minWritableBytes(%d) exceeds maxCapacity(%d): %s",
                    writerIndex, minWritableBytes, maxCapacity, this));
        }

        // 标准化可写字节数 2的幂次方
        final int fastWritable = maxFastWritableBytes();
        // 使内存的分配尽量标准化, 最好是扩展2的幂次方
        int newCapacity = fastWritable >= minWritableBytes ? writerIndex + fastWritable
                : alloc().calculateNewCapacity(targetCapacity, maxCapacity);
        capacity(newCapacity);
    }

    /**
     * 注意返回值
     * 0：表示目前的容量就可以正常写入, 不会扩容
     * 1: 表示在无法写入minWritableBytes的情况下 force为false(不强制扩容) 或当前容量已达到最大
     * 2. 可以写入minWritableBytes, 但需要扩容
     * 3. 无法写入minWritableBytes， 但还是强制扩容到maxCapacity
     * @param minWritableBytes
     * @param force
     * @return
     */
    public int ensureWritable(int minWritableBytes, boolean force) {
        ensureAccessible();
        checkPositiveOrZero(minWritableBytes, "minWritableBytes");
        // 这又和ensureWritable0写的逻辑不一样, 难道是俩人写的……
        if (minWritableBytes <= writableBytes()) {
            return 0;
        }

        final int maxCapacity = maxCapacity();
        final int writerIndex = writerIndex();
        if (minWritableBytes > maxCapacity - writerIndex) {
            if (!force || capacity() == maxCapacity) {
                return 1;
            }

            capacity(maxCapacity);
            return 3;
        }

        int fastWritable = maxFastWritableBytes();
        int newCapacity = fastWritable >= minWritableBytes ? writerIndex + fastWritable
                : alloc().calculateNewCapacity(writerIndex + minWritableBytes, maxCapacity);
        capacity(newCapacity);
        return 2;
    }

    // 下面是各种数据类型的get方法, 都是和具体实现有关 directBuffer heapBuffer
    @Override
    public byte getByte(int index) {
        checkIndex(index);
        return _getByte(index);
    }

    protected abstract byte _getByte(int index);






    protected final void checkIndex(int index) {
        checkIndex(index, 1);
    }

    protected final void checkIndex(int index, int fieldLength) {
        ensureAccessible();
        checkIndex0(index, fieldLength);
    }

    protected void checkIndex0(int index, int fieldLength) {
        if (checkBounds) {
            checkRangeBounds("index", index, fieldLength, capacity());
        }
    }

    private static void checkRangeBounds(final String indexName, final int index, final int fieldLength, final int capacity) {
        if (isOutOfBounds(index, fieldLength, capacity)) {
            throw new IndexOutOfBoundsException(String.format(
                    "%s: %d, length: %d (expected: range(0, %d))", indexName, index, fieldLength, capacity));
        }
    }

    /**
     * index/length/index+length/capacity 小于index + length 任一个小于0, 都会被认为越界
     * @param index
     * @param length
     * @param capacity
     * @return
     */
    public static boolean isOutOfBounds(int index, int length, int capacity) {
        return (index | length | (index + length) | (capacity - (index + length))) < 0;
    }
}
