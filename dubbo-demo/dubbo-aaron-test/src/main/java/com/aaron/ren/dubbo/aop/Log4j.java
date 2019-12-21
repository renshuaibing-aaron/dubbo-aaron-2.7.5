package com.aaron.ren.dubbo.aop;

public class Log4j implements Log {
    @Override
    public void execute() {
        System.out.println("this is log4j!");
    }
}
