package com.aaron.ren.dubbo.ioc;

import org.apache.dubbo.common.URL;

public class JavaBook implements Book {
    @Override
    public String bookName(URL url) {
        return "this is java book！" + url.getIp();
    }
}
