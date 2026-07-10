package com.eldercare.security;

/**
 * 保存当前请求的登录身份(用户名 + 角色),由拦截器写入、Controller 读取。
 * 用 ThreadLocal 保证每个请求线程隔离。
 */
public final class CurrentUser {

    public static final String ROLE_USER = "USER";
    public static final String ROLE_EMPLOYEE = "EMPLOYEE";
    public static final String ROLE_ADMIN = "ADMIN";

    private static final ThreadLocal<String> USERNAME = new ThreadLocal<>();
    private static final ThreadLocal<String> ROLE = new ThreadLocal<>();

    private CurrentUser() {}

    public static void set(String username, String role) {
        USERNAME.set(username);
        ROLE.set(role);
    }

    public static String username() { return USERNAME.get(); }
    public static String role() { return ROLE.get(); }

    public static void clear() {
        USERNAME.remove();
        ROLE.remove();
    }
}
