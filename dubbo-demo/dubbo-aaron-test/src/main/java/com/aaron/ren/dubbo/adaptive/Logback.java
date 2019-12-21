package com.aaron.ren.dubbo.adaptive;

public class Logback implements Log {
    @Override
    public void execute(String name) {
        System.out.println("this is logback! " + name);
    }
}
