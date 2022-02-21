package cn.shihy.beans;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * @ClassName: UserServiceImpl.java
 * @Description:
 * @Version: v1.0.0
 * @author: shihy
 * @date 2020年09月22日
 */
@Service
public class UserServiceImpl {

	@Autowired
	private  UserServiceImplB userServiceB;
	public void sayHi(){
		System.out.println("Hello Spring！");
	}
}

