package cn.shihy.beans;

import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * @ClassName: MainStat.java
 * @Description:
 * @Version: v1.0.0
 * @author: shihy
 * @date 2020年09月22日
 */
@Configuration
@ComponentScan("cn.shihy")
public class MainStat {

	public static void main(String[] args) {
		ApplicationContext context=new AnnotationConfigApplicationContext(MainStat.class);
		UserServiceImpl bean = context.getBean(UserServiceImpl.class);
		bean.sayHi();

	}

}
