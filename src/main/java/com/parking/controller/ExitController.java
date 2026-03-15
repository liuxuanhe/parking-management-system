package com.parking.controller;

import com.parking.common.ApiResponse;
import com.parking.common.RequestContext;
import com.parking.dto.ExitRequest;
import com.parking.dto.ExitResponse;
import com.parking.service.ExitService;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 车辆出场控制器
 * 处理 POST /api/v1/parking/exit 接口
 * Validates: Requirements 6.1, 6.2, 6.3
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/parking")
public class ExitController {

    private final ExitService exitService;

    public ExitController(ExitService exitService) {
        this.exitService = exitService;
    }

    /**
     * 车辆出场接口
     * POST /api/v1/parking/exit
     *
     * @param request 出场请求（包含 communityId 和 carNumber）
     * @return 出场响应
     */
    @PostMapping("/exit")
    public ApiResponse<ExitResponse> vehicleExit(@Valid @RequestBody ExitRequest request) {
        log.info("车辆出场请求: communityId={}, carNumber={}",
                request.getCommunityId(), request.getCarNumber());
        ExitResponse response = exitService.vehicleExit(request);
        return ApiResponse.success(response, RequestContext.getRequestId());
    }
}
