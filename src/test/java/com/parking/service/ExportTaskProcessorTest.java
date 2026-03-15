package com.parking.service;

import com.parking.mapper.AccessLogMapper;
import com.parking.mapper.ExportTaskMapper;
import com.parking.mapper.OperationLogMapper;
import com.parking.model.AccessLog;
import com.parking.model.ExportTask;
import com.parking.model.OperationLog;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * ExportTaskProcessor 单元测试
 */
@ExtendWith(MockitoExtension.class)
class ExportTaskProcessorTest {

    @Mock
    private ExportTaskMapper exportTaskMapper;

    @Mock
    private OperationLogMapper operationLogMapper;

    @Mock
    private AccessLogMapper accessLogMapper;

    @Mock
    private MaskingService maskingService;

    @InjectMocks
    private ExportTaskProcessor processor;

    private ExportTask createTask(String exportType) {
        ExportTask task = new ExportTask();
        task.setId(1L);
        task.setCommunityId(1001L);
        task.setExportType(exportType);
        task.setOperatorId(100L);
        task.setStatus("pending");
        return task;
    }

    @Test
    @DisplayName("处理导出任务 - 任务不存在时直接返回")
    void processExportTask_taskNotFound() {
        when(exportTaskMapper.selectById(999L)).thenReturn(null);

        processor.processExportTask(999L);

        verify(exportTaskMapper, never()).updateStatus(any());
    }

    @Test
    @DisplayName("处理导出任务 - 操作日志导出成功")
    void processExportTask_operationLogSuccess() {
        ExportTask task = createTask("operation_log");
        when(exportTaskMapper.selectById(1L)).thenReturn(task);

        OperationLog log1 = new OperationLog();
        log1.setId(1L);
        when(operationLogMapper.selectByCondition(eq(1001L), isNull(), isNull(), isNull(), isNull()))
                .thenReturn(List.of(log1));

        processor.processExportTask(1L);

        // 验证状态更新了两次：processing 和 completed
        verify(exportTaskMapper, times(2)).updateStatus(any(ExportTask.class));
        assertEquals("completed", task.getStatus());
        assertEquals(1, task.getRecordCount());
        assertNotNull(task.getFileUrl());
        assertNotNull(task.getExpireTime());
    }

    @Test
    @DisplayName("处理导出任务 - 访问日志导出成功")
    void processExportTask_accessLogSuccess() {
        ExportTask task = createTask("access_log");
        when(exportTaskMapper.selectById(1L)).thenReturn(task);

        AccessLog log1 = new AccessLog();
        log1.setId(1L);
        when(accessLogMapper.selectByCondition(eq(1001L), isNull(), isNull(), isNull(), isNull()))
                .thenReturn(List.of(log1));

        processor.processExportTask(1L);

        verify(exportTaskMapper, times(2)).updateStatus(any(ExportTask.class));
        assertEquals("completed", task.getStatus());
        assertEquals(1, task.getRecordCount());
    }

    @Test
    @DisplayName("处理导出任务 - 全部审计日志导出")
    void processExportTask_auditLogSuccess() {
        ExportTask task = createTask("audit_log");
        when(exportTaskMapper.selectById(1L)).thenReturn(task);

        when(operationLogMapper.selectByCondition(eq(1001L), isNull(), isNull(), isNull(), isNull()))
                .thenReturn(List.of(new OperationLog()));
        when(accessLogMapper.selectByCondition(eq(1001L), isNull(), isNull(), isNull(), isNull()))
                .thenReturn(List.of(new AccessLog(), new AccessLog()));

        processor.processExportTask(1L);

        assertEquals("completed", task.getStatus());
        assertEquals(3, task.getRecordCount());
    }

    @Test
    @DisplayName("处理导出任务 - 导出失败时记录错误")
    void processExportTask_failure() {
        ExportTask task = createTask("operation_log");
        when(exportTaskMapper.selectById(1L)).thenReturn(task);
        when(operationLogMapper.selectByCondition(any(), any(), any(), any(), any()))
                .thenThrow(new RuntimeException("数据库连接失败"));

        processor.processExportTask(1L);

        assertEquals("failed", task.getStatus());
        assertNotNull(task.getErrorMessage());
        assertTrue(task.getErrorMessage().contains("数据库连接失败"));
    }

    @Test
    @DisplayName("处理导出任务 - 无数据时记录数为0")
    void processExportTask_noData() {
        ExportTask task = createTask("operation_log");
        when(exportTaskMapper.selectById(1L)).thenReturn(task);
        when(operationLogMapper.selectByCondition(eq(1001L), isNull(), isNull(), isNull(), isNull()))
                .thenReturn(Collections.emptyList());

        processor.processExportTask(1L);

        assertEquals("completed", task.getStatus());
        assertEquals(0, task.getRecordCount());
    }

    @Test
    @DisplayName("单次导出最大记录数限制为100000")
    void maxExportRecords_isCorrect() {
        assertEquals(100000, ExportTaskProcessor.MAX_EXPORT_RECORDS);
    }

    @Test
    @DisplayName("默认导出执行脱敏 - needRawData 为 null")
    void executeExport_maskByDefault() {
        ExportTask task = createTask("operation_log");
        task.setNeedRawData(null);

        OperationLog log1 = new OperationLog();
        log1.setBeforeValue("手机号: 13812345678");
        log1.setAfterValue("身份证: 110101199001011234");
        when(operationLogMapper.selectByCondition(eq(1001L), isNull(), isNull(), isNull(), isNull()))
                .thenReturn(List.of(log1));

        int count = processor.executeExport(task);

        assertEquals(1, count);
        // 验证手机号被脱敏
        assertTrue(log1.getBeforeValue().contains("****"));
        // 验证身份证被脱敏
        assertTrue(log1.getAfterValue().contains("********"));
    }

    @Test
    @DisplayName("默认导出执行脱敏 - needRawData 为 0")
    void executeExport_maskWhenNeedRawDataZero() {
        ExportTask task = createTask("access_log");
        task.setNeedRawData(0);

        AccessLog log1 = new AccessLog();
        log1.setRequestBody("{\"phone\":\"13912345678\"}");
        log1.setQueryParams("phone=13912345678");
        when(accessLogMapper.selectByCondition(eq(1001L), isNull(), isNull(), isNull(), isNull()))
                .thenReturn(List.of(log1));

        int count = processor.executeExport(task);

        assertEquals(1, count);
        assertTrue(log1.getRequestBody().contains("****"));
        assertTrue(log1.getQueryParams().contains("****"));
    }

    @Test
    @DisplayName("原始数据导出跳过脱敏 - needRawData 为 1")
    void executeExport_skipMaskWhenRawData() {
        ExportTask task = createTask("operation_log");
        task.setNeedRawData(1);

        OperationLog log1 = new OperationLog();
        log1.setBeforeValue("手机号: 13812345678");
        log1.setAfterValue("身份证: 110101199001011234");
        when(operationLogMapper.selectByCondition(eq(1001L), isNull(), isNull(), isNull(), isNull()))
                .thenReturn(List.of(log1));

        int count = processor.executeExport(task);

        assertEquals(1, count);
        // 原始数据不脱敏，保持原值
        assertEquals("手机号: 13812345678", log1.getBeforeValue());
        assertEquals("身份证: 110101199001011234", log1.getAfterValue());
    }

    @Test
    @DisplayName("脱敏方法 - 手机号脱敏正确")
    void maskSensitiveContent_phone() {
        String result = processor.maskSensitiveContent("联系人手机: 13812345678");
        assertTrue(result.contains("138****5678"));
        assertFalse(result.contains("13812345678"));
    }

    @Test
    @DisplayName("脱敏方法 - null 和空字符串返回原值")
    void maskSensitiveContent_nullAndEmpty() {
        assertNull(processor.maskSensitiveContent(null));
        assertEquals("", processor.maskSensitiveContent(""));
    }
}
