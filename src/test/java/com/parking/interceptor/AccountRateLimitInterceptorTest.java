package com.parking.interceptor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.parking.common.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * AccountRateLimitInterceptor 单元测试
 * 验证账号级限流逻辑：每个账号每分钟最多 100 次
 */
@ExtendWith(MockitoExtension.class)
class AccountRateLimitInterceptorTest {

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private ValueOperations<String, Object> valueOperations;

    private AccountRateLimitInterceptor interceptor;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        interceptor = new AccountRateLimitInterceptor(redisTemplate, objectMapper);
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        request.setRequestURI("/api/v1/parking/config");
        request.setMethod("GET");
    }

    @Test
    @DisplayName("未认证请求（无 userId）应直接放行")
    void shouldPassWhenNoUserId() throws Exception {
        // 不设置 ATTR_USER_ID
        boolean result = interceptor.preHandle(request, response, new Object());
        assertTrue(result);
    }

    @Test
    @DisplayName("已认证请求且未超限应放行")
    void shouldPassWhenUnderLimit() throws Exception {
        request.setAttribute(AuthenticationInterceptor.ATTR_USER_ID, 10001L);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment(anyString())).thenReturn(50L);

        boolean result = interceptor.preHandle(request, response, new Object());
        assertTrue(result);
    }

    @Test
    @DisplayName("首次计数应设置过期时间")
    void shouldSetExpireOnFirstCount() throws Exception {
        request.setAttribute(AuthenticationInterceptor.ATTR_USER_ID, 10001L);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment(anyString())).thenReturn(1L);

        boolean result = interceptor.preHandle(request, response, new Object());
        assertTrue(result);
        verify(redisTemplate).expire(anyString(), eq(60L), eq(TimeUnit.SECONDS));
    }

    @Test
    @DisplayName("第100次请求应放行（恰好等于限制）")
    void shouldPassAtExactLimit() throws Exception {
        request.setAttribute(AuthenticationInterceptor.ATTR_USER_ID, 10001L);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment(anyString())).thenReturn(100L);

        boolean result = interceptor.preHandle(request, response, new Object());
        assertTrue(result);
    }

    @Test
    @DisplayName("第101次请求应触发限流并返回 PARKING_19005")
    void shouldBlockWhenOverLimit() throws Exception {
        request.setAttribute(AuthenticationInterceptor.ATTR_USER_ID, 10001L);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment(anyString())).thenReturn(101L);

        boolean result = interceptor.preHandle(request, response, new Object());
        assertFalse(result);

        String responseBody = response.getContentAsString();
        assertTrue(responseBody.contains(String.valueOf(ErrorCode.PARKING_19005.getCode())));
    }

    @Test
    @DisplayName("Redis 异常时应放行请求")
    void shouldPassWhenRedisException() throws Exception {
        request.setAttribute(AuthenticationInterceptor.ATTR_USER_ID, 10001L);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment(anyString())).thenThrow(new RuntimeException("Redis 连接失败"));

        boolean result = interceptor.preHandle(request, response, new Object());
        assertTrue(result);
    }

    @Test
    @DisplayName("不同用户应使用不同的限流计数器")
    void shouldUseDifferentKeysForDifferentUsers() throws Exception {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment(anyString())).thenReturn(1L);

        // 用户 A
        request.setAttribute(AuthenticationInterceptor.ATTR_USER_ID, 10001L);
        interceptor.preHandle(request, response, new Object());

        // 用户 B
        MockHttpServletRequest request2 = new MockHttpServletRequest();
        request2.setRequestURI("/api/v1/parking/config");
        request2.setAttribute(AuthenticationInterceptor.ATTR_USER_ID, 10002L);
        interceptor.preHandle(request2, new MockHttpServletResponse(), new Object());

        // 应调用两次 increment，使用不同的 key
        verify(valueOperations, times(2)).increment(anyString());
    }
}
