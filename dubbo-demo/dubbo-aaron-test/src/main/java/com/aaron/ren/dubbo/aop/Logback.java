package com.aaron.ren.dubbo.aop;

public class Logback implements Log {
    @Override
    public void execute() {
        System.out.println("this is logback!");
    }
}