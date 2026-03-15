package com.parking.service;

import com.parking.model.ExportTask;

/**
 * 导出服务接口
 * Validates: Requirements 16.4
 */
public interface ExportService {

    /**
     * 创建入场记录导出任务
     */
    ExportTask createParkingRecordExport(Long communityId, Long operatorId,
                                          String operatorName, String queryParams,
                                          Integer needRawData);

    /**
     * 查询导出任务状态
     */
    ExportTask getExportTaskStatus(Long exportId);
}
