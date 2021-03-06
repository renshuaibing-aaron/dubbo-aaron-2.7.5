package org.apache.dubbo.rpc.proxy;

import org.apache.dubbo.common.logger.Logger;
import org.apache.dubbo.common.logger.LoggerFactory;
import org.apache.dubbo.rpc.Invoker;
import org.apache.dubbo.rpc.Result;
import org.apache.dubbo.rpc.RpcInvocation;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;

/**
 * InvokerHandler
 * Proxy 发起调用时，会调用该类的 invoke(...)，在该 invoke(...) 方法中，默认会调用 MockClusterInvoker 的 invoke(...)，之后一路进行调用
 */
public class InvokerInvocationHandler implements InvocationHandler {
    private static final Logger logger = LoggerFactory.getLogger(InvokerInvocationHandler.class);

    //MockClusterInvoker实例
    private final Invoker<?> invoker;

    public InvokerInvocationHandler(Invoker<?> handler) {
        this.invoker = handler;
    }

    //代理发出请求
    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {

        System.out.println("=======InvokerInvocationHandler#invoke===============");
        // 获得方法名称
        String methodName = method.getName();

        // 获得方法参数类型
        Class<?>[] parameterTypes = method.getParameterTypes();

        // 如果该方法所在的类是Object类型，则直接调用invoke
        if (method.getDeclaringClass() == Object.class) {
            // 定义在 Object 类中的方法（未被子类重写），比如 wait/notify等，直接调用
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

        // 调用MockClusterInvoker#invoke  //服务降级的地方
        Result invoke = invoker.invoke(rpcInvocation);

        // 最后调用 recreate 方法返回结果数据
        return invoke.recreate();
    }
}
