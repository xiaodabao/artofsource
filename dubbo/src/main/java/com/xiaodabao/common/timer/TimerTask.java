package com.xiaodabao.common.timer;

/**
 * 任务接口
 */
public interface TimerTask {

    /**
     * 在指定的延迟时间后，执行任务
     *
     * @param timeout 与此任务关联的句柄
     */
    void run(Timeout timeout) throws Exception;
}
