package com.aaron.ren.dubbo;

import org.apache.dubbo.common.extension.ExtensionLoader;
import org.apache.dubbo.rpc.Protocol;

public class App {
    public static void main(String[] args) {
        Protocol adaptiveExtension = ExtensionLoader.getExtensionLoader(Protocol.class).getAdaptiveExtension();


        System.out.println(adaptiveExtension);
    }
}
