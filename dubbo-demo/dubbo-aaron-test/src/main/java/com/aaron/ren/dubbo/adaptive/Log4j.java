package com.aaron.ren.dubbo.adaptive;

public class Log4j implements Log {
    @Override
    public void execute(String name) {
        System.out.println("this is log4j! " + name);
    }
}