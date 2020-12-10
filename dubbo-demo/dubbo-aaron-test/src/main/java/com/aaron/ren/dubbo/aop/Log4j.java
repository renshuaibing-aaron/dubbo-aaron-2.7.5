package com.aaron.ren.dubbo.aop;

import org.apache.dubbo.common.URL;

public class Log4j implements Log {
    @Override
    public void execute(URL url) {
        System.out.println("this is log4j!");
    }
}
