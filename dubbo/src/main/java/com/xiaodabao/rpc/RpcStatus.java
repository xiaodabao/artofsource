package com.xiaodabao.rpc;

import com.xiaodabao.common.URL;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class RpcStatus {

    /**
     * RpcStatus最重要的两个全局变量 SERVICE_STATISTICS, METHOD_STATISTICS 存储Service/Method级别的Rpc状态数据
     * 使用ConcurrentHashMap保证并发安全
     */
    private static final ConcurrentMap<String, RpcStatus> SERVICE_STATISTICS = new ConcurrentHashMap<>();


    private static final ConcurrentMap<String, ConcurrentMap<String, RpcStatus>> METHOD_STATISTICS = new ConcurrentHashMap<>();


    private final ConcurrentMap<String, Object> values = new ConcurrentHashMap<>();

    /**
     * 当前正在进行的调用个数
     */
    private final AtomicInteger active = new AtomicInteger();

    /**
     * 总调用次数
     */
    private final AtomicLong total = new AtomicLong();

    /**
     * 调用失败次数
     */
    private final AtomicInteger failed = new AtomicInteger();

    /**
     * 所有调用总消耗时间
     */
    private final AtomicLong totalElapsed = new AtomicLong();

    /**
     * 调用失败消耗时间
     */
    private final AtomicLong failedElapsed = new AtomicLong();

    /**
     * 单次调用最长消耗时间
     */
    private final AtomicLong maxElapsed = new AtomicLong();

    /**
     * 单次调用失败最长消耗时间
     */
    private final AtomicLong failedMaxElapsed = new AtomicLong();

    /**
     * 调用成功最长消耗时间
     */
    private final AtomicLong succeededMaxElapsed = new AtomicLong();

    private RpcStatus() {

    }

    /**
     *
     * @param url
     * @return
     */
    public static RpcStatus getStatus(URL url) {
        String uri = url.toIdentityString();
        return SERVICE_STATISTICS.computeIfAbsent(uri, key -> new RpcStatus());
    }

    /**
     *
     * @param url
     */
    public static void removeStatus(URL url) {
        String uri = url.toIdentityString();
        SERVICE_STATISTICS.remove(uri);
    }

    /**
     * 获取指定方法的RpcStatus
     * 双层map， 第一层 url, 第二层 methodName
     * @param url
     * @param methodName
     * @return
     */
    public static RpcStatus getStatus(URL url, String methodName) {
        String uri = url.toIdentityString();
        ConcurrentMap<String, RpcStatus> map = METHOD_STATISTICS.computeIfAbsent(uri, k -> new ConcurrentHashMap<>());
        return map.computeIfAbsent(methodName, k -> new RpcStatus());
    }

    /**
     *
     * @param url
     * @param methodName
     */
    public static void removeStatus(URL url, String methodName) {
        String uri = url.toIdentityString();
        ConcurrentMap<String, RpcStatus> map = METHOD_STATISTICS.get(uri);
        if (map != null) {
            map.remove(methodName);
        }
    }


    public static void beginCount(URL url, String methodName) {
        beginCount(url, methodName, Integer.MAX_VALUE);
    }

    /**
     * 方法调用开始时，对接口、方法的当前正在调用数active进行自增
     * @param url
     * @param methodName
     * @param max
     * @return
     */
    public static boolean beginCount(URL url, String methodName, int max) {
        max = (max <= 0) ? Integer.MAX_VALUE : max;

        RpcStatus appStatus = getStatus(url);
        RpcStatus methodStatus = getStatus(url, methodName);

        if (methodStatus.active.get() == Integer.MAX_VALUE) {
            return false;
        }

        // 并发保证
        for (int i; ;) {
            i = methodStatus.active.get();
            if (i + 1 > max) {
                return false;
            }
            // methodStatus.active没有使用incrementAndGet的原因是为了进行i+1>max的判断
            if (methodStatus.active.compareAndSet(i, i + 1)) {
                break;
            }
        }
        appStatus.active.incrementAndGet();
        return true;
    }

    public static void endCount(URL url, String methodName, long elapsed, boolean succeeded) {
        endCount(getStatus(url), elapsed, succeeded);
        endCount(getStatus(url, methodName), elapsed, succeeded);
    }

    private static void endCount(RpcStatus status, long elapsed, boolean succeeded) {
        // 正在调用数减一，总调用次数加一
        status.active.decrementAndGet();
        status.total.incrementAndGet();

        // 维护totalElapsed，maxElapsed，succeededMaxElapsed，failedElapsed，failedMaxElapsed
        status.totalElapsed.addAndGet(elapsed);
        if (status.maxElapsed.get() < elapsed) {
            status.maxElapsed.set(elapsed);
        }
        if (succeeded) {
            if (status.succeededMaxElapsed.get() < elapsed) {
                status.succeededMaxElapsed.set(elapsed);
            }
        } else {
            status.failed.incrementAndGet();
            status.failedElapsed.addAndGet(elapsed);
            if (status.failedMaxElapsed.get() < elapsed) {
                status.failedMaxElapsed.set(elapsed);
            }
        }
    }


    public void set(String key, Object value) {
        values.put(key, value);
    }

    public Object get(String key) {
        return values.get(key);
    }

    /**
     * 获取active
     * @return
     */
    public int getActive() {
        return active.get();
    }

    /**
     * 获取总的调用次数
     * @return
     */
    public long getTotal() {
        return total.longValue();
    }


}
