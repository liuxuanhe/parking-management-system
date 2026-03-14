package com.parking.controller;

import com.parking.common.ApiResponse;
import com.parking.common.RequestContext;
import com.parking.dto.OwnerRegisterRequest;
import com.parking.dto.OwnerRegisterResponse;
import com.parking.service.OwnerService;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 业主控制器
 * 处理业主注册等接口
 * Validates: Requirements 1.1, 1.4, 1.5, 1.6, 1.7
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/owners")
public class OwnerController {

    private final OwnerService ownerService;

    public OwnerController(OwnerService ownerService) {
        this.ownerService = ownerService;
    }

    /**
     * 业主注册接口
     * POST /api/v1/owners/register
     *
     * @param request 注册请求
     * @return 注册响应
     */
    @PostMapping("/register")
    public ApiResponse<OwnerRegisterResponse> register(@Valid @RequestBody OwnerRegisterRequest request) {
        log.info("业主注册请求: phone={}, communityId={}, houseNo={}",
                request.getPhone(), request.getCommunityId(), request.getHouseNo());
        OwnerRegisterResponse response = ownerService.register(request);
        return ApiResponse.success(response, RequestContext.getRequestId());
    }
}
