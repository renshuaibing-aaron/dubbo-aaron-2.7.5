package com.aaron.ren.dubbo.dubbospi;

import org.apache.dubbo.common.extension.ExtensionLoader;

public class Main {
    public static void main(String[] args) {
        PrintService extension = ExtensionLoader.getExtensionLoader(PrintService.class).getDefaultExtension();


        extension.printInfo();
    }

}
