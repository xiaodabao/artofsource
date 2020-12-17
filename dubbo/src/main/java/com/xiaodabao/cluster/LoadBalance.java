package com.xiaodabao.cluster;

import com.xiaodabao.common.URL;
import com.xiaodabao.rpc.Invocation;
import com.xiaodabao.rpc.Invoker;
import com.xiaodabao.rpc.RpcException;

import java.util.List;

/**
 * 负载均衡接口
 */
public interface LoadBalance {

    /**
     * 选择Invoker
     * @param invokers
     * @param url
     * @param invocation
     * @param <T>
     * @return
     * @throws RpcException
     */
    <T> Invoker<T> select(List<Invoker<T>> invokers, URL url, Invocation invocation) throws RpcException;
}
