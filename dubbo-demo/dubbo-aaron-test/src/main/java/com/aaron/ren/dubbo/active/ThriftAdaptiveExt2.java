package com.aaron.ren.dubbo.active;


import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.extension.Adaptive;

/**
 * @author linyang on 18/4/20.
 */
@Adaptive
public class ThriftAdaptiveExt2 implements AdaptiveExt2 {

    @Override
    public String echo(String msg, URL url) {
        return "thrift";
    }
}