package com.aaron.ren.dubbo.active;

import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.extension.Adaptive;
import org.apache.dubbo.common.extension.SPI;

/**
 * @author linyang on 18/4/20.
 */
@SPI("dubbo")
public interface AdaptiveExt2 {
    @Adaptive
    String echo(String msg, URL url);
}