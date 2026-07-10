package com.eldercare.dto;

/** 登录成功返回:令牌 + 角色 + 姓名。 */
public class LoginResponse {
    private String token;
    private String role;
    private String name;

    public LoginResponse(String token, String role, String name) {
        this.token = token;
        this.role = role;
        this.name = name;
    }

    public String getToken() { return token; }
    public String getRole() { return role; }
    public String getName() { return name; }
}
