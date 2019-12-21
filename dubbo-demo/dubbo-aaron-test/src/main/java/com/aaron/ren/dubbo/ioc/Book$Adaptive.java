package com.aaron.ren.dubbo.ioc;

import org.apache.dubbo.common.extension.ExtensionLoader;

public class Book$Adaptive implements com.aaron.ren.dubbo.ioc.Book {
    @Override
    public java.lang.String bookName(org.apache.dubbo.common.URL arg0) {
        if (arg0 == null) {
            throw new IllegalArgumentException("url == null");
        }
        org.apache.dubbo.common.URL url = arg0;
        String extName = url.getParameter("language", "java");
        if (extName == null) {
            throw new IllegalStateException("Failed to get extension (com.aaron.ren.dubbo.ioc.Book) name from url (" + url.toString() + ") use keys([language])");
        }
        com.aaron.ren.dubbo.ioc.Book extension = (com.aaron.ren.dubbo.ioc.Book) ExtensionLoader.getExtensionLoader(com.aaron.ren.dubbo.ioc.Book.class).getExtension(extName);
        return extension.bookName(arg0);
    }
}