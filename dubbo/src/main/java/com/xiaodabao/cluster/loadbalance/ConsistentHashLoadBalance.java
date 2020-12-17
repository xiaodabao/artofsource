package com.xiaodabao.cluster.loadbalance;

import com.xiaodabao.common.URL;
import com.xiaodabao.rpc.Invocation;
import com.xiaodabao.rpc.Invoker;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.regex.Pattern;

/**
 * 负载均衡实现：一致性Hash
 */
public class ConsistentHashLoadBalance extends AbstractLoadBalance {

    public static final String NAME = "consistenthash";

    public static final String HASH_NODES = "hash.nodes";

    public static final String HASH_ARGUMENTS = "hash.arguments";

    static final Pattern COMMA_SPLIT_PATTERN = Pattern.compile("\\s*[,]+\\s*");

    private final ConcurrentMap<String, ConsistentHashSelector<?>> selectors = new ConcurrentHashMap<>();


    @Override
    protected <T> Invoker<T> doSelect(List<Invoker<T>> invokers, URL url, Invocation invocation) {
        String methodName = invocation.getMethodName();
        String key = invokers.get(0).getUrl().getServiceKey() + "." + methodName;
        // 每次都计算invokers的hash值, 一旦发生变化说明生产者列表变了, 需要重新进行hash
        int invokersHashCode = invokers.hashCode();
        ConsistentHashSelector<T> selector = (ConsistentHashSelector<T>) selectors.get(key);
        if (selector == null || selector.identityHashCode != invokersHashCode) {
            selectors.put(key, new ConsistentHashSelector<T>(invokers, methodName, invokersHashCode));
            selector = (ConsistentHashSelector<T>) selectors.get(key);
        }
        return selector.select(invocation);
    }


    private static final class ConsistentHashSelector<T> {
        private final TreeMap<Long, Invoker<T>> virtualInvokers;

        private final int replicaNumber;

        private final int identityHashCode;

        private final int[] argumentIndex;

        /**
         * 初始化ConsistentHashSelector 将invoker映射为虚拟节点
         * 这样做的好处是使hash环中的节点增加, 分布更加均匀, 最终使请求分布更加均匀
         * @param invokers
         * @param methodName
         * @param identityHashCode
         */
        ConsistentHashSelector(List<Invoker<T>> invokers, String methodName, int identityHashCode) {
            this.virtualInvokers = new TreeMap<>();
            this.identityHashCode = identityHashCode;
            URL url = invokers.get(0).getUrl();
            // 这里不再使用权重作为选择的控制手段了, 一致性hash中使用HASH_NODES的数量控制生产者的调用次数
            this.replicaNumber = url.getMethodParameter(methodName, HASH_NODES, 160);
            String[] index = COMMA_SPLIT_PATTERN.split(url.getMethodParameter(methodName, HASH_ARGUMENTS, "0"));
            argumentIndex = new int[index.length];
            for (int i = 0; i < index.length; i++) {
                argumentIndex[i] = Integer.parseInt(index[i]);
            }

            // 对于每个invoker, 都虚拟出 replicaNumber / 4 * 4 个虚拟节点存在treeMap中
            for (Invoker<T> invoker : invokers) {
                String address = invoker.getUrl().getAddress();
                for (int i = 0; i < replicaNumber / 4; i++) {
                    byte[] digest = md5(address + i);
                    for (int h = 0; h < 4; h++) {
                        long m = hash(digest, h);
                        virtualInvokers.put(m, invoker);
                    }
                }
            }
        }


        public Invoker<T> select(Invocation invocation) {
            String key = toKey(invocation.getArguments());
            byte[] digest = md5(key);
            return selectForKey(hash(digest, 0));
        }

        /**
         * 保证相同的参数调用到同一个生产者
         * 这里应该注意一个问题, 参数值应该重写toString()方法
         * @param args
         * @return
         */
        private String toKey(Object[] args) {
            StringBuilder buf = new StringBuilder();
            for (int i : argumentIndex) {
                if (i >= 0 && i < args.length) {
                    buf.append(args[i]);
                }
            }
            return buf.toString();
        }

        /**
         * 根据hash值选择一个虚拟节点, 最终返回真实的生产者
         * @param hash
         * @return
         */
        private Invoker<T> selectForKey(long hash) {
            // 向上取整, 未取到, 则应该取第一个虚拟节点
            Map.Entry<Long, Invoker<T>> entry = virtualInvokers.ceilingEntry(hash);
            if (entry == null) {
                entry = virtualInvokers.firstEntry();
            }
            return entry.getValue();
        }

        /**
         * 计算hash
         * @param digest
         * @param number
         * @return
         */
        private long hash(byte[] digest, int number) {
            return (((long) (digest[3 + number * 4] & 0xFF) << 24)
                    | ((long) (digest[2 + number * 4] & 0xFF) << 16)
                    | ((long) (digest[1 + number * 4] & 0xFF) << 8)
                    | (digest[number * 4] & 0xFF))
                    & 0xFFFFFFFFL;
        }

        /**
         * 计算md5值
         * @param value
         * @return
         */
        private byte[] md5(String value) {
            MessageDigest md5;
            try {
                md5 = MessageDigest.getInstance("MD5");
            } catch (NoSuchAlgorithmException e) {
                throw new IllegalStateException(e.getMessage(), e);
            }
            md5.reset();
            byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
            md5.update(bytes);
            return md5.digest();
        }
    }
}
