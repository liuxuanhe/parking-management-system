package com.parking.service;

import com.parking.service.impl.IdempotencyServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * IdempotencyService 单元测试
 * Validates: Requirements 2.8, 5.7, 7.10
 */
@ExtendWith(MockitoExtension.class)
class IdempotencyServiceTest {

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private IdempotencyServiceImpl idempotencyService;

    @BeforeEach
    void setUp() {
        idempotencyService = new IdempotencyServiceImpl(stringRedisTemplate);
    }

    @Nested
    @DisplayName("checkAndSet - 检查并设置幂等键")
    class CheckAndSetTests {

        @Test
        @DisplayName("首次请求应设置成功并返回 true")
        void checkAndSet_firstRequest_shouldReturnTrue() {
            when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.setIfAbsent(
                    eq("idempotency:owner_audit:1001:10001:req_001"),
                    eq("{\"code\":0}"),
                    eq(Duration.ofSeconds(300))
            )).thenReturn(Boolean.TRUE);

            boolean result = idempotencyService.checkAndSet(
                    "owner_audit:1001:10001:req_001", "{\"code\":0}", 300);

            assertTrue(result);
            verify(valueOperations).setIfAbsent(
                    eq("idempotency:owner_audit:1001:10001:req_001"),
                    eq("{\"code\":0}"),
                    eq(Duration.ofSeconds(300))
            );
        }

        @Test
        @DisplayName("重复请求应返回 false")
        void checkAndSet_duplicateRequest_shouldReturnFalse() {
            when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.setIfAbsent(
                    eq("idempotency:owner_audit:1001:10001:req_001"),
                    eq("{\"code\":0}"),
                    eq(Duration.ofSeconds(300))
            )).thenReturn(Boolean.FALSE);

            boolean result = idempotencyService.checkAndSet(
                    "owner_audit:1001:10001:req_001", "{\"code\":0}", 300);

            assertFalse(result);
        }

        @Test
        @DisplayName("null 幂等键应返回 true（跳过幂等检查）")
        void checkAndSet_nullKey_shouldReturnTrue() {
            boolean result = idempotencyService.checkAndSet(null, "{\"code\":0}", 300);
            assertTrue(result);
            verifyNoInteractions(stringRedisTemplate);
        }

        @Test
        @DisplayName("空字符串幂等键应返回 true（跳过幂等检查）")
        void checkAndSet_emptyKey_shouldReturnTrue() {
            boolean result = idempotencyService.checkAndSet("", "{\"code\":0}", 300);
            assertTrue(result);
            verifyNoInteractions(stringRedisTemplate);
        }

        @Test
        @DisplayName("空白字符串幂等键应返回 true（跳过幂等检查）")
        void checkAndSet_blankKey_shouldReturnTrue() {
            boolean result = idempotencyService.checkAndSet("   ", "{\"code\":0}", 300);
            assertTrue(result);
            verifyNoInteractions(stringRedisTemplate);
        }

        @Test
        @DisplayName("null 结果值应存储为空字符串")
        void checkAndSet_nullResult_shouldStoreEmptyString() {
            when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.setIfAbsent(
                    eq("idempotency:test_key"),
                    eq(""),
                    eq(Duration.ofSeconds(300))
            )).thenReturn(Boolean.TRUE);

            boolean result = idempotencyService.checkAndSet("test_key", null, 300);

            assertTrue(result);
            verify(valueOperations).setIfAbsent(
                    eq("idempotency:test_key"),
                    eq(""),
                    eq(Duration.ofSeconds(300))
            );
        }
    }

    @Nested
    @DisplayName("getResult - 获取幂等结果")
    class GetResultTests {

        @Test
        @DisplayName("键存在时应返回对应结果")
        void getResult_keyExists_shouldReturnResult() {
            when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.get("idempotency:owner_audit:1001:10001:req_001"))
                    .thenReturn("{\"code\":0,\"message\":\"success\"}");

            Optional<String> result = idempotencyService.getResult(
                    "owner_audit:1001:10001:req_001");

            assertTrue(result.isPresent());
            assertEquals("{\"code\":0,\"message\":\"success\"}", result.get());
        }

        @Test
        @DisplayName("键不存在时应返回 empty")
        void getResult_keyNotExists_shouldReturnEmpty() {
            when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.get("idempotency:nonexistent_key"))
                    .thenReturn(null);

            Optional<String> result = idempotencyService.getResult("nonexistent_key");

            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("null 幂等键应返回 empty")
        void getResult_nullKey_shouldReturnEmpty() {
            Optional<String> result = idempotencyService.getResult(null);
            assertTrue(result.isEmpty());
            verifyNoInteractions(stringRedisTemplate);
        }

        @Test
        @DisplayName("空字符串幂等键应返回 empty")
        void getResult_emptyKey_shouldReturnEmpty() {
            Optional<String> result = idempotencyService.getResult("");
            assertTrue(result.isEmpty());
            verifyNoInteractions(stringRedisTemplate);
        }
    }

    @Nested
    @DisplayName("generateKey - 生成幂等键")
    class GenerateKeyTests {

        @Test
        @DisplayName("应按格式生成幂等键: {operationType}:{communityId}:{targetId}:{requestId}")
        void generateKey_shouldFollowFormat() {
            String key = idempotencyService.generateKey(
                    "owner_audit", 1001L, 10001L, "req_001");

            assertEquals("owner_audit:1001:10001:req_001", key);
        }

        @Test
        @DisplayName("Visitor 审批幂等键生成")
        void generateKey_visitorAudit() {
            String key = idempotencyService.generateKey(
                    "visitor_audit", 2001L, 20001L, "req_abc123");

            assertEquals("visitor_audit:2001:20001:req_abc123", key);
        }

        @Test
        @DisplayName("车辆入场幂等键生成")
        void generateKey_vehicleEntry() {
            String key = idempotencyService.generateKey(
                    "vehicle_entry", 1001L, 30001L, "202401151000");

            assertEquals("vehicle_entry:1001:30001:202401151000", key);
        }
    }
}
