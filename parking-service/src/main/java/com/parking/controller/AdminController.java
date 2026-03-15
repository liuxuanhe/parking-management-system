package com.parking.controller;

import com.parking.common.ApiResponse;
import com.parking.common.RequestContext;
import com.parking.common.RequireRole;
import com.parking.dto.AdminCreateRequest;
import com.parking.dto.AdminCreateResponse;
import com.parking.dto.AdminListItem;
import com.parking.service.AdminService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 管理员管理控制器
 * 提供管理员 CRUD、解锁、重置密码等接口
 * Validates: Requirements 13.1, 13.2, 13.6, 13.7
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/admins")
@RequiredArgsConstructor
public class AdminController {

    private final AdminService adminService;

    /**
     * 创建物业管理员（Super_Admin 专属）
     * POST /api/v1/admins
     */
    @PostMapping
    @RequireRole({"super_admin"})
    public ApiResponse<AdminCreateResponse> createAdmin(
            @Valid @RequestBody AdminCreateRequest request,
            HttpServletRequest servletRequest) {
        Long operatorId = (Long) servletRequest.getAttribute("userId");
        log.info("创建物业管理员: username={}, communityId={}, operatorId={}",
                request.getUsername(), request.getCommunityId(), operatorId);
        AdminCreateResponse response = adminService.createAdmin(request, operatorId);
        return ApiResponse.success(response, RequestContext.getRequestId());
    }

    /**
     * 查询管理员列表
     * GET /api/v1/admins
     * Super_Admin 可查看所有，Property_Admin 仅查看本小区
     */
    @GetMapping
    public ApiResponse<List<AdminListItem>> listAdmins(HttpServletRequest servletRequest) {
        String role = (String) servletRequest.getAttribute("userRole");
        Long communityId = (Long) servletRequest.getAttribute("communityId");
        // Super_Admin 查看所有管理员，Property_Admin 仅查看本小区
        Long filterCommunityId = "super_admin".equals(role) ? null : communityId;
        log.info("查询管理员列表: role={}, communityId={}", role, filterCommunityId);
        List<AdminListItem> list = adminService.listAdmins(filterCommunityId);
        return ApiResponse.success(list, RequestContext.getRequestId());
    }

    /**
     * 解锁管理员账号（Super_Admin 专属）
     * POST /api/v1/admins/{adminId}/unlock
     */
    @PostMapping("/{adminId}/unlock")
    @RequireRole({"super_admin"})
    public ApiResponse<Void> unlockAdmin(
            @PathVariable Long adminId,
            HttpServletRequest servletRequest) {
        Long operatorId = (Long) servletRequest.getAttribute("userId");
        log.info("解锁管理员账号: adminId={}, operatorId={}", adminId, operatorId);
        adminService.unlockAdmin(adminId, operatorId);
        return ApiResponse.success(RequestContext.getRequestId());
    }

    /**
     * 重置管理员密码（Super_Admin 专属）
     * POST /api/v1/admins/{adminId}/reset-password
     */
    @PostMapping("/{adminId}/reset-password")
    @RequireRole({"super_admin"})
    public ApiResponse<Map<String, String>> resetPassword(
            @PathVariable Long adminId,
            HttpServletRequest servletRequest) {
        Long operatorId = (Long) servletRequest.getAttribute("userId");
        log.info("重置管理员密码: adminId={}, operatorId={}", adminId, operatorId);
        String newPassword = adminService.resetPassword(adminId, operatorId);
        return ApiResponse.success(Map.of("newPassword", newPassword), RequestContext.getRequestId());
    }
}
