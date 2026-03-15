package com.parking.service.impl;

import com.parking.common.BusinessException;
import com.parking.common.ErrorCode;
import com.parking.common.OperationLogAnnotation;
import com.parking.common.PasswordValidator;
import com.parking.dto.*;
import com.parking.mapper.AdminMapper;
import com.parking.model.Admin;
import com.parking.service.AdminService;
import com.parking.service.JwtTokenService;
import com.parking.service.MaskingService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;

/**
 * 管理员服务实现类
 * Validates: Requirements 13.1, 13.2, 13.4, 13.5, 13.6, 13.7, 13.8
 */
@Slf4j
@Service
public class AdminServiceImpl implements AdminService {

    /** 最大登录失败次数，达到后锁定账号 */
    private static final int MAX_LOGIN_FAIL_COUNT = 5;

    /** 随机密码字符集 */
    private static final String PASSWORD_CHARS =
            "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!@#$%^&*";

    private final AdminMapper adminMapper;
    private final JwtTokenService jwtTokenService;
    private final MaskingService maskingService;
    private final BCryptPasswordEncoder passwordEncoder;
    private final SecureRandom secureRandom;

    public AdminServiceImpl(AdminMapper adminMapper,
                            JwtTokenService jwtTokenService,
                            MaskingService maskingService) {
        this.adminMapper = adminMapper;
        this.jwtTokenService = jwtTokenService;
        this.maskingService = maskingService;
        this.passwordEncoder = new BCryptPasswordEncoder();
        this.secureRandom = new SecureRandom();
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
            int newFailCount = (admin.getLoginFailCount() == null ? 0 : admin.getLoginFailCount()) + 1;
            adminMapper.updateLoginFail(admin.getId());

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
        Admin admin = adminMapper.selectById(adminId);
        if (admin == null) {
            throw new BusinessException(ErrorCode.PARKING_13004);
        }

        if (!passwordEncoder.matches(request.getOldPassword(), admin.getPassword())) {
            log.warn("修改密码失败：旧密码错误, adminId={}", adminId);
            throw new BusinessException(ErrorCode.PARKING_13004);
        }

        if (!PasswordValidator.isValid(request.getNewPassword())) {
            log.warn("修改密码失败：新密码强度不足, adminId={}", adminId);
            throw new BusinessException(ErrorCode.PARAM_ERROR.getCode(), "新密码强度不足：至少8位，包含大小写字母、数字、特殊字符");
        }

        String encodedPassword = passwordEncoder.encode(request.getNewPassword());
        adminMapper.updatePassword(adminId, encodedPassword, 0);

        log.info("管理员修改密码成功: adminId={}", adminId);
    }

    @Override
    @OperationLogAnnotation(operationType = "CREATE", targetType = "admin")
    public AdminCreateResponse createAdmin(AdminCreateRequest request, Long operatorId) {
        // 1. 仅允许创建 property_admin 角色
        if (!"property_admin".equals(request.getRole())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR.getCode(), "仅允许创建 property_admin 角色");
        }

        // 2. 检查用户名是否已存在
        Admin existing = adminMapper.selectByUsername(request.getUsername());
        if (existing != null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR.getCode(), "用户名已存在");
        }

        // 3. 生成随机初始密码
        String rawPassword = generateRandomPassword(12);
        String encodedPassword = passwordEncoder.encode(rawPassword);

        // 4. 创建管理员记录
        Admin admin = new Admin();
        admin.setCommunityId(request.getCommunityId());
        admin.setUsername(request.getUsername());
        admin.setPassword(encodedPassword);
        admin.setRealName(request.getRealName());
        admin.setPhoneNumber(request.getPhoneNumber());
        admin.setRole("property_admin");
        admin.setStatus("active");
        admin.setLoginFailCount(0);
        admin.setMustChangePassword(1);
        adminMapper.insert(admin);

        log.info("创建物业管理员成功: adminId={}, username={}, communityId={}, operatorId={}",
                admin.getId(), admin.getUsername(), admin.getCommunityId(), operatorId);

        // 5. 构建响应
        AdminCreateResponse response = new AdminCreateResponse();
        response.setAdminId(admin.getId());
        response.setUsername(admin.getUsername());
        response.setInitialPassword(rawPassword);
        response.setRole(admin.getRole());
        response.setCommunityId(admin.getCommunityId());
        return response;
    }

    @Override
    public List<AdminListItem> listAdmins(Long communityId) {
        List<Admin> admins = adminMapper.selectList(communityId);
        List<AdminListItem> result = new ArrayList<>();
        for (Admin admin : admins) {
            AdminListItem item = new AdminListItem();
            item.setId(admin.getId());
            item.setUsername(admin.getUsername());
            item.setRealName(admin.getRealName());
            item.setRole(admin.getRole());
            item.setCommunityId(admin.getCommunityId());
            item.setStatus(admin.getStatus());
            // 手机号脱敏
            item.setPhoneNumber(maskingService.maskPhoneNumber(admin.getPhoneNumber()));
            item.setLastLoginTime(admin.getLastLoginTime());
            item.setCreateTime(admin.getCreateTime());
            result.add(item);
        }
        return result;
    }

    @Override
    @OperationLogAnnotation(operationType = "UPDATE", targetType = "admin")
    public void unlockAdmin(Long adminId, Long operatorId) {
        Admin admin = adminMapper.selectById(adminId);
        if (admin == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR.getCode(), "管理员不存在");
        }
        if (!"locked".equals(admin.getStatus())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR.getCode(), "该管理员未被锁定");
        }
        adminMapper.unlockAccount(adminId);
        log.info("解锁管理员账号成功: adminId={}, operatorId={}", adminId, operatorId);
    }

    @Override
    @OperationLogAnnotation(operationType = "UPDATE", targetType = "admin")
    public String resetPassword(Long adminId, Long operatorId) {
        Admin admin = adminMapper.selectById(adminId);
        if (admin == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR.getCode(), "管理员不存在");
        }

        String rawPassword = generateRandomPassword(12);
        String encodedPassword = passwordEncoder.encode(rawPassword);
        adminMapper.resetPassword(adminId, encodedPassword, 1);

        log.info("重置管理员密码成功: adminId={}, operatorId={}", adminId, operatorId);
        return rawPassword;
    }

    /**
     * 生成随机密码，确保包含大小写字母、数字和特殊字符
     */
    private String generateRandomPassword(int length) {
        StringBuilder sb = new StringBuilder(length);
        // 确保至少包含各类字符
        sb.append((char) ('A' + secureRandom.nextInt(26)));
        sb.append((char) ('a' + secureRandom.nextInt(26)));
        sb.append((char) ('0' + secureRandom.nextInt(10)));
        sb.append("!@#$%^&*".charAt(secureRandom.nextInt(8)));
        // 填充剩余字符
        for (int i = 4; i < length; i++) {
            sb.append(PASSWORD_CHARS.charAt(secureRandom.nextInt(PASSWORD_CHARS.length())));
        }
        // 打乱顺序
        char[] chars = sb.toString().toCharArray();
        for (int i = chars.length - 1; i > 0; i--) {
            int j = secureRandom.nextInt(i + 1);
            char tmp = chars[i];
            chars[i] = chars[j];
            chars[j] = tmp;
        }
        return new String(chars);
    }
}
