package com.aaron.ren.dubbo.newspi;

import org.apache.dubbo.common.extension.ExtensionLoader;

import java.util.Set;

public class TestBasic {
    public static void main(String[] args) {
        ExtensionLoader<Log> loader = ExtensionLoader.getExtensionLoader(Log.class);

        // 1. 指定名称获取具体 SPI 实现类
        Log logback = loader.getExtension("logback");
        logback.execute(); // this is logback!
        Log log4j = loader.getExtension("log4j");
        log4j.execute(); // this is log4j!


        // 2. 获取默认实现类 @SPI("logback") 中的 logback 就指定了默认的 SPI 实现类的 key
        Log defaultExtension = loader.getDefaultExtension();
        defaultExtension.execute(); // this is logback!
        System.out.println(loader.getDefaultExtensionName()); // logback

        // 3. 获取支持哪些 SPI 实现类
        Set<String> supportedExtensions = loader.getSupportedExtensions();
        supportedExtensions.forEach(System.out::println); // log4j \n logback

        // 4. 获取已经加载了哪些 SPI 实现类
        Set<String> loadedExtensions = loader.getLoadedExtensions();
        loadedExtensions.forEach(System.out::println); // log4j \n logback

        // 5. 根据 SPI 实现类实例或者实现类的 Class 信息获取其 key
        System.out.println(loader.getExtensionName(logback)); // logback
        System.out.println(loader.getExtensionName(Logback.class)); // logback

        // 6. 判断是否具有指定 key 的 SPI 实现类
        System.out.println(loader.hasExtension("logback")); // true
        System.out.println(loader.hasExtension("log4j2"));  // false
    }
}