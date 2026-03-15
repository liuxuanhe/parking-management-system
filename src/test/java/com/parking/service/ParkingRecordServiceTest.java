package com.parking.service;

import com.parking.common.BusinessException;
import com.parking.dto.ParkingRecordQueryRequest;
import com.parking.dto.ParkingRecordQueryResponse;
import com.parking.mapper.ParkingCarRecordMapper;
import com.parking.model.ParkingCarRecord;
import com.parking.service.impl.ParkingRecordServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * ParkingRecordService 单元测试
 * Validates: Requirements 11.2, 15.3, 15.4, 15.5, 16.1, 16.2, 16.3
 */
@ExtendWith(MockitoExtension.class)
class ParkingRecordServiceTest {

    @Mock
    private ParkingCarRecordMapper parkingCarRecordMapper;

    @Mock
    private MaskingService maskingService;

    private ParkingRecordServiceImpl parkingRecordService;

    private static final Long COMMUNITY_ID = 1001L;
    private static final String HOUSE_NO = "1-101";

    @BeforeEach
    void setUp() {
        parkingRecordService = new ParkingRecordServiceImpl(parkingCarRecordMapper, maskingService);
    }

    /**
     * 创建测试用查询请求
     */
    private ParkingRecordQueryRequest createRequest(LocalDateTime startTime, LocalDateTime endTime) {
        ParkingRecordQueryRequest request = new ParkingRecordQueryRequest();
        request.setCommunityId(COMMUNITY_ID);
        request.setHouseNo(HOUSE_NO);
        request.setStartTime(startTime);
        request.setEndTime(endTime);
        return request;
    }

    /**
     * 创建测试用入场记录
     */
    private ParkingCarRecord createRecord(Long id, LocalDateTime enterTime, String carNumber) {
        ParkingCarRecord record = new ParkingCarRecord();
        record.setId(id);
        record.setCommunityId(COMMUNITY_ID);
        record.setHouseNo(HOUSE_NO);
        record.setCarNumber(carNumber);
        record.setVehicleType("primary");
        record.setEnterTime(enterTime);
        record.setStatus("entered");
        return record;
    }

    @Nested
    @DisplayName("分表名称计算")
    class TableNameResolutionTests {

        @Test
        @DisplayName("同月查询应返回单个分表")
        void sameMonth_shouldReturnSingleTable() {
            LocalDateTime start = LocalDateTime.of(2025, 1, 1, 0, 0, 0);
            LocalDateTime end = LocalDateTime.of(2025, 1, 31, 23, 59, 59);

            List<String> tables = parkingRecordService.resolveTableNames(start, end);

            assertEquals(1, tables.size());
            assertEquals("parking_car_record_202501", tables.get(0));
        }

        @Test
        @DisplayName("跨月查询应返回多个分表")
        void crossMonth_shouldReturnMultipleTables() {
            LocalDateTime start = LocalDateTime.of(2025, 1, 15, 0, 0, 0);
            LocalDateTime end = LocalDateTime.of(2025, 3, 20, 23, 59, 59);

            List<String> tables = parkingRecordService.resolveTableNames(start, end);

            assertEquals(3, tables.size());
            assertEquals("parking_car_record_202501", tables.get(0));
            assertEquals("parking_car_record_202502", tables.get(1));
            assertEquals("parking_car_record_202503", tables.get(2));
        }

        @Test
        @DisplayName("跨年查询应正确计算分表")
        void crossYear_shouldReturnCorrectTables() {
            LocalDateTime start = LocalDateTime.of(2024, 11, 1, 0, 0, 0);
            LocalDateTime end = LocalDateTime.of(2025, 2, 28, 23, 59, 59);

            List<String> tables = parkingRecordService.resolveTableNames(start, end);

            assertEquals(4, tables.size());
            assertEquals("parking_car_record_202411", tables.get(0));
            assertEquals("parking_car_record_202412", tables.get(1));
            assertEquals("parking_car_record_202501", tables.get(2));
            assertEquals("parking_car_record_202502", tables.get(3));
        }
    }

    @Nested
    @DisplayName("时间范围校验")
    class TimeRangeValidationTests {

        @Test
        @DisplayName("startTime 晚于 endTime 应抛出异常")
        void startAfterEnd_shouldThrow() {
            LocalDateTime start = LocalDateTime.of(2025, 3, 1, 0, 0, 0);
            LocalDateTime end = LocalDateTime.of(2025, 1, 1, 0, 0, 0);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> parkingRecordService.validateTimeRange(start, end));
            assertTrue(ex.getMessage().contains("startTime"));
        }

        @Test
        @DisplayName("startTime 等于 endTime 不应抛出异常")
        void sameTime_shouldNotThrow() {
            LocalDateTime time = LocalDateTime.of(2025, 1, 15, 10, 0, 0);
            assertDoesNotThrow(() -> parkingRecordService.validateTimeRange(time, time));
        }
    }

    @Nested
    @DisplayName("游标解析")
    class CursorParsingTests {

        @Test
        @DisplayName("有效游标应正确解析")
        void validCursor_shouldParse() {
            String cursor = "2025-01-15 10:00:00_10001";
            String[] parts = parkingRecordService.parseCursor(cursor);
            assertEquals("2025-01-15 10:00:00", parts[0]);
            assertEquals("10001", parts[1]);
        }

        @Test
        @DisplayName("无效游标应抛出异常")
        void invalidCursor_shouldThrow() {
            assertThrows(BusinessException.class,
                    () -> parkingRecordService.parseCursor("invalid"));
        }

        @Test
        @DisplayName("空游标ID应抛出异常")
        void emptyCursorId_shouldThrow() {
            assertThrows(BusinessException.class,
                    () -> parkingRecordService.parseCursor("2025-01-15 10:00:00_abc"));
        }
    }

    @Nested
    @DisplayName("游标构建")
    class CursorBuildTests {

        @Test
        @DisplayName("应正确构建游标字符串")
        void shouldBuildCorrectCursor() {
            LocalDateTime time = LocalDateTime.of(2025, 1, 15, 10, 0, 0);
            String cursor = parkingRecordService.buildCursor(time, 10001L);
            assertEquals("2025-01-15 10:00:00_10001", cursor);
        }
    }

    @Nested
    @DisplayName("查询记录 - 游标分页")
    class QueryRecordsTests {

        @Test
        @DisplayName("无游标首次查询应返回正确结果")
        void firstPage_shouldReturnRecords() {
            ParkingRecordQueryRequest request = createRequest(
                    LocalDateTime.of(2025, 1, 1, 0, 0, 0),
                    LocalDateTime.of(2025, 1, 31, 23, 59, 59));
            request.setPageSize(2);

            // 返回3条（多查1条用于判断 hasMore）
            List<ParkingCarRecord> records = new ArrayList<>();
            records.add(createRecord(3L, LocalDateTime.of(2025, 1, 20, 10, 0, 0), "京A12345"));
            records.add(createRecord(2L, LocalDateTime.of(2025, 1, 15, 10, 0, 0), "京B67890"));
            records.add(createRecord(1L, LocalDateTime.of(2025, 1, 10, 10, 0, 0), "京C11111"));

            when(parkingCarRecordMapper.selectRecordsByUnionAll(
                    anyList(), eq(COMMUNITY_ID), eq(HOUSE_NO),
                    any(), any(), isNull(), isNull(), eq(3)))
                    .thenReturn(records);
            when(maskingService.mask(anyString(), eq(2), eq(2))).thenAnswer(inv -> {
                String val = inv.getArgument(0);
                return val.substring(0, 2) + "***" + val.substring(val.length() - 2);
            });

            ParkingRecordQueryResponse response = parkingRecordService.queryRecords(request);

            assertEquals(2, response.getRecords().size());
            assertTrue(response.isHasMore());
            assertNotNull(response.getNextCursor());
            assertEquals("2025-01-15 10:00:00_2", response.getNextCursor());
        }

        @Test
        @DisplayName("最后一页应返回 hasMore=false 且无 nextCursor")
        void lastPage_shouldReturnNoMore() {
            ParkingRecordQueryRequest request = createRequest(
                    LocalDateTime.of(2025, 1, 1, 0, 0, 0),
                    LocalDateTime.of(2025, 1, 31, 23, 59, 59));
            request.setPageSize(20);

            List<ParkingCarRecord> records = new ArrayList<>();
            records.add(createRecord(1L, LocalDateTime.of(2025, 1, 10, 10, 0, 0), "京A12345"));

            when(parkingCarRecordMapper.selectRecordsByUnionAll(
                    anyList(), eq(COMMUNITY_ID), eq(HOUSE_NO),
                    any(), any(), isNull(), isNull(), eq(21)))
                    .thenReturn(records);
            when(maskingService.mask(anyString(), eq(2), eq(2))).thenReturn("京A***45");

            ParkingRecordQueryResponse response = parkingRecordService.queryRecords(request);

            assertEquals(1, response.getRecords().size());
            assertFalse(response.isHasMore());
            assertNull(response.getNextCursor());
        }

        @Test
        @DisplayName("空结果应返回空列表")
        void emptyResult_shouldReturnEmptyList() {
            ParkingRecordQueryRequest request = createRequest(
                    LocalDateTime.of(2025, 1, 1, 0, 0, 0),
                    LocalDateTime.of(2025, 1, 31, 23, 59, 59));

            when(parkingCarRecordMapper.selectRecordsByUnionAll(
                    anyList(), eq(COMMUNITY_ID), eq(HOUSE_NO),
                    any(), any(), isNull(), isNull(), eq(21)))
                    .thenReturn(Collections.emptyList());

            ParkingRecordQueryResponse response = parkingRecordService.queryRecords(request);

            assertTrue(response.getRecords().isEmpty());
            assertFalse(response.isHasMore());
            assertNull(response.getNextCursor());
        }

        @Test
        @DisplayName("带游标查询应传递正确的游标参数")
        void withCursor_shouldPassCursorParams() {
            ParkingRecordQueryRequest request = createRequest(
                    LocalDateTime.of(2025, 1, 1, 0, 0, 0),
                    LocalDateTime.of(2025, 1, 31, 23, 59, 59));
            request.setCursor("2025-01-15 10:00:00_100");
            request.setPageSize(20);

            when(parkingCarRecordMapper.selectRecordsByUnionAll(
                    anyList(), eq(COMMUNITY_ID), eq(HOUSE_NO),
                    any(), any(),
                    eq(LocalDateTime.of(2025, 1, 15, 10, 0, 0)),
                    eq(100L),
                    eq(21)))
                    .thenReturn(Collections.emptyList());

            ParkingRecordQueryResponse response = parkingRecordService.queryRecords(request);

            assertNotNull(response);
            verify(parkingCarRecordMapper).selectRecordsByUnionAll(
                    anyList(), eq(COMMUNITY_ID), eq(HOUSE_NO),
                    any(), any(),
                    eq(LocalDateTime.of(2025, 1, 15, 10, 0, 0)),
                    eq(100L),
                    eq(21));
        }
    }

    @Nested
    @DisplayName("数据脱敏")
    class DataMaskingTests {

        @Test
        @DisplayName("车牌号应执行脱敏处理")
        void carNumber_shouldBeMasked() {
            ParkingRecordQueryRequest request = createRequest(
                    LocalDateTime.of(2025, 1, 1, 0, 0, 0),
                    LocalDateTime.of(2025, 1, 31, 23, 59, 59));

            List<ParkingCarRecord> records = new ArrayList<>();
            records.add(createRecord(1L, LocalDateTime.of(2025, 1, 10, 10, 0, 0), "京AD12345"));

            when(parkingCarRecordMapper.selectRecordsByUnionAll(
                    anyList(), eq(COMMUNITY_ID), eq(HOUSE_NO),
                    any(), any(), isNull(), isNull(), eq(21)))
                    .thenReturn(records);
            when(maskingService.mask("京AD12345", 2, 2)).thenReturn("京A*****45");

            ParkingRecordQueryResponse response = parkingRecordService.queryRecords(request);

            assertEquals("京A*****45", response.getRecords().get(0).getCarNumber());
            verify(maskingService).mask("京AD12345", 2, 2);
        }
    }

    @Nested
    @DisplayName("默认分页大小")
    class DefaultPageSizeTests {

        @Test
        @DisplayName("未指定 pageSize 时应使用默认值20")
        void noPageSize_shouldUseDefault() {
            ParkingRecordQueryRequest request = createRequest(
                    LocalDateTime.of(2025, 1, 1, 0, 0, 0),
                    LocalDateTime.of(2025, 1, 31, 23, 59, 59));
            // 不设置 pageSize

            when(parkingCarRecordMapper.selectRecordsByUnionAll(
                    anyList(), eq(COMMUNITY_ID), eq(HOUSE_NO),
                    any(), any(), isNull(), isNull(), eq(21)))
                    .thenReturn(Collections.emptyList());

            parkingRecordService.queryRecords(request);

            // 验证 limit = 20 + 1 = 21
            verify(parkingCarRecordMapper).selectRecordsByUnionAll(
                    anyList(), eq(COMMUNITY_ID), eq(HOUSE_NO),
                    any(), any(), isNull(), isNull(), eq(21));
        }
    }
}
