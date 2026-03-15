package com.parking.controller;

import com.parking.common.ApiResponse;
import com.parking.common.RequestContext;
import com.parking.common.RequireRole;
import com.parking.model.ExportTask;
import com.parking.service.ExportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * 导出功能控制器
 * Validates: Requirements 16.4, 16.6
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/exports")
@RequiredArgsConstructor
public class ExportController {

    private final ExportService exportService;

    /**
     * 创建入场记录导出任务
     * POST /api/v1/exports/parking-records
     */
    @PostMapping("/parking-records")
    public ApiResponse<ExportTask> exportParkingRecords(
            @RequestParam Long communityId,
            @RequestParam Long operatorId,
            @RequestParam String operatorName,
            @RequestParam(required = false) String queryParams,
            @RequestParam(defaultValue = "0") Integer needRawData) {
        log.info("导出入场记录: communityId={}, operatorId={}", communityId, operatorId);
        ExportTask task = exportService.createParkingRecordExport(
                communityId, operatorId, operatorName, queryParams, needRawData);
        return ApiResponse.success(task, RequestContext.getRequestId());
    }

    /**
     * 创建原始数据导出任务（需超级管理员权限 + IP 白名单校验）
     * POST /api/v1/exports/parking-records/raw
     */
    @PostMapping("/parking-records/raw")
    @RequireRole({"super_admin"})
    public ApiResponse<ExportTask> exportRawParkingRecords(
            @RequestParam Long communityId,
            @RequestParam Long operatorId,
            @RequestParam String operatorName,
            @RequestParam(required = false) String queryParams,
            @RequestParam String role,
            jakarta.servlet.http.HttpServletRequest request) {
        String ip = request.getRemoteAddr();
        log.info("原始数据导出: communityId={}, operatorId={}, ip={}", communityId, operatorId, ip);
        ExportTask task = exportService.createRawDataExport(
                communityId, operatorId, operatorName, queryParams, role, ip);
        return ApiResponse.success(task, RequestContext.getRequestId());
    }

    /**
     * 查询导出任务状态
     * GET /api/v1/exports/{exportId}/status
     */
    @GetMapping("/{exportId}/status")
    public ApiResponse<ExportTask> getExportStatus(@PathVariable Long exportId) {
        log.info("查询导出任务状态: exportId={}", exportId);
        ExportTask task = exportService.getExportTaskStatus(exportId);
        if (task == null) {
            return ApiResponse.error(10000, "导出任务不存在", RequestContext.getRequestId());
        }
        return ApiResponse.success(task, RequestContext.getRequestId());
    }

    /**
     * 下载导出文件
     * GET /api/v1/exports/{exportId}/download
     * 验证文件未过期后返回文件下载信息
     */
    @GetMapping("/{exportId}/download")
    public ApiResponse<ExportTask> downloadExportFile(@PathVariable Long exportId) {
        log.info("下载导出文件: exportId={}", exportId);
        ExportTask task = exportService.getDownloadableTask(exportId);
        return ApiResponse.success(task, RequestContext.getRequestId());
    }
}
