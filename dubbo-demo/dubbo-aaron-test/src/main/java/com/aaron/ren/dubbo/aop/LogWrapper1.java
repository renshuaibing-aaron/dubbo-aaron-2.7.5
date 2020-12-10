package com.aaron.ren.dubbo.aop;

import org.apache.dubbo.common.URL;

/**
 * wrapper 类也必须实现 SPI 接口，否则 loadClass() 处报错
 */
public class LogWrapper1 implements Log {
    private Log log;

    /**
     * wrapper 类必须有一个含有单个 Log 参数的构造器
     */
    public LogWrapper1(Log log) {
        System.out.println("=======【LogWrapper1】======="+log);
        this.log = log;
    }

    @Override
    public void execute(URL url) {
        System.out.println("LogWrapper1 before");
        log.execute(url);
        System.out.println("LogWrapper1 after");
    }
}
