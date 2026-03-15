package com.parking.service;

import com.parking.common.BusinessException;
import com.parking.common.ErrorCode;
import com.parking.dto.ZombieVehicleHandleRequest;
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
    private static final Long ADMIN_ID = 100L;

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

    // ===== 处理僵尸车辆测试 =====

    @Test
    @DisplayName("处理僵尸车辆 - contacted 成功")
    void handleZombieVehicle_contacted() {
        ZombieVehicle zombie = buildZombie(1L, "京A12345", "101", 10, "unhandled");
        when(zombieVehicleMapper.selectById(1L)).thenReturn(zombie);

        ZombieVehicleHandleRequest request = new ZombieVehicleHandleRequest();
        request.setCommunityId(COMMUNITY_ID);
        request.setHandleType("contacted");
        request.setContactRecord("已电话联系车主，约定明日移车");

        zombieVehicleService.handleZombieVehicle(1L, request, ADMIN_ID);

        verify(zombieVehicleMapper).updateHandle(argThat(z ->
                "contacted".equals(z.getStatus()) &&
                "已电话联系车主，约定明日移车".equals(z.getContactRecord()) &&
                ADMIN_ID.equals(z.getHandlerAdminId()) &&
                z.getHandleTime() != null
        ));
    }

    @Test
    @DisplayName("处理僵尸车辆 - resolved 成功")
    void handleZombieVehicle_resolved() {
        ZombieVehicle zombie = buildZombie(2L, "京B67890", "102", 8, "contacted");
        when(zombieVehicleMapper.selectById(2L)).thenReturn(zombie);

        ZombieVehicleHandleRequest request = new ZombieVehicleHandleRequest();
        request.setCommunityId(COMMUNITY_ID);
        request.setHandleType("resolved");
        request.setSolution("车主已将车辆移走");

        zombieVehicleService.handleZombieVehicle(2L, request, ADMIN_ID);

        verify(zombieVehicleMapper).updateHandle(argThat(z ->
                "resolved".equals(z.getStatus()) &&
                "车主已将车辆移走".equals(z.getSolution())
        ));
    }

    @Test
    @DisplayName("处理僵尸车辆 - ignored 成功")
    void handleZombieVehicle_ignored() {
        ZombieVehicle zombie = buildZombie(3L, "京C11111", "103", 15, "unhandled");
        when(zombieVehicleMapper.selectById(3L)).thenReturn(zombie);

        ZombieVehicleHandleRequest request = new ZombieVehicleHandleRequest();
        request.setCommunityId(COMMUNITY_ID);
        request.setHandleType("ignored");
        request.setIgnoreReason("车主长期出差，已知情");

        zombieVehicleService.handleZombieVehicle(3L, request, ADMIN_ID);

        verify(zombieVehicleMapper).updateHandle(argThat(z ->
                "ignored".equals(z.getStatus()) &&
                "车主长期出差，已知情".equals(z.getIgnoreReason())
        ));
    }

    @Test
    @DisplayName("处理僵尸车辆 - 记录不存在抛出 PARKING_22001")
    void handleZombieVehicle_notFound() {
        when(zombieVehicleMapper.selectById(999L)).thenReturn(null);

        ZombieVehicleHandleRequest request = new ZombieVehicleHandleRequest();
        request.setCommunityId(COMMUNITY_ID);
        request.setHandleType("contacted");

        BusinessException ex = assertThrows(BusinessException.class,
                () -> zombieVehicleService.handleZombieVehicle(999L, request, ADMIN_ID));
        assertEquals(ErrorCode.PARKING_22001.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("处理僵尸车辆 - 已 resolved 不允许重复操作")
    void handleZombieVehicle_alreadyResolved() {
        ZombieVehicle zombie = buildZombie(4L, "京D22222", "104", 9, "resolved");
        when(zombieVehicleMapper.selectById(4L)).thenReturn(zombie);

        ZombieVehicleHandleRequest request = new ZombieVehicleHandleRequest();
        request.setCommunityId(COMMUNITY_ID);
        request.setHandleType("contacted");

        BusinessException ex = assertThrows(BusinessException.class,
                () -> zombieVehicleService.handleZombieVehicle(4L, request, ADMIN_ID));
        assertEquals(ErrorCode.PARKING_22002.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("处理僵尸车辆 - 无效处理方式抛出 PARKING_22003")
    void handleZombieVehicle_invalidHandleType() {
        ZombieVehicleHandleRequest request = new ZombieVehicleHandleRequest();
        request.setCommunityId(COMMUNITY_ID);
        request.setHandleType("invalid_type");

        BusinessException ex = assertThrows(BusinessException.class,
                () -> zombieVehicleService.handleZombieVehicle(1L, request, ADMIN_ID));
        assertEquals(ErrorCode.PARKING_22003.getCode(), ex.getCode());
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
