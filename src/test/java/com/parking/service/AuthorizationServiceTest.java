package com.parking.service;

import com.parking.common.BusinessException;
import com.parking.mapper.IpWhitelistMapper;
import com.parking.service.impl.AuthorizationServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * AuthorizationService 单元测试
 * Validates: Requirements 12.5, 12.6, 12.7, 20.2, 20.3
 */
@ExtendWith(MockitoExtension.class)
class AuthorizationServiceTest {

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private ValueOperations<String, Object> valueOperations;

    @Mock
    private IpWhitelistMapper ipWhitelistMapper;

    private AuthorizationServiceImpl authorizationService;

    @BeforeEach
    void setUp() {
        authorizationService = new AuthorizationServiceImpl(redisTemplate, ipWhitelistMapper);
    }

    // ==================== checkCommunityAccess 测试 ====================

    @Nested
    @DisplayName("checkCommunityAccess - 小区访问权限校验")
    class CheckCommunityAccessTest {

        @Test
        @DisplayName("相同 communityId 校验通过")
        void shouldPassWhenCommunityIdMatches() {
            assertDoesNotThrow(() ->
                    authorizationService.checkCommunityAccess(1001L, 1001L));
        }

        @Test
        @DisplayName("不同 communityId 抛出 PARKING_12001")
        void shouldThrowWhenCommunityIdMismatch() {
            BusinessException ex = assertThrows(BusinessException.class, () ->
                    authorizationService.checkCommunityAccess(1001L, 1002L));
            assertEquals(12001, ex.getCode());
        }

        @Test
        @DisplayName("userCommunityId 为 null 抛出 PARKING_12001")
        void shouldThrowWhenUserCommunityIdIsNull() {
            BusinessException ex = assertThrows(BusinessException.class, () ->
                    authorizationService.checkCommunityAccess(null, 1001L));
            assertEquals(12001, ex.getCode());
        }

        @Test
        @DisplayName("requestCommunityId 为 null 抛出 PARKING_12001")
        void shouldThrowWhenRequestCommunityIdIsNull() {
            BusinessException ex = assertThrows(BusinessException.class, () ->
                    authorizationService.checkCommunityAccess(1001L, null));
            assertEquals(12001, ex.getCode());
        }
    }

    // ==================== checkHouseNoAccess 测试 ====================

    @Nested
    @DisplayName("checkHouseNoAccess - 房屋号数据域权限校验")
    class CheckHouseNoAccessTest {

        @Test
        @DisplayName("communityId 和 houseNo 均匹配时校验通过")
        void shouldPassWhenBothMatch() {
            assertDoesNotThrow(() ->
                    authorizationService.checkHouseNoAccess(1001L, "1-101", 1001L, "1-101"));
        }

        @Test
        @DisplayName("communityId 不匹配时抛出 PARKING_12001")
        void shouldThrowWhenCommunityIdMismatch() {
            BusinessException ex = assertThrows(BusinessException.class, () ->
                    authorizationService.checkHouseNoAccess(1001L, "1-101", 1002L, "1-101"));
            assertEquals(12001, ex.getCode());
        }

        @Test
        @DisplayName("houseNo 不匹配时抛出 PARKING_12001")
        void shouldThrowWhenHouseNoMismatch() {
            BusinessException ex = assertThrows(BusinessException.class, () ->
                    authorizationService.checkHouseNoAccess(1001L, "1-101", 1001L, "2-202"));
            assertEquals(12001, ex.getCode());
        }

        @Test
        @DisplayName("userHouseNo 为 null 抛出 PARKING_12001")
        void shouldThrowWhenUserHouseNoIsNull() {
            BusinessException ex = assertThrows(BusinessException.class, () ->
                    authorizationService.checkHouseNoAccess(1001L, null, 1001L, "1-101"));
            assertEquals(12001, ex.getCode());
        }

        @Test
        @DisplayName("requestHouseNo 为 null 抛出 PARKING_12001")
        void shouldThrowWhenRequestHouseNoIsNull() {
            BusinessException ex = assertThrows(BusinessException.class, () ->
                    authorizationService.checkHouseNoAccess(1001L, "1-101", 1001L, null));
            assertEquals(12001, ex.getCode());
        }
    }

    // ==================== checkIpWhitelist 测试 ====================

    @Nested
    @DisplayName("checkIpWhitelist - IP 白名单校验")
    class CheckIpWhitelistTest {

        @Test
        @DisplayName("IP 精确匹配白名单时校验通过")
        void shouldPassWhenIpExactMatch() {
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            List<String> whitelist = Arrays.asList("192.168.1.100", "10.0.0.1");
            when(valueOperations.get("ip_whitelist:modify_parking_config")).thenReturn(whitelist);

            assertDoesNotThrow(() ->
                    authorizationService.checkIpWhitelist("192.168.1.100", "modify_parking_config"));
        }

        @Test
        @DisplayName("IP 匹配 CIDR 网段时校验通过")
        void shouldPassWhenIpMatchesCidr() {
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            List<String> whitelist = Arrays.asList("192.168.1.0/24", "10.0.0.0/8");
            when(valueOperations.get("ip_whitelist:modify_parking_config")).thenReturn(whitelist);

            assertDoesNotThrow(() ->
                    authorizationService.checkIpWhitelist("192.168.1.50", "modify_parking_config"));
        }

        @Test
        @DisplayName("IP 不在白名单中抛出 PARKING_20001")
        void shouldThrowWhenIpNotInWhitelist() {
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            List<String> whitelist = Arrays.asList("192.168.1.100", "10.0.0.1");
            when(valueOperations.get("ip_whitelist:disable_owner")).thenReturn(whitelist);

            BusinessException ex = assertThrows(BusinessException.class, () ->
                    authorizationService.checkIpWhitelist("172.16.0.1", "disable_owner"));
            assertEquals(20001, ex.getCode());
        }

        @Test
        @DisplayName("IP 为空时抛出 PARKING_20001")
        void shouldThrowWhenIpIsEmpty() {
            BusinessException ex = assertThrows(BusinessException.class, () ->
                    authorizationService.checkIpWhitelist("", "modify_parking_config"));
            assertEquals(20001, ex.getCode());
        }

        @Test
        @DisplayName("IP 为 null 时抛出 PARKING_20001")
        void shouldThrowWhenIpIsNull() {
            BusinessException ex = assertThrows(BusinessException.class, () ->
                    authorizationService.checkIpWhitelist(null, "modify_parking_config"));
            assertEquals(20001, ex.getCode());
        }

        @Test
        @DisplayName("缓存未命中时从数据库加载并写入缓存")
        void shouldLoadFromDatabaseWhenCacheMiss() {
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.get("ip_whitelist:export_raw_data")).thenReturn(null);

            // 默认 loadWhitelistFromDatabase 返回空列表，白名单为空时拒绝
            BusinessException ex = assertThrows(BusinessException.class, () ->
                    authorizationService.checkIpWhitelist("192.168.1.1", "export_raw_data"));
            assertEquals(20001, ex.getCode());

            // 验证缓存写入被调用
            verify(valueOperations).set(eq("ip_whitelist:export_raw_data"),
                    eq(Collections.emptyList()), eq(1L), eq(TimeUnit.HOURS));
        }

        @Test
        @DisplayName("CIDR /32 精确匹配单个 IP")
        void shouldMatchCidr32() {
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            List<String> whitelist = List.of("10.0.0.5/32");
            when(valueOperations.get("ip_whitelist:disable_owner")).thenReturn(whitelist);

            assertDoesNotThrow(() ->
                    authorizationService.checkIpWhitelist("10.0.0.5", "disable_owner"));

            BusinessException ex = assertThrows(BusinessException.class, () ->
                    authorizationService.checkIpWhitelist("10.0.0.6", "disable_owner"));
            assertEquals(20001, ex.getCode());
        }

        @Test
        @DisplayName("CIDR /16 匹配网段")
        void shouldMatchCidr16() {
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            List<String> whitelist = List.of("172.16.0.0/16");
            when(valueOperations.get("ip_whitelist:modify_parking_config")).thenReturn(whitelist);

            assertDoesNotThrow(() ->
                    authorizationService.checkIpWhitelist("172.16.255.255", "modify_parking_config"));

            BusinessException ex = assertThrows(BusinessException.class, () ->
                    authorizationService.checkIpWhitelist("172.17.0.1", "modify_parking_config"));
            assertEquals(20001, ex.getCode());
        }
    }

    // ==================== checkRolePermission 测试 ====================

    @Nested
    @DisplayName("checkRolePermission - 角色权限校验")
    class CheckRolePermissionTest {

        @Test
        @DisplayName("超级管理员拥有所有权限")
        void superAdminShouldHaveAllPermissions() {
            assertDoesNotThrow(() ->
                    authorizationService.checkRolePermission("super_admin", "modify_parking_config"));
            assertDoesNotThrow(() ->
                    authorizationService.checkRolePermission("super_admin", "disable_owner"));
            assertDoesNotThrow(() ->
                    authorizationService.checkRolePermission("super_admin", "export_raw_data"));
            assertDoesNotThrow(() ->
                    authorizationService.checkRolePermission("super_admin", "any_operation"));
        }

        @Test
        @DisplayName("物业管理员拥有审批和管理权限")
        void propertyAdminShouldHaveManagementPermissions() {
            assertDoesNotThrow(() ->
                    authorizationService.checkRolePermission("property_admin", "audit_owner"));
            assertDoesNotThrow(() ->
                    authorizationService.checkRolePermission("property_admin", "audit_visitor"));
            assertDoesNotThrow(() ->
                    authorizationService.checkRolePermission("property_admin", "view_report"));
        }

        @Test
        @DisplayName("物业管理员无权执行高危操作（如导出原始数据）")
        void propertyAdminShouldNotHaveHighRiskPermissions() {
            BusinessException ex = assertThrows(BusinessException.class, () ->
                    authorizationService.checkRolePermission("property_admin", "export_raw_data"));
            assertEquals(12001, ex.getCode());
        }

        @Test
        @DisplayName("业主拥有本人数据域操作权限")
        void ownerShouldHaveOwnDataPermissions() {
            assertDoesNotThrow(() ->
                    authorizationService.checkRolePermission("owner", "manage_own_vehicle"));
            assertDoesNotThrow(() ->
                    authorizationService.checkRolePermission("owner", "apply_visitor"));
            assertDoesNotThrow(() ->
                    authorizationService.checkRolePermission("owner", "view_own_records"));
        }

        @Test
        @DisplayName("业主无权执行管理操作")
        void ownerShouldNotHaveAdminPermissions() {
            BusinessException ex = assertThrows(BusinessException.class, () ->
                    authorizationService.checkRolePermission("owner", "audit_owner"));
            assertEquals(12001, ex.getCode());
        }

        @Test
        @DisplayName("未知角色抛出 PARKING_12001")
        void unknownRoleShouldThrow() {
            BusinessException ex = assertThrows(BusinessException.class, () ->
                    authorizationService.checkRolePermission("unknown_role", "view_report"));
            assertEquals(12001, ex.getCode());
        }

        @Test
        @DisplayName("role 为 null 抛出 PARKING_12001")
        void nullRoleShouldThrow() {
            BusinessException ex = assertThrows(BusinessException.class, () ->
                    authorizationService.checkRolePermission(null, "view_report"));
            assertEquals(12001, ex.getCode());
        }

        @Test
        @DisplayName("operation 为 null 抛出 PARKING_12001")
        void nullOperationShouldThrow() {
            BusinessException ex = assertThrows(BusinessException.class, () ->
                    authorizationService.checkRolePermission("owner", null));
            assertEquals(12001, ex.getCode());
        }
    }

    // ==================== CIDR 匹配边界测试（通过 checkIpWhitelist 间接测试） ====================

    @Nested
    @DisplayName("CIDR 匹配边界测试")
    class CidrMatchTest {

        @Test
        @DisplayName("无效 CIDR 格式不匹配，IP 被拒绝")
        void invalidCidrShouldNotMatch() {
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            List<String> whitelist = List.of("invalid/cidr/format");
            when(valueOperations.get("ip_whitelist:modify_parking_config")).thenReturn(whitelist);

            BusinessException ex = assertThrows(BusinessException.class, () ->
                    authorizationService.checkIpWhitelist("192.168.1.1", "modify_parking_config"));
            assertEquals(20001, ex.getCode());
        }

        @Test
        @DisplayName("CIDR /0 匹配所有 IP")
        void cidr0ShouldMatchAll() {
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            List<String> whitelist = List.of("0.0.0.0/0");
            when(valueOperations.get("ip_whitelist:modify_parking_config")).thenReturn(whitelist);

            assertDoesNotThrow(() ->
                    authorizationService.checkIpWhitelist("1.2.3.4", "modify_parking_config"));
            assertDoesNotThrow(() ->
                    authorizationService.checkIpWhitelist("255.255.255.255", "modify_parking_config"));
        }

        @Test
        @DisplayName("CIDR 前缀长度超出范围不匹配")
        void invalidPrefixLengthShouldNotMatch() {
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            List<String> whitelist = List.of("192.168.1.0/33");
            when(valueOperations.get("ip_whitelist:modify_parking_config")).thenReturn(whitelist);

            BusinessException ex = assertThrows(BusinessException.class, () ->
                    authorizationService.checkIpWhitelist("192.168.1.1", "modify_parking_config"));
            assertEquals(20001, ex.getCode());
        }
    }
}
