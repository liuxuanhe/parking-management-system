package com.parking.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.parking.common.BusinessException;
import com.parking.dto.EntryRequest;
import com.parking.dto.EntryResponse;
import com.parking.mapper.CarPlateMapper;
import com.parking.mapper.ParkingCarRecordMapper;
import com.parking.mapper.VisitorAuthorizationMapper;
import com.parking.mapper.VisitorSessionMapper;
import com.parking.model.CarPlate;
import com.parking.model.VisitorAuthorization;
import com.parking.model.VisitorSession;
import com.parking.service.impl.EntryServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * EntryService 单元测试
 * Validates: Requirements 5.1, 5.3, 5.4, 5.7, 5.9, 5.10, 15.2
 */
@ExtendWith(MockitoExtension.class)
class EntryServiceTest {

    @Mock
    private IdempotencyService idempotencyService;

    @Mock
    private DistributedLockService distributedLockService;

    @Mock
    private ParkingSpaceCalculator parkingSpaceCalculator;

    @Mock
    private CarPlateMapper carPlateMapper;

    @Mock
    private ParkingCarRecordMapper parkingCarRecordMapper;

    @Mock
    private VisitorAuthorizationMapper visitorAuthorizationMapper;

    @Mock
    private VisitorSessionMapper visitorSessionMapper;

    @Mock
    private CacheService cacheService;

    private EntryServiceImpl entryService;

    private static final Long COMMUNITY_ID = 1001L;
    private static final String CAR_NUMBER = "京A12345";
    private static final String HOUSE_NO = "1-101";

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        entryService = new EntryServiceImpl(
                idempotencyService,
                distributedLockService,
                parkingSpaceCalculator,
                carPlateMapper,
                parkingCarRecordMapper,
                visitorAuthorizationMapper,
                visitorSessionMapper,
                cacheService,
                objectMapper
        );
    }

    /**
     * 创建测试用入场请求
     */
    private EntryRequest createEntryRequest() {
        EntryRequest request = new EntryRequest();
        request.setCommunityId(COMMUNITY_ID);
        request.setCarNumber(CAR_NUMBER);
        return request;
    }

    /**
     * 创建测试用 Primary 车牌
     */
    private CarPlate createPrimaryCarPlate() {
        CarPlate carPlate = new CarPlate();
        carPlate.setId(1L);
        carPlate.setCommunityId(COMMUNITY_ID);
        carPlate.setHouseNo(HOUSE_NO);
        carPlate.setCarNumber(CAR_NUMBER);
        carPlate.setStatus("primary");
        carPlate.setIsDeleted(0);
        return carPlate;
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
    @DisplayName("vehicleEntry - Primary 车辆自动入场")
    class PrimaryVehicleEntryTests {

        @Test
        @DisplayName("Primary 车辆有可用车位时应成功入场")
        void primaryVehicle_spaceAvailable_shouldSucceed() {
            EntryRequest request = createEntryRequest();
            CarPlate carPlate = createPrimaryCarPlate();

            // 幂等键不存在（首次请求）
            when(idempotencyService.getResult(anyString())).thenReturn(Optional.empty());
            // 查询车牌
            when(carPlateMapper.selectByCommunityAndCarNumber(COMMUNITY_ID, CAR_NUMBER)).thenReturn(carPlate);
            // 分布式锁直接执行
            setupLockMock();
            // 有可用车位
            when(parkingSpaceCalculator.calculateAvailableSpaces(COMMUNITY_ID)).thenReturn(50);
            // 幂等键设置成功
            when(idempotencyService.checkAndSet(anyString(), anyString(), eq(300))).thenReturn(true);

            EntryResponse response = entryService.vehicleEntry(request);

            assertNotNull(response);
            assertEquals(CAR_NUMBER, response.getCarNumber());
            assertEquals("primary", response.getVehicleType());
            assertEquals("entered", response.getStatus());
            assertNotNull(response.getEnterTime());

            // 验证入场记录已插入
            verify(parkingCarRecordMapper).insertToTable(anyString(), any());
            // 验证幂等键已设置
            verify(idempotencyService).checkAndSet(anyString(), anyString(), eq(300));
            // 验证报表缓存已失效
            verify(cacheService).deleteByPrefix(contains("report:" + COMMUNITY_ID));
        }

        @Test
        @DisplayName("车位已满时应拒绝入场并抛出 PARKING_5001")
        void noAvailableSpace_shouldThrowParking5001() {
            EntryRequest request = createEntryRequest();
            CarPlate carPlate = createPrimaryCarPlate();

            when(idempotencyService.getResult(anyString())).thenReturn(Optional.empty());
            when(carPlateMapper.selectByCommunityAndCarNumber(COMMUNITY_ID, CAR_NUMBER)).thenReturn(carPlate);
            setupLockMock();
            // 车位已满
            when(parkingSpaceCalculator.calculateAvailableSpaces(COMMUNITY_ID)).thenReturn(0);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> entryService.vehicleEntry(request));
            assertEquals(5001, ex.getCode());

            // 验证未插入入场记录
            verify(parkingCarRecordMapper, never()).insertToTable(anyString(), any());
        }
    }

    @Nested
    @DisplayName("vehicleEntry - 幂等性检查")
    class IdempotencyTests {

        @Test
        @DisplayName("重复请求应返回已有结果而不创建新记录")
        void duplicateRequest_shouldReturnExistingResult() {
            EntryRequest request = createEntryRequest();
            String existingJson = "{\"recordId\":100,\"carNumber\":\"京A12345\",\"vehicleType\":\"primary\",\"status\":\"entered\"}";

            // 幂等键已存在
            when(idempotencyService.getResult(anyString())).thenReturn(Optional.of(existingJson));

            EntryResponse response = entryService.vehicleEntry(request);

            assertNotNull(response);
            assertEquals(100L, response.getRecordId());
            assertEquals(CAR_NUMBER, response.getCarNumber());

            // 验证未查询车牌、未插入记录
            verify(carPlateMapper, never()).selectByCommunityAndCarNumber(anyLong(), anyString());
            verify(parkingCarRecordMapper, never()).insertToTable(anyString(), any());
        }
    }

    @Nested
    @DisplayName("vehicleEntry - 车牌验证")
    class CarPlateValidationTests {

        @Test
        @DisplayName("车牌未注册时应抛出异常")
        void carPlateNotFound_shouldThrow() {
            EntryRequest request = createEntryRequest();

            when(idempotencyService.getResult(anyString())).thenReturn(Optional.empty());
            when(carPlateMapper.selectByCommunityAndCarNumber(COMMUNITY_ID, CAR_NUMBER)).thenReturn(null);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> entryService.vehicleEntry(request));
            assertTrue(ex.getMessage().contains("车牌未注册"));
        }

        @Test
        @DisplayName("普通车辆（normal 状态）应拒绝入场")
        void normalVehicle_shouldRejectEntry() {
            EntryRequest request = createEntryRequest();
            CarPlate carPlate = createPrimaryCarPlate();
            carPlate.setStatus("normal");

            when(idempotencyService.getResult(anyString())).thenReturn(Optional.empty());
            when(carPlateMapper.selectByCommunityAndCarNumber(COMMUNITY_ID, CAR_NUMBER)).thenReturn(carPlate);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> entryService.vehicleEntry(request));
            assertTrue(ex.getMessage().contains("无入场权限"));
        }
    }

    @Nested
    @DisplayName("vehicleEntry - 分布式锁")
    class DistributedLockTests {

        @Test
        @DisplayName("应使用正确的分布式锁键名 lock:space:{communityId}")
        void shouldUseCorrectLockKey() {
            EntryRequest request = createEntryRequest();
            CarPlate carPlate = createPrimaryCarPlate();

            when(idempotencyService.getResult(anyString())).thenReturn(Optional.empty());
            when(carPlateMapper.selectByCommunityAndCarNumber(COMMUNITY_ID, CAR_NUMBER)).thenReturn(carPlate);
            setupLockMock();
            when(parkingSpaceCalculator.calculateAvailableSpaces(COMMUNITY_ID)).thenReturn(50);
            when(idempotencyService.checkAndSet(anyString(), anyString(), eq(300))).thenReturn(true);

            entryService.vehicleEntry(request);

            verify(distributedLockService).executeWithLock(eq("lock:space:" + COMMUNITY_ID), any());
        }

        @Test
        @DisplayName("分布式锁获取失败时应抛出异常")
        void lockAcquireFailed_shouldThrow() {
            EntryRequest request = createEntryRequest();
            CarPlate carPlate = createPrimaryCarPlate();

            when(idempotencyService.getResult(anyString())).thenReturn(Optional.empty());
            when(carPlateMapper.selectByCommunityAndCarNumber(COMMUNITY_ID, CAR_NUMBER)).thenReturn(carPlate);
            when(distributedLockService.executeWithLock(anyString(), any()))
                    .thenThrow(new BusinessException(com.parking.common.ErrorCode.LOCK_ACQUIRE_FAILED));

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> entryService.vehicleEntry(request));
            assertEquals(10010, ex.getCode());
        }
    }

    @Nested
    @DisplayName("分表路由 - 通过入场时间验证")
    class TableNameRoutingTests {

        @Test
        @DisplayName("入场记录应写入正确的月份分表")
        void shouldInsertToCorrectMonthTable() {
            EntryRequest request = createEntryRequest();
            CarPlate carPlate = createPrimaryCarPlate();

            when(idempotencyService.getResult(anyString())).thenReturn(Optional.empty());
            when(carPlateMapper.selectByCommunityAndCarNumber(COMMUNITY_ID, CAR_NUMBER)).thenReturn(carPlate);
            setupLockMock();
            when(parkingSpaceCalculator.calculateAvailableSpaces(COMMUNITY_ID)).thenReturn(50);
            when(idempotencyService.checkAndSet(anyString(), anyString(), eq(300))).thenReturn(true);

            entryService.vehicleEntry(request);

            // 验证分表名称格式为 parking_car_record_yyyyMM
            verify(parkingCarRecordMapper).insertToTable(matches("parking_car_record_\\d{6}"), any());
        }
    }

    @Nested
    @DisplayName("幂等键生成 - 格式验证")
    class IdempotencyKeyTests {

        @Test
        @DisplayName("幂等键应包含 communityId 和 carNumber")
        void idempotencyKeyShouldContainCommunityAndCar() {
            EntryRequest request = createEntryRequest();
            CarPlate carPlate = createPrimaryCarPlate();

            when(idempotencyService.getResult(anyString())).thenReturn(Optional.empty());
            when(carPlateMapper.selectByCommunityAndCarNumber(COMMUNITY_ID, CAR_NUMBER)).thenReturn(carPlate);
            setupLockMock();
            when(parkingSpaceCalculator.calculateAvailableSpaces(COMMUNITY_ID)).thenReturn(50);
            when(idempotencyService.checkAndSet(anyString(), anyString(), eq(300))).thenReturn(true);

            entryService.vehicleEntry(request);

            // 验证幂等键格式: vehicle_entry:{communityId}:{carNumber}:{minute}
            verify(idempotencyService).getResult(matches("vehicle_entry:1001:京A12345:\\d{12}"));
            verify(idempotencyService).checkAndSet(matches("vehicle_entry:1001:京A12345:\\d{12}"), anyString(), eq(300));
        }
    }

    @Nested
    @DisplayName("vehicleEntry - Visitor 首次入场激活")
    class VisitorEntryActivationTests {

        private CarPlate createVisitorCarPlate() {
            CarPlate carPlate = new CarPlate();
            carPlate.setId(2L);
            carPlate.setCommunityId(COMMUNITY_ID);
            carPlate.setHouseNo(HOUSE_NO);
            carPlate.setCarNumber(CAR_NUMBER);
            carPlate.setStatus("visitor");
            carPlate.setIsDeleted(0);
            return carPlate;
        }

        private VisitorAuthorization createPendingAuthorization(LocalDateTime expireTime) {
            VisitorAuthorization auth = new VisitorAuthorization();
            auth.setId(10L);
            auth.setCommunityId(COMMUNITY_ID);
            auth.setHouseNo(HOUSE_NO);
            auth.setCarPlateId(2L);
            auth.setCarNumber(CAR_NUMBER);
            auth.setStatus("approved_pending_activation");
            auth.setStartTime(expireTime.minusHours(24));
            auth.setExpireTime(expireTime);
            return auth;
        }

        @Test
        @DisplayName("Visitor 首次入场 - 激活窗口内，激活成功并创建会话")
        void visitorEntry_shouldActivateWithinWindow() {
            EntryRequest request = createEntryRequest();
            CarPlate carPlate = createVisitorCarPlate();
            // 激活窗口截止时间在未来
            VisitorAuthorization auth = createPendingAuthorization(LocalDateTime.now().plusHours(12));

            when(idempotencyService.getResult(anyString())).thenReturn(Optional.empty());
            when(carPlateMapper.selectByCommunityAndCarNumber(COMMUNITY_ID, CAR_NUMBER)).thenReturn(carPlate);
            setupLockMock();
            when(parkingSpaceCalculator.calculateAvailableSpaces(COMMUNITY_ID)).thenReturn(50);
            when(visitorAuthorizationMapper.selectPendingActivation(COMMUNITY_ID, CAR_NUMBER)).thenReturn(auth);
            when(idempotencyService.checkAndSet(anyString(), anyString(), eq(300))).thenReturn(true);

            EntryResponse response = entryService.vehicleEntry(request);

            assertNotNull(response);
            // 验证授权状态更新为 activated
            verify(visitorAuthorizationMapper).updateActivation(eq(10L), eq("activated"), any(LocalDateTime.class));
            // 验证创建了 visitor_session
            verify(visitorSessionMapper).insert(argThat(session ->
                    session.getCommunityId().equals(COMMUNITY_ID)
                            && session.getHouseNo().equals(HOUSE_NO)
                            && session.getAuthorizationId().equals(10L)
                            && "in_park".equals(session.getStatus())
                            && session.getAccumulatedDuration() == 0
            ));
            // 验证创建了入场记录
            verify(parkingCarRecordMapper).insertToTable(anyString(), any());
        }

        @Test
        @DisplayName("Visitor 首次入场 - 激活窗口已过期，拒绝入场并取消授权")
        void visitorEntry_shouldRejectWhenWindowExpired() {
            EntryRequest request = createEntryRequest();
            CarPlate carPlate = createVisitorCarPlate();
            // 激活窗口截止时间在过去
            VisitorAuthorization auth = createPendingAuthorization(LocalDateTime.now().minusHours(1));

            when(idempotencyService.getResult(anyString())).thenReturn(Optional.empty());
            when(carPlateMapper.selectByCommunityAndCarNumber(COMMUNITY_ID, CAR_NUMBER)).thenReturn(carPlate);
            setupLockMock();
            when(parkingSpaceCalculator.calculateAvailableSpaces(COMMUNITY_ID)).thenReturn(50);
            when(visitorAuthorizationMapper.selectPendingActivation(COMMUNITY_ID, CAR_NUMBER)).thenReturn(auth);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> entryService.vehicleEntry(request));

            assertEquals(8001, ex.getCode()); // PARKING_8001
            // 验证授权状态更新为 canceled_no_entry
            verify(visitorAuthorizationMapper).updateStatus(10L, "canceled_no_entry");
            // 验证未创建入场记录
            verify(parkingCarRecordMapper, never()).insertToTable(anyString(), any());
        }

        @Test
        @DisplayName("Visitor 入场 - 无待激活授权，有 out_of_park 会话，再次入场成功")
        void visitorEntry_shouldReenterWhenOutOfParkSession() {
            EntryRequest request = createEntryRequest();
            CarPlate carPlate = createVisitorCarPlate();

            VisitorSession outSession = new VisitorSession();
            outSession.setId(20L);
            outSession.setCommunityId(COMMUNITY_ID);
            outSession.setHouseNo(HOUSE_NO);
            outSession.setCarNumber(CAR_NUMBER);
            outSession.setStatus("out_of_park");
            outSession.setAccumulatedDuration(60);

            when(idempotencyService.getResult(anyString())).thenReturn(Optional.empty());
            when(carPlateMapper.selectByCommunityAndCarNumber(COMMUNITY_ID, CAR_NUMBER)).thenReturn(carPlate);
            setupLockMock();
            when(parkingSpaceCalculator.calculateAvailableSpaces(COMMUNITY_ID)).thenReturn(50);
            when(visitorAuthorizationMapper.selectPendingActivation(COMMUNITY_ID, CAR_NUMBER)).thenReturn(null);
            when(visitorSessionMapper.selectActiveByCarNumber(COMMUNITY_ID, CAR_NUMBER)).thenReturn(null);
            when(visitorSessionMapper.selectOutOfParkByCarNumber(COMMUNITY_ID, CAR_NUMBER)).thenReturn(outSession);
            when(idempotencyService.checkAndSet(anyString(), anyString(), eq(300))).thenReturn(true);

            EntryResponse response = entryService.vehicleEntry(request);

            assertNotNull(response);
            // 验证更新会话状态为 in_park
            verify(visitorSessionMapper).updateStatusAndEntryTime(eq(20L), eq("in_park"), any(LocalDateTime.class));
            // 验证创建了入场记录
            verify(parkingCarRecordMapper).insertToTable(anyString(), any());
        }
    }
}
