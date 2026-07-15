package com.eldercare.dto;

import jakarta.validation.constraints.*;

/** 注册请求。role 为 USER 或 EMPLOYEE，老人用户需要填写 address。 */
public class RegisterRequest {

    @NotBlank(message = "角色不能为空")
    private String role;                 // USER / EMPLOYEE

    @NotBlank(message = "用户名不能为空")
    @Size(max = 50, message = "用户名过长")
    private String username;

    @NotBlank(message = "密码不能为空")
    @Size(min = 1, max = 100, message = "密码长度不合法")
    private String password;

    @NotBlank(message = "姓名不能为空")
    private String name;

    @NotBlank(message = "联系电话不能为空")
    @Pattern(regexp = "\\d{6,20}", message = "请输入有效的联系电话")
    private String phone;

    @NotNull(message = "年龄不能为空")
    @Min(value = 1, message = "年龄必须大于0")
    @Max(value = 120, message = "年龄不能超过120")
    private Integer age;

    // 用户特有
    private String address;

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public Integer getAge() { return age; }
    public void setAge(Integer age) { this.age = age; }
    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }
}
