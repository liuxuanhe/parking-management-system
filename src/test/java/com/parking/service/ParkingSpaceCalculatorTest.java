package com.parking.service;

import com.parking.common.BusinessException;
import com.parking.mapper.ParkingConfigMapper;
import com.parking.model.ParkingConfig;
import com.parking.service.impl.ParkingSpaceCalculatorImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * ParkingSpaceCalculator 单元测试
 * Validates: Requirements 5.2, 5.3, 5.4, 9.1, 9.3, 9.4
 */
@ExtendWith(MockitoExtension.class)
class ParkingSpaceCalculatorTest {

    @Mock
    private ParkingConfigMapper parkingConfigMapper;

    @Mock
    private DistributedLockService distributedLockService;

    private ParkingSpaceCalculatorImpl calculator;

    private static final Long COMMUNITY_ID = 1001L;

    @BeforeEach
    void setUp() {
        calculator = new ParkingSpaceCalculatorImpl(parkingConfigMapper, distributedLockService);
    }

    /**
     * 创建测试用停车场配置
     */
    private ParkingConfig createConfig(int totalSpaces) {
        ParkingConfig config = new ParkingConfig();
        config.setId(1L);
        config.setCommunityId(COMMUNITY_ID);
        config.setTotalSpaces(totalSpaces);
        return config;
    }

    @Nested
    @DisplayName("calculateAvailableSpaces - 计算可用车位数")
    class CalculateAvailableSpacesTests {

        @Test
        @DisplayName("总车位100，在场30辆，应返回70")
        void shouldReturnCorrectAvailableSpaces() {
            when(parkingConfigMapper.selectByCommunityId(COMMUNITY_ID)).thenReturn(createConfig(100));
            when(parkingConfigMapper.countEnteredVehicles(COMMUNITY_ID)).thenReturn(30);

            int result = calculator.calculateAvailableSpaces(COMMUNITY_ID);

            assertEquals(70, result);
        }

        @Test
        @DisplayName("总车位100，在场0辆，应返回100")
        void shouldReturnTotalWhenNoVehiclesEntered() {
            when(parkingConfigMapper.selectByCommunityId(COMMUNITY_ID)).thenReturn(createConfig(100));
            when(parkingConfigMapper.countEnteredVehicles(COMMUNITY_ID)).thenReturn(0);

            int result = calculator.calculateAvailableSpaces(COMMUNITY_ID);

            assertEquals(100, result);
        }

        @Test
        @DisplayName("总车位100，在场100辆（满），应返回0")
        void shouldReturnZeroWhenFull() {
            when(parkingConfigMapper.selectByCommunityId(COMMUNITY_ID)).thenReturn(createConfig(100));
            when(parkingConfigMapper.countEnteredVehicles(COMMUNITY_ID)).thenReturn(100);

            int result = calculator.calculateAvailableSpaces(COMMUNITY_ID);

            assertEquals(0, result);
        }

        @Test
        @DisplayName("在场车辆数超过总车位数时，应返回0而非负数")
        void shouldReturnZeroWhenOverCapacity() {
            when(parkingConfigMapper.selectByCommunityId(COMMUNITY_ID)).thenReturn(createConfig(100));
            when(parkingConfigMapper.countEnteredVehicles(COMMUNITY_ID)).thenReturn(120);

            int result = calculator.calculateAvailableSpaces(COMMUNITY_ID);

            assertEquals(0, result);
        }

        @Test
        @DisplayName("停车场配置不存在时应抛出异常")
        void shouldThrowWhenConfigNotFound() {
            when(parkingConfigMapper.selectByCommunityId(COMMUNITY_ID)).thenReturn(null);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> calculator.calculateAvailableSpaces(COMMUNITY_ID));
            assertTrue(ex.getMessage().contains("停车场配置不存在"));
        }
    }

    @Nested
    @DisplayName("calculateVisitorAvailableSpaces - 计算 Visitor 可开放车位数")
    class CalculateVisitorAvailableSpacesTests {

        @Test
        @DisplayName("总车位100，在场30辆，Visitor 可开放车位应为70")
        void shouldReturnCorrectVisitorAvailableSpaces() {
            when(parkingConfigMapper.selectByCommunityId(COMMUNITY_ID)).thenReturn(createConfig(100));
            when(parkingConfigMapper.countEnteredVehicles(COMMUNITY_ID)).thenReturn(30);

            int result = calculator.calculateVisitorAvailableSpaces(COMMUNITY_ID);

            assertEquals(70, result);
        }

        @Test
        @DisplayName("车位已满时 Visitor 可开放车位应为0")
        void shouldReturnZeroWhenFull() {
            when(parkingConfigMapper.selectByCommunityId(COMMUNITY_ID)).thenReturn(createConfig(100));
            when(parkingConfigMapper.countEnteredVehicles(COMMUNITY_ID)).thenReturn(100);

            int result = calculator.calculateVisitorAvailableSpaces(COMMUNITY_ID);

            assertEquals(0, result);
        }

        @Test
        @DisplayName("停车场配置不存在时应抛出异常")
        void shouldThrowWhenConfigNotFound() {
            when(parkingConfigMapper.selectByCommunityId(COMMUNITY_ID)).thenReturn(null);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> calculator.calculateVisitorAvailableSpaces(COMMUNITY_ID));
            assertTrue(ex.getMessage().contains("停车场配置不存在"));
        }
    }

    @Nested
    @DisplayName("checkSpaceAvailable - 检查车位是否充足")
    class CheckSpaceAvailableTests {

        @BeforeEach
        void setUpLock() {
            // 模拟分布式锁：直接执行传入的 Supplier
            when(distributedLockService.executeWithLock(anyString(), any()))
                    .thenAnswer(invocation -> {
                        Supplier<?> supplier = invocation.getArgument(1);
                        return supplier.get();
                    });
        }

        @Test
        @DisplayName("Primary 车辆有可用车位时应返回 true")
        void primaryVehicle_spaceAvailable_shouldReturnTrue() {
            when(parkingConfigMapper.selectByCommunityId(COMMUNITY_ID)).thenReturn(createConfig(100));
            when(parkingConfigMapper.countEnteredVehicles(COMMUNITY_ID)).thenReturn(50);

            boolean result = calculator.checkSpaceAvailable(COMMUNITY_ID, "primary");

            assertTrue(result);
        }

        @Test
        @DisplayName("Primary 车辆车位已满时应返回 false")
        void primaryVehicle_noSpace_shouldReturnFalse() {
            when(parkingConfigMapper.selectByCommunityId(COMMUNITY_ID)).thenReturn(createConfig(100));
            when(parkingConfigMapper.countEnteredVehicles(COMMUNITY_ID)).thenReturn(100);

            boolean result = calculator.checkSpaceAvailable(COMMUNITY_ID, "primary");

            assertFalse(result);
        }

        @Test
        @DisplayName("Visitor 车辆有可用车位时应返回 true")
        void visitorVehicle_spaceAvailable_shouldReturnTrue() {
            when(parkingConfigMapper.selectByCommunityId(COMMUNITY_ID)).thenReturn(createConfig(100));
            when(parkingConfigMapper.countEnteredVehicles(COMMUNITY_ID)).thenReturn(50);

            boolean result = calculator.checkSpaceAvailable(COMMUNITY_ID, "visitor");

            assertTrue(result);
        }

        @Test
        @DisplayName("Visitor 车辆车位已满时应返回 false")
        void visitorVehicle_noSpace_shouldReturnFalse() {
            when(parkingConfigMapper.selectByCommunityId(COMMUNITY_ID)).thenReturn(createConfig(100));
            when(parkingConfigMapper.countEnteredVehicles(COMMUNITY_ID)).thenReturn(100);

            boolean result = calculator.checkSpaceAvailable(COMMUNITY_ID, "visitor");

            assertFalse(result);
        }

        @Test
        @DisplayName("应使用正确的分布式锁键名 lock:space:{communityId}")
        void shouldUseCorrectLockKey() {
            when(parkingConfigMapper.selectByCommunityId(COMMUNITY_ID)).thenReturn(createConfig(100));
            when(parkingConfigMapper.countEnteredVehicles(COMMUNITY_ID)).thenReturn(50);

            calculator.checkSpaceAvailable(COMMUNITY_ID, "primary");

            verify(distributedLockService).executeWithLock(eq("lock:space:" + COMMUNITY_ID), any());
        }

        @Test
        @DisplayName("vehicleType 大小写不敏感，VISITOR 应走 Visitor 逻辑")
        void vehicleType_caseInsensitive() {
            when(parkingConfigMapper.selectByCommunityId(COMMUNITY_ID)).thenReturn(createConfig(100));
            when(parkingConfigMapper.countEnteredVehicles(COMMUNITY_ID)).thenReturn(50);

            boolean result = calculator.checkSpaceAvailable(COMMUNITY_ID, "VISITOR");

            assertTrue(result);
        }

        @Test
        @DisplayName("分布式锁获取失败时应抛出 LOCK_ACQUIRE_FAILED 异常")
        void lockAcquireFailed_shouldThrowException() {
            // 重置 mock，模拟锁获取失败
            reset(distributedLockService);
            when(distributedLockService.executeWithLock(anyString(), any()))
                    .thenThrow(new BusinessException(com.parking.common.ErrorCode.LOCK_ACQUIRE_FAILED));

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> calculator.checkSpaceAvailable(COMMUNITY_ID, "primary"));
            assertEquals(10010, ex.getCode());
        }
    }
}
