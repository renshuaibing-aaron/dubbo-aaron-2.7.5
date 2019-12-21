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
package org.apache.dubbo.rpc.proxy;

import org.apache.dubbo.common.utils.ReflectUtils;
import org.apache.dubbo.rpc.Invoker;
import org.apache.dubbo.rpc.ProxyFactory;
import org.apache.dubbo.rpc.RpcException;
import org.apache.dubbo.rpc.service.Destroyable;
import org.apache.dubbo.rpc.service.GenericService;

import com.alibaba.dubbo.rpc.service.EchoService;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static org.apache.dubbo.common.constants.CommonConstants.COMMA_SPLIT_PATTERN;
import static org.apache.dubbo.rpc.Constants.INTERFACES;

/**
 * AbstractProxyFactory
 * 代理工厂模板类（封装了获取组装接口的功能，用于创建动态代理），提供了模板方法；
 */
public abstract class AbstractProxyFactory implements ProxyFactory {
    private static final Class<?>[] INTERNAL_INTERFACES = new Class<?>[]{
            EchoService.class, Destroyable.class
    };

    @Override
    public <T> T getProxy(Invoker<T> invoker) throws RpcException {
        return getProxy(invoker, false);
    }


    @Override
    public <T> T getProxy(Invoker<T> invoker, boolean generic) throws RpcException {
        // 获取url中interfaces配置的接口列表
        Set<Class<?>> interfaces = new HashSet<>();

        String config = invoker.getUrl().getParameter(INTERFACES);
        if (config != null && config.length() > 0) {
            String[] types = COMMA_SPLIT_PATTERN.split(config);
            if (types != null && types.length > 0) {
                for (int i = 0; i < types.length; i++) {
                    // TODO can we load successfully for a different classloader?.
                    // 反射加载接口类
                    interfaces.add(ReflectUtils.forName(types[i]));
                }
            }
        }

        // 为 http 和 hessian 协议提供泛化调用支持
        if (!GenericService.class.isAssignableFrom(invoker.getInterface()) && generic) {
            interfaces.add(com.alibaba.dubbo.rpc.service.GenericService.class);
        }

        interfaces.add(invoker.getInterface());
        interfaces.addAll(Arrays.asList(INTERNAL_INTERFACES));

        //2. 调用子类的实现去创建代理
        return getProxy(invoker, interfaces.toArray(new Class<?>[0]));
    }

    // 提供给子类的抽象方法
    public abstract <T> T getProxy(Invoker<T> invoker, Class<?>[] types);

}
