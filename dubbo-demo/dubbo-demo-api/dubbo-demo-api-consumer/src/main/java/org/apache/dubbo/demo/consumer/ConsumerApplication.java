package org.apache.dubbo.demo.consumer;

import org.apache.dubbo.config.ApplicationConfig;
import org.apache.dubbo.config.ReferenceConfig;
import org.apache.dubbo.config.RegistryConfig;
import org.apache.dubbo.config.bootstrap.DubboBootstrap;
import org.apache.dubbo.demo.DemoService;

/**
 * @author renshuaibing
 */
public class ConsumerApplication {

    /**
     * 配置加载
     * 创建invoker
     * 创建服务接口代理类
     *
     * @param args
     */
    public static void main(String[] args) {
        ReferenceConfig<DemoService> reference = new ReferenceConfig<>();
        reference.setInterface(DemoService.class);
        reference.setLoadbalance("roundrobin");
        reference.setScope("remote");
        reference.setProxy("javassist");


        RegistryConfig registryConfig = new RegistryConfig("zookeeper://127.0.0.1:2181");

        DubboBootstrap bootstrap = DubboBootstrap.getInstance();
        bootstrap
                .application(new ApplicationConfig("dubbo-demo-api-consumer"))
                .registry(registryConfig)
                .reference(reference)
                .start();


        DemoService demoService = reference.get();
        //利用缓存
        // DemoService demoService = ReferenceConfigCache.getCache().get(reference);


        //利用上一行代码的代理对象执行 方法
        //proxy0.sayHello(String paramString)
        String message = demoService.sayHello("dubbo");


        System.out.println("===========获取信息=================");
        System.out.println(message);
    }
    /**
     * 在服务引用过程中需要进行配置检查和信息收集工作，根据收集到的信息决定服务引用的具体方式，服务引用有以下三种方式：
     * 1、引用本地 (JVM) 服务
     * 2、直连方式引用远程服务（不经过注册中心）
     * 3、通过注册中心引用远程服务
     * 上述无论哪种方式引用服务，都需要通过 Invoker 实例载体进行。如果有多个注册中心和多个服务提供者，
     * 这时候会得到一组 Invoker 实例（Invoker 列表），此时通过集群管理类 Cluster 将多个 Invoker 合并成一个实例。
     * 合并后的 Invoker 实例已经具备调用本地或远程服务的能力了，但此时还不能暴露给用户使用，不然会对用户业务代码造成侵入。
     * 此时框架还需要通过代理工厂类 (ProxyFactory) 为服务接口生成代理类，并让代理类去调用 Invoker 逻辑。
     * 这样避免了 Dubbo 框架代码对业务代码的侵入，同时也让框架更容易使用
     *
     */
}
