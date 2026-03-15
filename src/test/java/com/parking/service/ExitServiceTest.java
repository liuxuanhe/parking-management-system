package com.parking.service;

import com.parking.dto.ExitRequest;
import com.parking.dto.ExitResponse;
import com.parking.mapper.ParkingCarRecordMapper;
import com.parking.model.ParkingCarRecord;
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
                notificationService
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
    @DisplayName("vehicleExit - Visitor 车辆")
    class VisitorExitTests {

        @Test
        @DisplayName("Visitor 车辆出场时应正常出场（时长累计预留）")
        void visitorVehicle_shouldExitNormally() {
            ExitRequest request = createExitRequest();
            ParkingCarRecord entryRecord = createEnteredRecord();
            entryRecord.setVehicleType("visitor");

            when(parkingCarRecordMapper.selectEnteredRecord(anyString(), eq(COMMUNITY_ID), eq(CAR_NUMBER)))
                    .thenReturn(entryRecord);
            setupLockMock();

            ExitResponse response = exitService.vehicleExit(request);

            assertNotNull(response);
            assertEquals("visitor", response.getVehicleType());
            assertEquals("exited", response.getStatus());
            assertNotNull(response.getDuration());
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
}
