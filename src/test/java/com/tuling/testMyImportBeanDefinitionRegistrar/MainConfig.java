package com.tuling.testMyImportBeanDefinitionRegistrar;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * @ClassName: MainConfig.java
 * @Description:
 * @Version: v1.0.0
 * @author: shihy
 * @date 2020年01月22日
 */
@Configuration

@Import(MyImportBeanDefinitionRegistrar.class)
public class MainConfig {
}
