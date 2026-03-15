package com.parking.service;

import com.parking.common.BusinessException;
import com.parking.common.ErrorCode;
import com.parking.dto.AdminChangePasswordRequest;
import com.parking.dto.AdminLoginRequest;
import com.parking.dto.AdminLoginResponse;
import com.parking.mapper.AdminMapper;
import com.parking.model.Admin;
import com.parking.service.impl.AdminServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * AdminService 单元测试
 * Validates: Requirements 13.4, 13.5, 13.6, 13.8
 */
@ExtendWith(MockitoExtension.class)
class AdminServiceTest {

    @Mock
    private AdminMapper adminMapper;

    @Mock
    private JwtTokenService jwtTokenService;

    private AdminServiceImpl adminService;

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    /** 测试用的原始密码 */
    private static final String RAW_PASSWORD = "Admin@123";

    @BeforeEach
    void setUp() {
        adminService = new AdminServiceImpl(adminMapper, jwtTokenService);
    }

    /**
     * 构建测试用管理员实体
     */
    private Admin buildTestAdmin() {
        Admin admin = new Admin();
        admin.setId(1L);
        admin.setUsername("admin");
        admin.setPassword(passwordEncoder.encode(RAW_PASSWORD));
        admin.setRole("super_admin");
        admin.setStatus("active");
        admin.setCommunityId(0L);
        admin.setLoginFailCount(0);
        admin.setMustChangePassword(0);
        return admin;
    }

    @Nested
    @DisplayName("login - 管理员登录")
    class LoginTests {

        @Test
        @DisplayName("登录成功应返回 Token 和管理员信息（Requirements 13.4）")
        void login_success_shouldReturnTokens() {
            Admin admin = buildTestAdmin();
            when(adminMapper.selectByUsername("admin")).thenReturn(admin);
            when(jwtTokenService.generateAccessToken(eq(1L), eq("super_admin"), eq(0L), isNull()))
                    .thenReturn("access_token_123");
            when(jwtTokenService.generateRefreshToken(1L)).thenReturn("refresh_token_123");

            AdminLoginRequest request = new AdminLoginRequest();
            request.setUsername("admin");
            request.setPassword(RAW_PASSWORD);

            AdminLoginResponse response = adminService.login(request, "127.0.0.1");

            assertNotNull(response);
            assertEquals("access_token_123", response.getAccessToken());
            assertEquals("refresh_token_123", response.getRefreshToken());
            assertEquals(1L, response.getAdminId());
            assertEquals("super_admin", response.getRole());
            assertEquals(0L, response.getCommunityId());
            assertFalse(response.getMustChangePassword());

            // 验证登录成功后更新了登录信息
            verify(adminMapper).updateLoginSuccess(1L, "127.0.0.1");
        }

        @Test
        @DisplayName("用户名不存在应抛出 PARKING_13004（Requirements 13.4）")
        void login_usernameNotFound_shouldThrow13004() {
            when(adminMapper.selectByUsername("nonexistent")).thenReturn(null);

            AdminLoginRequest request = new AdminLoginRequest();
            request.setUsername("nonexistent");
            request.setPassword("anyPassword");

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> adminService.login(request, "127.0.0.1"));
            assertEquals(ErrorCode.PARKING_13004.getCode(), ex.getCode());
        }

        @Test
        @DisplayName("密码错误应抛出 PARKING_13004 并增加失败次数（Requirements 13.5）")
        void login_wrongPassword_shouldThrow13004AndIncrementFail() {
            Admin admin = buildTestAdmin();
            when(adminMapper.selectByUsername("admin")).thenReturn(admin);

            AdminLoginRequest request = new AdminLoginRequest();
            request.setUsername("admin");
            request.setPassword("WrongPassword@1");

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> adminService.login(request, "127.0.0.1"));
            assertEquals(ErrorCode.PARKING_13004.getCode(), ex.getCode());

            // 验证增加了失败次数
            verify(adminMapper).updateLoginFail(1L);
            // 验证未锁定（只失败1次）
            verify(adminMapper, never()).lockAccount(anyLong());
        }

        @Test
        @DisplayName("5次密码错误应锁定账号（Requirements 13.5）")
        void login_fiveFailures_shouldLockAccount() {
            Admin admin = buildTestAdmin();
            admin.setLoginFailCount(4); // 已失败4次，这次是第5次
            when(adminMapper.selectByUsername("admin")).thenReturn(admin);

            AdminLoginRequest request = new AdminLoginRequest();
            request.setUsername("admin");
            request.setPassword("WrongPassword@1");

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> adminService.login(request, "127.0.0.1"));
            assertEquals(ErrorCode.PARKING_13005.getCode(), ex.getCode());

            // 验证增加了失败次数并锁定了账号
            verify(adminMapper).updateLoginFail(1L);
            verify(adminMapper).lockAccount(1L);
        }

        @Test
        @DisplayName("已锁定账号应拒绝登录（Requirements 13.6）")
        void login_lockedAccount_shouldThrow13005() {
            Admin admin = buildTestAdmin();
            admin.setStatus("locked");
            when(adminMapper.selectByUsername("admin")).thenReturn(admin);

            AdminLoginRequest request = new AdminLoginRequest();
            request.setUsername("admin");
            request.setPassword(RAW_PASSWORD);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> adminService.login(request, "127.0.0.1"));
            assertEquals(ErrorCode.PARKING_13005.getCode(), ex.getCode());

            // 验证未尝试验证密码或更新任何状态
            verify(adminMapper, never()).updateLoginFail(anyLong());
            verify(adminMapper, never()).updateLoginSuccess(anyLong(), anyString());
        }

        @Test
        @DisplayName("mustChangePassword=1 时响应应标记为 true（Requirements 13.3）")
        void login_mustChangePassword_shouldReturnTrue() {
            Admin admin = buildTestAdmin();
            admin.setMustChangePassword(1);
            when(adminMapper.selectByUsername("admin")).thenReturn(admin);
            when(jwtTokenService.generateAccessToken(eq(1L), eq("super_admin"), eq(0L), isNull()))
                    .thenReturn("access_token");
            when(jwtTokenService.generateRefreshToken(1L)).thenReturn("refresh_token");

            AdminLoginRequest request = new AdminLoginRequest();
            request.setUsername("admin");
            request.setPassword(RAW_PASSWORD);

            AdminLoginResponse response = adminService.login(request, "127.0.0.1");

            assertTrue(response.getMustChangePassword());
        }

        @Test
        @DisplayName("loginFailCount 为 null 时密码错误应正常处理")
        void login_nullFailCount_shouldHandleGracefully() {
            Admin admin = buildTestAdmin();
            admin.setLoginFailCount(null);
            when(adminMapper.selectByUsername("admin")).thenReturn(admin);

            AdminLoginRequest request = new AdminLoginRequest();
            request.setUsername("admin");
            request.setPassword("WrongPassword@1");

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> adminService.login(request, "127.0.0.1"));
            assertEquals(ErrorCode.PARKING_13004.getCode(), ex.getCode());
            verify(adminMapper).updateLoginFail(1L);
            verify(adminMapper, never()).lockAccount(anyLong());
        }
    }

    @Nested
    @DisplayName("changePassword - 修改密码")
    class ChangePasswordTests {

        @Test
        @DisplayName("修改密码成功（Requirements 13.4）")
        void changePassword_success() {
            Admin admin = buildTestAdmin();
            when(adminMapper.selectById(1L)).thenReturn(admin);

            AdminChangePasswordRequest request = new AdminChangePasswordRequest();
            request.setOldPassword(RAW_PASSWORD);
            request.setNewPassword("NewPass@456");

            assertDoesNotThrow(() -> adminService.changePassword(1L, request));

            // 验证更新了密码，mustChangePassword 设为 0
            verify(adminMapper).updatePassword(eq(1L), anyString(), eq(0));
        }

        @Test
        @DisplayName("旧密码错误应抛出 PARKING_13004")
        void changePassword_wrongOldPassword_shouldThrow() {
            Admin admin = buildTestAdmin();
            when(adminMapper.selectById(1L)).thenReturn(admin);

            AdminChangePasswordRequest request = new AdminChangePasswordRequest();
            request.setOldPassword("WrongOldPass@1");
            request.setNewPassword("NewPass@456");

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> adminService.changePassword(1L, request));
            assertEquals(ErrorCode.PARKING_13004.getCode(), ex.getCode());

            verify(adminMapper, never()).updatePassword(anyLong(), anyString(), anyInt());
        }

        @Test
        @DisplayName("新密码强度不足应抛出 PARAM_ERROR（Requirements 13.4）")
        void changePassword_weakNewPassword_shouldThrow() {
            Admin admin = buildTestAdmin();
            when(adminMapper.selectById(1L)).thenReturn(admin);

            AdminChangePasswordRequest request = new AdminChangePasswordRequest();
            request.setOldPassword(RAW_PASSWORD);
            request.setNewPassword("weak"); // 不满足强度要求

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> adminService.changePassword(1L, request));
            assertEquals(ErrorCode.PARAM_ERROR.getCode(), ex.getCode());

            verify(adminMapper, never()).updatePassword(anyLong(), anyString(), anyInt());
        }

        @Test
        @DisplayName("管理员不存在应抛出 PARKING_13004")
        void changePassword_adminNotFound_shouldThrow() {
            when(adminMapper.selectById(999L)).thenReturn(null);

            AdminChangePasswordRequest request = new AdminChangePasswordRequest();
            request.setOldPassword("anyOld");
            request.setNewPassword("NewPass@456");

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> adminService.changePassword(999L, request));
            assertEquals(ErrorCode.PARKING_13004.getCode(), ex.getCode());
        }
    }
}
