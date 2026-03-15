package com.parking.service;

import com.parking.common.PasswordValidator;
import com.parking.mapper.AdminMapper;
import com.parking.model.Admin;
import com.parking.service.impl.AdminInitServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * AdminInitService 单元测试
 * Validates: Requirements 13.1, 13.2, 13.3
 */
@ExtendWith(MockitoExtension.class)
class AdminInitServiceTest {

    @Mock
    private AdminMapper adminMapper;

    private AdminInitServiceImpl adminInitService;

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @BeforeEach
    void setUp() {
        adminInitService = new AdminInitServiceImpl(adminMapper);
    }

    @Nested
    @DisplayName("initSuperAdmin - 超级管理员初始化")
    class InitSuperAdminTests {

        @Test
        @DisplayName("首次启动应创建超级管理员账号（Requirements 13.1）")
        void initSuperAdmin_firstStart_shouldCreateAdmin() {
            when(adminMapper.countByRole("super_admin")).thenReturn(0);

            adminInitService.initSuperAdmin();

            ArgumentCaptor<Admin> captor = ArgumentCaptor.forClass(Admin.class);
            verify(adminMapper).insert(captor.capture());
            Admin admin = captor.getValue();

            assertEquals("admin", admin.getUsername());
            assertEquals("super_admin", admin.getRole());
            assertEquals("active", admin.getStatus());
            assertEquals(0L, admin.getCommunityId());
            assertEquals(0, admin.getLoginFailCount());
        }

        @Test
        @DisplayName("创建的管理员应设置 mustChangePassword=1（Requirements 13.3）")
        void initSuperAdmin_shouldSetMustChangePassword() {
            when(adminMapper.countByRole("super_admin")).thenReturn(0);

            adminInitService.initSuperAdmin();

            ArgumentCaptor<Admin> captor = ArgumentCaptor.forClass(Admin.class);
            verify(adminMapper).insert(captor.capture());
            Admin admin = captor.getValue();

            assertEquals(1, admin.getMustChangePassword());
        }

        @Test
        @DisplayName("创建的管理员密码应使用 BCrypt 加密")
        void initSuperAdmin_passwordShouldBeBCryptEncoded() {
            when(adminMapper.countByRole("super_admin")).thenReturn(0);

            adminInitService.initSuperAdmin();

            ArgumentCaptor<Admin> captor = ArgumentCaptor.forClass(Admin.class);
            verify(adminMapper).insert(captor.capture());
            Admin admin = captor.getValue();

            // BCrypt 加密后的密码以 $2a$ 或 $2b$ 开头
            assertNotNull(admin.getPassword());
            assertTrue(admin.getPassword().startsWith("$2a$") || admin.getPassword().startsWith("$2b$"));
        }

        @Test
        @DisplayName("已存在超级管理员时不应重复创建")
        void initSuperAdmin_alreadyExists_shouldSkip() {
            when(adminMapper.countByRole("super_admin")).thenReturn(1);

            adminInitService.initSuperAdmin();

            verify(adminMapper, never()).insert(any());
        }

        @Test
        @DisplayName("创建的管理员应设置密码过期时间")
        void initSuperAdmin_shouldSetPasswordExpireTime() {
            when(adminMapper.countByRole("super_admin")).thenReturn(0);

            adminInitService.initSuperAdmin();

            ArgumentCaptor<Admin> captor = ArgumentCaptor.forClass(Admin.class);
            verify(adminMapper).insert(captor.capture());
            Admin admin = captor.getValue();

            assertNotNull(admin.getPasswordExpireTime());
        }
    }

    @Nested
    @DisplayName("generateRandomPassword - 随机密码生成")
    class GenerateRandomPasswordTests {

        @Test
        @DisplayName("生成的密码长度应为16位")
        void generateRandomPassword_shouldBe16Chars() {
            String password = adminInitService.generateRandomPassword();
            assertEquals(16, password.length());
        }

        @Test
        @DisplayName("生成的密码应满足强度要求（Requirements 13.2）")
        void generateRandomPassword_shouldPassValidation() {
            // 多次生成验证稳定性
            for (int i = 0; i < 20; i++) {
                String password = adminInitService.generateRandomPassword();
                assertTrue(PasswordValidator.isValid(password),
                        "生成的密码不满足强度要求: " + password);
            }
        }

        @Test
        @DisplayName("每次生成的密码应不同")
        void generateRandomPassword_shouldBeDifferentEachTime() {
            String password1 = adminInitService.generateRandomPassword();
            String password2 = adminInitService.generateRandomPassword();
            assertNotEquals(password1, password2);
        }
    }

    @Nested
    @DisplayName("PasswordValidator - 密码强度验证")
    class PasswordValidatorTests {

        @Test
        @DisplayName("满足所有条件的密码应通过验证")
        void isValid_strongPassword_shouldReturnTrue() {
            assertTrue(PasswordValidator.isValid("Abc12345!"));
            assertTrue(PasswordValidator.isValid("P@ssw0rd"));
            assertTrue(PasswordValidator.isValid("MyStr0ng#Pass"));
        }

        @Test
        @DisplayName("null 密码应返回 false")
        void isValid_null_shouldReturnFalse() {
            assertFalse(PasswordValidator.isValid(null));
        }

        @Test
        @DisplayName("空字符串应返回 false")
        void isValid_empty_shouldReturnFalse() {
            assertFalse(PasswordValidator.isValid(""));
        }

        @Test
        @DisplayName("少于8位应返回 false")
        void isValid_tooShort_shouldReturnFalse() {
            assertFalse(PasswordValidator.isValid("Ab1!"));
            assertFalse(PasswordValidator.isValid("Ab1!xyz"));
        }

        @Test
        @DisplayName("缺少大写字母应返回 false")
        void isValid_noUppercase_shouldReturnFalse() {
            assertFalse(PasswordValidator.isValid("abc12345!"));
        }

        @Test
        @DisplayName("缺少小写字母应返回 false")
        void isValid_noLowercase_shouldReturnFalse() {
            assertFalse(PasswordValidator.isValid("ABC12345!"));
        }

        @Test
        @DisplayName("缺少数字应返回 false")
        void isValid_noDigit_shouldReturnFalse() {
            assertFalse(PasswordValidator.isValid("Abcdefgh!"));
        }

        @Test
        @DisplayName("缺少特殊字符应返回 false")
        void isValid_noSpecialChar_shouldReturnFalse() {
            assertFalse(PasswordValidator.isValid("Abcdefg1"));
        }

        @Test
        @DisplayName("刚好8位且满足所有条件应通过")
        void isValid_exactlyMinLength_shouldReturnTrue() {
            assertTrue(PasswordValidator.isValid("Aa1!xxxx"));
        }
    }
}
