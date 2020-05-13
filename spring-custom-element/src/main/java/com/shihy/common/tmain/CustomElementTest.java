package com.shihy.common.tmain;

import com.shihy.common.bean.User;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

/**
 * test custom tag.
 */
// new ClassPathXmlApplicationContext("application-context.xml");
@SuppressWarnings("deprecation")
public class CustomElementTest
{
    private static DefaultListableBeanFactory factory;
    
    public static void main(String[] args)
    {

        ApplicationContext bf = new ClassPathXmlApplicationContext("application-context.xml");
        User user=(User) bf.getBean("testbean");
        System.out.println(user.getUserName()+","+user.getEmail());
    }
}
