package com.eldercare.controller;

import com.eldercare.common.ApiResponse;
import com.eldercare.entity.Appointment;
import com.eldercare.entity.Employee;
import com.eldercare.service.BookingService;
import com.eldercare.service.EmployeeService;
import com.eldercare.security.CurrentUser;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** 员工(护工)侧接口。 */
@RestController
@RequestMapping("/api/employees")
public class EmployeeController {

    private final EmployeeService employeeService;
    private final BookingService bookingService;

    public EmployeeController(EmployeeService employeeService, BookingService bookingService) {
        this.employeeService = employeeService;
        this.bookingService = bookingService;
    }

    /** 我的信息。 */
    @GetMapping("/me")
    public ApiResponse<Employee> me() {
        return ApiResponse.ok(employeeService.getSelf(CurrentUser.username()));
    }

    /** 切换工作状态(是否接单)。 */
    @PatchMapping("/me/status")
    public ApiResponse<Void> updateStatus(@RequestBody Map<String, Boolean> body) {
        Boolean working = body.get("isWorking");
        if (working == null) {
            working = false;
        }
        employeeService.updateWorkingStatus(CurrentUser.username(), working);
        return ApiResponse.ok();
    }

    /** 分配给我的预约(可按状态过滤)。 */
    @GetMapping("/me/appointments")
    public ApiResponse<List<Appointment>> myAppointments(@RequestParam(required = false) String status) {
        return ApiResponse.ok(bookingService.myAppointmentsAsEmployee(CurrentUser.username(), status));
    }

    /** 员工标记预约完成。 */
    @PatchMapping("/me/appointments/{id}/complete")
    public ApiResponse<Void> complete(@PathVariable int id) {
        bookingService.completeByEmployee(CurrentUser.username(), id);
        return ApiResponse.ok();
    }

    /** 员工取消分配给自己的预约。 */
    @PatchMapping("/me/appointments/{id}/cancel")
    public ApiResponse<Void> cancel(@PathVariable int id) {
        bookingService.cancelByEmployee(CurrentUser.username(), id);
        return ApiResponse.ok();
    }
}
