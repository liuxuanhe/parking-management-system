package com.parking.service;

import com.parking.common.BusinessException;
import com.parking.dto.OwnerRegisterRequest;
import com.parking.dto.OwnerRegisterResponse;
import com.parking.mapper.HouseMapper;
import com.parking.mapper.OwnerHouseRelMapper;
import com.parking.mapper.OwnerMapper;
import com.parking.model.House;
import com.parking.model.Owner;
import com.parking.model.OwnerHouseRel;
import com.parking.service.impl.OwnerServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * OwnerService 单元测试
 * Validates: Requirements 1.1, 1.4, 1.5, 1.6, 1.7
 */
@ExtendWith(MockitoExtension.class)
class OwnerServiceTest {

    @Mock
    private OwnerMapper ownerMapper;

    @Mock
    private OwnerHouseRelMapper ownerHouseRelMapper;

    @Mock
    private HouseMapper houseMapper;

    @Mock
    private VerificationCodeService verificationCodeService;

    private OwnerServiceImpl ownerService;

    private static final Long COMMUNITY_ID = 1001L;
    private static final String HOUSE_NO = "1-101";
    private static final String PHONE = "13812345678";
    private static final String CODE = "123456";
    private static final String ID_CARD_LAST4 = "1234";

    @BeforeEach
    void setUp() {
        ownerService = new OwnerServiceImpl(ownerMapper, ownerHouseRelMapper,
                houseMapper, verificationCodeService);
    }

    private OwnerRegisterRequest createValidRequest() {
        OwnerRegisterRequest request = new OwnerRegisterRequest();
        request.setPhone(PHONE);
        request.setVerificationCode(CODE);
        request.setCommunityId(COMMUNITY_ID);
        request.setHouseNo(HOUSE_NO);
        request.setIdCardLast4(ID_CARD_LAST4);
        return request;
    }

    private House createHouse() {
        House house = new House();
        house.setId(1L);
        house.setCommunityId(COMMUNITY_ID);
        house.setHouseNo(HOUSE_NO);
        house.setStatus("normal");
        return house;
    }

    @Nested
    @DisplayName("register - 业主注册")
    class RegisterTests {

        @Test
        @DisplayName("注册成功应创建业主账号并绑定房屋号")
        void register_success_shouldCreateOwnerAndBindHouse() {
            OwnerRegisterRequest request = createValidRequest();
            when(verificationCodeService.verify(PHONE, CODE)).thenReturn(true);
            when(houseMapper.selectByCommunityAndHouseNo(COMMUNITY_ID, HOUSE_NO))
                    .thenReturn(createHouse());
            // 模拟 insert 后设置 ID
            doAnswer(invocation -> {
                Owner owner = invocation.getArgument(0);
                owner.setId(10001L);
                return null;
            }).when(ownerMapper).insert(any(Owner.class));

            OwnerRegisterResponse response = ownerService.register(request);

            assertNotNull(response);
            assertEquals(10001L, response.getOwnerId());
            assertEquals("pending", response.getStatus());
        }

        @Test
        @DisplayName("注册成功应创建业主记录，状态为 pending")
        void register_shouldCreateOwnerWithPendingStatus() {
            OwnerRegisterRequest request = createValidRequest();
            when(verificationCodeService.verify(PHONE, CODE)).thenReturn(true);
            when(houseMapper.selectByCommunityAndHouseNo(COMMUNITY_ID, HOUSE_NO))
                    .thenReturn(createHouse());
            doAnswer(invocation -> {
                Owner owner = invocation.getArgument(0);
                owner.setId(10001L);
                return null;
            }).when(ownerMapper).insert(any(Owner.class));

            ownerService.register(request);

            ArgumentCaptor<Owner> ownerCaptor = ArgumentCaptor.forClass(Owner.class);
            verify(ownerMapper).insert(ownerCaptor.capture());
            Owner captured = ownerCaptor.getValue();
            assertEquals("pending", captured.getStatus());
            assertEquals("active", captured.getAccountStatus());
            assertEquals(COMMUNITY_ID, captured.getCommunityId());
            assertEquals(HOUSE_NO, captured.getHouseNo());
            assertEquals(PHONE, captured.getPhoneNumber());
            assertEquals(ID_CARD_LAST4, captured.getIdCardLast4());
        }

        @Test
        @DisplayName("注册成功应创建业主房屋号关联记录")
        void register_shouldCreateOwnerHouseRel() {
            OwnerRegisterRequest request = createValidRequest();
            when(verificationCodeService.verify(PHONE, CODE)).thenReturn(true);
            when(houseMapper.selectByCommunityAndHouseNo(COMMUNITY_ID, HOUSE_NO))
                    .thenReturn(createHouse());
            doAnswer(invocation -> {
                Owner owner = invocation.getArgument(0);
                owner.setId(10001L);
                return null;
            }).when(ownerMapper).insert(any(Owner.class));

            ownerService.register(request);

            ArgumentCaptor<OwnerHouseRel> relCaptor = ArgumentCaptor.forClass(OwnerHouseRel.class);
            verify(ownerHouseRelMapper).insert(relCaptor.capture());
            OwnerHouseRel captured = relCaptor.getValue();
            assertEquals(COMMUNITY_ID, captured.getCommunityId());
            assertEquals(10001L, captured.getOwnerId());
            assertEquals(HOUSE_NO, captured.getHouseNo());
            assertEquals("owner", captured.getRelationType());
        }

        @Test
        @DisplayName("验证码校验失败应抛出异常")
        void register_verificationFailed_shouldThrowException() {
            OwnerRegisterRequest request = createValidRequest();
            when(verificationCodeService.verify(PHONE, CODE))
                    .thenThrow(new BusinessException(1002, "验证码已过期，请重新获取"));

            BusinessException exception = assertThrows(BusinessException.class,
                    () -> ownerService.register(request));

            assertEquals(1002, exception.getCode());
            verify(ownerMapper, never()).insert(any());
            verify(ownerHouseRelMapper, never()).insert(any());
        }

        @Test
        @DisplayName("房屋号不存在应抛出参数错误异常")
        void register_houseNotFound_shouldThrowException() {
            OwnerRegisterRequest request = createValidRequest();
            when(verificationCodeService.verify(PHONE, CODE)).thenReturn(true);
            when(houseMapper.selectByCommunityAndHouseNo(COMMUNITY_ID, HOUSE_NO))
                    .thenReturn(null);

            BusinessException exception = assertThrows(BusinessException.class,
                    () -> ownerService.register(request));

            assertEquals(10000, exception.getCode());
            assertTrue(exception.getMessage().contains("房屋号不存在"));
            verify(ownerMapper, never()).insert(any());
        }

        @Test
        @DisplayName("同一房屋号允许多个业主注册（Requirements 1.7）")
        void register_sameHouseNo_shouldAllowMultipleOwners() {
            // 第一个业主注册
            OwnerRegisterRequest request1 = createValidRequest();
            when(verificationCodeService.verify(PHONE, CODE)).thenReturn(true);
            when(houseMapper.selectByCommunityAndHouseNo(COMMUNITY_ID, HOUSE_NO))
                    .thenReturn(createHouse());
            doAnswer(invocation -> {
                Owner owner = invocation.getArgument(0);
                owner.setId(10001L);
                return null;
            }).when(ownerMapper).insert(any(Owner.class));

            OwnerRegisterResponse response1 = ownerService.register(request1);
            assertNotNull(response1);

            // 第二个业主注册（不同手机号，同一房屋号）
            OwnerRegisterRequest request2 = createValidRequest();
            request2.setPhone("13987654321");
            request2.setIdCardLast4("5678");
            when(verificationCodeService.verify("13987654321", CODE)).thenReturn(true);
            doAnswer(invocation -> {
                Owner owner = invocation.getArgument(0);
                owner.setId(10002L);
                return null;
            }).when(ownerMapper).insert(any(Owner.class));

            OwnerRegisterResponse response2 = ownerService.register(request2);
            assertNotNull(response2);
            assertEquals(10002L, response2.getOwnerId());

            // 验证 insert 被调用了两次
            verify(ownerMapper, times(2)).insert(any(Owner.class));
            verify(ownerHouseRelMapper, times(2)).insert(any(OwnerHouseRel.class));
        }
    }
}
