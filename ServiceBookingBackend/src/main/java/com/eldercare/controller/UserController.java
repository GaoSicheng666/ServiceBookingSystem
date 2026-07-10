package com.eldercare.controller;

import com.eldercare.common.ApiResponse;
import com.eldercare.entity.Appointment;
import com.eldercare.entity.Employee;
import com.eldercare.entity.User;
import com.eldercare.dto.BookingRequest;
import com.eldercare.service.BookingService;
import com.eldercare.service.UserService;
import com.eldercare.security.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/** 用户(老人)侧接口。 */
@RestController
@RequestMapping("/api")
public class UserController {

    private final UserService userService;
    private final BookingService bookingService;

    public UserController(UserService userService, BookingService bookingService) {
        this.userService = userService;
        this.bookingService = bookingService;
    }

    /** 我的信息。 */
    @GetMapping("/users/me")
    public ApiResponse<User> me() {
        return ApiResponse.ok(userService.getSelf(CurrentUser.username()));
    }

    /** 指定日期可预约的员工。 */
    @GetMapping("/employees/available")
    public ApiResponse<List<Employee>> availableEmployees(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        List<Employee> list = bookingService.availableEmployees(date);
        list.forEach(e -> e.setPassword(null));
        return ApiResponse.ok(list);
    }

    /** 下预约单。 */
    @PostMapping("/appointments")
    public ApiResponse<Void> book(@Valid @RequestBody BookingRequest req) {
        bookingService.book(CurrentUser.username(), req);
        return ApiResponse.ok();
    }

    /** 我的预约 + 历史(可按状态过滤)。 */
    @GetMapping("/appointments/me")
    public ApiResponse<List<Appointment>> myAppointments(@RequestParam(required = false) String status) {
        return ApiResponse.ok(bookingService.myAppointmentsAsUser(CurrentUser.username(), status));
    }

    /** 取消我的预约。 */
    @PatchMapping("/appointments/{id}/cancel")
    public ApiResponse<Void> cancel(@PathVariable int id) {
        bookingService.cancelByUser(CurrentUser.username(), id);
        return ApiResponse.ok();
    }
}
