package com.eldercare.service;

import com.eldercare.common.BusinessException;
import com.eldercare.entity.Appointment;
import com.eldercare.entity.Employee;
import com.eldercare.entity.ServiceItem;
import com.eldercare.entity.User;
import com.eldercare.dto.PageResult;
import com.eldercare.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** 管理后台服务:服务项目 CRUD、用户/员工管理、预约总览。 */
@Service
public class AdminService {

    public static final int APPOINTMENT_RECORD_LIMIT = 9999;
    public static final int DEFAULT_APPOINTMENT_PAGE_SIZE = 10;
    public static final int MAX_APPOINTMENT_PAGE_SIZE = 50;

    private final ServiceRepository serviceRepo;
    private final UserRepository userRepo;
    private final EmployeeRepository employeeRepo;
    private final EmployeeTrainingRepository trainingRepo;
    private final AppointmentRepository appointmentRepo;

    public AdminService(ServiceRepository serviceRepo, UserRepository userRepo,
                        EmployeeRepository employeeRepo, EmployeeTrainingRepository trainingRepo,
                        AppointmentRepository appointmentRepo) {
        this.serviceRepo = serviceRepo;
        this.userRepo = userRepo;
        this.employeeRepo = employeeRepo;
        this.trainingRepo = trainingRepo;
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

    /** 管理员可跳过阅读环节，为护工直接开放培训答题。 */
    public void grantEmployeeTraining(int id) {
        employeeRepo.findById(id).orElseThrow(() -> new BusinessException("护工不存在"));
        trainingRepo.completeTraining(id);
    }

    public void deleteEmployee(int id) {
        employeeRepo.deleteById(id);
    }

    // ---- 预约总览 ----
    public List<Appointment> listAppointments(String status) {
        return appointmentRepo.findAll(status);
    }

    /** 后端分页，防止管理员一次加载全部历史预约。 */
    public PageResult<Appointment> pageAppointments(String status, int requestedPage, int requestedSize) {
        if (requestedSize < 1 || requestedSize > MAX_APPOINTMENT_PAGE_SIZE) {
            throw new BusinessException("每页记录数必须在1至50之间");
        }
        long total = appointmentRepo.count(status);
        int totalPages = Math.max(1, (int) Math.ceil((double) total / requestedSize));
        int page = Math.min(Math.max(1, requestedPage), totalPages);
        int offset = (page - 1) * requestedSize;
        List<Appointment> items = appointmentRepo.findPage(status, requestedSize, offset);
        return new PageResult<>(items, page, requestedSize, total, APPOINTMENT_RECORD_LIMIT);
    }

    /** 管理员永久删除预约；数据库外键负责级联清理预约附属记录。 */
    @Transactional
    public void deleteAppointment(int id) {
        if (appointmentRepo.deleteById(id) == 0) {
            throw new BusinessException("预约记录不存在或已被删除");
        }
    }
}
