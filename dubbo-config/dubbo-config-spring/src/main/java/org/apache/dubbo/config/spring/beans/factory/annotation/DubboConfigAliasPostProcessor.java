package org.apache.dubbo.config.spring.beans.factory.annotation;

import org.apache.dubbo.config.AbstractConfig;
import org.apache.dubbo.config.spring.context.annotation.DubboConfigConfigurationRegistrar;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;

import static com.alibaba.spring.util.BeanRegistrar.hasAlias;
import static org.springframework.util.ObjectUtils.nullSafeEquals;
import static org.springframework.util.StringUtils.hasText;

/**
 * A Post-Processor class to set the alias of Dubbo Config bean using its {@link AbstractConfig#getId()}
 *
 * @since 2.7.5
 */
public class DubboConfigAliasPostProcessor implements BeanDefinitionRegistryPostProcessor, BeanPostProcessor {

    /**
     * The bean name of {@link DubboConfigConfigurationRegistrar}
     */
    public final static String BEAN_NAME = "dubboConfigAliasPostProcessor";

    private BeanDefinitionRegistry registry;

    @Override
    public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) throws BeansException {
        this.registry = registry;
    }

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
        // DO NOTHING
    }

    @Override
    public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
        // DO NOTHING
        return bean;
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        if (bean instanceof AbstractConfig) {
            String id = ((AbstractConfig) bean).getId();
            if (hasText(id)                                // id MUST be present in AbstractConfig
                    && !nullSafeEquals(id, beanName)            // id MUST NOT be equal to bean name
                    && !hasAlias(registry, beanName, id)) {

                // id MUST NOT be present in AliasRegistry
                System.out.println("==232313134dd================="+beanName);
                registry.registerAlias(beanName, id);
            }
        }
        return bean;
    }
}
