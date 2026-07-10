package com.eldercare.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 预约单实体,对应 appointments 表。
 * 相比旧版"一对一绑定",增加了服务项目、预约日期与状态。
 */
public class Appointment {
    private Integer id;
    private Integer userId;
    private Integer employeeId;
    private Integer serviceId;
    private LocalDate appointmentDate;
    private String status;              // PENDING / COMPLETED / CANCELLED
    private LocalDateTime createdAt;

    // 便于前端展示的关联字段(来自 JOIN,非表列)
    private String userName;
    private String userPhone;
    private String userAddress;
    private String employeeName;
    private String employeePhone;
    private String serviceName;

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }
    public Integer getUserId() { return userId; }
    public void setUserId(Integer userId) { this.userId = userId; }
    public Integer getEmployeeId() { return employeeId; }
    public void setEmployeeId(Integer employeeId) { this.employeeId = employeeId; }
    public Integer getServiceId() { return serviceId; }
    public void setServiceId(Integer serviceId) { this.serviceId = serviceId; }
    public LocalDate getAppointmentDate() { return appointmentDate; }
    public void setAppointmentDate(LocalDate appointmentDate) { this.appointmentDate = appointmentDate; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public String getUserName() { return userName; }
    public void setUserName(String userName) { this.userName = userName; }
    public String getUserPhone() { return userPhone; }
    public void setUserPhone(String userPhone) { this.userPhone = userPhone; }
    public String getUserAddress() { return userAddress; }
    public void setUserAddress(String userAddress) { this.userAddress = userAddress; }
    public String getEmployeeName() { return employeeName; }
    public void setEmployeeName(String employeeName) { this.employeeName = employeeName; }
    public String getEmployeePhone() { return employeePhone; }
    public void setEmployeePhone(String employeePhone) { this.employeePhone = employeePhone; }
    public String getServiceName() { return serviceName; }
    public void setServiceName(String serviceName) { this.serviceName = serviceName; }
}
