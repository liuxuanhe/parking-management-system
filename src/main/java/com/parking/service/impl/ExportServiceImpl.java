package com.parking.service.impl;

import com.parking.common.BusinessException;
import com.parking.common.ErrorCode;
import com.parking.mapper.ExportTaskMapper;
import com.parking.model.ExportTask;
import com.parking.service.AuthorizationService;
import com.parking.service.ExportService;
import com.parking.service.ExportTaskProcessor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 导出服务实现
 * Validates: Requirements 16.4, 16.5, 16.6, 17.3, 17.4, 17.5
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExportServiceImpl implements ExportService {

    private final ExportTaskMapper exportTaskMapper;
    private final ExportTaskProcessor exportTaskProcessor;
    private final AuthorizationService authorizationService;

    @Override
    public ExportTask createParkingRecordExport(Long communityId, Long operatorId,
                                                 String operatorName, String queryParams,
                                                 Integer needRawData) {
        log.info("创建入场记录导出任务: communityId={}, operatorId={}", communityId, operatorId);

        ExportTask task = new ExportTask();
        task.setCommunityId(communityId);
        task.setExportType("parking_record");
        task.setOperatorId(operatorId);
        task.setOperatorName(operatorName);
        task.setQueryParams(queryParams);
        task.setNeedRawData(needRawData != null ? needRawData : 0);
        task.setStatus("pending");
        task.setCreateTime(LocalDateTime.now());

        exportTaskMapper.insert(task);
        log.info("入场记录导出任务已创建: taskId={}", task.getId());

        // 提交异步处理
        exportTaskProcessor.processExportTask(task.getId());

        return task;
    }

    @Override
    public ExportTask createRawDataExport(Long communityId, Long operatorId,
                                           String operatorName, String queryParams,
                                           String role, String ip) {
        log.info("创建原始数据导出任务: communityId={}, operatorId={}, role={}", communityId, operatorId, role);

        // 验证超级管理员权限
        authorizationService.checkRolePermission(role, "export_raw_data");

        // 验证 IP 白名单
        authorizationService.checkIpWhitelist(ip, "export_raw_data");

        ExportTask task = new ExportTask();
        task.setCommunityId(communityId);
        task.setExportType("parking_record");
        task.setOperatorId(operatorId);
        task.setOperatorName(operatorName);
        task.setQueryParams(queryParams);
        task.setNeedRawData(1);
        task.setStatus("pending");
        task.setCreateTime(LocalDateTime.now());

        exportTaskMapper.insert(task);
        log.info("原始数据导出任务已创建: taskId={}", task.getId());

        // 提交异步处理
        exportTaskProcessor.processExportTask(task.getId());

        return task;
    }

    @Override
    public ExportTask getExportTaskStatus(Long exportId) {
        return exportTaskMapper.selectById(exportId);
    }

    @Override
    public ExportTask getDownloadableTask(Long exportId) {
        ExportTask task = exportTaskMapper.selectById(exportId);
        if (task == null) {
            throw new com.parking.common.BusinessException(com.parking.common.ErrorCode.PARAM_ERROR);
        }
        if (!"completed".equals(task.getStatus())) {
            throw new com.parking.common.BusinessException(com.parking.common.ErrorCode.PARAM_ERROR);
        }
        if (task.getExpireTime() != null && task.getExpireTime().isBefore(LocalDateTime.now())) {
            throw new com.parking.common.BusinessException(com.parking.common.ErrorCode.PARAM_ERROR);
        }
        return task;
    }
}
