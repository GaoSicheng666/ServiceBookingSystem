package com.eldercare.controller;

import com.eldercare.common.ApiResponse;
import com.eldercare.entity.Appointment;
import com.eldercare.entity.Employee;
import com.eldercare.dto.AvailabilityRequest;
import com.eldercare.dto.CancelAppointmentRequest;
import com.eldercare.dto.EmployeeProfileRequest;
import com.eldercare.dto.TrainingQuizRequest;
import com.eldercare.service.BookingService;
import com.eldercare.service.EmployeeService;
import com.eldercare.security.CurrentUser;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;

import java.util.List;
import java.util.Map;
import java.math.BigDecimal;

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

    /** 修改当前护工的头像、联系方式和公开服务介绍。 */
    @PutMapping("/me/profile")
    public ApiResponse<Void> updateProfile(@Valid @RequestBody EmployeeProfileRequest request) {
        employeeService.updateProfile(CurrentUser.username(), request);
        return ApiResponse.ok();
    }

    /** 护工确认已阅读培训内容，随后可以进入答题。 */
    @PatchMapping("/me/training/complete")
    public ApiResponse<Void> completeTraining() {
        employeeService.completeTraining(CurrentUser.username());
        return ApiResponse.ok();
    }

    /** 提交四道培训题，答对至少三题即通过。 */
    @PostMapping("/me/training/quiz")
    public ApiResponse<Map<String, Object>> submitQuiz(@RequestBody TrainingQuizRequest request) {
        return ApiResponse.ok(employeeService.submitQuiz(CurrentUser.username(), request));
    }

    /** 查询护工星期一至星期日的可工作时段。 */
    @GetMapping("/me/availability")
    public ApiResponse<List<String>> availability() {
        return ApiResponse.ok(employeeService.getAvailability(CurrentUser.username()));
    }

    /** 整体保存护工星期一至星期日的可工作时段。 */
    @PutMapping("/me/availability")
    public ApiResponse<Void> updateAvailability(@RequestBody AvailabilityRequest request) {
        employeeService.updateAvailability(CurrentUser.username(), request);
        return ApiResponse.ok();
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

    /** 按已完成服务参考价统计当前护工累计收入。 */
    @GetMapping("/me/earnings")
    public ApiResponse<BigDecimal> earnings() {
        return ApiResponse.ok(bookingService.completedEarningsAsEmployee(CurrentUser.username()));
    }

    /** 员工标记预约完成。 */
    @PatchMapping("/me/appointments/{id}/complete")
    public ApiResponse<Void> complete(@PathVariable int id) {
        bookingService.completeByEmployee(CurrentUser.username(), id);
        return ApiResponse.ok();
    }

    /** 员工取消分配给自己的预约。 */
    @PatchMapping("/me/appointments/{id}/cancel")
    public ApiResponse<Void> cancel(@PathVariable int id,
                                    @Valid @RequestBody CancelAppointmentRequest request) {
        bookingService.cancelByEmployee(CurrentUser.username(), id, request.getReason());
        return ApiResponse.ok();
    }
}
