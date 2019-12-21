package com.aaron.ren.dubbo.auto;

import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.extension.ExtensionLoader;

public class TestAdaptiveAuto {
    public static void main(String[] args) {
        ExtensionLoader<Log> loader = ExtensionLoader.getExtensionLoader(Log.class);

        Log adaptiveExtension = loader.getAdaptiveExtension();
        URL url = new URL("dubbo", "10.211.55.6", 8080);
        adaptiveExtension.execute(url.addParameter("xxx", "log4j")); // this is log4j! 10.211.55.6
    }
}