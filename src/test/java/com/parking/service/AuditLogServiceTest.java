package com.parking.service;

import com.parking.mapper.AccessLogMapper;
import com.parking.mapper.ExportTaskMapper;
import com.parking.mapper.OperationLogMapper;
import com.parking.model.AccessLog;
import com.parking.model.ExportTask;
import com.parking.model.OperationLog;
import com.parking.service.impl.AuditLogServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * AuditLogServiceImpl 单元测试
 */
@ExtendWith(MockitoExtension.class)
class AuditLogServiceTest {

    @Mock
    private OperationLogMapper operationLogMapper;

    @Mock
    private AccessLogMapper accessLogMapper;

    @Mock
    private ExportTaskMapper exportTaskMapper;

    @InjectMocks
    private AuditLogServiceImpl auditLogService;

    @Test
    @DisplayName("查询操作日志 - 有数据")
    void queryOperationLogs_withData() {
        OperationLog log1 = new OperationLog();
        log1.setId(1L);
        log1.setOperationType("CREATE");
        log1.setTargetType("owner");
        log1.setOperationResult("SUCCESS");
        log1.setOperationTime(LocalDateTime.now());

        when(operationLogMapper.selectByCondition(1001L, null, "CREATE", null, null))
                .thenReturn(List.of(log1));

        List<OperationLog> result = auditLogService.queryOperationLogs(1001L, null, "CREATE", null, null);

        assertEquals(1, result.size());
        assertEquals("CREATE", result.get(0).getOperationType());
        verify(operationLogMapper).selectByCondition(1001L, null, "CREATE", null, null);
    }

    @Test
    @DisplayName("查询操作日志 - 无数据返回空列表")
    void queryOperationLogs_noData() {
        when(operationLogMapper.selectByCondition(any(), any(), any(), any(), any()))
                .thenReturn(Collections.emptyList());

        List<OperationLog> result = auditLogService.queryOperationLogs(1001L, null, null, null, null);

        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("查询访问日志 - 有数据")
    void queryAccessLogs_withData() {
        AccessLog log1 = new AccessLog();
        log1.setId(1L);
        log1.setApiPath("/api/v1/vehicles");
        log1.setHttpMethod("GET");
        log1.setResponseCode(200);
        log1.setAccessTime(LocalDateTime.now());

        when(accessLogMapper.selectByCondition(1001L, 100L, null, null, null))
                .thenReturn(List.of(log1));

        List<AccessLog> result = auditLogService.queryAccessLogs(1001L, 100L, null, null, null);

        assertEquals(1, result.size());
        assertEquals("/api/v1/vehicles", result.get(0).getApiPath());
    }

    @Test
    @DisplayName("查询访问日志 - 按路径筛选")
    void queryAccessLogs_filterByPath() {
        when(accessLogMapper.selectByCondition(null, null, "/api/v1/parking", null, null))
                .thenReturn(Collections.emptyList());

        List<AccessLog> result = auditLogService.queryAccessLogs(null, null, "/api/v1/parking", null, null);

        assertTrue(result.isEmpty());
        verify(accessLogMapper).selectByCondition(null, null, "/api/v1/parking", null, null);
    }

    @Test
    @DisplayName("导出审计日志 - 创建导出任务成功")
    void exportAuditLogs_success() {
        doAnswer(invocation -> {
            ExportTask task = invocation.getArgument(0);
            task.setId(1L);
            return null;
        }).when(exportTaskMapper).insert(any(ExportTask.class));

        ExportTask result = auditLogService.exportAuditLogs(
                1001L, 100L, "管理员A", "audit_log", "{\"startTime\":\"2026-03-01\"}", 0);

        assertNotNull(result);
        assertEquals(1L, result.getId());
        assertEquals(1001L, result.getCommunityId());
        assertEquals("audit_log", result.getExportType());
        assertEquals(100L, result.getOperatorId());
        assertEquals("管理员A", result.getOperatorName());
        assertEquals("pending", result.getStatus());
        assertEquals(0, result.getNeedRawData());
        assertNotNull(result.getCreateTime());
        verify(exportTaskMapper).insert(any(ExportTask.class));
    }

    @Test
    @DisplayName("导出审计日志 - needRawData 为 null 时默认为 0")
    void exportAuditLogs_needRawDataNull() {
        doAnswer(invocation -> {
            ExportTask task = invocation.getArgument(0);
            task.setId(2L);
            return null;
        }).when(exportTaskMapper).insert(any(ExportTask.class));

        ExportTask result = auditLogService.exportAuditLogs(
                1001L, 100L, "管理员B", "operation_log", null, null);

        assertNotNull(result);
        assertEquals(0, result.getNeedRawData());
        assertNull(result.getQueryParams());
    }

    @Test
    @DisplayName("导出审计日志 - 需要原始数据")
    void exportAuditLogs_withRawData() {
        doAnswer(invocation -> {
            ExportTask task = invocation.getArgument(0);
            task.setId(3L);
            return null;
        }).when(exportTaskMapper).insert(any(ExportTask.class));

        ExportTask result = auditLogService.exportAuditLogs(
                1001L, 100L, "超级管理员", "audit_log", "{}", 1);

        assertNotNull(result);
        assertEquals(1, result.getNeedRawData());
        assertEquals("pending", result.getStatus());
    }
}
