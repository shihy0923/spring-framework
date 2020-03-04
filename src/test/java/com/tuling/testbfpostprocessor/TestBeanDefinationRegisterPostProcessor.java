package com.tuling.testbfpostprocessor;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * @ClassName: TestBeanDefinationRegisterPostProcessor.java
 * @Description:
 * @Version: v1.0.0
 * @author: shihy
 * @date 2020年01月22日
 */
@Component
public class TestBeanDefinationRegisterPostProcessor /*implements BeanDefinitionRegistryPostProcessor*/ {
    @Autowired
    private TuLingLog1 tuLingLog1;
   /* @Override
    public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) throws BeansException {
        System.out.println("TestBeanDefinationRegisterPostProcessor的postProcessBeanDefinitionRegistry方法");
        System.out.println("bean定义的数据量:"+registry.getBeanDefinitionCount());
        RootBeanDefinition rootBeanDefinition = new RootBeanDefinition(TuLingLog1.class);
        registry.registerBeanDefinition("testLog",rootBeanDefinition);
    }

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
        System.out.println("TestBeanDefinationRegisterPostProcessor的postProcessBeanFactory方法");
        System.out.println(beanFactory.getBeanDefinitionCount());
    }*/
    public void test() {
        System.out.println(tuLingLog1.toString());
    }
}

