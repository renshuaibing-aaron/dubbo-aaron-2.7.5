package com.aaron.ren.dubbo.aop;

import org.apache.dubbo.common.URL;

public class Logback implements Log {
    @Override
    public void execute(URL url) {
        System.out.println("this is logback!");
    }
}