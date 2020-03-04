package com.tuling.testMyImportBeanDefinitionRegistrar;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;

/**
 * @ClassName: MainClass.java
 * @Description:
 * @Version: v1.0.0
 * @author: shihy
 * @date 2020年01月22日
 */
public class MainClass {
    public static void main(String[] args) {
        AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext(MainConfig.class);

    }
}
