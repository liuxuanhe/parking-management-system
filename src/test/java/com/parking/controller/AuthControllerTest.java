package com.parking.controller;

import com.parking.common.ApiResponse;
import com.parking.dto.AdminChangePasswordRequest;
import com.parking.dto.AdminLoginRequest;
import com.parking.dto.AdminLoginResponse;
import com.parking.service.AdminService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * AuthController 单元测试
 * Validates: Requirements 13.4, 13.5, 13.6, 13.8
 */
@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock
    private AdminService adminService;

    @InjectMocks
    private AuthController authController;

    @Test
    @DisplayName("登录接口应调用 AdminService 并返回成功响应")
    void login_shouldCallServiceAndReturnSuccess() {
        AdminLoginRequest request = new AdminLoginRequest();
        request.setUsername("admin");
        request.setPassword("Admin@123");

        AdminLoginResponse serviceResponse = new AdminLoginResponse();
        serviceResponse.setAccessToken("access_token_123");
        serviceResponse.setRefreshToken("refresh_token_123");
        serviceResponse.setMustChangePassword(false);
        serviceResponse.setAdminId(1L);
        serviceResponse.setRole("super_admin");
        serviceResponse.setCommunityId(0L);

        HttpServletRequest servletRequest = mock(HttpServletRequest.class);
        when(servletRequest.getHeader("X-Forwarded-For")).thenReturn(null);
        when(servletRequest.getHeader("X-Real-IP")).thenReturn(null);
        when(servletRequest.getRemoteAddr()).thenReturn("127.0.0.1");
        when(adminService.login(any(AdminLoginRequest.class), eq("127.0.0.1")))
                .thenReturn(serviceResponse);

        ApiResponse<AdminLoginResponse> result = authController.login(request, servletRequest);

        assertNotNull(result);
        assertEquals(0, result.getCode());
        assertEquals("success", result.getMessage());
        assertNotNull(result.getData());
        assertEquals("access_token_123", result.getData().getAccessToken());
        assertEquals("refresh_token_123", result.getData().getRefreshToken());
        assertEquals(1L, result.getData().getAdminId());
        verify(adminService).login(request, "127.0.0.1");
    }

    @Test
    @DisplayName("登录接口应从 X-Forwarded-For 获取客户端IP")
    void login_shouldGetIpFromXForwardedFor() {
        AdminLoginRequest request = new AdminLoginRequest();
        request.setUsername("admin");
        request.setPassword("Admin@123");

        AdminLoginResponse serviceResponse = new AdminLoginResponse();
        serviceResponse.setAccessToken("token");
        serviceResponse.setRefreshToken("refresh");
        serviceResponse.setMustChangePassword(false);
        serviceResponse.setAdminId(1L);
        serviceResponse.setRole("super_admin");
        serviceResponse.setCommunityId(0L);

        HttpServletRequest servletRequest = mock(HttpServletRequest.class);
        when(servletRequest.getHeader("X-Forwarded-For")).thenReturn("192.168.1.100, 10.0.0.1");
        when(adminService.login(any(AdminLoginRequest.class), eq("192.168.1.100")))
                .thenReturn(serviceResponse);

        authController.login(request, servletRequest);

        verify(adminService).login(request, "192.168.1.100");
    }

    @Test
    @DisplayName("修改密码接口应调用 AdminService")
    void changePassword_shouldCallService() {
        AdminChangePasswordRequest request = new AdminChangePasswordRequest();
        request.setOldPassword("OldPass@123");
        request.setNewPassword("NewPass@456");

        HttpServletRequest servletRequest = mock(HttpServletRequest.class);
        when(servletRequest.getAttribute("userId")).thenReturn(1L);

        ApiResponse<Void> result = authController.changePassword(request, servletRequest);

        assertNotNull(result);
        assertEquals(0, result.getCode());
        verify(adminService).changePassword(1L, request);
    }
}
