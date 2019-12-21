/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.dubbo.demo.provider;

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

        ServiceConfig<DemoServiceImpl> service = new ServiceConfig<>();
        service.setInterface(DemoService.class);
        service.setRef(new DemoServiceImpl());


        ProtocolConfig protocolConfig=new ProtocolConfig();
        protocolConfig.setName("dubbo");
        protocolConfig.setPort(20881);

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
                .service(service)
                .start()
                .await();
    }
}
