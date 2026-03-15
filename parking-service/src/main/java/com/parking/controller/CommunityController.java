package com.parking.controller;

import com.parking.common.ApiResponse;
import com.parking.common.RequestContext;
import com.parking.common.RequireRole;
import com.parking.model.Community;
import com.parking.service.CommunityService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 小区管理控制器
 * 提供小区列表查询与切换功能
 * Validates: Requirements 12.2, 12.3
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class CommunityController {

    private final CommunityService communityService;

    /**
     * 查询小区列表
     * GET /api/v1/communities
     * Super_Admin 返回所有小区，Property_Admin 仅返回本小区
     */
    @GetMapping("/api/v1/communities")
    public ApiResponse<List<Community>> listCommunities(HttpServletRequest servletRequest) {
        String role = (String) servletRequest.getAttribute("userRole");
        Long communityId = (Long) servletRequest.getAttribute("communityId");
        log.info("查询小区列表: role={}, communityId={}", role, communityId);
        List<Community> list = communityService.listCommunities(role, communityId);
        return ApiResponse.success(list, RequestContext.getRequestId());
    }

    /**
     * Super_Admin 切换当前操作小区
     * POST /api/v1/auth/switch-community
     * 重新签发包含新 communityId 的 Access Token
     */
    @PostMapping("/api/v1/auth/switch-community")
    @RequireRole({"super_admin"})
    public ApiResponse<Map<String, String>> switchCommunity(
            @RequestBody Map<String, Long> body,
            HttpServletRequest servletRequest) {
        Long adminId = (Long) servletRequest.getAttribute("userId");
        Long targetCommunityId = body.get("communityId");
        log.info("切换操作小区: adminId={}, targetCommunityId={}", adminId, targetCommunityId);
        String newToken = communityService.switchCommunity(adminId, targetCommunityId);
        return ApiResponse.success(Map.of("accessToken", newToken), RequestContext.getRequestId());
    }
}
