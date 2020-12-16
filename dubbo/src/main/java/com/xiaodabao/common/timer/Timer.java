package com.xiaodabao.common.timer;

import java.util.Set;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

/**
 * dubbo时间轮接口
 */
public interface Timer {

    /**
     * 在指定的延迟时间{delay}之后执行任务（TimerTask)
     *
     * @return 返回一个与TimerTask相关联的句柄
     * @throws IllegalStateException 当时间轮停止之后, 再新增任务, 抛出状态非法异常
     * @throws RejectedExecutionException 等待执行的任务过多, 拒绝执行新任务
     */
    Timeout newTimeout(TimerTask task, long delay, TimeUnit unit);

    /**
     * 释放所有资源并取消所有尚未执行的任务
     *
     * @return 返回被取消的任务句柄(Timeout)集合
     */
    Set<Timeout> stop();

    /**
     * 判断时间轮是否已停止
     *
     * @return true for stop
     */
    boolean isStop();
}
