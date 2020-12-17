package com.xiaodabao.cluster.loadbalance;

import com.xiaodabao.common.URL;
import com.xiaodabao.rpc.Invocation;
import com.xiaodabao.rpc.Invoker;

import java.util.List;
import java.util.Random;

/**
 * 随机负载均衡
 * 名字虽然是随机，实际上是根据每个Invoker的权重相关，权重高，选择到的几率更大
 */
public class RandomLoadBalance extends AbstractLoadBalance {

    /**
     * 用作dubbo SPI的名字
     */
    public static final String NAME = "random";

    /**
     * 程序能走到这一步，肯定有两个或以上的Invoker
     * @param invokers
     * @param url
     * @param invocation
     * @param <T>
     * @return
     */
    @Override
    protected <T> Invoker<T> doSelect(List<Invoker<T>> invokers, URL url, Invocation invocation) {

        int length = invokers.size();

        // 这个标记用来判断所有Invoker的权重是否一样, 如果一样的话，直接随机就可以了
        boolean sameWeight = true;

        // 存储所有invoker的权重值
        int[] weights = new int[length];

        int firstWeight = getWeight(invokers.get(0), invocation);
        weights[0] = firstWeight;

        // 用来计算所有权重的和
        int totalWeight = firstWeight;

        // 计算所有invoker权重之和，外加判断权重是否都相同
        for (int i = 1; i < length; i++) {
            int weight = getWeight(invokers.get(i), invocation);
            weights[i] = weight;
            totalWeight += weight;

            if (sameWeight && weight != firstWeight) {
                sameWeight = false;
            }
        }

        // 所有invoker的权重相同和不同分别不同的处理
        if (totalWeight > 0 && !sameWeight) {
            // dubbo 使用的ThreadLocalRandom 这里用java的Random代替
            int offset = new Random().nextInt(totalWeight);

            // 这里的算法有点意思, 本质上还是去找随机出来的offset属于哪个区间
            // 只是这里的区间只是一个数值, 使用减的方式计算而已
            for (int i = 0; i < length; i++) {
                offset -= weights[i];
                if (offset < 0) {
                    return invokers.get(i);
                }
            }
        }

        return invokers.get(new Random().nextInt(length));
    }
}
