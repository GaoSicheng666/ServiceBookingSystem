package com.eldercare.service;

import com.eldercare.common.BusinessException;
import com.eldercare.entity.Appointment;
import com.eldercare.entity.Employee;
import com.eldercare.entity.ServiceItem;
import com.eldercare.entity.User;
import com.eldercare.repository.*;
import org.springframework.stereotype.Service;

import java.util.List;

/** 管理后台服务:服务项目 CRUD、用户/员工管理、预约总览。 */
@Service
public class AdminService {

    private final ServiceRepository serviceRepo;
    private final UserRepository userRepo;
    private final EmployeeRepository employeeRepo;
    private final AppointmentRepository appointmentRepo;

    public AdminService(ServiceRepository serviceRepo, UserRepository userRepo,
                        EmployeeRepository employeeRepo, AppointmentRepository appointmentRepo) {
        this.serviceRepo = serviceRepo;
        this.userRepo = userRepo;
        this.employeeRepo = employeeRepo;
        this.appointmentRepo = appointmentRepo;
    }

    // ---- 服务项目 ----
    public List<ServiceItem> listServices() {
        return serviceRepo.findAll();
    }

    public void createService(ServiceItem s) {
        if (s.getName() == null || s.getName().isBlank()) {
            throw new BusinessException("服务名称不能为空");
        }
        serviceRepo.insert(s);
    }

    public void updateService(int id, ServiceItem s) {
        serviceRepo.findById(id).orElseThrow(() -> new BusinessException("服务项目不存在"));
        s.setId(id);
        serviceRepo.update(s);
    }

    public void deleteService(int id) {
        serviceRepo.deleteById(id);
    }

    // ---- 用户管理 ----
    public List<User> listUsers() {
        List<User> users = userRepo.findAll();
        users.forEach(u -> u.setPassword(null));
        return users;
    }

    public void setUserActive(int id, boolean active) {
        userRepo.setActive(id, active);
    }

    public void deleteUser(int id) {
        userRepo.deleteById(id);
    }

    // ---- 员工管理 ----
    public List<Employee> listEmployees() {
        List<Employee> employees = employeeRepo.findAll();
        employees.forEach(e -> e.setPassword(null));
        return employees;
    }

    public void setEmployeeActive(int id, boolean active) {
        employeeRepo.setActive(id, active);
    }

    public void deleteEmployee(int id) {
        employeeRepo.deleteById(id);
    }

    // ---- 预约总览 ----
    public List<Appointment> listAppointments(String status) {
        return appointmentRepo.findAll(status);
    }
}
