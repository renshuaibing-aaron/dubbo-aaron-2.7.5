/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.dubbo.rpc;

import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.extension.Adaptive;
import org.apache.dubbo.common.extension.SPI;

import static org.apache.dubbo.rpc.Constants.PROXY_KEY;

/**
 * ProxyFactory. (API/SPI, Singleton, ThreadSafe)
 * 代理工厂接口
 */
@SPI("javassist")
public interface ProxyFactory {

    /**
     * 使用端：consumer
     *
     * 创造一个代理，用于服务引用创建代理
     * @param invoker 会被proxy调用的第一层Invoker，默认是 MockClusterInvoker
     * @return proxy 代理对象
     */
    @Adaptive({PROXY_KEY})
    <T> T getProxy(Invoker<T> invoker) throws RpcException;

    /**
     * create proxy.
     *
     * @param invoker
     * @return proxy
     */
    @Adaptive({PROXY_KEY})
    <T> T getProxy(Invoker<T> invoker, boolean generic) throws RpcException;

    /**
     * 使用端：provider
     *
     * 创建一个Invoker，默认是代理Invoker -- AbstractProxyInvoker 的子类对象
     * @param <T> 接口 eg. com.alibaba.dubbo.demo.DemoService
     * @param proxy ref实例, eg. emoServiceImpl实例
     * @param type interface eg. com.alibaba.dubbo.demo.DemoService
     * @param url --
     *       injvm://127.0.0.1/com.alibaba.dubbo.demo.DemoService?anyhost=true...
     *       registry://127.0.0.1:2181/com.alibaba.dubbo.registry.RegistryService?application=demo-provider...&export=dubbo://10.213.11.98:20880/com.alibaba.dubbo.demo.DemoService?anyhost=true...
     * @return invoker，默认是代理Invoker -- AbstractProxyInvoker 的子类对象
     */
    @Adaptive({PROXY_KEY})
    <T> Invoker<T> getInvoker(T proxy, Class<T> type, URL url) throws RpcException;

}