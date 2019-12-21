/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.dubbo.rpc.protocol.dubbo;

import org.apache.dubbo.rpc.Exporter;
import org.apache.dubbo.rpc.Invoker;
import org.apache.dubbo.rpc.protocol.AbstractExporter;

import java.util.Map;

/**
 * DubboExporter
 */
public class DubboExporter<T> extends AbstractExporter<T> {

    //serviceKey: group/path:version:port
    private final String key;

    /**
     * 注意：exporterMap 对象实际上持有的是 AbstractProtocol 中的 exporterMap 对象引用
     * key: serviceKey
     * value: 具体的 Exporter 实例，eg. DubboExporter
     */
    private final Map<String, Exporter<?>> exporterMap;

    /**
     * @param invoker  invoker：经过filter包装的InvokerDelegete实例
     * @param key key:com.alibaba.dubbo.demo.DemoService:20880 (group/path:version:port)
     * @param exporterMap exporterMap:传入时为空，构造器执行后又执行了put，为{"com.alibaba.dubbo.demo.DemoService:20880", 当前的DubboExporter实例}
     */
    public DubboExporter(Invoker<T> invoker, String key, Map<String, Exporter<?>> exporterMap) {
        // 存储该 DubboExporter 实例管理的 Invoker 实例
        super(invoker);
        this.key = key;
        this.exporterMap = exporterMap;
    }

    @Override
    public void unexport() {
        super.unexport();
        exporterMap.remove(key);
    }

}