package com.parking.controller;

import com.parking.common.ApiResponse;
import com.parking.common.RequestContext;
import com.parking.dto.BatchAuditRequest;
import com.parking.dto.BatchAuditResponse;
import com.parking.dto.VisitorApplyRequest;
import com.parking.dto.VisitorApplyResponse;
import com.parking.dto.VisitorAuditRequest;
import com.parking.dto.VisitorQueryResponse;
import com.parking.dto.VisitorQuotaResponse;
import com.parking.service.BatchAuditService;
import com.parking.service.VisitorService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Visitor 权限控制器
 * Validates: Requirements 7.1, 7.2, 7.3, 7.4, 9.2
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/visitors")
@RequiredArgsConstructor
public class VisitorController {

    private final VisitorService visitorService;
    private final BatchAuditService batchAuditService;

    /**
     * 申请 Visitor 权限
     * POST /api/v1/visitors/apply
     */
    @PostMapping("/apply")
    public ApiResponse<VisitorApplyResponse> apply(@Valid @RequestBody VisitorApplyRequest request,
                                                    @RequestParam Long ownerId,
                                                    @RequestParam Long communityId,
                                                    @RequestParam String houseNo) {
        log.info("Visitor 申请请求: carNumber={}, communityId={}, houseNo={}, ownerId={}",
                request.getCarNumber(), communityId, houseNo, ownerId);
        VisitorApplyResponse response = visitorService.apply(request, ownerId, communityId, houseNo);
        return ApiResponse.success(response, RequestContext.getRequestId());
    }

    /**
     * 审批 Visitor 申请
     * POST /api/v1/visitors/{visitorId}/audit
     */
    @PostMapping("/{visitorId}/audit")
    public ApiResponse<Void> audit(@PathVariable Long visitorId,
                                    @Valid @RequestBody VisitorAuditRequest request,
                                    @RequestParam Long adminId,
                                    @RequestParam Long communityId) {
        log.info("Visitor 审批请求: visitorId={}, action={}, adminId={}, communityId={}",
                visitorId, request.getAction(), adminId, communityId);
        visitorService.audit(visitorId, request, adminId, communityId);
        return ApiResponse.success(RequestContext.getRequestId());
    }

    /**
     * 查询 Visitor 权限列表
     * GET /api/v1/visitors
     */
    @GetMapping
    public ApiResponse<List<VisitorQueryResponse>> listVisitors(@RequestParam Long communityId,
                                                                 @RequestParam String houseNo) {
        log.info("查询 Visitor 权限列表: communityId={}, houseNo={}", communityId, houseNo);
        List<VisitorQueryResponse> result = visitorService.listVisitors(communityId, houseNo);
        return ApiResponse.success(result, RequestContext.getRequestId());
    }

    /**
     * 查询 Visitor 月度配额
     * GET /api/v1/visitors/quota
     */
    @GetMapping("/quota")
    public ApiResponse<VisitorQuotaResponse> getQuota(@RequestParam Long communityId,
                                                       @RequestParam String houseNo) {
        log.info("查询 Visitor 月度配额: communityId={}, houseNo={}", communityId, houseNo);
        VisitorQuotaResponse response = visitorService.getQuota(communityId, houseNo);
        return ApiResponse.success(response, RequestContext.getRequestId());
    }

    /**
     * 批量审批 Visitor 接口
     * POST /api/v1/visitors/batch-audit
     * 限制每次最多处理50条记录，使用事务和幂等键
     * Validates: Requirements 23.1, 23.2, 23.3, 23.4, 23.5, 23.6, 23.7
     *
     * @param request     批量审批请求
     * @param adminId     管理员ID
     * @param communityId 小区ID
     * @return 批量审批响应
     */
    @PostMapping("/batch-audit")
    public ApiResponse<BatchAuditResponse> batchAudit(@Valid @RequestBody BatchAuditRequest request,
                                                       @RequestParam Long adminId,
                                                       @RequestParam Long communityId) {
        log.info("批量审批 Visitor 请求: adminId={}, communityId={}, 数量={}, action={}",
                adminId, communityId, request.getIds().size(), request.getAction());
        BatchAuditResponse response = batchAuditService.batchAuditVisitors(request, adminId, communityId);
        return ApiResponse.success(response, RequestContext.getRequestId());
    }
}
