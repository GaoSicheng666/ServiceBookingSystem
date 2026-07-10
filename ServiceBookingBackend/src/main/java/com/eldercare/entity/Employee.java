package com.eldercare.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 员工(护工)实体,对应 employees 表。 */
public class Employee {
    private Integer id;
    private String username;
    private String password;
    private String name;
    private Integer age;
    private String phone;
    private BigDecimal salary;
    private boolean working = false;
    private boolean active = true;
    private LocalDateTime createdAt;

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Integer getAge() { return age; }
    public void setAge(Integer age) { this.age = age; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public BigDecimal getSalary() { return salary; }
    public void setSalary(BigDecimal salary) { this.salary = salary; }
    public boolean isWorking() { return working; }
    public void setWorking(boolean working) { this.working = working; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
