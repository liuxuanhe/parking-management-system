package com.parking.service;

import com.parking.common.BusinessException;
import com.parking.dto.ExitExceptionHandleRequest;
import com.parking.dto.ExitRequest;
import com.parking.dto.ExitResponse;
import com.parking.mapper.ParkingCarRecordMapper;
import com.parking.mapper.VisitorSessionMapper;
import com.parking.model.ParkingCarRecord;
import com.parking.model.VisitorSession;
import com.parking.service.impl.ExitServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * ExitService 单元测试
 * Validates: Requirements 6.1, 6.2, 6.3, 6.4, 6.7, 6.8
 */
@ExtendWith(MockitoExtension.class)
class ExitServiceTest {

    @Mock
    private ParkingCarRecordMapper parkingCarRecordMapper;

    @Mock
    private DistributedLockService distributedLockService;

    @Mock
    private CacheService cacheService;

    @Mock
    private NotificationService notificationService;

    @Mock
    private VisitorSessionMapper visitorSessionMapper;

    private ExitServiceImpl exitService;

    private static final Long COMMUNITY_ID = 1001L;
    private static final String CAR_NUMBER = "京A12345";
    private static final String HOUSE_NO = "1-101";

    @BeforeEach
    void setUp() {
        exitService = new ExitServiceImpl(
                parkingCarRecordMapper,
                distributedLockService,
                cacheService,
                notificationService,
                visitorSessionMapper
        );
    }

    /**
     * 创建测试用出场请求
     */
    private ExitRequest createExitRequest() {
        ExitRequest request = new ExitRequest();
        request.setCommunityId(COMMUNITY_ID);
        request.setCarNumber(CAR_NUMBER);
        return request;
    }

    /**
     * 创建测试用入场记录（status='entered'）
     */
    private ParkingCarRecord createEnteredRecord() {
        ParkingCarRecord record = new ParkingCarRecord();
        record.setId(100L);
        record.setCommunityId(COMMUNITY_ID);
        record.setHouseNo(HOUSE_NO);
        record.setCarNumber(CAR_NUMBER);
        record.setVehicleType("primary");
        record.setEnterTime(LocalDateTime.now().minusHours(2));
        record.setStatus("entered");
        return record;
    }

    /**
     * 配置分布式锁 mock：直接执行传入的 Supplier
     */
    private void setupLockMock() {
        when(distributedLockService.executeWithLock(anyString(), any()))
                .thenAnswer(invocation -> {
                    Supplier<?> supplier = invocation.getArgument(1);
                    return supplier.get();
                });
    }

    @Nested
    @DisplayName("vehicleExit - 正常出场")
    class NormalExitTests {

        @Test
        @DisplayName("有入场记录时应正常出场，状态更新为 exited")
        void withEntryRecord_shouldExitNormally() {
            ExitRequest request = createExitRequest();
            ParkingCarRecord entryRecord = createEnteredRecord();

            // 当前月分表能找到入场记录
            when(parkingCarRecordMapper.selectEnteredRecord(anyString(), eq(COMMUNITY_ID), eq(CAR_NUMBER)))
                    .thenReturn(entryRecord);
            setupLockMock();

            ExitResponse response = exitService.vehicleExit(request);

            assertNotNull(response);
            assertEquals(100L, response.getRecordId());
            assertEquals(CAR_NUMBER, response.getCarNumber());
            assertEquals("primary", response.getVehicleType());
            assertEquals("exited", response.getStatus());
            assertNotNull(response.getExitTime());
            assertNotNull(response.getDuration());
            assertTrue(response.getDuration() >= 0);

            // 验证更新了出场记录
            verify(parkingCarRecordMapper).updateExitRecord(anyString(), any());
            // 验证失效了报表缓存
            verify(cacheService).deleteByPrefix(contains("report:" + COMMUNITY_ID));
            // 验证未通知物业（正常出场不需要通知）
            verify(notificationService, never()).sendSubscriptionMessage(anyLong(), anyString(), any());
        }

        @Test
        @DisplayName("停放时长应正确计算（分钟）")
        void shouldCalculateDurationCorrectly() {
            ExitRequest request = createExitRequest();
            ParkingCarRecord entryRecord = createEnteredRecord();
            // 设置入场时间为3小时前
            entryRecord.setEnterTime(LocalDateTime.now().minusHours(3));

            when(parkingCarRecordMapper.selectEnteredRecord(anyString(), eq(COMMUNITY_ID), eq(CAR_NUMBER)))
                    .thenReturn(entryRecord);
            setupLockMock();

            ExitResponse response = exitService.vehicleExit(request);

            // 停放时长约180分钟（允许1分钟误差）
            assertNotNull(response.getDuration());
            assertTrue(response.getDuration() >= 179 && response.getDuration() <= 181,
                    "停放时长应约为180分钟，实际: " + response.getDuration());
        }

        @Test
        @DisplayName("出场记录应更新到入场时间对应的分表")
        void shouldUpdateToCorrectTable() {
            ExitRequest request = createExitRequest();
            ParkingCarRecord entryRecord = createEnteredRecord();

            when(parkingCarRecordMapper.selectEnteredRecord(anyString(), eq(COMMUNITY_ID), eq(CAR_NUMBER)))
                    .thenReturn(entryRecord);
            setupLockMock();

            exitService.vehicleExit(request);

            // 验证更新操作使用了正确的分表名
            ArgumentCaptor<String> tableCaptor = ArgumentCaptor.forClass(String.class);
            verify(parkingCarRecordMapper).updateExitRecord(tableCaptor.capture(), any());
            assertTrue(tableCaptor.getValue().matches("parking_car_record_\\d{6}"));
        }
    }

    @Nested
    @DisplayName("vehicleExit - 异常出场")
    class ExceptionExitTests {

        @Test
        @DisplayName("无入场记录时应创建异常出场记录")
        void noEntryRecord_shouldCreateExceptionRecord() {
            ExitRequest request = createExitRequest();

            // 当前月和上个月分表都找不到入场记录
            when(parkingCarRecordMapper.selectEnteredRecord(anyString(), eq(COMMUNITY_ID), eq(CAR_NUMBER)))
                    .thenReturn(null);
            setupLockMock();

            ExitResponse response = exitService.vehicleExit(request);

            assertNotNull(response);
            assertEquals(CAR_NUMBER, response.getCarNumber());
            assertEquals("exit_exception", response.getStatus());
            assertNotNull(response.getExitTime());

            // 验证插入了异常出场记录
            ArgumentCaptor<ParkingCarRecord> recordCaptor = ArgumentCaptor.forClass(ParkingCarRecord.class);
            verify(parkingCarRecordMapper).insertToTable(anyString(), recordCaptor.capture());
            ParkingCarRecord insertedRecord = recordCaptor.getValue();
            assertEquals("exit_exception", insertedRecord.getStatus());
            assertEquals("无对应入场记录", insertedRecord.getExceptionReason());
        }

        @Test
        @DisplayName("异常出场时应通知物业管理员")
        void exceptionExit_shouldNotifyAdmin() {
            ExitRequest request = createExitRequest();

            when(parkingCarRecordMapper.selectEnteredRecord(anyString(), eq(COMMUNITY_ID), eq(CAR_NUMBER)))
                    .thenReturn(null);
            setupLockMock();

            exitService.vehicleExit(request);

            // 验证通知了物业管理员
            verify(notificationService).sendSubscriptionMessage(
                    eq(COMMUNITY_ID), eq("exit_exception"), any(Map.class));
        }
    }

    @Nested
    @DisplayName("vehicleExit - Visitor 车辆时长累计")
    class VisitorExitTests {

        private VisitorSession createActiveSession() {
            VisitorSession session = new VisitorSession();
            session.setId(50L);
            session.setCommunityId(COMMUNITY_ID);
            session.setHouseNo(HOUSE_NO);
            session.setAuthorizationId(10L);
            session.setCarNumber(CAR_NUMBER);
            session.setSessionStart(LocalDateTime.now().minusHours(5));
            session.setLastEntryTime(LocalDateTime.now().minusHours(2));
            session.setAccumulatedDuration(60); // 已累计60分钟
            session.setStatus("in_park");
            session.setTimeoutNotified(0);
            return session;
        }

        @Test
        @DisplayName("Visitor 出场 - 正常出场并累计时长")
        void visitorVehicle_shouldExitAndAccumulateDuration() {
            ExitRequest request = createExitRequest();
            ParkingCarRecord entryRecord = createEnteredRecord();
            entryRecord.setVehicleType("visitor");

            VisitorSession session = createActiveSession();

            when(parkingCarRecordMapper.selectEnteredRecord(anyString(), eq(COMMUNITY_ID), eq(CAR_NUMBER)))
                    .thenReturn(entryRecord);
            setupLockMock();
            when(visitorSessionMapper.selectActiveByCarNumber(COMMUNITY_ID, CAR_NUMBER)).thenReturn(session);

            ExitResponse response = exitService.vehicleExit(request);

            assertNotNull(response);
            assertEquals("visitor", response.getVehicleType());
            assertEquals("exited", response.getStatus());
            // 验证累计时长更新：原60分钟 + 本次停放时长
            verify(visitorSessionMapper).updateDurationAndStatus(
                    eq(50L), anyInt(), eq("out_of_park"));
        }

        @Test
        @DisplayName("Visitor 出场 - 无活跃会话时仅正常出场")
        void visitorVehicle_shouldExitNormallyWhenNoSession() {
            ExitRequest request = createExitRequest();
            ParkingCarRecord entryRecord = createEnteredRecord();
            entryRecord.setVehicleType("visitor");

            when(parkingCarRecordMapper.selectEnteredRecord(anyString(), eq(COMMUNITY_ID), eq(CAR_NUMBER)))
                    .thenReturn(entryRecord);
            setupLockMock();
            when(visitorSessionMapper.selectActiveByCarNumber(COMMUNITY_ID, CAR_NUMBER)).thenReturn(null);

            ExitResponse response = exitService.vehicleExit(request);

            assertNotNull(response);
            assertEquals("exited", response.getStatus());
            // 无活跃会话，不应更新时长
            verify(visitorSessionMapper, never()).updateDurationAndStatus(anyLong(), anyInt(), anyString());
        }

        @Test
        @DisplayName("Visitor 出场 - 累计时长计算正确性")
        void visitorVehicle_shouldCalculateDurationCorrectly() {
            ExitRequest request = createExitRequest();
            ParkingCarRecord entryRecord = createEnteredRecord();
            entryRecord.setVehicleType("visitor");
            entryRecord.setEnterTime(LocalDateTime.now().minusMinutes(90)); // 入场90分钟前

            VisitorSession session = createActiveSession();
            session.setAccumulatedDuration(120); // 已累计120分钟

            when(parkingCarRecordMapper.selectEnteredRecord(anyString(), eq(COMMUNITY_ID), eq(CAR_NUMBER)))
                    .thenReturn(entryRecord);
            setupLockMock();
            when(visitorSessionMapper.selectActiveByCarNumber(COMMUNITY_ID, CAR_NUMBER)).thenReturn(session);

            exitService.vehicleExit(request);

            // 验证累计时长 = 120 + 90 = 210 分钟
            verify(visitorSessionMapper).updateDurationAndStatus(eq(50L), eq(210), eq("out_of_park"));
        }
    }

    @Nested
    @DisplayName("vehicleExit - 分布式锁")
    class DistributedLockTests {

        @Test
        @DisplayName("应使用正确的分布式锁键名 lock:space:{communityId}")
        void shouldUseCorrectLockKey() {
            ExitRequest request = createExitRequest();

            when(parkingCarRecordMapper.selectEnteredRecord(anyString(), eq(COMMUNITY_ID), eq(CAR_NUMBER)))
                    .thenReturn(null);
            setupLockMock();

            exitService.vehicleExit(request);

            verify(distributedLockService).executeWithLock(eq("lock:space:" + COMMUNITY_ID), any());
        }
    }

    @Nested
    @DisplayName("vehicleExit - 跨月查询")
    class CrossMonthQueryTests {

        @Test
        @DisplayName("当前月未找到入场记录时应查询上个月分表")
        void shouldQueryLastMonthTable() {
            ExitRequest request = createExitRequest();
            ParkingCarRecord entryRecord = createEnteredRecord();

            // 第一次查询（当前月）返回 null，第二次查询（上个月）返回记录
            when(parkingCarRecordMapper.selectEnteredRecord(anyString(), eq(COMMUNITY_ID), eq(CAR_NUMBER)))
                    .thenReturn(null)
                    .thenReturn(entryRecord);
            setupLockMock();

            ExitResponse response = exitService.vehicleExit(request);

            assertNotNull(response);
            assertEquals("exited", response.getStatus());

            // 验证查询了两次（当前月 + 上个月）
            verify(parkingCarRecordMapper, atLeast(2))
                    .selectEnteredRecord(anyString(), eq(COMMUNITY_ID), eq(CAR_NUMBER));
        }
    }

    @Nested
    @DisplayName("vehicleExit - 缓存失效")
    class CacheInvalidationTests {

        @Test
        @DisplayName("出场后应失效报表缓存")
        void shouldInvalidateReportCache() {
            ExitRequest request = createExitRequest();

            when(parkingCarRecordMapper.selectEnteredRecord(anyString(), eq(COMMUNITY_ID), eq(CAR_NUMBER)))
                    .thenReturn(null);
            setupLockMock();

            exitService.vehicleExit(request);

            verify(cacheService).deleteByPrefix("report:" + COMMUNITY_ID);
        }

        @Test
        @DisplayName("缓存失效失败不应影响主流程")
        void cacheFailure_shouldNotAffectMainFlow() {
            ExitRequest request = createExitRequest();

            when(parkingCarRecordMapper.selectEnteredRecord(anyString(), eq(COMMUNITY_ID), eq(CAR_NUMBER)))
                    .thenReturn(null);
            setupLockMock();
            when(cacheService.deleteByPrefix(anyString())).thenThrow(new RuntimeException("Redis 连接失败"));

            // 不应抛出异常
            ExitResponse response = assertDoesNotThrow(() -> exitService.vehicleExit(request));
            assertNotNull(response);
        }
    }

    @Nested
    @DisplayName("handleExitException - 异常出场处理")
    class HandleExitExceptionTests {

        private static final Long ADMIN_ID = 999L;
        private static final Long RECORD_ID = 200L;

        /**
         * 创建测试用异常出场处理请求
         */
        private ExitExceptionHandleRequest createHandleRequest() {
            ExitExceptionHandleRequest request = new ExitExceptionHandleRequest();
            request.setRecordId(RECORD_ID);
            request.setCommunityId(COMMUNITY_ID);
            request.setHandleRemark("经核实为临时施工车辆，已放行");
            return request;
        }

        /**
         * 创建测试用异常出场记录（status='exit_exception'）
         */
        private ParkingCarRecord createExceptionRecord() {
            ParkingCarRecord record = new ParkingCarRecord();
            record.setId(RECORD_ID);
            record.setCommunityId(COMMUNITY_ID);
            record.setCarNumber(CAR_NUMBER);
            record.setExitTime(LocalDateTime.now().minusHours(1));
            record.setStatus("exit_exception");
            record.setExceptionReason("无对应入场记录");
            return record;
        }

        @Test
        @DisplayName("正常处理异常出场记录，状态更新为 exception_handled")
        void shouldHandleExceptionRecordSuccessfully() {
            ExitExceptionHandleRequest request = createHandleRequest();
            ParkingCarRecord exceptionRecord = createExceptionRecord();

            // 当前月分表能找到异常记录
            when(parkingCarRecordMapper.selectById(anyString(), eq(RECORD_ID), eq(COMMUNITY_ID)))
                    .thenReturn(exceptionRecord);

            exitService.handleExitException(request, ADMIN_ID);

            // 验证更新了异常出场记录
            ArgumentCaptor<ParkingCarRecord> recordCaptor = ArgumentCaptor.forClass(ParkingCarRecord.class);
            verify(parkingCarRecordMapper).updateExceptionHandle(anyString(), recordCaptor.capture());

            ParkingCarRecord updatedRecord = recordCaptor.getValue();
            assertEquals("exception_handled", updatedRecord.getStatus());
            assertEquals(ADMIN_ID, updatedRecord.getHandlerAdminId());
            assertNotNull(updatedRecord.getHandleTime());
            assertEquals("经核实为临时施工车辆，已放行", updatedRecord.getHandleRemark());
        }

        @Test
        @DisplayName("指定分表名称时应使用指定的分表查询")
        void withTableName_shouldUseSpecifiedTable() {
            ExitExceptionHandleRequest request = createHandleRequest();
            request.setTableName("parking_car_record_202501");
            ParkingCarRecord exceptionRecord = createExceptionRecord();

            when(parkingCarRecordMapper.selectById(eq("parking_car_record_202501"), eq(RECORD_ID), eq(COMMUNITY_ID)))
                    .thenReturn(exceptionRecord);

            exitService.handleExitException(request, ADMIN_ID);

            // 验证使用了指定的分表名称查询
            verify(parkingCarRecordMapper).selectById(eq("parking_car_record_202501"), eq(RECORD_ID), eq(COMMUNITY_ID));
            verify(parkingCarRecordMapper).updateExceptionHandle(eq("parking_car_record_202501"), any());
        }

        @Test
        @DisplayName("记录不存在时应抛出 PARKING_5002 异常")
        void recordNotFound_shouldThrowException() {
            ExitExceptionHandleRequest request = createHandleRequest();

            // 当前月和上个月分表都找不到记录
            when(parkingCarRecordMapper.selectById(anyString(), eq(RECORD_ID), eq(COMMUNITY_ID)))
                    .thenReturn(null);

            BusinessException exception = assertThrows(BusinessException.class,
                    () -> exitService.handleExitException(request, ADMIN_ID));

            assertEquals(5002, exception.getCode());
        }

        @Test
        @DisplayName("记录状态不是 exit_exception 时应抛出 PARKING_5003 异常")
        void invalidStatus_shouldThrowException() {
            ExitExceptionHandleRequest request = createHandleRequest();
            ParkingCarRecord record = createExceptionRecord();
            record.setStatus("exited"); // 非异常出场状态

            when(parkingCarRecordMapper.selectById(anyString(), eq(RECORD_ID), eq(COMMUNITY_ID)))
                    .thenReturn(record);

            BusinessException exception = assertThrows(BusinessException.class,
                    () -> exitService.handleExitException(request, ADMIN_ID));

            assertEquals(5003, exception.getCode());
        }

        @Test
        @DisplayName("已处理的记录不应允许重复处理")
        void alreadyHandled_shouldThrowException() {
            ExitExceptionHandleRequest request = createHandleRequest();
            ParkingCarRecord record = createExceptionRecord();
            record.setStatus("exception_handled"); // 已处理状态

            when(parkingCarRecordMapper.selectById(anyString(), eq(RECORD_ID), eq(COMMUNITY_ID)))
                    .thenReturn(record);

            BusinessException exception = assertThrows(BusinessException.class,
                    () -> exitService.handleExitException(request, ADMIN_ID));

            assertEquals(5003, exception.getCode());
        }
    }
}
