package com.parking.controller;

import com.parking.common.ApiResponse;
import com.parking.common.RequestContext;
import com.parking.dto.AdminChangePasswordRequest;
import com.parking.dto.AdminLoginRequest;
import com.parking.dto.AdminLoginResponse;
import com.parking.service.AdminService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 认证控制器
 * 处理管理员登录、修改密码等接口
 * Validates: Requirements 13.4, 13.5, 13.6, 13.8
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AdminService adminService;

    public AuthController(AdminService adminService) {
        this.adminService = adminService;
    }

    /**
     * 管理员登录接口
     * POST /api/v1/auth/login
     *
     * @param request        登录请求
     * @param servletRequest HTTP 请求（用于获取客户端IP）
     * @return 登录响应
     */
    @PostMapping("/login")
    public ApiResponse<AdminLoginResponse> login(
            @Valid @RequestBody AdminLoginRequest request,
            HttpServletRequest servletRequest) {
        String clientIp = getClientIp(servletRequest);
        log.info("管理员登录请求: username={}, ip={}", request.getUsername(), clientIp);
        AdminLoginResponse response = adminService.login(request, clientIp);
        return ApiResponse.success(response, RequestContext.getRequestId());
    }

    /**
     * 修改密码接口（首次登录强制修改）
     * POST /api/v1/auth/change-password
     *
     * @param request        修改密码请求
     * @param servletRequest HTTP 请求（用于获取当前登录管理员ID）
     * @return 成功响应
     */
    @PostMapping("/change-password")
    public ApiResponse<Void> changePassword(
            @Valid @RequestBody AdminChangePasswordRequest request,
            HttpServletRequest servletRequest) {
        // 从请求属性中获取当前登录管理员ID（由 AuthenticationInterceptor 设置）
        Long adminId = (Long) servletRequest.getAttribute("userId");
        log.info("管理员修改密码请求: adminId={}", adminId);
        adminService.changePassword(adminId, request);
        return ApiResponse.success(RequestContext.getRequestId());
    }

    /**
     * 获取客户端真实IP
     * 优先从 X-Forwarded-For 和 X-Real-IP 头获取
     */
    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
            // X-Forwarded-For 可能包含多个IP，取第一个
            return ip.split(",")[0].trim();
        }
        ip = request.getHeader("X-Real-IP");
        if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
            return ip;
        }
        return request.getRemoteAddr();
    }
}
