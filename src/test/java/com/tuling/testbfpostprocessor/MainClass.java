package com.tuling.testbfpostprocessor;

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
        /*TestBeanDefinationRegisterPostProcessor obj= (TestBeanDefinationRegisterPostProcessor) ctx.getBean("testBeanDefinationRegisterPostProcessor");
        obj.test();
        TuLingLog1 obj2= (TuLingLog1) ctx.getBean("tuLingLog1");
        obj2.test();*/

    }
}
