package com.parking.service;

import com.parking.common.BusinessException;
import com.parking.dto.ParkingConfigResponse;
import com.parking.dto.ParkingConfigUpdateRequest;
import com.parking.mapper.ParkingConfigMapper;
import com.parking.model.ParkingConfig;
import com.parking.service.impl.ParkingConfigServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * ParkingConfigService 单元测试
 * Validates: Requirements 9.5, 9.6, 9.7, 9.8
 */
@ExtendWith(MockitoExtension.class)
class ParkingConfigServiceTest {

    @Mock
    private ParkingConfigMapper parkingConfigMapper;

    @Mock
    private CacheService cacheService;

    private ParkingConfigServiceImpl parkingConfigService;

    private static final Long COMMUNITY_ID = 1001L;

    @BeforeEach
    void setUp() {
        parkingConfigService = new ParkingConfigServiceImpl(parkingConfigMapper, cacheService);
    }

    /**
     * 创建测试用停车场配置
     */
    private ParkingConfig createConfig() {
        ParkingConfig config = new ParkingConfig();
        config.setId(1L);
        config.setCommunityId(COMMUNITY_ID);
        config.setTotalSpaces(100);
        config.setReservedSpaces(10);
        config.setVisitorQuotaHours(72);
        config.setVisitorSingleDurationHours(24);
        config.setVisitorActivationWindowHours(24);
        config.setZombieVehicleThresholdDays(7);
        config.setVersion(1);
        config.setCreateTime(LocalDateTime.of(2024, 1, 1, 0, 0));
        config.setUpdateTime(LocalDateTime.of(2024, 1, 1, 0, 0));
        return config;
    }

    /**
     * 创建测试用修改请求
     */
    private ParkingConfigUpdateRequest createUpdateRequest(int totalSpaces, int version) {
        ParkingConfigUpdateRequest request = new ParkingConfigUpdateRequest();
        request.setCommunityId(COMMUNITY_ID);
        request.setTotalSpaces(totalSpaces);
        request.setReservedSpaces(10);
        request.setVisitorQuotaHours(72);
        request.setVisitorSingleDurationHours(24);
        request.setVisitorActivationWindowHours(24);
        request.setZombieVehicleThresholdDays(7);
        request.setVersion(version);
        return request;
    }

    @Nested
    @DisplayName("getConfig - 查询停车场配置")
    class GetConfigTests {

        @Test
        @DisplayName("缓存命中时应直接返回缓存结果")
        void getConfig_cacheHit_shouldReturnCached() {
            String cacheKey = "parking_config:1001";
            ParkingConfigResponse cachedResponse = new ParkingConfigResponse();
            cachedResponse.setTotalSpaces(100);

            when(cacheService.generateKey("parking_config", COMMUNITY_ID)).thenReturn(cacheKey);
            when(cacheService.get(cacheKey)).thenReturn(Optional.of(cachedResponse));

            ParkingConfigResponse result = parkingConfigService.getConfig(COMMUNITY_ID);

            assertNotNull(result);
            assertEquals(100, result.getTotalSpaces());
            verify(parkingConfigMapper, never()).selectByCommunityId(anyLong());
        }

        @Test
        @DisplayName("缓存未命中时应查询数据库并写入缓存")
        void getConfig_cacheMiss_shouldQueryDbAndSetCache() {
            String cacheKey = "parking_config:1001";
            ParkingConfig config = createConfig();

            when(cacheService.generateKey("parking_config", COMMUNITY_ID)).thenReturn(cacheKey);
            when(cacheService.get(cacheKey)).thenReturn(Optional.empty());
            when(parkingConfigMapper.selectByCommunityId(COMMUNITY_ID)).thenReturn(config);

            ParkingConfigResponse result = parkingConfigService.getConfig(COMMUNITY_ID);

            assertNotNull(result);
            assertEquals(100, result.getTotalSpaces());
            assertEquals(1, result.getVersion());
            verify(cacheService).set(eq(cacheKey), any(ParkingConfigResponse.class), eq(30L), any());
        }

        @Test
        @DisplayName("配置不存在时应自动创建默认配置")
        void getConfig_notFound_shouldCreateDefaultConfig() {
            String cacheKey = "parking_config:1001";

            when(cacheService.generateKey("parking_config", COMMUNITY_ID)).thenReturn(cacheKey);
            when(cacheService.get(cacheKey)).thenReturn(Optional.empty());
            when(parkingConfigMapper.selectByCommunityId(COMMUNITY_ID)).thenReturn(null);
            when(parkingConfigMapper.insert(any(ParkingConfig.class))).thenReturn(1);

            ParkingConfigResponse result = parkingConfigService.getConfig(COMMUNITY_ID);

            assertNotNull(result);
            // 验证默认值
            assertEquals(100, result.getTotalSpaces());
            assertEquals(0, result.getReservedSpaces());
            assertEquals(72, result.getVisitorQuotaHours());
            assertEquals(24, result.getVisitorSingleDurationHours());
            assertEquals(24, result.getVisitorActivationWindowHours());
            assertEquals(7, result.getZombieVehicleThresholdDays());
            assertEquals(1, result.getVersion());
            // 验证调用了 insert
            verify(parkingConfigMapper).insert(any(ParkingConfig.class));
            // 验证写入了缓存
            verify(cacheService).set(eq(cacheKey), any(ParkingConfigResponse.class), eq(30L), any());
        }

        @Test
        @DisplayName("应正确映射所有字段到响应 DTO")
        void getConfig_shouldMapAllFields() {
            String cacheKey = "parking_config:1001";
            ParkingConfig config = createConfig();

            when(cacheService.generateKey("parking_config", COMMUNITY_ID)).thenReturn(cacheKey);
            when(cacheService.get(cacheKey)).thenReturn(Optional.empty());
            when(parkingConfigMapper.selectByCommunityId(COMMUNITY_ID)).thenReturn(config);

            ParkingConfigResponse result = parkingConfigService.getConfig(COMMUNITY_ID);

            assertEquals(1L, result.getId());
            assertEquals(COMMUNITY_ID, result.getCommunityId());
            assertEquals(100, result.getTotalSpaces());
            assertEquals(10, result.getReservedSpaces());
            assertEquals(72, result.getVisitorQuotaHours());
            assertEquals(24, result.getVisitorSingleDurationHours());
            assertEquals(24, result.getVisitorActivationWindowHours());
            assertEquals(7, result.getZombieVehicleThresholdDays());
            assertEquals(1, result.getVersion());
            assertNotNull(result.getCreateTime());
            assertNotNull(result.getUpdateTime());
        }
    }

    @Nested
    @DisplayName("updateConfig - 修改停车场配置")
    class UpdateConfigTests {

        @Test
        @DisplayName("正常修改配置应成功并返回更新后的配置")
        void updateConfig_success() {
            ParkingConfig config = createConfig();
            ParkingConfig updatedConfig = createConfig();
            updatedConfig.setTotalSpaces(150);
            updatedConfig.setVersion(2);

            ParkingConfigUpdateRequest request = createUpdateRequest(150, 1);

            when(parkingConfigMapper.selectByCommunityId(COMMUNITY_ID))
                    .thenReturn(config)
                    .thenReturn(updatedConfig);
            when(parkingConfigMapper.countEnteredVehicles(COMMUNITY_ID)).thenReturn(50);
            when(parkingConfigMapper.updateByOptimisticLock(any(ParkingConfig.class))).thenReturn(1);
            when(cacheService.generateKey("parking_config", COMMUNITY_ID)).thenReturn("parking_config:1001");

            ParkingConfigResponse result = parkingConfigService.updateConfig(request);

            assertNotNull(result);
            assertEquals(150, result.getTotalSpaces());
            assertEquals(2, result.getVersion());
        }

        @Test
        @DisplayName("新 totalSpaces < 当前在场车辆数应抛出 PARKING_9002")
        void updateConfig_totalSpacesLessThanEntered_shouldThrowParking9002() {
            ParkingConfig config = createConfig();
            ParkingConfigUpdateRequest request = createUpdateRequest(30, 1);

            when(parkingConfigMapper.selectByCommunityId(COMMUNITY_ID)).thenReturn(config);
            when(parkingConfigMapper.countEnteredVehicles(COMMUNITY_ID)).thenReturn(50);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> parkingConfigService.updateConfig(request));
            assertEquals(9002, ex.getCode());
            verify(parkingConfigMapper, never()).updateByOptimisticLock(any());
        }

        @Test
        @DisplayName("新 totalSpaces 等于当前在场车辆数应允许修改")
        void updateConfig_totalSpacesEqualsEntered_shouldSucceed() {
            ParkingConfig config = createConfig();
            ParkingConfig updatedConfig = createConfig();
            updatedConfig.setTotalSpaces(50);
            updatedConfig.setVersion(2);

            ParkingConfigUpdateRequest request = createUpdateRequest(50, 1);

            when(parkingConfigMapper.selectByCommunityId(COMMUNITY_ID))
                    .thenReturn(config)
                    .thenReturn(updatedConfig);
            when(parkingConfigMapper.countEnteredVehicles(COMMUNITY_ID)).thenReturn(50);
            when(parkingConfigMapper.updateByOptimisticLock(any(ParkingConfig.class))).thenReturn(1);
            when(cacheService.generateKey("parking_config", COMMUNITY_ID)).thenReturn("parking_config:1001");

            ParkingConfigResponse result = parkingConfigService.updateConfig(request);

            assertNotNull(result);
            assertEquals(50, result.getTotalSpaces());
        }

        @Test
        @DisplayName("乐观锁冲突（version 不匹配）应抛出异常")
        void updateConfig_optimisticLockConflict_shouldThrowException() {
            ParkingConfig config = createConfig();
            ParkingConfigUpdateRequest request = createUpdateRequest(150, 1);

            when(parkingConfigMapper.selectByCommunityId(COMMUNITY_ID)).thenReturn(config);
            when(parkingConfigMapper.countEnteredVehicles(COMMUNITY_ID)).thenReturn(50);
            when(parkingConfigMapper.updateByOptimisticLock(any(ParkingConfig.class))).thenReturn(0);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> parkingConfigService.updateConfig(request));
            assertEquals(10000, ex.getCode());
            assertTrue(ex.getMessage().contains("数据已被其他操作修改"));
        }

        @Test
        @DisplayName("修改配置后应失效停车场配置缓存和报表缓存")
        void updateConfig_shouldInvalidateCaches() {
            ParkingConfig config = createConfig();
            ParkingConfig updatedConfig = createConfig();
            updatedConfig.setTotalSpaces(150);
            updatedConfig.setVersion(2);

            ParkingConfigUpdateRequest request = createUpdateRequest(150, 1);

            when(parkingConfigMapper.selectByCommunityId(COMMUNITY_ID))
                    .thenReturn(config)
                    .thenReturn(updatedConfig);
            when(parkingConfigMapper.countEnteredVehicles(COMMUNITY_ID)).thenReturn(50);
            when(parkingConfigMapper.updateByOptimisticLock(any(ParkingConfig.class))).thenReturn(1);
            when(cacheService.generateKey("parking_config", COMMUNITY_ID)).thenReturn("parking_config:1001");

            parkingConfigService.updateConfig(request);

            // 验证停车场配置缓存失效
            verify(cacheService).delete("parking_config:1001");
            // 验证报表缓存失效
            verify(cacheService).deleteByPrefix("report:1001");
        }

        @Test
        @DisplayName("配置不存在时应抛出异常")
        void updateConfig_configNotFound_shouldThrowException() {
            ParkingConfigUpdateRequest request = createUpdateRequest(150, 1);

            when(parkingConfigMapper.selectByCommunityId(COMMUNITY_ID)).thenReturn(null);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> parkingConfigService.updateConfig(request));
            assertEquals(10000, ex.getCode());
            assertTrue(ex.getMessage().contains("停车场配置不存在"));
        }

        @Test
        @DisplayName("部分字段为 null 时应保留原值")
        void updateConfig_partialUpdate_shouldKeepOriginalValues() {
            ParkingConfig config = createConfig();
            ParkingConfig updatedConfig = createConfig();
            updatedConfig.setTotalSpaces(200);
            updatedConfig.setVersion(2);

            ParkingConfigUpdateRequest request = new ParkingConfigUpdateRequest();
            request.setCommunityId(COMMUNITY_ID);
            request.setTotalSpaces(200);
            request.setVersion(1);
            // 其他字段为 null，应保留原值

            when(parkingConfigMapper.selectByCommunityId(COMMUNITY_ID))
                    .thenReturn(config)
                    .thenReturn(updatedConfig);
            when(parkingConfigMapper.countEnteredVehicles(COMMUNITY_ID)).thenReturn(10);
            when(parkingConfigMapper.updateByOptimisticLock(any(ParkingConfig.class))).thenReturn(1);
            when(cacheService.generateKey("parking_config", COMMUNITY_ID)).thenReturn("parking_config:1001");

            ParkingConfigResponse result = parkingConfigService.updateConfig(request);

            assertNotNull(result);
            // 验证 updateByOptimisticLock 被调用时，未修改的字段保留原值
            verify(parkingConfigMapper).updateByOptimisticLock(argThat(c ->
                    c.getReservedSpaces() == 10 &&
                    c.getVisitorQuotaHours() == 72 &&
                    c.getVisitorSingleDurationHours() == 24
            ));
        }
    }
}
