package com.eldercare.controller;

import com.eldercare.common.ApiResponse;
import com.eldercare.entity.ServiceItem;
import com.eldercare.service.BookingService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 服务项目查询(登录用户可读;维护在管理后台)。 */
@RestController
@RequestMapping("/api/services")
public class ServiceController {

    private final BookingService bookingService;

    public ServiceController(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    @GetMapping
    public ApiResponse<List<ServiceItem>> list() {
        return ApiResponse.ok(bookingService.availableServices());
    }
}
