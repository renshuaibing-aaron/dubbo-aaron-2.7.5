package org.apache.dubbo.demo.consumer;

import org.apache.dubbo.demo.DemoService;

import org.springframework.context.support.ClassPathXmlApplicationContext;

import java.util.concurrent.CompletableFuture;

public class XmlConsumerApplication {
    /**
     * In order to make sure multicast registry works, need to specify '-Djava.net.preferIPv4Stack=true' before
     * launch the application
     */
    public static void main(String[] args) throws Exception {
        ClassPathXmlApplicationContext context = new ClassPathXmlApplicationContext("spring/dubbo-consumer.xml");
        context.start();

        // 获取远程服务代理
       DemoService demoService = context.getBean("demoService", DemoService.class);

        // 执行远程方法
      String world = demoService.sayHello("第一次调用");

        // 显示调用结果
        System.out.println("第一次调用: " + world);
        System.out.println("---------------------------");

        String world2 = demoService.sayHello("第二次调用");

        System.out.println("第二次调用: " + world2);
        System.in.read();
    }
}
