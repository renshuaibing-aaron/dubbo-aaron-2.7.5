package com.aaron.ren.dubbo.newspi;

import org.apache.dubbo.common.extension.SPI;

@SPI("logback")
public interface Log {
    void execute();
}