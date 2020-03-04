package com.tuling.testbfpostprocessor;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * @ClassName: TuLingLog.java
 * @Description:
 * @Version: v1.0.0
 * @author: shihy
 * @date 2020年01月22日
 */
@Component
public class TuLingLog1 {
    @Autowired
    private TestBeanDefinationRegisterPostProcessor testBeanDefinationRegisterPostProcessor;
    public TuLingLog1() {
        System.out.println("我是TuLingLog的构造方法");
    }

    public void test() {
        System.out.println(testBeanDefinationRegisterPostProcessor.toString());
    }
}
