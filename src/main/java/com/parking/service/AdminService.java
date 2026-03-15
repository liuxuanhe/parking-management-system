package com.parking.service;

import com.parking.dto.AdminChangePasswordRequest;
import com.parking.dto.AdminLoginRequest;
import com.parking.dto.AdminLoginResponse;

/**
 * 管理员服务接口
 * Validates: Requirements 13.4, 13.5, 13.6, 13.8
 */
public interface AdminService {

    /**
     * 管理员登录
     *
     * @param request  登录请求
     * @param clientIp 客户端IP
     * @return 登录响应（包含 Token 和 mustChangePassword 标志）
     */
    AdminLoginResponse login(AdminLoginRequest request, String clientIp);

    /**
     * 修改密码（首次登录强制修改）
     *
     * @param adminId 管理员ID
     * @param request 修改密码请求
     */
    void changePassword(Long adminId, AdminChangePasswordRequest request);
}
