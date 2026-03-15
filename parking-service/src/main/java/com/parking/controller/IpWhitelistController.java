package com.parking.controller;

import com.parking.common.ApiResponse;
import com.parking.common.RequestContext;
import com.parking.common.RequireRole;
import com.parking.model.IpWhitelist;
import com.parking.service.IpWhitelistService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * IP 白名单管理控制器
 * 仅允许超级管理员操作
 * Validates: Requirements 20.4, 20.5, 20.6
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/ip-whitelist")
@RequiredArgsConstructor
public class IpWhitelistController {

    private final IpWhitelistService ipWhitelistService;

    /**
     * 添加 IP 白名单
     * POST /api/v1/ip-whitelist
     */
    @PostMapping
    @RequireRole({"super_admin"})
    public ApiResponse<IpWhitelist> addIpWhitelist(
            @RequestParam Long communityId,
            @RequestParam String ipAddress,
            @RequestParam(required = false) String ipRange,
            @RequestParam String operationType,
            @RequestParam(required = false) String description,
            @RequestParam Long adminId) {
        log.info("添加 IP 白名单: communityId={}, ip={}", communityId, ipAddress);
        IpWhitelist result = ipWhitelistService.addIpWhitelist(
                communityId, ipAddress, ipRange, operationType, description, adminId);
        return ApiResponse.success(result, RequestContext.getRequestId());
    }

    /**
     * 删除 IP 白名单
     * DELETE /api/v1/ip-whitelist/{id}
     */
    @DeleteMapping("/{id}")
    @RequireRole({"super_admin"})
    public ApiResponse<Void> deleteIpWhitelist(@PathVariable Long id) {
        log.info("删除 IP 白名单: id={}", id);
        ipWhitelistService.deleteIpWhitelist(id);
        return ApiResponse.success(null, RequestContext.getRequestId());
    }

    /**
     * 查询 IP 白名单列表
     * GET /api/v1/ip-whitelist
     */
    @GetMapping
    @RequireRole({"super_admin"})
    public ApiResponse<List<IpWhitelist>> listIpWhitelist() {
        log.info("查询 IP 白名单列表");
        List<IpWhitelist> list = ipWhitelistService.listIpWhitelist();
        return ApiResponse.success(list, RequestContext.getRequestId());
    }
}
