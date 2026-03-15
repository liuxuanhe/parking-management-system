package com.parking.service.impl;

import com.parking.mapper.ExportTaskMapper;
import com.parking.model.ExportTask;
import com.parking.service.ExportService;
import com.parking.service.ExportTaskProcessor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 导出服务实现
 * Validates: Requirements 16.4, 16.5, 16.6
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExportServiceImpl implements ExportService {

    private final ExportTaskMapper exportTaskMapper;
    private final ExportTaskProcessor exportTaskProcessor;

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
    public ExportTask getExportTaskStatus(Long exportId) {
        return exportTaskMapper.selectById(exportId);
    }
}
