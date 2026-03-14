package com.parking.service;

import com.parking.service.impl.CacheServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * CacheService 单元测试
 * Validates: Requirements 21.7, 21.8
 */
@ExtendWith(MockitoExtension.class)
class CacheServiceTest {

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private ValueOperations<String, Object> valueOperations;

    private CacheServiceImpl cacheService;

    @BeforeEach
    void setUp() {
        cacheService = new CacheServiceImpl(redisTemplate);
    }

    @Nested
    @DisplayName("get - 获取缓存值")
    class GetTests {

        @Test
        @DisplayName("缓存命中时应返回对应值")
        void get_cacheHit_shouldReturnValue() {
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.get("cache:report:1001:1-101")).thenReturn("reportData");

            Optional<Object> result = cacheService.get("report:1001:1-101");

            assertTrue(result.isPresent());
            assertEquals("reportData", result.get());
        }

        @Test
        @DisplayName("缓存未命中时应返回 empty")
        void get_cacheMiss_shouldReturnEmpty() {
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.get("cache:report:1001:1-101")).thenReturn(null);

            Optional<Object> result = cacheService.get("report:1001:1-101");

            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("null 键应返回 empty")
        void get_nullKey_shouldReturnEmpty() {
            Optional<Object> result = cacheService.get(null);
            assertTrue(result.isEmpty());
            verifyNoInteractions(redisTemplate);
        }

        @Test
        @DisplayName("空字符串键应返回 empty")
        void get_emptyKey_shouldReturnEmpty() {
            Optional<Object> result = cacheService.get("");
            assertTrue(result.isEmpty());
            verifyNoInteractions(redisTemplate);
        }

        @Test
        @DisplayName("空白字符串键应返回 empty")
        void get_blankKey_shouldReturnEmpty() {
            Optional<Object> result = cacheService.get("   ");
            assertTrue(result.isEmpty());
            verifyNoInteractions(redisTemplate);
        }
    }

    @Nested
    @DisplayName("get(key, type) - 获取缓存值并转换类型")
    class GetWithTypeTests {

        @Test
        @DisplayName("类型匹配时应返回转换后的值")
        void getWithType_typeMatch_shouldReturnTypedValue() {
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.get("cache:vehicles:1001:1-101")).thenReturn("vehicleList");

            Optional<String> result = cacheService.get("vehicles:1001:1-101", String.class);

            assertTrue(result.isPresent());
            assertEquals("vehicleList", result.get());
        }

        @Test
        @DisplayName("类型不匹配时应返回 empty")
        void getWithType_typeMismatch_shouldReturnEmpty() {
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.get("cache:vehicles:1001:1-101")).thenReturn("stringValue");

            Optional<Integer> result = cacheService.get("vehicles:1001:1-101", Integer.class);

            assertTrue(result.isEmpty());
        }
    }

    @Nested
    @DisplayName("set - 设置缓存值")
    class SetTests {

        @Test
        @DisplayName("应正确设置缓存值和过期时间")
        void set_shouldSetValueWithExpiry() {
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);

            cacheService.set("report:1001:1-101", "reportData", 1, TimeUnit.HOURS);

            verify(valueOperations).set(
                    eq("cache:report:1001:1-101"),
                    eq("reportData"),
                    eq(1L),
                    eq(TimeUnit.HOURS)
            );
        }

        @Test
        @DisplayName("null 键应跳过设置")
        void set_nullKey_shouldSkip() {
            cacheService.set(null, "value", 1, TimeUnit.HOURS);
            verifyNoInteractions(redisTemplate);
        }

        @Test
        @DisplayName("空字符串键应跳过设置")
        void set_emptyKey_shouldSkip() {
            cacheService.set("", "value", 1, TimeUnit.HOURS);
            verifyNoInteractions(redisTemplate);
        }
    }

    @Nested
    @DisplayName("delete - 删除缓存")
    class DeleteTests {

        @Test
        @DisplayName("键存在时应删除成功并返回 true")
        void delete_keyExists_shouldReturnTrue() {
            when(redisTemplate.delete("cache:report:1001:1-101")).thenReturn(Boolean.TRUE);

            boolean result = cacheService.delete("report:1001:1-101");

            assertTrue(result);
            verify(redisTemplate).delete("cache:report:1001:1-101");
        }

        @Test
        @DisplayName("键不存在时应返回 false")
        void delete_keyNotExists_shouldReturnFalse() {
            when(redisTemplate.delete("cache:report:1001:1-101")).thenReturn(Boolean.FALSE);

            boolean result = cacheService.delete("report:1001:1-101");

            assertFalse(result);
        }

        @Test
        @DisplayName("null 键应返回 false")
        void delete_nullKey_shouldReturnFalse() {
            boolean result = cacheService.delete(null);
            assertFalse(result);
            verify(redisTemplate, never()).delete(anyString());
        }
    }

    @Nested
    @DisplayName("exists - 检查缓存键是否存在")
    class ExistsTests {

        @Test
        @DisplayName("键存在时应返回 true")
        void exists_keyExists_shouldReturnTrue() {
            when(redisTemplate.hasKey("cache:ip_whitelist:1001")).thenReturn(Boolean.TRUE);

            boolean result = cacheService.exists("ip_whitelist:1001");

            assertTrue(result);
        }

        @Test
        @DisplayName("键不存在时应返回 false")
        void exists_keyNotExists_shouldReturnFalse() {
            when(redisTemplate.hasKey("cache:ip_whitelist:1001")).thenReturn(Boolean.FALSE);

            boolean result = cacheService.exists("ip_whitelist:1001");

            assertFalse(result);
        }

        @Test
        @DisplayName("null 键应返回 false")
        void exists_nullKey_shouldReturnFalse() {
            boolean result = cacheService.exists(null);
            assertFalse(result);
            verifyNoInteractions(redisTemplate);
        }
    }

    @Nested
    @DisplayName("deleteByPrefix - 按前缀批量删除缓存")
    class DeleteByPrefixTests {

        @Test
        @DisplayName("应删除匹配前缀的所有缓存键")
        void deleteByPrefix_shouldDeleteMatchingKeys() {
            Set<String> keys = Set.of(
                    "cache:report:1001:1-101",
                    "cache:report:1001:2-202"
            );
            when(redisTemplate.keys("cache:report:1001*")).thenReturn(keys);
            when(redisTemplate.delete(keys)).thenReturn(2L);

            long count = cacheService.deleteByPrefix("report:1001");

            assertEquals(2, count);
            verify(redisTemplate).delete(keys);
        }

        @Test
        @DisplayName("无匹配键时应返回 0")
        void deleteByPrefix_noMatchingKeys_shouldReturnZero() {
            when(redisTemplate.keys("cache:report:9999*")).thenReturn(Set.of());

            long count = cacheService.deleteByPrefix("report:9999");

            assertEquals(0, count);
            verify(redisTemplate, never()).delete(anyCollection());
        }

        @Test
        @DisplayName("keys 返回 null 时应返回 0")
        void deleteByPrefix_keysReturnsNull_shouldReturnZero() {
            when(redisTemplate.keys("cache:report:9999*")).thenReturn(null);

            long count = cacheService.deleteByPrefix("report:9999");

            assertEquals(0, count);
        }

        @Test
        @DisplayName("null 前缀应返回 0")
        void deleteByPrefix_nullPrefix_shouldReturnZero() {
            long count = cacheService.deleteByPrefix(null);
            assertEquals(0, count);
            verifyNoInteractions(redisTemplate);
        }

        @Test
        @DisplayName("空字符串前缀应返回 0")
        void deleteByPrefix_emptyPrefix_shouldReturnZero() {
            long count = cacheService.deleteByPrefix("");
            assertEquals(0, count);
            verifyNoInteractions(redisTemplate);
        }
    }

    @Nested
    @DisplayName("generateKey - 生成缓存键")
    class GenerateKeyTests {

        @Test
        @DisplayName("应按格式生成三段式缓存键: {resource}:{communityId}:{houseNo}")
        void generateKey_withHouseNo_shouldFollowFormat() {
            String key = cacheService.generateKey("report", 1001L, "1-101");
            assertEquals("report:1001:1-101", key);
        }

        @Test
        @DisplayName("应按格式生成两段式缓存键: {resource}:{communityId}")
        void generateKey_withoutHouseNo_shouldFollowFormat() {
            String key = cacheService.generateKey("ip_whitelist", 1001L);
            assertEquals("ip_whitelist:1001", key);
        }

        @Test
        @DisplayName("车辆缓存键生成")
        void generateKey_vehicles() {
            String key = cacheService.generateKey("vehicles", 2001L, "3-303");
            assertEquals("vehicles:2001:3-303", key);
        }

        @Test
        @DisplayName("报表缓存键生成（不含 houseNo）")
        void generateKey_report_withoutHouseNo() {
            String key = cacheService.generateKey("report", 1001L);
            assertEquals("report:1001", key);
        }
    }

    @Nested
    @DisplayName("缓存过期时间常量验证")
    class CacheTtlConstantsTests {

        @Test
        @DisplayName("报表缓存过期时间应为1小时")
        void reportTtl_shouldBeOneHour() {
            assertEquals(1, CacheServiceImpl.REPORT_TTL);
            assertEquals(TimeUnit.HOURS, CacheServiceImpl.REPORT_TTL_UNIT);
        }

        @Test
        @DisplayName("IP 白名单缓存过期时间应为1小时")
        void ipWhitelistTtl_shouldBeOneHour() {
            assertEquals(1, CacheServiceImpl.IP_WHITELIST_TTL);
            assertEquals(TimeUnit.HOURS, CacheServiceImpl.IP_WHITELIST_TTL_UNIT);
        }

        @Test
        @DisplayName("热点数据缓存过期时间应为30分钟")
        void hotspotTtl_shouldBeThirtyMinutes() {
            assertEquals(30, CacheServiceImpl.HOTSPOT_TTL);
            assertEquals(TimeUnit.MINUTES, CacheServiceImpl.HOTSPOT_TTL_UNIT);
        }
    }
}
