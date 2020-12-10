package org.apache.dubbo.provider;

import org.apache.dubbo.config.ApplicationConfig;
import org.apache.dubbo.config.ProtocolConfig;
import org.apache.dubbo.config.RegistryConfig;
import org.apache.dubbo.config.ServiceConfig;
import org.apache.dubbo.config.bootstrap.DubboBootstrap;
import org.apache.dubbo.demo.DemoService;

/**
 * @author renshuaibing
 */
public class ProviderApplication {
    public static void main(String[] args) throws Exception {

        ServiceConfig<DemoServiceImpl> serviceConfig = new ServiceConfig<>();
        serviceConfig.setInterface(DemoService.class);
        serviceConfig.setRef(new DemoServiceImpl());
        serviceConfig.setLoadbalance("consistenthash");
        serviceConfig.setScope("remote");


        ProtocolConfig protocolConfig=new ProtocolConfig();
        protocolConfig.setName("dubbo");
        protocolConfig.setPort(20882);

        DubboBootstrap bootstrap = DubboBootstrap.getInstance();


        RegistryConfig registryConfig = new RegistryConfig("127.0.0.1:2181");
        registryConfig.setRegister(false);
        RegistryConfig registryConfigzk = new RegistryConfig("zookeeper://127.0.0.1:2181");


        /**
         * 1.首先 ServiceConfig 类拿到对外提供服务的实际类 ref(如：DemoServiceImpl)；
         * 2.然后通过 JavassistProxyFactory 类的 getInvoker 方法将 ref 封装到一个 AbstractProxyInvoker 实例；
         * 3.最后通过 InjvmProtocol（本地暴露）和 DubboProtocol（远程暴露）
         * 将AbstractProxyInvoker 实例转换成 InjvmExporter 和 DubboExporter。
         */
        bootstrap
                .application(new ApplicationConfig("dubbo-demo-api-provider"))
                .registry(registryConfigzk)
               //.registry(registryConfig)
                .protocol(protocolConfig)
                .service(serviceConfig)
                .start()
                .await();
    }
}
