package com.parking.controller;

import com.parking.common.ApiResponse;
import com.parking.common.RequestContext;
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
}
