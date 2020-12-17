package com.xiaodabao.rpc;

import com.xiaodabao.common.Node;

/**
 * 调用者
 * @param <T>
 */
public interface Invoker<T> extends Node {

    /**
     * get service interface
     * @return
     */
    Class<T> getInterface();

    /**
     * invoke.
     * @param invocation
     * @return
     * @throws RpcException
     */
    Result invoke(Invocation invocation) throws RpcException;
}
