package cn.shihy.beans;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * @ClassName: UserServiceImplB.java
 * @Description:
 * @Version: v1.0.0
 * @author: shihy
 * @date 2022年02月06日
 */
@Component
public class UserServiceImplB {
    @Autowired
    private  UserServiceImpl userServiceA;

}
