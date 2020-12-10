package com.alibaba.dubbo.rpc;

import org.apache.dubbo.rpc.FutureContext;

import com.alibaba.dubbo.rpc.protocol.dubbo.FutureAdapter;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;

@Deprecated
public class RpcContext extends org.apache.dubbo.rpc.RpcContext {

    public static RpcContext getContext() {
        return newInstance(org.apache.dubbo.rpc.RpcContext.getContext());
    }

    private static RpcContext newInstance(org.apache.dubbo.rpc.RpcContext rpcContext) {
        RpcContext copy = new RpcContext();
        copy.getAttachments().putAll(rpcContext.getAttachments());
        copy.get().putAll(rpcContext.get());

        copy.setUrls(rpcContext.getUrls());
        copy.setUrl(rpcContext.getUrl());
        copy.setMethodName(rpcContext.getMethodName());
        copy.setParameterTypes(rpcContext.getParameterTypes());
        copy.setArguments(rpcContext.getArguments());
        copy.setLocalAddress(rpcContext.getLocalAddress());
        copy.setRemoteAddress(rpcContext.getRemoteAddress());
        copy.setRemoteApplicationName(rpcContext.getRemoteApplicationName());
        copy.setInvokers(rpcContext.getInvokers());
        copy.setInvoker(rpcContext.getInvoker());
        copy.setInvocation(rpcContext.getInvocation());

        copy.setRequest(rpcContext.getRequest());
        copy.setResponse(rpcContext.getResponse());
        copy.setAsyncContext(rpcContext.getAsyncContext());

        return copy;
    }

    @Override
    public <T> Future<T> getFuture() {
        CompletableFuture completableFuture = FutureContext.getContext().getCompatibleCompletableFuture();
        if (completableFuture == null) {
            return null;
        }
        return new FutureAdapter(completableFuture);
    }

    @Override
    public void setFuture(CompletableFuture<?> future) {
        FutureContext.getContext().setCompatibleFuture(future);
    }
}
