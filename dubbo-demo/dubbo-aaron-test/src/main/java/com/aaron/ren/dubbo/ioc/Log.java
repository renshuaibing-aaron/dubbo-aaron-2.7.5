package com.aaron.ren.dubbo.ioc;

import org.apache.dubbo.common.extension.SPI;

@SPI("logback")
public interface Log {
    void execute();
}
