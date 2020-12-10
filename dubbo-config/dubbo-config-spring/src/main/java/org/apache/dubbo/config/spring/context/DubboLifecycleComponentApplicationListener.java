package org.apache.dubbo.config.spring.context;


import org.apache.dubbo.common.context.Lifecycle;
import org.apache.dubbo.common.utils.CollectionUtils;
import org.apache.dubbo.config.DubboShutdownHook;
import org.apache.dubbo.config.bootstrap.DubboBootstrap;

import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ApplicationContextEvent;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.SmartApplicationListener;

import java.util.Map;

import static org.springframework.beans.factory.BeanFactoryUtils.beansOfTypeIncludingAncestors;

/**
 * A {@link ApplicationListener listener} for the {@link Lifecycle Dubbo Lifecycle} components
 *
 * @see {@link Lifecycle Dubbo Lifecycle}
 * @see SmartApplicationListener
 * @since 2.7.5
 */
public class DubboLifecycleComponentApplicationListener implements ApplicationListener {

    /**
     * 什么时候进行监听器
     * @param event
     */
    @Override
    public void onApplicationEvent(ApplicationEvent event) {

        System.out.println("===发布服务=onApplicationEvent 刷新情况===================");
        if (!supportsEvent(event)) {
            return;
        }

        if (event instanceof ContextRefreshedEvent) {
            onContextRefreshedEvent((ContextRefreshedEvent) event);
        } else if (event instanceof ContextClosedEvent) {
            onContextClosedEvent((ContextClosedEvent) event);
        }
    }

    protected void onContextRefreshedEvent(ContextRefreshedEvent event) {
        ApplicationContext context = event.getApplicationContext();
        DubboBootstrap bootstrap = loadBootsttrapAsBean(context);
        if (bootstrap == null) {
            bootstrap = DubboBootstrap.getInstance();
        }
        System.out.println("======= 发布服务==监听器导出服务===========");
        bootstrap.start();
    }

    protected void onContextClosedEvent(ContextClosedEvent event) {
        DubboShutdownHook.getDubboShutdownHook().doDestroy();
    }

    private DubboBootstrap loadBootsttrapAsBean(ApplicationContext context) {
        Map<String, DubboBootstrap> beans = beansOfTypeIncludingAncestors(context, DubboBootstrap.class);
        if (CollectionUtils.isNotEmptyMap(beans)) {
            return beans.values().iterator().next();
        }
        return null;
    }

    /**
     * the specified {@link ApplicationEvent event} must be {@link ApplicationContextEvent} and
     * its correlative {@link ApplicationContext} must be root
     *
     * @param event
     * @return
     */
    private boolean supportsEvent(ApplicationEvent event) {
        return event instanceof ApplicationContextEvent &&
                isRootApplicationContext((ApplicationContextEvent) event);
    }


    private boolean isRootApplicationContext(ApplicationContextEvent event) {
        return event.getApplicationContext().getParent() == null;
    }
}
