package com.tuling.testbfpostprocessor;

import org.springframework.context.annotation.Bean;

/**
 * @ClassName: MainCofig2.java
 * @Description:
 * @Version: v1.0.0
 * @author: shihy
 * @date 2020年01月25日
 */
/*
@Configuration
*/
public class MainConfig2 {
    @Bean
    public TulingLog2 tulingLog2() {
        return new TulingLog2();
    }
}
