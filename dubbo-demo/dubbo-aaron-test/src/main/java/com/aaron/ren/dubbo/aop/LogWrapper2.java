package com.aaron.ren.dubbo.aop;

public class LogWrapper2 implements Log {
    private Log log;
    public LogWrapper2(Log log) {
        this.log = log;
    }

    @Override
    public void execute() {
        System.out.println("LogWrapper2 before");
        log.execute();
        System.out.println("LogWrapper2 after");
    }
}