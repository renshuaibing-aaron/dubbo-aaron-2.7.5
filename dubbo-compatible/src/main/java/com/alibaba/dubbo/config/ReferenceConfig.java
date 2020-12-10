package com.alibaba.dubbo.config;

import org.apache.dubbo.config.annotation.Reference;

@Deprecated
public class ReferenceConfig<T> extends org.apache.dubbo.config.ReferenceConfig<T> {

    public ReferenceConfig() {
    }

    public ReferenceConfig(Reference reference) {
        super(reference);
    }


}
