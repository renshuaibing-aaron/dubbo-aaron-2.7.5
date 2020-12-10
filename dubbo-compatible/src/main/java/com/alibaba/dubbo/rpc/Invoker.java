package com.alibaba.dubbo.rpc;

import com.alibaba.dubbo.common.URL;

/**
 * invoker 直译调用者
 * 在服务提供方: invoker 对象被构造出来去调用提供服务的函数
 * 在服务的消费方: invoker用于调用 执行远程过程调用的类
 * @param <T>
 */
@Deprecated
public interface Invoker<T> extends org.apache.dubbo.rpc.Invoker<T> {

    Result invoke(Invocation invocation) throws RpcException;

    @Override
    URL getUrl();

    default org.apache.dubbo.rpc.Invoker<T> getOriginal() {
        return null;
    }

    // This method will never be called for a legacy invoker.
    @Override
    default org.apache.dubbo.rpc.Result invoke(org.apache.dubbo.rpc.Invocation invocation) throws org.apache.dubbo.rpc.RpcException {
        return null;
    }

    class CompatibleInvoker<T> implements Invoker<T> {

        private org.apache.dubbo.rpc.Invoker<T> invoker;

        public CompatibleInvoker(org.apache.dubbo.rpc.Invoker<T> invoker) {
            this.invoker = invoker;
        }

        @Override
        public Class<T> getInterface() {
            return invoker.getInterface();
        }

        @Override
        public Result invoke(Invocation invocation) throws RpcException {
            return new Result.CompatibleResult(invoker.invoke(invocation.getOriginal()));
        }

        @Override
        public URL getUrl() {
            return new URL(invoker.getUrl());
        }

        @Override
        public boolean isAvailable() {
            return invoker.isAvailable();
        }

        @Override
        public void destroy() {
            invoker.destroy();
        }

        @Override
        public org.apache.dubbo.rpc.Invoker<T> getOriginal() {
            return invoker;
        }
    }
}
