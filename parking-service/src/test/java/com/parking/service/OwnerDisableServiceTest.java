package com.parking.service;

import com.parking.common.BusinessException;
import com.parking.common.ErrorCode;
import com.parking.mapper.CarPlateMapper;
import com.parking.mapper.CommunityMapper;
import com.parking.mapper.HouseMapper;
import com.parking.mapper.OwnerHouseRelMapper;
import com.parking.mapper.OwnerMapper;
import com.parking.model.Owner;
import com.parking.service.impl.OwnerServiceImpl;
import com.parking.service.JwtTokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 业主账号注销服务单元测试
 * Validates: Requirements 14.1, 14.2, 14.3, 14.4, 14.5, 14.6, 14.7, 14.8
 */
@ExtendWith(MockitoExtension.class)
class OwnerDisableServiceTest {

    @Mock
    private OwnerMapper ownerMapper;

    @Mock
    private OwnerHouseRelMapper ownerHouseRelMapper;

    @Mock
    private HouseMapper houseMapper;

    @Mock
    private VerificationCodeService verificationCodeService;

    @Mock
    private CarPlateMapper carPlateMapper;

    @Mock
    private JwtTokenService jwtTokenService;

    @Mock
    private CommunityMapper communityMapper;

    private OwnerServiceImpl ownerService;

    private static final Long OWNER_ID = 10001L;
    private static final Long COMMUNITY_ID = 1001L;
    private static final String HOUSE_NO = "1-101";
    private static final Long OPERATOR_ID = 1L;
    private static final String REASON = "业主主动申请注销";

    @BeforeEach
    void setUp() {
        ownerService = new OwnerServiceImpl(ownerMapper, ownerHouseRelMapper,
                houseMapper, communityMapper, verificationCodeService, carPlateMapper, jwtTokenService);
    }

    private Owner createActiveOwner() {
        Owner owner = new Owner();
        owner.setId(OWNER_ID);
        owner.setCommunityId(COMMUNITY_ID);
        owner.setHouseNo(HOUSE_NO);
        owner.setPhoneNumber("13812345678");
        owner.setAccountStatus("active");
        owner.setStatus("approved");
        return owner;
    }

    @Nested
    @DisplayName("disable - 业主账号注销")
    class DisableTests {

        @Test
        @DisplayName("注销成功：业主存在、状态为 active、无在场车辆")
        void disable_success_shouldUpdateStatusAndDisablePlates() {
            Owner owner = createActiveOwner();
            when(ownerMapper.selectById(OWNER_ID)).thenReturn(owner);
            when(carPlateMapper.countEnteredByOwnerHouse(COMMUNITY_ID, HOUSE_NO)).thenReturn(0);
            when(ownerMapper.updateAccountStatus(OWNER_ID, "disabled")).thenReturn(1);
            when(carPlateMapper.disableByOwnerHouse(COMMUNITY_ID, HOUSE_NO)).thenReturn(3);

            // 执行注销
            assertDoesNotThrow(() -> ownerService.disable(OWNER_ID, REASON, OPERATOR_ID));

            // 验证账号状态更新为 disabled（Requirements 14.4）
            verify(ownerMapper).updateAccountStatus(OWNER_ID, "disabled");
            // 验证车牌批量禁用（Requirements 14.5）
            verify(carPlateMapper).disableByOwnerHouse(COMMUNITY_ID, HOUSE_NO);
        }

        @Test
        @DisplayName("业主不存在应抛出参数错误异常")
        void disable_ownerNotFound_shouldThrowException() {
            when(ownerMapper.selectById(OWNER_ID)).thenReturn(null);

            BusinessException exception = assertThrows(BusinessException.class,
                    () -> ownerService.disable(OWNER_ID, REASON, OPERATOR_ID));

            assertEquals(ErrorCode.PARAM_ERROR.getCode(), exception.getCode());
            assertTrue(exception.getMessage().contains("业主不存在"));
            // 不应调用后续操作
            verify(carPlateMapper, never()).countEnteredByOwnerHouse(anyLong(), anyString());
            verify(ownerMapper, never()).updateAccountStatus(anyLong(), anyString());
        }

        @Test
        @DisplayName("业主已禁用应抛出参数错误异常")
        void disable_alreadyDisabled_shouldThrowException() {
            Owner owner = createActiveOwner();
            owner.setAccountStatus("disabled");
            when(ownerMapper.selectById(OWNER_ID)).thenReturn(owner);

            BusinessException exception = assertThrows(BusinessException.class,
                    () -> ownerService.disable(OWNER_ID, REASON, OPERATOR_ID));

            assertEquals(ErrorCode.PARAM_ERROR.getCode(), exception.getCode());
            assertTrue(exception.getMessage().contains("业主账号已被禁用"));
            verify(carPlateMapper, never()).countEnteredByOwnerHouse(anyLong(), anyString());
            verify(ownerMapper, never()).updateAccountStatus(anyLong(), anyString());
        }

        @Test
        @DisplayName("有车辆在场应拒绝注销并返回 PARKING_14001（Requirements 14.2, 14.3）")
        void disable_vehiclesEntered_shouldThrowParking14001() {
            Owner owner = createActiveOwner();
            when(ownerMapper.selectById(OWNER_ID)).thenReturn(owner);
            when(carPlateMapper.countEnteredByOwnerHouse(COMMUNITY_ID, HOUSE_NO)).thenReturn(2);

            BusinessException exception = assertThrows(BusinessException.class,
                    () -> ownerService.disable(OWNER_ID, REASON, OPERATOR_ID));

            assertEquals(ErrorCode.PARKING_14001.getCode(), exception.getCode());
            assertEquals(ErrorCode.PARKING_14001.getMessage(), exception.getMessage());
            // 不应执行禁用操作
            verify(ownerMapper, never()).updateAccountStatus(anyLong(), anyString());
            verify(carPlateMapper, never()).disableByOwnerHouse(anyLong(), anyString());
        }

        @Test
        @DisplayName("注销成功应调用车牌批量禁用（Requirements 14.5）")
        void disable_success_shouldDisableAllCarPlates() {
            Owner owner = createActiveOwner();
            when(ownerMapper.selectById(OWNER_ID)).thenReturn(owner);
            when(carPlateMapper.countEnteredByOwnerHouse(COMMUNITY_ID, HOUSE_NO)).thenReturn(0);
            when(ownerMapper.updateAccountStatus(OWNER_ID, "disabled")).thenReturn(1);
            when(carPlateMapper.disableByOwnerHouse(COMMUNITY_ID, HOUSE_NO)).thenReturn(5);

            ownerService.disable(OWNER_ID, REASON, OPERATOR_ID);

            // 验证使用正确的 communityId 和 houseNo 禁用车牌
            verify(carPlateMapper).disableByOwnerHouse(COMMUNITY_ID, HOUSE_NO);
        }
    }
}
