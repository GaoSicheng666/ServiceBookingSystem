package com.eldercare.controller;

import com.eldercare.common.ApiResponse;
import com.eldercare.entity.Appointment;
import com.eldercare.entity.Employee;
import com.eldercare.entity.ServiceItem;
import com.eldercare.entity.User;
import com.eldercare.dto.PageResult;
import com.eldercare.service.AdminService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 管理后台接口。路径统一 /api/admin/**,由拦截器强制 ADMIN 角色。
 */
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final AdminService adminService;

    public AdminController(AdminService adminService) {
        this.adminService = adminService;
    }

    // ---- 服务项目管理 ----
    @GetMapping("/services")
    public ApiResponse<List<ServiceItem>> listServices() {
        return ApiResponse.ok(adminService.listServices());
    }

    @PostMapping("/services")
    public ApiResponse<Void> createService(@RequestBody ServiceItem s) {
        adminService.createService(s);
        return ApiResponse.ok();
    }

    @PutMapping("/services/{id}")
    public ApiResponse<Void> updateService(@PathVariable int id, @RequestBody ServiceItem s) {
        adminService.updateService(id, s);
        return ApiResponse.ok();
    }

    @DeleteMapping("/services/{id}")
    public ApiResponse<Void> deleteService(@PathVariable int id) {
        adminService.deleteService(id);
        return ApiResponse.ok();
    }

    // ---- 用户管理 ----
    @GetMapping("/users")
    public ApiResponse<List<User>> listUsers() {
        return ApiResponse.ok(adminService.listUsers());
    }

    @PatchMapping("/users/{id}/active")
    public ApiResponse<Void> setUserActive(@PathVariable int id, @RequestBody Map<String, Boolean> body) {
        adminService.setUserActive(id, Boolean.TRUE.equals(body.get("active")));
        return ApiResponse.ok();
    }

    @DeleteMapping("/users/{id}")
    public ApiResponse<Void> deleteUser(@PathVariable int id) {
        adminService.deleteUser(id);
        return ApiResponse.ok();
    }

    // ---- 员工管理 ----
    @GetMapping("/employees")
    public ApiResponse<List<Employee>> listEmployees() {
        return ApiResponse.ok(adminService.listEmployees());
    }

    @PatchMapping("/employees/{id}/active")
    public ApiResponse<Void> setEmployeeActive(@PathVariable int id, @RequestBody Map<String, Boolean> body) {
        adminService.setEmployeeActive(id, Boolean.TRUE.equals(body.get("active")));
        return ApiResponse.ok();
    }

    /** 管理员为护工开放答题权限。 */
    @PatchMapping("/employees/{id}/training")
    public ApiResponse<Void> grantEmployeeTraining(@PathVariable int id) {
        adminService.grantEmployeeTraining(id);
        return ApiResponse.ok();
    }

    @DeleteMapping("/employees/{id}")
    public ApiResponse<Void> deleteEmployee(@PathVariable int id) {
        adminService.deleteEmployee(id);
        return ApiResponse.ok();
    }

    // ---- 预约总览 ----
    @GetMapping("/appointments")
    public ApiResponse<List<Appointment>> listAppointments(@RequestParam(required = false) String status) {
        return ApiResponse.ok(adminService.listAppointments(status));
    }

    /** 管理员预约分页；单页最多 50 条，前端默认使用 10 条。 */
    @GetMapping("/appointments/page")
    public ApiResponse<PageResult<Appointment>> pageAppointments(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ApiResponse.ok(adminService.pageAppointments(status, page, size));
    }

    /** 永久删除预约及其时段、金额和取消原因。 */
    @DeleteMapping("/appointments/{id}")
    public ApiResponse<Void> deleteAppointment(@PathVariable int id) {
        adminService.deleteAppointment(id);
        return ApiResponse.ok();
    }
}
