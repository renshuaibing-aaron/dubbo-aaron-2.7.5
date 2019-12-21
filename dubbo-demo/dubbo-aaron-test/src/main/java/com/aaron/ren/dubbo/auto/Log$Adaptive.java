package com.aaron.ren.dubbo.auto;

import org.apache.dubbo.common.extension.ExtensionLoader;

public class Log$Adaptive implements com.aaron.ren.dubbo.auto.Log {
    @Override
    public void execute(org.apache.dubbo.common.URL arg0) {
        if (arg0 == null) {
            throw new IllegalArgumentException("url == null");
        }
        org.apache.dubbo.common.URL url = arg0;
        String extName = url.getParameter("xxx", url.getParameter("ooo", "logback"));
        if (extName == null) {
            throw new IllegalStateException("Failed to get extension (com.aaron.ren.dubbo.auto.Log) name from url (" + url.toString() + ") use keys([xxx, ooo])");
        }
        com.aaron.ren.dubbo.auto.Log extension = (com.aaron.ren.dubbo.auto.Log) ExtensionLoader.getExtensionLoader(com.aaron.ren.dubbo.auto.Log.class).getExtension(extName);
        extension.execute(arg0);
    }

    @Override
    public void test() {
        throw new UnsupportedOperationException("The method public abstract void com.aaron.ren.dubbo.auto.Log.test() of interface com.aaron.ren.dubbo.auto.Log is not adaptive method!");
    }
}