package com.xiaodabao.common.util;

/**
 * 对象的引用计数
 */
public interface ReferenceCounted {

    /**
     * 对象的引用次数
     * @return
     */
    int refCnt();

    /**
     * 此操作会增加1次引用次数
     * @return
     */
    ReferenceCounted retain();

    /**
     * 增加指定数量的引用次数
     * @param increment
     * @return
     */
    ReferenceCounted retain(int increment);


    ReferenceCounted touch();


    ReferenceCounted touch(Object hint);

    /**
     * 减少一次引用次数
     * 当且仅当引用次数为0时，释放对象
     * @return
     */
    boolean release();

    /**
     * 减少decrement次引用次数
     * 当且仅当引用次数为0时，释放对象
     * @return
     */
    boolean release(int decrement);
}
