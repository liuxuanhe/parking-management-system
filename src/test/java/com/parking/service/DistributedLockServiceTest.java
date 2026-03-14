package com.parking.service;

import com.parking.common.BusinessException;
import com.parking.service.impl.DistributedLockServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;

import java.time.Duration;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * DistributedLockService 单元测试
 * Validates: Requirements 4.10, 5.10
 */
@ExtendWith(MockitoExtension.class)
class DistributedLockServiceTest {

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private DistributedLockServiceImpl lockService;

    @BeforeEach
    void setUp() {
        lockService = new DistributedLockServiceImpl(stringRedisTemplate);
    }

    @Nested
    @DisplayName("tryLock - 获取分布式锁")
    class TryLockTests {

        @Test
        @DisplayName("锁未被占用时应获取成功并返回 true")
        void tryLock_lockAvailable_shouldReturnTrue() {
            when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.setIfAbsent(eq("lock:space:1001"), anyString(), eq(Duration.ofSeconds(5))))
                    .thenReturn(Boolean.TRUE);

            boolean result = lockService.tryLock("space:1001", 5);

            assertTrue(result);
            verify(valueOperations).setIfAbsent(eq("lock:space:1001"), anyString(), eq(Duration.ofSeconds(5)));
        }

        @Test
        @DisplayName("锁已被占用时应返回 false")
        void tryLock_lockOccupied_shouldReturnFalse() {
            when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.setIfAbsent(eq("lock:space:1001"), anyString(), eq(Duration.ofSeconds(5))))
                    .thenReturn(Boolean.FALSE);

            boolean result = lockService.tryLock("space:1001", 5);

            assertFalse(result);
        }

        @Test
        @DisplayName("应使用 UUID 作为锁的 value")
        void tryLock_shouldUseUuidAsValue() {
            when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
            ArgumentCaptor<String> valueCaptor = ArgumentCaptor.forClass(String.class);
            when(valueOperations.setIfAbsent(anyString(), valueCaptor.capture(), any(Duration.class)))
                    .thenReturn(Boolean.TRUE);

            lockService.tryLock("test:key", 5);

            String capturedValue = valueCaptor.getValue();
            assertNotNull(capturedValue);
            // UUID 格式: xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx (36 字符)
            assertEquals(36, capturedValue.length());
            assertTrue(capturedValue.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"));
        }

        @Test
        @DisplayName("应使用指定的超时时间")
        void tryLock_shouldUseSpecifiedTimeout() {
            when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.setIfAbsent(anyString(), anyString(), eq(Duration.ofSeconds(10))))
                    .thenReturn(Boolean.TRUE);

            lockService.tryLock("test:key", 10);

            verify(valueOperations).setIfAbsent(anyString(), anyString(), eq(Duration.ofSeconds(10)));
        }
    }

    @Nested
    @DisplayName("unlock - 释放分布式锁")
    class UnlockTests {

        @Test
        @DisplayName("持有锁时应使用 Lua 脚本原子释放")
        @SuppressWarnings("unchecked")
        void unlock_holdingLock_shouldUseLuaScript() {
            // 先获取锁
            when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.setIfAbsent(eq("lock:space:1001"), anyString(), any(Duration.class)))
                    .thenReturn(Boolean.TRUE);
            lockService.tryLock("space:1001", 5);

            // 释放锁时使用 Lua 脚本
            when(stringRedisTemplate.execute(any(RedisScript.class), anyList(), anyString()))
                    .thenReturn(1L);

            lockService.unlock("space:1001");

            verify(stringRedisTemplate).execute(any(RedisScript.class),
                    eq(Collections.singletonList("lock:space:1001")), anyString());
        }

        @Test
        @DisplayName("未持有锁时释放应安全跳过")
        void unlock_notHoldingLock_shouldSkipSafely() {
            // 直接释放未获取的锁，不应抛出异常
            assertDoesNotThrow(() -> lockService.unlock("nonexistent:key"));

            // 不应调用 Redis execute
            verify(stringRedisTemplate, never()).execute(any(RedisScript.class), anyList(), anyString());
        }

        @Test
        @DisplayName("锁已过期时释放应安全处理")
        @SuppressWarnings("unchecked")
        void unlock_lockExpired_shouldHandleSafely() {
            // 先获取锁
            when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                    .thenReturn(Boolean.TRUE);
            lockService.tryLock("space:1001", 5);

            // Lua 脚本返回 0 表示锁已过期或被其他线程持有
            when(stringRedisTemplate.execute(any(RedisScript.class), anyList(), anyString()))
                    .thenReturn(0L);

            assertDoesNotThrow(() -> lockService.unlock("space:1001"));
        }
    }

    @Nested
    @DisplayName("executeWithLock - 带锁执行业务逻辑")
    class ExecuteWithLockTests {

        @Test
        @DisplayName("获取锁成功时应执行业务逻辑并返回结果")
        @SuppressWarnings("unchecked")
        void executeWithLock_lockAcquired_shouldExecuteAndReturn() {
            when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.setIfAbsent(eq("lock:space:1001"), anyString(), any(Duration.class)))
                    .thenReturn(Boolean.TRUE);
            when(stringRedisTemplate.execute(any(RedisScript.class), anyList(), anyString()))
                    .thenReturn(1L);

            String result = lockService.executeWithLock("space:1001", () -> "业务执行成功");

            assertEquals("业务执行成功", result);
        }

        @Test
        @DisplayName("获取锁成功后应自动释放锁")
        @SuppressWarnings("unchecked")
        void executeWithLock_afterExecution_shouldReleaseLock() {
            when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.setIfAbsent(eq("lock:space:1001"), anyString(), any(Duration.class)))
                    .thenReturn(Boolean.TRUE);
            when(stringRedisTemplate.execute(any(RedisScript.class), anyList(), anyString()))
                    .thenReturn(1L);

            lockService.executeWithLock("space:1001", () -> "ok");

            // 验证 Lua 脚本被调用（释放锁）
            verify(stringRedisTemplate).execute(any(RedisScript.class),
                    eq(Collections.singletonList("lock:space:1001")), anyString());
        }

        @Test
        @DisplayName("业务逻辑抛出异常时应自动释放锁")
        @SuppressWarnings("unchecked")
        void executeWithLock_supplierThrows_shouldReleaseLock() {
            when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.setIfAbsent(eq("lock:space:1001"), anyString(), any(Duration.class)))
                    .thenReturn(Boolean.TRUE);
            when(stringRedisTemplate.execute(any(RedisScript.class), anyList(), anyString()))
                    .thenReturn(1L);

            assertThrows(RuntimeException.class, () ->
                    lockService.executeWithLock("space:1001", () -> {
                        throw new RuntimeException("业务异常");
                    }));

            // 即使业务异常，锁也应被释放
            verify(stringRedisTemplate).execute(any(RedisScript.class),
                    eq(Collections.singletonList("lock:space:1001")), anyString());
        }

        @Test
        @DisplayName("获取锁失败且重试全部失败时应抛出 BusinessException")
        void executeWithLock_allRetriesFailed_shouldThrowBusinessException() {
            when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
            // 所有重试都返回 false
            when(valueOperations.setIfAbsent(eq("lock:space:1001"), anyString(), any(Duration.class)))
                    .thenReturn(Boolean.FALSE);

            AtomicInteger callCount = new AtomicInteger(0);

            BusinessException exception = assertThrows(BusinessException.class, () ->
                    lockService.executeWithLock("space:1001", () -> {
                        callCount.incrementAndGet();
                        return "不应执行";
                    }));

            assertEquals(10010, exception.getCode());
            // 业务逻辑不应被执行
            assertEquals(0, callCount.get());
            // 应尝试获取锁3次（最大重试次数）
            verify(valueOperations, times(3)).setIfAbsent(eq("lock:space:1001"), anyString(), any(Duration.class));
        }

        @Test
        @DisplayName("第二次重试获取锁成功时应执行业务逻辑")
        @SuppressWarnings("unchecked")
        void executeWithLock_secondRetrySucceeds_shouldExecute() {
            when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
            // 第一次失败，第二次成功
            when(valueOperations.setIfAbsent(eq("lock:space:1001"), anyString(), any(Duration.class)))
                    .thenReturn(Boolean.FALSE)
                    .thenReturn(Boolean.TRUE);
            when(stringRedisTemplate.execute(any(RedisScript.class), anyList(), anyString()))
                    .thenReturn(1L);

            String result = lockService.executeWithLock("space:1001", () -> "重试成功");

            assertEquals("重试成功", result);
            // 应尝试获取锁2次
            verify(valueOperations, times(2)).setIfAbsent(eq("lock:space:1001"), anyString(), any(Duration.class));
        }

        @Test
        @DisplayName("第三次重试获取锁成功时应执行业务逻辑")
        @SuppressWarnings("unchecked")
        void executeWithLock_thirdRetrySucceeds_shouldExecute() {
            when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
            // 前两次失败，第三次成功
            when(valueOperations.setIfAbsent(eq("lock:space:1001"), anyString(), any(Duration.class)))
                    .thenReturn(Boolean.FALSE)
                    .thenReturn(Boolean.FALSE)
                    .thenReturn(Boolean.TRUE);
            when(stringRedisTemplate.execute(any(RedisScript.class), anyList(), anyString()))
                    .thenReturn(1L);

            String result = lockService.executeWithLock("space:1001", () -> "第三次成功");

            assertEquals("第三次成功", result);
            verify(valueOperations, times(3)).setIfAbsent(eq("lock:space:1001"), anyString(), any(Duration.class));
        }
    }
}
