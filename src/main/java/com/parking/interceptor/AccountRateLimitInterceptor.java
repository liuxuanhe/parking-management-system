package com.parking.interceptor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.parking.common.ApiResponse;
import com.parking.common.ErrorCode;
import com.parking.common.RequestContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/**
 * 账号级限流拦截器
 * 管理端接口：每个账号每分钟最多 100 次
 * 使用 Redis 计数器实现
 * Validates: Requirements 19.3
 */
@Slf4j
public class AccountRateLimitInterceptor implements HandlerInterceptor {

    private static final String RATE_LIMIT_PREFIX = "rate_limit:account:";

    /** 每个账号每分钟最多 100 次请求 */
    private static final int ACCOUNT_LIMIT = 100;
    private static final int WINDOW_SECONDS = 60;

    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;

    public AccountRateLimitInterceptor(RedisTemplate<String, Object> redisTemplate,
                                       ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response,
                             Object handler) throws Exception {
        // 从请求属性中获取已认证的用户 ID（由 AuthenticationInterceptor 设置）
        Object userIdObj = request.getAttribute(AuthenticationInterceptor.ATTR_USER_ID);
        if (userIdObj == null) {
            // 未认证的请求不做账号级限流（由 IP 级限流覆盖）
            return true;
        }

        Long userId = (Long) userIdObj;
        long windowId = System.currentTimeMillis() / (WINDOW_SECONDS * 1000L);
        String cacheKey = RATE_LIMIT_PREFIX + userId + ":" + windowId;

        if (isRateLimited(cacheKey)) {
            log.warn("账号级限流触发: userId={}, path={} {}", userId, request.getMethod(), request.getRequestURI());
            writeErrorResponse(response);
            return false;
        }

        return true;
    }

    /**
     * 判断是否触发限流
     */
    private boolean isRateLimited(String cacheKey) {
        try {
            Long count = redisTemplate.opsForValue().increment(cacheKey);
            if (count != null && count == 1) {
                redisTemplate.expire(cacheKey, WINDOW_SECONDS, TimeUnit.SECONDS);
            }
            return count != null && count > ACCOUNT_LIMIT;
        } catch (Exception e) {
            log.error("账号级限流计数器异常，放行请求: cacheKey={}", cacheKey, e);
            // Redis 异常时放行，避免影响正常业务
            return false;
        }
    }

    /**
     * 写入限流错误响应
     */
    private void writeErrorResponse(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());

        ApiResponse<Void> apiResponse = ApiResponse.error(
                ErrorCode.PARKING_19005, RequestContext.getRequestId());

        response.getWriter().write(objectMapper.writeValueAsString(apiResponse));
        response.getWriter().flush();
    }
}
