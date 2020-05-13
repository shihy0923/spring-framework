package com.shihy.common.bean;

public class User {
    private String userName;
    private String email;
    //省略set/get方法

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }
}
