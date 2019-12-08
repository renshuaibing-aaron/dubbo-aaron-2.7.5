package com.aaron.ren.dubbo.dubbospi;

import org.apache.dubbo.common.extension.SPI;

@SPI("impl")
public interface PrintService {
    void printInfo();
}
