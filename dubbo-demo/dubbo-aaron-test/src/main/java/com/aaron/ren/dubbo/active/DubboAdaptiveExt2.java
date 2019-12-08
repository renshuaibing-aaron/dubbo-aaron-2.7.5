package com.aaron.ren.dubbo.active;

import org.apache.dubbo.common.URL;

/**
 * @author linyang on 18/4/20.
 */
public class DubboAdaptiveExt2 implements AdaptiveExt2 {

    @Override
    public String echo(String msg, URL url) {
        return "dubbo";
    }
}