package com.aaron.ren.dubbo.aop;

import org.apache.dubbo.common.extension.ExtensionLoader;

public class TestAOP {
    public static void main(String[] args) {
        ExtensionLoader<Log> loader = ExtensionLoader.getExtensionLoader(Log.class);

        System.out.println("======================= 根据指定名称获取具体的 SPI 实现类（测试 wrapper） =======================");
        Log logback = loader.getExtension("logback"); // 最外层的 wrapper 类实例


        /**
         * 输出
         * LogWrapper2 before
         * LogWrapper1 before
         * this is logback!
         * LogWrapper1 after
         * LogWrapper2 after
         */
        logback.execute();
    }
}