package com.eldercare.security;

import com.eldercare.common.ApiResponse;
import com.eldercare.repository.LoginSessionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 认证与鉴权拦截器:
 * - 校验请求头 Authorization: Bearer <token>
 * - 解析出 username/role 存入 CurrentUser
 * - 对 /api/admin/** 强制要求 ADMIN 角色
 */
@Component
public class AuthInterceptor implements HandlerInterceptor {

    private final JwtUtil jwtUtil;
    private final LoginSessionRepository sessionRepo;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AuthInterceptor(JwtUtil jwtUtil, LoginSessionRepository sessionRepo) {
        this.jwtUtil = jwtUtil;
        this.sessionRepo = sessionRepo;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 放行预检请求
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            return reject(response, 401, "未登录或令牌缺失");
        }

        String token = header.substring(7);
        String username;
        String role;
        String sessionId;
        try {
            Claims claims = jwtUtil.parse(token);
            username = claims.getSubject();
            role = claims.get("role", String.class);
            sessionId = claims.get("sid", String.class);
        } catch (Exception e) {
            return reject(response, 401, "登录已过期或令牌无效,请重新登录");
        }

        if (sessionId == null || !sessionRepo.isCurrent(username, role, sessionId)) {
            return reject(response, 401, "该账号已在其他设备登录，请重新登录");
        }

        // 管理后台接口:仅 ADMIN 可访问
        String path = request.getRequestURI();
        if (path.startsWith("/api/admin/") && !CurrentUser.ROLE_ADMIN.equals(role)) {
            return reject(response, 403, "无权限访问");
        }

        CurrentUser.set(username, role);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        CurrentUser.clear();     // 请求结束清理 ThreadLocal,避免线程复用串号
    }

    private boolean reject(HttpServletResponse response, int code, String message) throws Exception {
        response.setStatus(code == 401 ? HttpServletResponse.SC_UNAUTHORIZED : HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(ApiResponse.error(code, message)));
        return false;
    }
}
