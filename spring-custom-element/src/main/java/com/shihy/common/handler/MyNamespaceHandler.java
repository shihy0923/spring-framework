package com.shihy.common.handler;

import com.shihy.common.parser.UserBeanDefinitionParser;
import org.springframework.beans.factory.xml.NamespaceHandlerSupport;

/**
 * 自定义标签解码入口
 */
public class MyNamespaceHandler extends NamespaceHandlerSupport  {
    public void init() {
        registerBeanDefinitionParser("user", new UserBeanDefinitionParser());
    }

}
