package com.aaron.ren.dubbo.ioc;

import org.apache.dubbo.common.URL;

public class GoBook implements Book {
    @Override
    public String bookName(URL url) {
        return "this is go book！" + url.getIp();
    }
}