package com.parking.controller;

import com.parking.common.ApiResponse;
import com.parking.common.RequestContext;
import com.parking.model.AccessLog;
import com.parking.model.ExportTask;
import com.parking.model.OperationLog;
import com.parking.service.AuditLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 审计日志控制器
 * Validates: Requirements 18.8
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/audit")
@RequiredArgsConstructor
public class AuditLogController {

    private final AuditLogService auditLogService;

    /**
     * 查询操作日志
     * GET /api/v1/audit/operation-logs
     */
    @GetMapping("/operation-logs")
    public ApiResponse<List<OperationLog>> queryOperationLogs(
            @RequestParam(required = false) Long communityId,
            @RequestParam(required = false) Long operatorId,
            @RequestParam(required = false) String operationType,
            @RequestParam(required = false) String startTime,
            @RequestParam(required = false) String endTime) {
        log.info("查询操作日志: communityId={}, operatorId={}", communityId, operatorId);
        List<OperationLog> logs = auditLogService.queryOperationLogs(
                communityId, operatorId, operationType, startTime, endTime);
        return ApiResponse.success(logs, RequestContext.getRequestId());
    }

    /**
     * 查询访问日志
     * GET /api/v1/audit/access-logs
     */
    @GetMapping("/access-logs")
    public ApiResponse<List<AccessLog>> queryAccessLogs(
            @RequestParam(required = false) Long communityId,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String apiPath,
            @RequestParam(required = false) String startTime,
            @RequestParam(required = false) String endTime) {
        log.info("查询访问日志: communityId={}, userId={}", communityId, userId);
        List<AccessLog> logs = auditLogService.queryAccessLogs(
                communityId, userId, apiPath, startTime, endTime);
        return ApiResponse.success(logs, RequestContext.getRequestId());
    }

    /**
     * 导出审计日志（异步任务）
     * POST /api/v1/audit/logs/export
     * 需要超级管理员权限
     */
    @PostMapping("/logs/export")
    public ApiResponse<ExportTask> exportAuditLogs(
            @RequestParam Long communityId,
            @RequestParam Long operatorId,
            @RequestParam String operatorName,
            @RequestParam(defaultValue = "audit_log") String exportType,
            @RequestParam(required = false) String queryParams,
            @RequestParam(defaultValue = "0") Integer needRawData) {
        log.info("导出审计日志: communityId={}, operatorId={}, exportType={}", communityId, operatorId, exportType);
        ExportTask task = auditLogService.exportAuditLogs(
                communityId, operatorId, operatorName, exportType, queryParams, needRawData);
        return ApiResponse.success(task, RequestContext.getRequestId());
    }
}
