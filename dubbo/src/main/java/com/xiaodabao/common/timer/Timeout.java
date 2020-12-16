package com.xiaodabao.common.timer;

/**
 *
 */
public interface Timeout {

    /**
     * 返回创建此句柄的时间轮
     */
    Timer timer();

    /**
     * 返回与此句柄关联的任务
     */
    TimerTask task();

    /**
     * 当且仅当关联的任务过期后，返回true
     */
    boolean isExpired();

    /**
     * 当且仅当关联的任务被取消后，返回true
     */
    boolean isCancelled();

    /**
     * 试图取消关联的任务，如果任务已执行或已取消，则不做任何操作
     * @return 只有在取消成功的情况下返回true
     */
    boolean cancel();
}
