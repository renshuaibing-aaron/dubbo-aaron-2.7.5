package org.apache.dubbo.common.bytecode;
import com.alibaba.dubbo.rpc.service.EchoService;
import org.apache.dubbo.common.bytecode.ClassGenerator;
import org.apache.dubbo.demo.DemoService;
import org.apache.dubbo.rpc.service.Destroyable;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.concurrent.CompletableFuture;

public class proxy0
        implements ClassGenerator.DC, Destroyable, EchoService, DemoService {
    public static Method[] methods;
    private InvocationHandler handler;

    @Override
    public String sayHello(String paramString) {
        // 将运行时参数存储到数组中
        Object[] arrayOfObject = new Object[1];
        arrayOfObject[0] = paramString;
        // 调用 InvocationHandler 实现类的 invoke 方法得到调用结果
        Object localObject = null;
        try {
            localObject = this.handler.invoke(this, methods[0], arrayOfObject);
        } catch (Throwable throwable) {
            throwable.printStackTrace();
        }
        return ((String) localObject);
    }

    @Override
    public CompletableFuture sayHelloAsync(String paramString)
            throws Throwable {
        Object[] arrayOfObject = new Object[1];
        arrayOfObject[0] = paramString;
        Object localObject = this.handler.invoke(this, methods[1], arrayOfObject);
        return ((CompletableFuture) localObject);
    }

    @Override
    public void $destroy() {
        Object[] arrayOfObject = new Object[0];
        try {
            Object localObject = this.handler.invoke(this, methods[2], arrayOfObject);
        } catch (Throwable throwable) {
            throwable.printStackTrace();
        }
    }

    @Override
    public Object $echo(Object paramObject) {
        Object[] arrayOfObject = new Object[1];
        arrayOfObject[0] = paramObject;
        Object localObject = null;
        try {
            localObject = this.handler.invoke(this, methods[3], arrayOfObject);
        } catch (Throwable throwable) {
            throwable.printStackTrace();
        }
        return ((Object) localObject);
    }

    public proxy0() {
    }

    public proxy0(InvocationHandler paramInvocationHandler) {
        this.handler = paramInvocationHandler;
    }
}


      //  import java.lang.reflect.InvocationHandler;

class Proxy0 extends Proxy
        implements ClassGenerator.DC
{
    @Override
    public Object newInstance(InvocationHandler paramInvocationHandler)
    {
        return new proxy0(paramInvocationHandler);
    }
}