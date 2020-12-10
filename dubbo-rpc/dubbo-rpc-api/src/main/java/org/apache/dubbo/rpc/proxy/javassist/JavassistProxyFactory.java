package org.apache.dubbo.rpc.proxy.javassist;

import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.bytecode.Proxy;
import org.apache.dubbo.common.bytecode.Wrapper;
import org.apache.dubbo.rpc.Invoker;
import org.apache.dubbo.rpc.proxy.AbstractProxyFactory;
import org.apache.dubbo.rpc.proxy.AbstractProxyInvoker;
import org.apache.dubbo.rpc.proxy.InvokerInvocationHandler;

/**
 * JavaassistRpcProxyFactory
 * 基于 Javassist 实现的代理工厂
 */
public class JavassistProxyFactory extends AbstractProxyFactory {


    // 使用端：consumer
    @Override
    @SuppressWarnings("unchecked")
    public <T> T getProxy(Invoker<T> invoker, Class<?>[] interfaces) {

        System.out.println("========生成代理对象======================"+invoker);
        // 使用 com.alibaba.dubbo.common.bytecode.Proxy 创建代理，
        // 创建了动态代理的逻辑处理类 InvokerInvocationHandler，并且传入了 MockClusterInvoker
        InvokerInvocationHandler invokerInvocationHandler = new InvokerInvocationHandler(invoker);

        //Proxy.getProxy(interfaces)
        Proxy proxy = Proxy.getProxy(interfaces);

        //最终返回的代理对象其实是一个proxy0对象，当我们调用其sayHello方法时，其调用内部的handler.invoke方法
       return   (T) proxy.newInstance(invokerInvocationHandler);


    }

    // 使用端：provider
    @Override
    public <T> Invoker<T> getInvoker(T proxy, Class<T> type, URL url) {
        // TODO wrapper cannot handle this scenario correctly: the classname contains '$'
        // wrapper：通过动态生成一个真实的服务提供者（DemoServiceImpl）的wrapper类，来避免反射调用
        final Wrapper wrapper = Wrapper.getWrapper(proxy.getClass().getName().indexOf('$') < 0 ? proxy.getClass() : type);


        return new AbstractProxyInvoker<T>(proxy, type, url) {
            @Override
            protected Object doInvoke(T proxy, String methodName, Class<?>[] parameterTypes, Object[] arguments) throws Throwable {
               // 直接调用wrapper，wrapper底层调用DemoServiceImpl
                //Wrapper类的invokeMethod方法，Wrapper是一个动态生成的类
                //具体查看Wrapper1
                return wrapper.invokeMethod(proxy, methodName, parameterTypes, arguments);
            }
        };
    }

    /**
     * JavassistProxyFactory 的 getInvoker 方法创建了一个继承自 AbstractProxyInvoker 类的匿名对象，
     * 并覆写了抽象方法 doInvoke。doInvoke 仅是将调用请求转发给了 Wrapper 类的 invokeMethod 方法。
     * Wrapper 用于“包裹”目标类，Wrapper 是一个abstract抽象类，仅可通过 getWrapper(Class) 方法创建子类。
     * 在创建 Wrapper 子类的过程中，子类代码生成逻辑会对 getWrapper 方法传入的 Class 对象进行解析，拿到诸如类方法，
     * 类成员变量等信息。以及生成 invokeMethod 方法代码和其他一些方法代码。代码生成完毕后，通过 Javassist 生成 Class 对象，
     * 最后再通过反射创建 Wrapper 实例
     */

}
