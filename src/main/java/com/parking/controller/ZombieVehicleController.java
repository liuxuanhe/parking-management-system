package com.parking.controller;

import com.parking.common.ApiResponse;
import com.parking.common.RequestContext;
import com.parking.dto.ZombieVehicleHandleRequest;
import com.parking.dto.ZombieVehicleQueryResponse;
import com.parking.service.ZombieVehicleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 僵尸车辆控制器
 * Validates: Requirements 22.9
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/zombie-vehicles")
@RequiredArgsConstructor
public class ZombieVehicleController {

    private final ZombieVehicleService zombieVehicleService;

    /**
     * 查询僵尸车辆列表
     * GET /api/v1/zombie-vehicles?communityId={}&status={}
     */
    @GetMapping
    public ApiResponse<List<ZombieVehicleQueryResponse>> listZombieVehicles(
            @RequestParam Long communityId,
            @RequestParam(required = false) String status) {
        log.info("查询僵尸车辆列表: communityId={}, status={}", communityId, status);
        List<ZombieVehicleQueryResponse> list = zombieVehicleService.listZombieVehicles(communityId, status);
        return ApiResponse.success(list, RequestContext.getRequestId());
    }

    /**
     * 处理僵尸车辆
     * POST /api/v1/zombie-vehicles/{zombieId}/handle
     */
    @PostMapping("/{zombieId}/handle")
    public ApiResponse<Void> handleZombieVehicle(
            @PathVariable Long zombieId,
            @Valid @RequestBody ZombieVehicleHandleRequest request) {
        log.info("处理僵尸车辆: zombieId={}, handleType={}", zombieId, request.getHandleType());
        // TODO: 从认证上下文获取 adminId，暂用占位值
        Long adminId = 1L;
        zombieVehicleService.handleZombieVehicle(zombieId, request, adminId);
        return ApiResponse.success(null, RequestContext.getRequestId());
    }
}
