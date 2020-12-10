package org.apache.dubbo.rpc.cluster.loadbalance;

import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.utils.CollectionUtils;
import org.apache.dubbo.rpc.Invocation;
import org.apache.dubbo.rpc.Invoker;
import org.apache.dubbo.rpc.cluster.LoadBalance;

import java.util.List;

import static org.apache.dubbo.common.constants.CommonConstants.TIMESTAMP_KEY;
import static org.apache.dubbo.common.constants.RegistryConstants.REGISTRY_KEY;
import static org.apache.dubbo.rpc.cluster.Constants.DEFAULT_WARMUP;
import static org.apache.dubbo.rpc.cluster.Constants.DEFAULT_WEIGHT;
import static org.apache.dubbo.rpc.cluster.Constants.WARMUP_KEY;
import static org.apache.dubbo.rpc.cluster.Constants.WEIGHT_KEY;

/**
 * AbstractLoadBalance
 * 负载均衡模板基类
 * 1）、select 方法中调用具体实现类的 doSelect 方法选择 invoker
 * 2）、getWeight 方法中计算权重
 */
public abstract class AbstractLoadBalance implements LoadBalance {
    /**
     * Calculate the weight according to the uptime proportion of warmup time
     * the new weight will be within 1(inclusive) to weight(inclusive)
     *
     * @param uptime the uptime in milliseconds
     * @param warmup the warmup time in milliseconds
     * @param weight the weight of an invoker
     * @return weight which takes warmup into account
     */
    static int calculateWarmupWeight(int uptime, int warmup, int weight) {
        // 计算权重，下面代码逻辑上等同于 (uptime / warmup) * weight
        // 随着 provider 运行时间 uptime 的增大，权重计算值 ww 会慢慢接近配置值 weight
        int ww = (int) ( uptime / ((float) warmup / weight));
        return ww < 1 ? 1 : (Math.min(ww, weight));
    }

    @Override
    public <T> Invoker<T> select(List<Invoker<T>> invokers, URL url, Invocation invocation) {
        if (CollectionUtils.isEmpty(invokers)) {
            return null;
        }
        // 1. 如果只有一个 Invoker，直接返回
        if (invokers.size() == 1) {
            return invokers.get(0);
        }
        // 2. 调用子类进行选择
        return doSelect(invokers, url, invocation);
    }

    //子类重写的方法：真正选择 Invoker(filtered) 的方法
    protected abstract <T> Invoker<T> doSelect(List<Invoker<T>> invokers, URL url, Invocation invocation);


    /**
     * Get the weight of the invoker's invocation which takes warmup time into account
     * if the uptime is within the warmup time, the weight will be reduce proportionally
     *在计算权重的过程中，主要保证了当 provider 运行时长小于预热时间时，对 provider 服务降权，
     * 避免该 provider 在启动之初就处于高负载状态。服务预热是一个优化手段，与此类似的还有 JVM 预热。
     * 主要目的是让服务启动后“低功率”运行一段时间，使其效率慢慢提升至最佳状态
     * @param invoker    the invoker
     * @param invocation the invocation of this invoker
     * @return weight
     */
    /**
     * 获取一个 Invoker(filtered) 的权重
     * 1、获取当前Invoker设置的权重weight和预热时间warmup，并且计算启动至今时间uptime
     * 2、如果uptime<warmup，则重新计算当前Invoker的weight（uptime/warmup*weight），否则直接返回设置的weight
     */
    int getWeight(Invoker<?> invoker, Invocation invocation) {
        int weight = 0;
        URL url = invoker.getUrl();
        // Multiple registry scenario, load balance among multiple registries.
        if (url.getServiceInterface().equals("org.apache.dubbo.registry.RegistryService")) {
            weight = url.getParameter(REGISTRY_KEY + "." + WEIGHT_KEY, DEFAULT_WEIGHT);
        } else {
            // 从 URL 中获取权重 weight 配置值，默认是 100
            weight = url.getMethodParameter(invocation.getMethodName(), WEIGHT_KEY, DEFAULT_WEIGHT);
            if (weight > 0) {


                long timestamp = invoker.getUrl().getParameter(TIMESTAMP_KEY, 0L);

                if (timestamp > 0L) {
                    // 计算 provider 运行时长
                    long uptime = System.currentTimeMillis() - timestamp;
                    if (uptime < 0) {
                        return 1;
                    }
                    // 获取服务预热时间，默认为10分钟
                    int warmup = invoker.getUrl().getParameter(WARMUP_KEY, DEFAULT_WARMUP);
                    // 如果 provider 运行时间小于预热时间，则重新计算服务权重，即降权
                    if (uptime > 0 && uptime < warmup) {
                        // 计算权重
                        weight = calculateWarmupWeight((int)uptime, warmup, weight);
                    }
                }
            }
        }
        // 权重大于等于0时，直接返回该值，否则返回 0
        return Math.max(weight, 0);
    }
}
