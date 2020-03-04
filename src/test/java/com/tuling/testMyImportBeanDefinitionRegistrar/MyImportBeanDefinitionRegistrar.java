package com.tuling.testMyImportBeanDefinitionRegistrar;

import com.tuling.testbfpostprocessor.TulingLog2;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.context.annotation.ImportBeanDefinitionRegistrar;
import org.springframework.core.type.AnnotationMetadata;

/**
 * @ClassName: MyImportBeanDefinitionRegistrar.java
 * @Description:
 * @Version: v1.0.0
 * @author: shihy
 * @date 2020年01月26日
 */
public class MyImportBeanDefinitionRegistrar implements ImportBeanDefinitionRegistrar {

    /**
     * AnnotationMetadata：当前类的注解信息
     * BeanDefinitionRegistry:BeanDefinition注册类；
     *      把所有需要添加到容器中的bean；调用
     *      BeanDefinitionRegistry.registerBeanDefinition手工注册进来
     */
    @Override
    public void registerBeanDefinitions(AnnotationMetadata importingClassMetadata, BeanDefinitionRegistry registry) {


        //指定Bean定义信息；（Bean的类型，Bean。。。）
        RootBeanDefinition beanDefinition = new RootBeanDefinition(TulingLog2.class);
        //注册一个Bean，指定bean名
        registry.registerBeanDefinition("TulingLog2", beanDefinition);

    }
}