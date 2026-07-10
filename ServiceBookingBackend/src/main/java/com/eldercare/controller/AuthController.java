package com.eldercare.controller;

import com.eldercare.common.ApiResponse;
import com.eldercare.dto.LoginRequest;
import com.eldercare.dto.LoginResponse;
import com.eldercare.dto.RegisterRequest;
import com.eldercare.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

/** 认证接口:注册、登录(无需令牌)。 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public ApiResponse<Void> register(@Valid @RequestBody RegisterRequest req) {
        authService.register(req);
        return ApiResponse.ok();
    }

    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest req) {
        return ApiResponse.ok(authService.login(req));
    }
}
