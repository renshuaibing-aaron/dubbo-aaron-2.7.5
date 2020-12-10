package com.aaron.ren.dubbo.aop;

import org.apache.dubbo.common.URL;

public class LogWrapper2 implements Log {
    private Log log;
    public LogWrapper2(Log log) {
        System.out.println("=======【LogWrapper2】======="+log);
        this.log = log;
    }

    @Override
    public void execute(URL url) {
        System.out.println("LogWrapper2 before");
        log.execute(url);
        System.out.println("LogWrapper2 after");
    }
}