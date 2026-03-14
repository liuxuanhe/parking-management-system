package com.parking.service.impl;

import com.parking.service.IdempotencyService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;

/**
 * 幂等性服务实现类
 * 使用 Redis 的 setIfAbsent 原子操作实现幂等键的检查与设置
 * - checkAndSet: 原子性地检查键是否存在并设置，防止并发重复操作
 * - getResult: 获取幂等键对应的操作结果，用于重复请求返回原结果
 * - generateKey: 按固定格式生成幂等键
 * Validates: Requirements 2.8, 5.7, 7.10
 */
@Slf4j
@Service
public class IdempotencyServiceImpl implements IdempotencyService {

    /** 幂等键在 Redis 中的键前缀 */
    private static final String IDEMPOTENCY_KEY_PREFIX = "idempotency:";

    private final StringRedisTemplate stringRedisTemplate;

    public IdempotencyServiceImpl(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public boolean checkAndSet(String idempotencyKey, String result, int expireSeconds) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            log.warn("幂等键为空，跳过幂等检查");
            return true;
        }

        String redisKey = IDEMPOTENCY_KEY_PREFIX + idempotencyKey;
        Duration expireDuration = Duration.ofSeconds(expireSeconds);

        // 使用 setIfAbsent 原子操作：键不存在则设置并返回 true，否则返回 false
        Boolean success = stringRedisTemplate.opsForValue()
                .setIfAbsent(redisKey, result != null ? result : "", expireDuration);

        if (Boolean.TRUE.equals(success)) {
            log.debug("幂等键设置成功（首次请求）: {}", idempotencyKey);
            return true;
        }

        log.info("幂等键已存在（重复请求）: {}", idempotencyKey);
        return false;
    }

    @Override
    public Optional<String> getResult(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return Optional.empty();
        }

        String redisKey = IDEMPOTENCY_KEY_PREFIX + idempotencyKey;
        String value = stringRedisTemplate.opsForValue().get(redisKey);

        if (value != null) {
            log.debug("获取幂等结果成功: key={}", idempotencyKey);
            return Optional.of(value);
        }

        log.debug("幂等键不存在: {}", idempotencyKey);
        return Optional.empty();
    }

    @Override
    public String generateKey(String operationType, Long communityId, Long targetId, String requestId) {
        // 格式: {operationType}:{communityId}:{targetId}:{requestId}
        return String.format("%s:%d:%d:%s", operationType, communityId, targetId, requestId);
    }
}
