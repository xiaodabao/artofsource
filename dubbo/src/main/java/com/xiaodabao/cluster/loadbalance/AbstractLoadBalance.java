package com.xiaodabao.cluster.loadbalance;

import com.xiaodabao.cluster.LoadBalance;
import com.xiaodabao.common.URL;
import com.xiaodabao.rpc.Invocation;
import com.xiaodabao.rpc.Invoker;
import com.xiaodabao.rpc.RpcException;

import java.util.List;

/**
 * 负载均衡抽象类
 */
public abstract class AbstractLoadBalance implements LoadBalance {

    @Override
    public <T> Invoker<T> select(List<Invoker<T>> invokers, URL url, Invocation invocation) throws RpcException {
        if (invokers == null || invokers.isEmpty()) {
            return null;
        }

        // 只有一个，没得选
        if (invokers.size() == 1) {
            return invokers.get(0);
        }

        return doSelect(invokers, url, invocation);
    }

    /**
     * 设计模式-模板方法  doXXX 类似于Spring，真正实现选择逻辑的方法
     * @param invokers
     * @param url
     * @param invocation
     * @param <T>
     * @return
     */
    protected abstract <T> Invoker<T> doSelect(List<Invoker<T>> invokers, URL url, Invocation invocation);


    int getWeight(Invoker<?> invoker, Invocation invocation) {
        int weight;

        URL url = invoker.getUrl();

        return 1;
    }

}
