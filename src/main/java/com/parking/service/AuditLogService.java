package com.parking.service;

import com.parking.model.AccessLog;
import com.parking.model.ExportTask;
import com.parking.model.OperationLog;

import java.util.List;

/**
 * 审计日志服务接口
 * Validates: Requirements 18.8
 */
public interface AuditLogService {

    /**
     * 查询操作日志
     */
    List<OperationLog> queryOperationLogs(Long communityId, Long operatorId,
                                           String operationType, String startTime, String endTime);

    /**
     * 查询访问日志
     */
    List<AccessLog> queryAccessLogs(Long communityId, Long userId,
                                     String apiPath, String startTime, String endTime);

    /**
     * 导出审计日志（异步任务）
     */
    ExportTask exportAuditLogs(Long communityId, Long operatorId, String operatorName,
                                String exportType, String queryParams, Integer needRawData);
}
