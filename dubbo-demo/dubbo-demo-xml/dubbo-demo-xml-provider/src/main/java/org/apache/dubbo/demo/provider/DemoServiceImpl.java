package org.apache.dubbo.demo.provider;

import org.apache.dubbo.demo.DemoService;
import org.apache.dubbo.rpc.RpcContext;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;

public class DemoServiceImpl implements DemoService {
    private static final Logger logger = LoggerFactory.getLogger(DemoServiceImpl.class);

    @Override
    public String sayHello(String name) {
        System.out.println("=========sayHello========="+name);
        logger.info("Hello " + name + ", request from consumer: " + RpcContext.getContext().getRemoteAddress());

        return "Hello " + name + ", response from provider: " + RpcContext.getContext().getLocalAddress();
    }

    @Override
    public CompletableFuture<String> sayHelloAsync(String name) {

        System.out.println("=======收到消息=sayHelloAsync=========="+name);
        CompletableFuture<String> cf = CompletableFuture.supplyAsync(() -> {
     /*      try {
               Thread.sleep(1000);
          } catch (InterruptedException e) {
               e.printStackTrace();
           }*/
            return "async result";
        });
        return cf;
    }
}
