package com.parking.service;

import com.parking.dto.*;

import java.util.List;

/**
 * 管理员服务接口
 * Validates: Requirements 13.1, 13.2, 13.4, 13.5, 13.6, 13.7, 13.8
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

    /**
     * 创建物业管理员（仅 Super_Admin 可执行）
     *
     * @param request    创建请求
     * @param operatorId 操作人ID
     * @return 创建响应（包含初始密码）
     */
    AdminCreateResponse createAdmin(AdminCreateRequest request, Long operatorId);

    /**
     * 查询管理员列表
     * Super_Admin 查看所有，Property_Admin 仅查看本小区
     *
     * @param communityId 小区ID（Super_Admin 传 null 查所有）
     * @return 管理员列表（手机号已脱敏）
     */
    List<AdminListItem> listAdmins(Long communityId);

    /**
     * 解锁管理员账号（仅 Super_Admin 可执行）
     *
     * @param adminId    被解锁的管理员ID
     * @param operatorId 操作人ID
     */
    void unlockAdmin(Long adminId, Long operatorId);

    /**
     * 重置管理员密码（仅 Super_Admin 可执行）
     *
     * @param adminId    被重置的管理员ID
     * @param operatorId 操作人ID
     * @return 新的随机密码
     */
    String resetPassword(Long adminId, Long operatorId);
}
