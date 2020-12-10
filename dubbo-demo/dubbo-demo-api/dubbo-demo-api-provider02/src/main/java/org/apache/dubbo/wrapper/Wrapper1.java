package org.apache.dubbo.wrapper;

import org.apache.dubbo.common.bytecode.Wrapper;
import org.apache.dubbo.provider.DemoServiceImpl;

import java.util.HashMap;

public abstract class Wrapper1 extends Wrapper {

    public static String[] pns;//property name array
    public static java.util.Map pts = new HashMap();//<property key, property value>
    public static String[] mns;//method names
    public static String[] dmns;//
    public static Class[] mts0;
    /**
     * @param o  实现类
     * @param n  方法名称
     * @param p  参数类型
     * @param v  参数名称
     * @return
     * @throws java.lang.reflect.InvocationTargetException
     */
    @Override
    public Object invokeMethod(Object o, String n, Class[] p, Object[] v) throws java.lang.reflect.InvocationTargetException {
        DemoServiceImpl w;
        try {
            w = ((DemoServiceImpl) o);
        } catch (Throwable e) {
            throw new IllegalArgumentException(e);
        }
        try {
            if ("sayHello".equals(n) && p.length == 1) {
                return w.sayHello((String) v[0]);
            }
        } catch (Throwable e) {
            throw new java.lang.reflect.InvocationTargetException(e);
        }
        throw new org.apache.dubbo.common.bytecode.NoSuchMethodException("Not found method \"" + n + "\" in class com.alibaba.dubbo.demo.provider.DemoServiceImpl.");
    }
}