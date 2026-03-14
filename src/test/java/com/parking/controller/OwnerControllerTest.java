package com.parking.controller;

import com.parking.common.ApiResponse;
import com.parking.common.RequestContext;
import com.parking.dto.OwnerRegisterRequest;
import com.parking.dto.OwnerRegisterResponse;
import com.parking.service.OwnerService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * OwnerController 单元测试
 */
@ExtendWith(MockitoExtension.class)
class OwnerControllerTest {

    @Mock
    private OwnerService ownerService;

    @InjectMocks
    private OwnerController ownerController;

    @Test
    @DisplayName("注册接口应调用 OwnerService 并返回成功响应")
    void register_shouldCallServiceAndReturnSuccess() {
        OwnerRegisterRequest request = new OwnerRegisterRequest();
        request.setPhone("13812345678");
        request.setVerificationCode("123456");
        request.setCommunityId(1001L);
        request.setHouseNo("1-101");
        request.setIdCardLast4("1234");

        OwnerRegisterResponse serviceResponse = new OwnerRegisterResponse();
        serviceResponse.setOwnerId(10001L);
        serviceResponse.setStatus("pending");
        serviceResponse.setCreateTime(LocalDateTime.now());

        when(ownerService.register(any(OwnerRegisterRequest.class))).thenReturn(serviceResponse);

        ApiResponse<OwnerRegisterResponse> result = ownerController.register(request);

        assertNotNull(result);
        assertEquals(0, result.getCode());
        assertEquals("success", result.getMessage());
        assertNotNull(result.getData());
        assertEquals(10001L, result.getData().getOwnerId());
        assertEquals("pending", result.getData().getStatus());
        verify(ownerService).register(request);
    }
}
