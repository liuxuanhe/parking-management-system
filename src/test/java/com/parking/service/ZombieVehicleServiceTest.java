package com.parking.service;

import com.parking.dto.ZombieVehicleQueryResponse;
import com.parking.mapper.ZombieVehicleMapper;
import com.parking.model.ZombieVehicle;
import com.parking.service.impl.ZombieVehicleServiceImpl;
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
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * ZombieVehicleServiceImpl 单元测试
 */
@ExtendWith(MockitoExtension.class)
class ZombieVehicleServiceTest {

    @Mock
    private ZombieVehicleMapper zombieVehicleMapper;

    @Mock
    private MaskingService maskingService;

    @InjectMocks
    private ZombieVehicleServiceImpl zombieVehicleService;

    private static final Long COMMUNITY_ID = 1001L;

    @Test
    @DisplayName("查询僵尸车辆列表 - 有数据且车牌脱敏")
    void listZombieVehicles_withData() {
        ZombieVehicle z1 = buildZombie(1L, "京A12345", "101", 10, "unhandled");
        ZombieVehicle z2 = buildZombie(2L, "京B67890", "102", 8, "contacted");
        when(zombieVehicleMapper.selectByCommunityAndStatus(COMMUNITY_ID, null))
                .thenReturn(List.of(z1, z2));
        when(maskingService.mask(eq("京A12345"), eq(2), eq(2))).thenReturn("京A***45");
        when(maskingService.mask(eq("京B67890"), eq(2), eq(2))).thenReturn("京B***90");

        List<ZombieVehicleQueryResponse> result = zombieVehicleService.listZombieVehicles(COMMUNITY_ID, null);

        assertEquals(2, result.size());
        assertEquals("京A***45", result.get(0).getCarNumber());
        assertEquals(10, result.get(0).getContinuousDays());
        assertEquals("unhandled", result.get(0).getStatus());
        assertEquals("京B***90", result.get(1).getCarNumber());
    }

    @Test
    @DisplayName("查询僵尸车辆列表 - 按状态筛选")
    void listZombieVehicles_filterByStatus() {
        ZombieVehicle z = buildZombie(3L, "京C11111", "103", 12, "unhandled");
        when(zombieVehicleMapper.selectByCommunityAndStatus(COMMUNITY_ID, "unhandled"))
                .thenReturn(List.of(z));
        when(maskingService.mask(anyString(), eq(2), eq(2))).thenReturn("京C***11");

        List<ZombieVehicleQueryResponse> result = zombieVehicleService.listZombieVehicles(COMMUNITY_ID, "unhandled");

        assertEquals(1, result.size());
        assertEquals("unhandled", result.get(0).getStatus());
        verify(zombieVehicleMapper).selectByCommunityAndStatus(COMMUNITY_ID, "unhandled");
    }

    @Test
    @DisplayName("查询僵尸车辆列表 - 无数据返回空列表")
    void listZombieVehicles_noData() {
        when(zombieVehicleMapper.selectByCommunityAndStatus(COMMUNITY_ID, null))
                .thenReturn(Collections.emptyList());

        List<ZombieVehicleQueryResponse> result = zombieVehicleService.listZombieVehicles(COMMUNITY_ID, null);

        assertTrue(result.isEmpty());
    }

    private ZombieVehicle buildZombie(Long id, String carNumber, String houseNo, int days, String status) {
        ZombieVehicle z = new ZombieVehicle();
        z.setId(id);
        z.setCommunityId(COMMUNITY_ID);
        z.setCarNumber(carNumber);
        z.setHouseNo(houseNo);
        z.setEnterTime(LocalDateTime.now().minusDays(days));
        z.setContinuousDays(days);
        z.setStatus(status);
        z.setCreateTime(LocalDateTime.now());
        return z;
    }
}
