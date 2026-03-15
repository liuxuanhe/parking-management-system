package com.parking.service;

import com.parking.common.BusinessException;
import com.parking.dto.VisitorApplyRequest;
import com.parking.dto.VisitorApplyResponse;
import com.parking.dto.VisitorAuditRequest;
import com.parking.dto.VisitorQueryResponse;
import com.parking.dto.VisitorQuotaResponse;
import com.parking.mapper.CarPlateMapper;
import com.parking.mapper.VisitorApplicationMapper;
import com.parking.mapper.VisitorAuthorizationMapper;
import com.parking.model.CarPlate;
import com.parking.model.VisitorApplication;
import com.parking.model.VisitorAuthorization;
import com.parking.service.impl.VisitorServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * VisitorService 单元测试
 * Validates: Requirements 7.1, 7.2, 7.3, 7.4, 9.2
 */
@ExtendWith(MockitoExtension.class)
class VisitorServiceTest {

    @Mock
    private CarPlateMapper carPlateMapper;

    @Mock
    private VisitorApplicationMapper visitorApplicationMapper;

    @Mock
    private VisitorAuthorizationMapper visitorAuthorizationMapper;

    @Mock
    private VisitorQuotaManager visitorQuotaManager;

    @Mock
    private ParkingSpaceCalculator parkingSpaceCalculator;

    @Mock
    private MaskingService maskingService;

    @InjectMocks
    private VisitorServiceImpl visitorService;

    private static final Long COMMUNITY_ID = 1001L;
    private static final String HOUSE_NO = "1-101";
    private static final Long OWNER_ID = 100L;
    private static final Long CAR_PLATE_ID = 200L;

    private VisitorApplyRequest buildRequest() {
        VisitorApplyRequest request = new VisitorApplyRequest();
        request.setCarPlateId(CAR_PLATE_ID);
        request.setCarNumber("京A12345");
        request.setApplyReason("临时访客");
        return request;
    }

    private CarPlate buildCarPlate() {
        CarPlate carPlate = new CarPlate();
        carPlate.setId(CAR_PLATE_ID);
        carPlate.setCommunityId(COMMUNITY_ID);
        carPlate.setHouseNo(HOUSE_NO);
        carPlate.setOwnerId(OWNER_ID);
        carPlate.setCarNumber("京A12345");
        carPlate.setStatus("normal");
        return carPlate;
    }

    @Test
    @DisplayName("申请成功 - 配额充足且车位充足")
    void apply_shouldSucceedWhenQuotaAndSpaceSufficient() {
        VisitorApplyRequest request = buildRequest();
        CarPlate carPlate = buildCarPlate();

        when(carPlateMapper.selectById(CAR_PLATE_ID)).thenReturn(carPlate);
        when(visitorQuotaManager.checkQuotaSufficient(COMMUNITY_ID, HOUSE_NO)).thenReturn(true);
        when(parkingSpaceCalculator.calculateVisitorAvailableSpaces(COMMUNITY_ID)).thenReturn(5);

        VisitorApplyResponse response = visitorService.apply(request, OWNER_ID, COMMUNITY_ID, HOUSE_NO);

        assertNotNull(response);
        assertEquals("submitted", response.getStatus());
        verify(visitorApplicationMapper).insert(argThat(app ->
                app.getCommunityId().equals(COMMUNITY_ID)
                        && app.getHouseNo().equals(HOUSE_NO)
                        && app.getOwnerId().equals(OWNER_ID)
                        && "submitted".equals(app.getStatus())
        ));
    }

    @Test
    @DisplayName("申请失败 - 车牌不存在")
    void apply_shouldFailWhenCarPlateNotFound() {
        VisitorApplyRequest request = buildRequest();
        when(carPlateMapper.selectById(CAR_PLATE_ID)).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> visitorService.apply(request, OWNER_ID, COMMUNITY_ID, HOUSE_NO));

        assertEquals(10000, ex.getCode()); // PARAM_ERROR
        verify(visitorApplicationMapper, never()).insert(any());
    }

    @Test
    @DisplayName("申请失败 - 车牌不属于当前小区")
    void apply_shouldFailWhenCarPlateNotInCommunity() {
        VisitorApplyRequest request = buildRequest();
        CarPlate carPlate = buildCarPlate();
        carPlate.setCommunityId(9999L); // 不同小区

        when(carPlateMapper.selectById(CAR_PLATE_ID)).thenReturn(carPlate);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> visitorService.apply(request, OWNER_ID, COMMUNITY_ID, HOUSE_NO));

        assertEquals(10000, ex.getCode());
        verify(visitorApplicationMapper, never()).insert(any());
    }

    @Test
    @DisplayName("申请失败 - 车牌不属于当前房屋号")
    void apply_shouldFailWhenCarPlateNotInHouse() {
        VisitorApplyRequest request = buildRequest();
        CarPlate carPlate = buildCarPlate();
        carPlate.setHouseNo("2-202"); // 不同房屋号

        when(carPlateMapper.selectById(CAR_PLATE_ID)).thenReturn(carPlate);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> visitorService.apply(request, OWNER_ID, COMMUNITY_ID, HOUSE_NO));

        assertEquals(10000, ex.getCode());
    }

    @Test
    @DisplayName("申请失败 - 月度配额不足 (PARKING_7001)")
    void apply_shouldFailWhenQuotaInsufficient() {
        VisitorApplyRequest request = buildRequest();
        CarPlate carPlate = buildCarPlate();

        when(carPlateMapper.selectById(CAR_PLATE_ID)).thenReturn(carPlate);
        when(visitorQuotaManager.checkQuotaSufficient(COMMUNITY_ID, HOUSE_NO)).thenReturn(false);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> visitorService.apply(request, OWNER_ID, COMMUNITY_ID, HOUSE_NO));

        assertEquals(7001, ex.getCode()); // PARKING_7001
        verify(visitorApplicationMapper, never()).insert(any());
    }

    @Test
    @DisplayName("申请失败 - Visitor 可开放车位不足 (PARKING_9001)")
    void apply_shouldFailWhenNoVisitorSpaces() {
        VisitorApplyRequest request = buildRequest();
        CarPlate carPlate = buildCarPlate();

        when(carPlateMapper.selectById(CAR_PLATE_ID)).thenReturn(carPlate);
        when(visitorQuotaManager.checkQuotaSufficient(COMMUNITY_ID, HOUSE_NO)).thenReturn(true);
        when(parkingSpaceCalculator.calculateVisitorAvailableSpaces(COMMUNITY_ID)).thenReturn(0);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> visitorService.apply(request, OWNER_ID, COMMUNITY_ID, HOUSE_NO));

        assertEquals(9001, ex.getCode()); // PARKING_9001
        verify(visitorApplicationMapper, never()).insert(any());
    }

    // ========== listVisitors 测试 ==========

    @Test
    @DisplayName("查询成功 - 返回申请和授权关联数据并脱敏")
    void listVisitors_shouldReturnApplicationsWithAuthorizations() {
        VisitorApplication app = new VisitorApplication();
        app.setId(1L);
        app.setCarNumber("京A12345");
        app.setApplyReason("临时访客");
        app.setStatus("approved_pending_activation");
        app.setCreateTime(LocalDateTime.of(2026, 3, 15, 10, 0));

        VisitorAuthorization auth = new VisitorAuthorization();
        auth.setId(10L);
        auth.setApplicationId(1L);
        auth.setStatus("approved_pending_activation");
        auth.setStartTime(LocalDateTime.of(2026, 3, 15, 10, 0));
        auth.setExpireTime(LocalDateTime.of(2026, 3, 16, 10, 0));

        when(visitorApplicationMapper.selectByHouse(COMMUNITY_ID, HOUSE_NO)).thenReturn(List.of(app));
        when(visitorAuthorizationMapper.selectByHouse(COMMUNITY_ID, HOUSE_NO)).thenReturn(List.of(auth));
        when(maskingService.mask("京A12345", 2, 2)).thenReturn("京A***45");

        List<VisitorQueryResponse> result = visitorService.listVisitors(COMMUNITY_ID, HOUSE_NO);

        assertEquals(1, result.size());
        VisitorQueryResponse resp = result.get(0);
        assertEquals(1L, resp.getApplicationId());
        assertEquals("京A***45", resp.getCarNumber());
        assertEquals("临时访客", resp.getApplyReason());
        assertEquals("approved_pending_activation", resp.getApplicationStatus());
        assertEquals(10L, resp.getAuthorizationId());
        assertEquals("approved_pending_activation", resp.getAuthorizationStatus());
        assertNotNull(resp.getStartTime());
        assertNotNull(resp.getExpireTime());
    }

    @Test
    @DisplayName("查询成功 - 无授权记录时授权字段为空")
    void listVisitors_shouldReturnNullAuthorizationWhenNoAuth() {
        VisitorApplication app = new VisitorApplication();
        app.setId(2L);
        app.setCarNumber("京B67890");
        app.setApplyReason("朋友来访");
        app.setStatus("submitted");
        app.setCreateTime(LocalDateTime.of(2026, 3, 15, 11, 0));

        when(visitorApplicationMapper.selectByHouse(COMMUNITY_ID, HOUSE_NO)).thenReturn(List.of(app));
        when(visitorAuthorizationMapper.selectByHouse(COMMUNITY_ID, HOUSE_NO)).thenReturn(Collections.emptyList());
        when(maskingService.mask("京B67890", 2, 2)).thenReturn("京B***90");

        List<VisitorQueryResponse> result = visitorService.listVisitors(COMMUNITY_ID, HOUSE_NO);

        assertEquals(1, result.size());
        VisitorQueryResponse resp = result.get(0);
        assertEquals("submitted", resp.getApplicationStatus());
        assertNull(resp.getAuthorizationId());
        assertNull(resp.getAuthorizationStatus());
    }

    @Test
    @DisplayName("查询成功 - 无申请记录时返回空列表")
    void listVisitors_shouldReturnEmptyListWhenNoApplications() {
        when(visitorApplicationMapper.selectByHouse(COMMUNITY_ID, HOUSE_NO)).thenReturn(Collections.emptyList());
        when(visitorAuthorizationMapper.selectByHouse(COMMUNITY_ID, HOUSE_NO)).thenReturn(Collections.emptyList());

        List<VisitorQueryResponse> result = visitorService.listVisitors(COMMUNITY_ID, HOUSE_NO);

        assertTrue(result.isEmpty());
    }

    // ========== getQuota 测试 ==========

    @Test
    @DisplayName("配额查询 - 正常使用量，未接近超限")
    void getQuota_shouldReturnNormalUsage() {
        YearMonth currentMonth = YearMonth.now();
        when(visitorQuotaManager.calculateMonthlyUsage(COMMUNITY_ID, HOUSE_NO, currentMonth)).thenReturn(1000L);

        VisitorQuotaResponse response = visitorService.getQuota(COMMUNITY_ID, HOUSE_NO);

        assertEquals(4320L, response.getTotalQuotaMinutes());
        assertEquals(1000L, response.getUsedMinutes());
        assertEquals(3320L, response.getRemainingMinutes());
        assertFalse(response.isNearLimit());
        assertEquals(currentMonth.toString(), response.getMonth());
    }

    @Test
    @DisplayName("配额查询 - 使用量 ≥ 3600分钟，接近超限提醒")
    void getQuota_shouldFlagNearLimitWhenUsageHigh() {
        YearMonth currentMonth = YearMonth.now();
        when(visitorQuotaManager.calculateMonthlyUsage(COMMUNITY_ID, HOUSE_NO, currentMonth)).thenReturn(3600L);

        VisitorQuotaResponse response = visitorService.getQuota(COMMUNITY_ID, HOUSE_NO);

        assertEquals(3600L, response.getUsedMinutes());
        assertEquals(720L, response.getRemainingMinutes());
        assertTrue(response.isNearLimit());
    }

    @Test
    @DisplayName("配额查询 - 使用量超过总配额，剩余为0")
    void getQuota_shouldReturnZeroRemainingWhenExceeded() {
        YearMonth currentMonth = YearMonth.now();
        when(visitorQuotaManager.calculateMonthlyUsage(COMMUNITY_ID, HOUSE_NO, currentMonth)).thenReturn(5000L);

        VisitorQuotaResponse response = visitorService.getQuota(COMMUNITY_ID, HOUSE_NO);

        assertEquals(5000L, response.getUsedMinutes());
        assertEquals(0L, response.getRemainingMinutes());
        assertTrue(response.isNearLimit());
    }

    @Test
    @DisplayName("配额查询 - 零使用量")
    void getQuota_shouldReturnFullQuotaWhenNoUsage() {
        YearMonth currentMonth = YearMonth.now();
        when(visitorQuotaManager.calculateMonthlyUsage(COMMUNITY_ID, HOUSE_NO, currentMonth)).thenReturn(0L);

        VisitorQuotaResponse response = visitorService.getQuota(COMMUNITY_ID, HOUSE_NO);

        assertEquals(0L, response.getUsedMinutes());
        assertEquals(4320L, response.getRemainingMinutes());
        assertFalse(response.isNearLimit());
    }
}
