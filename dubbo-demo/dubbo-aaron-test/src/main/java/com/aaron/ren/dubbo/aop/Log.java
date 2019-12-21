package com.aaron.ren.dubbo.aop;

import org.apache.dubbo.common.extension.SPI;

@SPI("logback")
public interface Log {
    void execute();
}
