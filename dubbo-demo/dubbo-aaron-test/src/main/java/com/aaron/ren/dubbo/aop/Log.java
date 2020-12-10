package com.aaron.ren.dubbo.aop;

import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.extension.Adaptive;
import org.apache.dubbo.common.extension.SPI;

@SPI("logback")
public interface Log {
    @Adaptive({"xxx","ooo"})
    void execute(URL url);
}
