package com.parking.service.impl;

import com.parking.service.CacheService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * 缓存管理服务实现类
 * 使用 RedisTemplate 实现通用缓存操作，支持：
 * - 缓存键生成策略: {resource}:{communityId}:{houseNo}
 * - 按前缀批量失效缓存（如某个小区的所有报表缓存）
 * - 预定义缓存过期时间常量
 * Validates: Requirements 21.7, 21.8
 */
@Slf4j
@Service
public class CacheServiceImpl implements CacheService {

    /** 缓存键前缀 */
    private static final String CACHE_KEY_PREFIX = "cache:";

    /** 报表缓存过期时间: 1小时 */
    public static final long REPORT_TTL = 1;
    public static final TimeUnit REPORT_TTL_UNIT = TimeUnit.HOURS;

    /** IP 白名单缓存过期时间: 1小时 */
    public static final long IP_WHITELIST_TTL = 1;
    public static final TimeUnit IP_WHITELIST_TTL_UNIT = TimeUnit.HOURS;

    /** 热点数据缓存过期时间: 30分钟 */
    public static final long HOTSPOT_TTL = 30;
    public static final TimeUnit HOTSPOT_TTL_UNIT = TimeUnit.MINUTES;

    private final RedisTemplate<String, Object> redisTemplate;

    public CacheServiceImpl(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public Optional<Object> get(String key) {
        if (key == null || key.isBlank()) {
            log.warn("缓存键为空，跳过查询");
            return Optional.empty();
        }

        String redisKey = CACHE_KEY_PREFIX + key;
        Object value = redisTemplate.opsForValue().get(redisKey);

        if (value != null) {
            log.debug("缓存命中: key={}", key);
            return Optional.of(value);
        }

        log.debug("缓存未命中: key={}", key);
        return Optional.empty();
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> Optional<T> get(String key, Class<T> type) {
        Optional<Object> value = get(key);
        if (value.isPresent() && type.isInstance(value.get())) {
            return Optional.of((T) value.get());
        }
        return Optional.empty();
    }

    @Override
    public void set(String key, Object value, long timeout, TimeUnit unit) {
        if (key == null || key.isBlank()) {
            log.warn("缓存键为空，跳过设置");
            return;
        }

        String redisKey = CACHE_KEY_PREFIX + key;
        redisTemplate.opsForValue().set(redisKey, value, timeout, unit);
        log.debug("缓存设置成功: key={}, timeout={} {}", key, timeout, unit);
    }

    @Override
    public boolean delete(String key) {
        if (key == null || key.isBlank()) {
            log.warn("缓存键为空，跳过删除");
            return false;
        }

        String redisKey = CACHE_KEY_PREFIX + key;
        Boolean result = redisTemplate.delete(redisKey);
        boolean deleted = Boolean.TRUE.equals(result);

        if (deleted) {
            log.debug("缓存删除成功: key={}", key);
        } else {
            log.debug("缓存键不存在，无需删除: key={}", key);
        }

        return deleted;
    }

    @Override
    public boolean exists(String key) {
        if (key == null || key.isBlank()) {
            return false;
        }

        String redisKey = CACHE_KEY_PREFIX + key;
        Boolean result = redisTemplate.hasKey(redisKey);
        return Boolean.TRUE.equals(result);
    }

    @Override
    public long deleteByPrefix(String prefix) {
        if (prefix == null || prefix.isBlank()) {
            log.warn("缓存前缀为空，跳过批量删除");
            return 0;
        }

        String pattern = CACHE_KEY_PREFIX + prefix + "*";
        Set<String> keys = redisTemplate.keys(pattern);

        if (keys == null || keys.isEmpty()) {
            log.debug("未找到匹配前缀的缓存键: prefix={}", prefix);
            return 0;
        }

        Long deletedCount = redisTemplate.delete(keys);
        long count = deletedCount != null ? deletedCount : 0;
        log.info("批量删除缓存完成: prefix={}, 删除数量={}", prefix, count);
        return count;
    }

    @Override
    public String generateKey(String resource, Long communityId, String houseNo) {
        // 格式: {resource}:{communityId}:{houseNo}
        return String.format("%s:%d:%s", resource, communityId, houseNo);
    }

    @Override
    public String generateKey(String resource, Long communityId) {
        // 格式: {resource}:{communityId}
        return String.format("%s:%d", resource, communityId);
    }
}
