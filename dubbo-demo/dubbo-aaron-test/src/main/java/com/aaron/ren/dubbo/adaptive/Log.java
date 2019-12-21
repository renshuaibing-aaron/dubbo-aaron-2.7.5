package com.aaron.ren.dubbo.adaptive;

import org.apache.dubbo.common.extension.SPI;

@SPI("logback")
public interface Log {
    void execute(String name);
}