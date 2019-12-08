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

import org.apache.dubbo.common.logger.Logger;
import org.apache.dubbo.common.logger.LoggerFactory;
import org.apache.dubbo.rpc.Invoker;
import org.apache.dubbo.rpc.RpcInvocation;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;

/**
 * InvokerHandler
 */
public class InvokerInvocationHandler implements InvocationHandler {
    private static final Logger logger = LoggerFactory.getLogger(InvokerInvocationHandler.class);
    private final Invoker<?> invoker;

    public InvokerInvocationHandler(Invoker<?> handler) {
        this.invoker = handler;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {

        System.out.println("=======InvokerInvocationHandler#invoke===============");
        // 获得方法名称
        String methodName = method.getName();

        // 获得方法参数类型
        Class<?>[] parameterTypes = method.getParameterTypes();

        // 如果该方法所在的类是Object类型，则直接调用invoke
        if (method.getDeclaringClass() == Object.class) {
            return method.invoke(invoker, args);
        }
        // 如果这个方法是toString，则直接调用invoker.toString()
        if ("toString".equals(methodName) && parameterTypes.length == 0) {
            return invoker.toString();
        }

        // 如果这个方法是hashCode直接调用invoker.hashCode()
        if ("hashCode".equals(methodName) && parameterTypes.length == 0) {
            return invoker.hashCode();
        }
        // 如果这个方法是equals，直接调用invoker.equals(args[0])
        if ("equals".equals(methodName) && parameterTypes.length == 1) {
            return invoker.equals(args[0]);
        }
        if ("$destroy".equals(methodName) && parameterTypes.length == 0) {
            invoker.destroy();
            return null;
        }

        //所有执行的参数转化为这个rpcInvocation
        RpcInvocation rpcInvocation = new RpcInvocation(method, invoker.getInterface().getName(), args);
        rpcInvocation.setTargetServiceUniqueName(invoker.getUrl().getServiceKey());

        // 调用MockClusterInvoker#invoke
        return invoker.invoke(rpcInvocation).recreate();
    }
}
