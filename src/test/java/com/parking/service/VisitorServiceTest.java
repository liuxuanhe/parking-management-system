package com.parking.service;

import com.parking.common.BusinessException;
import com.parking.dto.VisitorApplyRequest;
import com.parking.dto.VisitorApplyResponse;
import com.parking.mapper.CarPlateMapper;
import com.parking.mapper.VisitorApplicationMapper;
import com.parking.model.CarPlate;
import com.parking.service.impl.VisitorServiceImpl;
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
    private VisitorQuotaManager visitorQuotaManager;

    @Mock
    private ParkingSpaceCalculator parkingSpaceCalculator;

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
}
