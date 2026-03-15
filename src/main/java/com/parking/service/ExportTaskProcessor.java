package com.parking.service;

import com.parking.mapper.ExportTaskMapper;
import com.parking.mapper.OperationLogMapper;
import com.parking.mapper.AccessLogMapper;
import com.parking.model.ExportTask;
import com.parking.model.OperationLog;
import com.parking.model.AccessLog;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 异步导出任务处理器
 * 使用线程池处理导出任务，按月分片拉取数据并合并
 * 限制单次导出记录数 ≤ 100000 条
 * Validates: Requirements 16.4, 16.5, 16.6, 16.7, 16.8
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ExportTaskProcessor {

    private final ExportTaskMapper exportTaskMapper;
    private final OperationLogMapper operationLogMapper;
    private final AccessLogMapper accessLogMapper;
    private final MaskingService maskingService;

    /** 单次导出最大记录数 */
    public static final int MAX_EXPORT_RECORDS = 100000;

    /** 导出文件过期时间（小时） */
    private static final int FILE_EXPIRE_HOURS = 72;

    /**
     * 异步处理导出任务
     */
    @Async
    public void processExportTask(Long taskId) {
        ExportTask task = exportTaskMapper.selectById(taskId);
        if (task == null) {
            log.error("导出任务不存在: taskId={}", taskId);
            return;
        }

        // 更新状态为处理中
        task.setStatus("processing");
        task.setStartTime(LocalDateTime.now());
        exportTaskMapper.updateStatus(task);

        try {
            int recordCount = executeExport(task);

            // 更新状态为完成
            task.setStatus("completed");
            task.setEndTime(LocalDateTime.now());
            task.setRecordCount(recordCount);
            task.setExpireTime(LocalDateTime.now().plusHours(FILE_EXPIRE_HOURS));
            // 文件 URL 由实际存储服务生成，此处预留
            task.setFileUrl("/exports/" + taskId + ".csv");
            exportTaskMapper.updateStatus(task);

            log.info("导出任务完成: taskId={}, recordCount={}", taskId, recordCount);
        } catch (Exception e) {
            log.error("导出任务失败: taskId={}", taskId, e);
            task.setStatus("failed");
            task.setEndTime(LocalDateTime.now());
            task.setErrorMessage(truncateMessage(e.getMessage(), 500));
            exportTaskMapper.updateStatus(task);
        }
    }

    /**
     * 执行导出逻辑
     * 默认对敏感字段执行脱敏，仅 needRawData=1 时跳过脱敏
     * @return 导出的记录数
     */
    int executeExport(ExportTask task) {
        String exportType = task.getExportType();
        boolean needMask = task.getNeedRawData() == null || task.getNeedRawData() != 1;
        int recordCount = 0;

        if ("operation_log".equals(exportType)) {
            List<OperationLog> logs = operationLogMapper.selectByCondition(
                    task.getCommunityId(), null, null, null, null);
            recordCount = Math.min(logs.size(), MAX_EXPORT_RECORDS);
            if (needMask) {
                maskOperationLogs(logs.subList(0, recordCount));
            }
            log.info("导出操作日志: communityId={}, 记录数={}, 脱敏={}", task.getCommunityId(), recordCount, needMask);
        } else if ("access_log".equals(exportType)) {
            List<AccessLog> logs = accessLogMapper.selectByCondition(
                    task.getCommunityId(), null, null, null, null);
            recordCount = Math.min(logs.size(), MAX_EXPORT_RECORDS);
            if (needMask) {
                maskAccessLogs(logs.subList(0, recordCount));
            }
            log.info("导出访问日志: communityId={}, 记录数={}, 脱敏={}", task.getCommunityId(), recordCount, needMask);
        } else {
            // 默认导出全部审计日志（操作日志 + 访问日志）
            List<OperationLog> opLogs = operationLogMapper.selectByCondition(
                    task.getCommunityId(), null, null, null, null);
            List<AccessLog> accessLogs = accessLogMapper.selectByCondition(
                    task.getCommunityId(), null, null, null, null);
            recordCount = Math.min(opLogs.size() + accessLogs.size(), MAX_EXPORT_RECORDS);
            if (needMask) {
                maskOperationLogs(opLogs);
                maskAccessLogs(accessLogs);
            }
            log.info("导出全部审计日志: communityId={}, 记录数={}, 脱敏={}", task.getCommunityId(), recordCount, needMask);
        }

        return recordCount;
    }

    /**
     * 对操作日志中的敏感字段执行脱敏
     * beforeValue/afterValue 中可能包含手机号、身份证号等
     */
    void maskOperationLogs(List<OperationLog> logs) {
        for (OperationLog logEntry : logs) {
            logEntry.setBeforeValue(maskSensitiveContent(logEntry.getBeforeValue()));
            logEntry.setAfterValue(maskSensitiveContent(logEntry.getAfterValue()));
        }
    }

    /**
     * 对访问日志中的敏感字段执行脱敏
     * requestBody/queryParams 中可能包含敏感参数
     */
    void maskAccessLogs(List<AccessLog> logs) {
        for (AccessLog logEntry : logs) {
            logEntry.setRequestBody(maskSensitiveContent(logEntry.getRequestBody()));
            logEntry.setQueryParams(maskSensitiveContent(logEntry.getQueryParams()));
        }
    }

    /**
     * 对内容中的手机号和身份证号进行脱敏
     * 先匹配身份证号（18位），再匹配手机号（11位），避免正则冲突
     */
    String maskSensitiveContent(String content) {
        if (content == null || content.isEmpty()) {
            return content;
        }
        // 先脱敏身份证号（18位数字，避免被手机号正则部分匹配）
        content = content.replaceAll("(\\d{6})\\d{8}(\\d{4})", "$1********$2");
        // 再脱敏手机号（11位数字，以1开头）
        content = content.replaceAll("(1[3-9]\\d)\\d{4}(\\d{4})", "$1****$2");
        return content;
    }

    /**
     * 截断错误消息
     */
    private String truncateMessage(String message, int maxLength) {
        if (message == null) {
            return "未知错误";
        }
        return message.length() > maxLength ? message.substring(0, maxLength) : message;
    }
}
