package com.parking.service;

import com.parking.mapper.ExportTaskMapper;
import com.parking.model.ExportTask;
import com.parking.service.impl.ExportServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * ExportServiceImpl 单元测试
 */
@ExtendWith(MockitoExtension.class)
class ExportServiceTest {

    @Mock
    private ExportTaskMapper exportTaskMapper;

    @Mock
    private ExportTaskProcessor exportTaskProcessor;

    @InjectMocks
    private ExportServiceImpl exportService;

    @Test
    @DisplayName("创建入场记录导出任务 - 成功")
    void createParkingRecordExport_success() {
        doAnswer(invocation -> {
            ExportTask t = invocation.getArgument(0);
            t.setId(1L);
            return null;
        }).when(exportTaskMapper).insert(any(ExportTask.class));

        ExportTask result = exportService.createParkingRecordExport(
                1001L, 100L, "管理员A", "{}", 0);

        assertNotNull(result);
        assertEquals(1L, result.getId());
        assertEquals("parking_record", result.getExportType());
        assertEquals("pending", result.getStatus());
        assertEquals(1001L, result.getCommunityId());
        verify(exportTaskProcessor).processExportTask(1L);
    }

    @Test
    @DisplayName("创建入场记录导出任务 - needRawData 为 null 时默认0")
    void createParkingRecordExport_needRawDataNull() {
        doAnswer(invocation -> {
            ExportTask t = invocation.getArgument(0);
            t.setId(2L);
            return null;
        }).when(exportTaskMapper).insert(any(ExportTask.class));

        ExportTask result = exportService.createParkingRecordExport(
                1001L, 100L, "管理员B", null, null);

        assertEquals(0, result.getNeedRawData());
        assertEquals("parking_record", result.getExportType());
    }

    @Test
    @DisplayName("查询导出任务状态 - 任务存在")
    void getExportTaskStatus_found() {
        ExportTask task = new ExportTask();
        task.setId(1L);
        task.setStatus("completed");
        when(exportTaskMapper.selectById(1L)).thenReturn(task);

        ExportTask result = exportService.getExportTaskStatus(1L);

        assertNotNull(result);
        assertEquals("completed", result.getStatus());
    }

    @Test
    @DisplayName("查询导出任务状态 - 任务不存在")
    void getExportTaskStatus_notFound() {
        when(exportTaskMapper.selectById(999L)).thenReturn(null);

        ExportTask result = exportService.getExportTaskStatus(999L);

        assertNull(result);
    }
}
