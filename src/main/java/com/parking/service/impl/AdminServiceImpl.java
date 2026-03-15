package com.parking.service.impl;

import com.parking.common.BusinessException;
import com.parking.common.ErrorCode;
import com.parking.common.PasswordValidator;
import com.parking.dto.AdminChangePasswordRequest;
import com.parking.dto.AdminLoginRequest;
import com.parking.dto.AdminLoginResponse;
import com.parking.mapper.AdminMapper;
import com.parking.model.Admin;
import com.parking.service.AdminService;
import com.parking.service.JwtTokenService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * 管理员服务实现类
 * 实现管理员登录、修改密码等功能
 * Validates: Requirements 13.4, 13.5, 13.6, 13.8
 */
@Slf4j
@Service
public class AdminServiceImpl implements AdminService {

    /** 最大登录失败次数，达到后锁定账号 */
    private static final int MAX_LOGIN_FAIL_COUNT = 5;

    private final AdminMapper adminMapper;
    private final JwtTokenService jwtTokenService;
    private final BCryptPasswordEncoder passwordEncoder;

    public AdminServiceImpl(AdminMapper adminMapper, JwtTokenService jwtTokenService) {
        this.adminMapper = adminMapper;
        this.jwtTokenService = jwtTokenService;
        this.passwordEncoder = new BCryptPasswordEncoder();
    }

    @Override
    public AdminLoginResponse login(AdminLoginRequest request, String clientIp) {
        // 1. 根据用户名查询管理员
        Admin admin = adminMapper.selectByUsername(request.getUsername());
        if (admin == null) {
            log.warn("登录失败：用户名不存在, username={}", request.getUsername());
            throw new BusinessException(ErrorCode.PARKING_13004);
        }

        // 2. 验证账号状态（非 locked）
        if ("locked".equals(admin.getStatus())) {
            log.warn("登录失败：账号已被锁定, username={}", request.getUsername());
            throw new BusinessException(ErrorCode.PARKING_13005);
        }

        // 3. BCrypt 验证密码
        if (!passwordEncoder.matches(request.getPassword(), admin.getPassword())) {
            // 密码错误：增加 loginFailCount
            int newFailCount = (admin.getLoginFailCount() == null ? 0 : admin.getLoginFailCount()) + 1;
            adminMapper.updateLoginFail(admin.getId());

            // 达到5次锁定账号
            if (newFailCount >= MAX_LOGIN_FAIL_COUNT) {
                adminMapper.lockAccount(admin.getId());
                log.warn("账号已被锁定：连续登录失败{}次, username={}", newFailCount, request.getUsername());
                throw new BusinessException(ErrorCode.PARKING_13005);
            }

            log.warn("登录失败：密码错误, username={}, failCount={}", request.getUsername(), newFailCount);
            throw new BusinessException(ErrorCode.PARKING_13004);
        }

        // 4. 密码正确：重置 loginFailCount，更新 lastLoginTime 和 lastLoginIp
        adminMapper.updateLoginSuccess(admin.getId(), clientIp);

        // 5. 生成 JWT Access Token 和 Refresh Token
        String accessToken = jwtTokenService.generateAccessToken(
                admin.getId(), admin.getRole(), admin.getCommunityId(), null);
        String refreshToken = jwtTokenService.generateRefreshToken(admin.getId());

        // 6. 构建响应
        AdminLoginResponse response = new AdminLoginResponse();
        response.setAccessToken(accessToken);
        response.setRefreshToken(refreshToken);
        response.setMustChangePassword(admin.getMustChangePassword() != null && admin.getMustChangePassword() == 1);
        response.setAdminId(admin.getId());
        response.setRole(admin.getRole());
        response.setCommunityId(admin.getCommunityId());

        log.info("管理员登录成功: username={}, adminId={}, role={}", admin.getUsername(), admin.getId(), admin.getRole());
        return response;
    }

    @Override
    public void changePassword(Long adminId, AdminChangePasswordRequest request) {
        // 1. 根据ID查询管理员
        Admin admin = adminMapper.selectById(adminId);
        if (admin == null) {
            throw new BusinessException(ErrorCode.PARKING_13004);
        }

        // 2. 验证旧密码
        if (!passwordEncoder.matches(request.getOldPassword(), admin.getPassword())) {
            log.warn("修改密码失败：旧密码错误, adminId={}", adminId);
            throw new BusinessException(ErrorCode.PARKING_13004);
        }

        // 3. 验证新密码强度
        if (!PasswordValidator.isValid(request.getNewPassword())) {
            log.warn("修改密码失败：新密码强度不足, adminId={}", adminId);
            throw new BusinessException(ErrorCode.PARAM_ERROR.getCode(), "新密码强度不足：至少8位，包含大小写字母、数字、特殊字符");
        }

        // 4. BCrypt 加密新密码，更新密码，设置 mustChangePassword=0
        String encodedPassword = passwordEncoder.encode(request.getNewPassword());
        adminMapper.updatePassword(adminId, encodedPassword, 0);

        log.info("管理员修改密码成功: adminId={}", adminId);
    }
}
