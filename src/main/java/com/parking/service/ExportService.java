package com.parking.service;

import com.parking.model.ExportTask;

/**
 * 导出服务接口
 * Validates: Requirements 16.4, 17.3, 17.4, 17.5
 */
public interface ExportService {

    /**
     * 创建入场记录导出任务
     */
    ExportTask createParkingRecordExport(Long communityId, Long operatorId,
                                          String operatorName, String queryParams,
                                          Integer needRawData);

    /**
     * 创建原始数据导出任务（需超级管理员权限 + IP 白名单校验）
     */
    ExportTask createRawDataExport(Long communityId, Long operatorId,
                                    String operatorName, String queryParams,
                                    String role, String ip);

    /**
     * 查询导出任务状态
     */
    ExportTask getExportTaskStatus(Long exportId);

    /**
     * 获取可下载的导出任务（验证文件未过期）
     */
    ExportTask getDownloadableTask(Long exportId);
}
