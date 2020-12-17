package com.xiaodabao.cluster.loadbalance;

import com.xiaodabao.common.URL;
import com.xiaodabao.rpc.Invocation;
import com.xiaodabao.rpc.Invoker;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 负载均衡实现：基于权重的轮询
 */
public class RoundRobinLoadBalance extends AbstractLoadBalance {

    public static final String NAME = "roundrobin";

    private static final int RECYCLE_PERIOD = 60000;

    private ConcurrentMap<String, ConcurrentMap<String, WeightedRoundRobin>> methodWeightMap = new ConcurrentHashMap<>();

    @Override
    protected <T> Invoker<T> doSelect(List<Invoker<T>> invokers, URL url, Invocation invocation) {

        String key = invokers.get(0).getUrl().getServiceKey() + "." + invocation.getMethodName();
        ConcurrentMap<String, WeightedRoundRobin> map = methodWeightMap.computeIfAbsent(key, k -> new ConcurrentHashMap<>());
        int totalWeight = 0;
        long maxCurrent = Long.MIN_VALUE;
        long now = System.currentTimeMillis();
        Invoker<T> selectedInvoker = null;
        WeightedRoundRobin selectedWRR = null;

        // 选出当前权重最大的invoker，同时计算全部invoker的权重和
        for (Invoker<T> invoker : invokers) {
            String identifyString = invoker.getUrl().toIdentityString();
            int weight = getWeight(invoker, invocation);
            WeightedRoundRobin weightedRoundRobin = map.computeIfAbsent(identifyString, k -> {
                WeightedRoundRobin wrr = new WeightedRoundRobin();
                wrr.setWeight(weight);
                return wrr;
            });

            // 因为dubbo服务的预热机制, 导致服务刚启动时计算出的权重会不断变化
            if (weight != weightedRoundRobin.getWeight()) {
                weightedRoundRobin.setWeight(weight);
            }

            // 每经过一次选择, WeightedRoundRobin的current值都会增加weight，不断提升优先级
            long cur = weightedRoundRobin.increaseCurrent();
            weightedRoundRobin.setLastUpdate(now);

            if (cur > maxCurrent) {
                maxCurrent = cur;
                selectedInvoker = invoker;
                selectedWRR = weightedRoundRobin;
            }
            totalWeight += weight;
        }

        if (invokers.size() != map.size()) {
            map.entrySet().removeIf(item -> now - item.getValue().getLastUpdate() > RECYCLE_PERIOD);
        }

        if (selectedInvoker != null) {
            // 被选中的invoker, current减掉所有invoker的权重之和, 这样优先级被降到了最低
            selectedWRR.sel(totalWeight);
            return selectedInvoker;
        }

        return invokers.get(0);
    }

    /**
     * 权重类, 维护生产者（方法级别）权重（1. 本身权重weight 2. 不断变化的权重current)
     */
    protected static class WeightedRoundRobin {
        private int weight;
        private AtomicLong current = new AtomicLong(0);
        private long lastUpdate;

        public int getWeight() {
            return weight;
        }

        public void setWeight(int weight) {
            this.weight = weight;
            current.set(0);
        }

        /**
         * 消费者每一次调用, 若当前生产者未被选中，则current增加weight
         * 随着调用的不断进行，权重不断提高
         * @return
         */
        public long increaseCurrent() {
            return current.addAndGet(weight);
        }

        /**
         * 当前生产者被选中，则current降低total权重
         * @param total
         */
        public void sel(int total) {
            current.addAndGet(-1 * total);
        }

        public long getLastUpdate() {
            return lastUpdate;
        }

        public void setLastUpdate(long lastUpdate) {
            this.lastUpdate = lastUpdate;
        }
    }

}
