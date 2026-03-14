package com.parking.service.impl;

import com.parking.common.BusinessException;
import com.parking.common.ErrorCode;
import com.parking.service.DistributedLockService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * 分布式锁服务实现类
 * 使用 Redis 的 setIfAbsent 原子操作获取锁，Lua 脚本原子释放锁
 * - tryLock: 使用 UUID 作为 value，防止误释放其他线程的锁
 * - unlock: 使用 Lua 脚本检查 value 后删除，保证原子性
 * - executeWithLock: 模板方法，自动获取锁、执行业务、释放锁
 * Validates: Requirements 4.10, 5.10
 */
@Slf4j
@Service
public class DistributedLockServiceImpl implements DistributedLockService {

    /** 锁在 Redis 中的键前缀 */
    private static final String LOCK_KEY_PREFIX = "lock:";

    /** 锁获取失败时的最大重试次数 */
    private static final int MAX_RETRY_COUNT = 3;

    /** 重试间隔（毫秒） */
    private static final long RETRY_INTERVAL_MS = 100;

    /** 默认锁超时时间（秒） */
    private static final int DEFAULT_TIMEOUT_SECONDS = 5;

    /**
     * Lua 脚本：原子性地检查锁的 value 并删除
     * 只有当锁的 value 与传入的 value 一致时才删除，防止释放其他线程的锁
     * 返回 1 表示删除成功，0 表示锁不存在或 value 不匹配
     */
    private static final String UNLOCK_LUA_SCRIPT =
            "if redis.call('get', KEYS[1]) == ARGV[1] then " +
            "    return redis.call('del', KEYS[1]) " +
            "else " +
            "    return 0 " +
            "end";

    private final StringRedisTemplate stringRedisTemplate;

    /** 线程本地存储，保存当前线程持有的锁的 value（UUID），用于释放时校验 */
    private final Map<String, String> lockValues = new ConcurrentHashMap<>();

    public DistributedLockServiceImpl(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public boolean tryLock(String lockKey, int timeoutSeconds) {
        String redisKey = LOCK_KEY_PREFIX + lockKey;
        String lockValue = UUID.randomUUID().toString();
        Duration timeout = Duration.ofSeconds(timeoutSeconds);

        // 使用 setIfAbsent 原子操作获取锁
        Boolean success = stringRedisTemplate.opsForValue()
                .setIfAbsent(redisKey, lockValue, timeout);

        if (Boolean.TRUE.equals(success)) {
            // 获取锁成功，保存 lockValue 用于释放时校验
            lockValues.put(redisKey, lockValue);
            log.debug("获取分布式锁成功: key={}, value={}, timeout={}s", lockKey, lockValue, timeoutSeconds);
            return true;
        }

        log.debug("获取分布式锁失败（锁已被占用）: key={}", lockKey);
        return false;
    }

    @Override
    public void unlock(String lockKey) {
        String redisKey = LOCK_KEY_PREFIX + lockKey;
        String lockValue = lockValues.remove(redisKey);

        if (lockValue == null) {
            log.warn("释放分布式锁失败（未找到锁的 value）: key={}", lockKey);
            return;
        }

        // 使用 Lua 脚本原子性地检查 value 并删除
        DefaultRedisScript<Long> script = new DefaultRedisScript<>(UNLOCK_LUA_SCRIPT, Long.class);
        Long result = stringRedisTemplate.execute(script, Collections.singletonList(redisKey), lockValue);

        if (result != null && result == 1L) {
            log.debug("释放分布式锁成功: key={}", lockKey);
        } else {
            log.warn("释放分布式锁失败（锁已过期或被其他线程持有）: key={}", lockKey);
        }
    }

    @Override
    public <T> T executeWithLock(String lockKey, Supplier<T> supplier) {
        // 带重试机制获取锁
        boolean locked = tryLockWithRetry(lockKey);
        if (!locked) {
            log.error("获取分布式锁失败（已重试{}次）: key={}", MAX_RETRY_COUNT, lockKey);
            throw new BusinessException(ErrorCode.LOCK_ACQUIRE_FAILED);
        }

        try {
            return supplier.get();
        } finally {
            unlock(lockKey);
        }
    }

    /**
     * 带重试机制的锁获取
     * 最多重试 MAX_RETRY_COUNT 次，每次间隔 RETRY_INTERVAL_MS 毫秒
     *
     * @param lockKey 锁的键名
     * @return true 表示获取成功，false 表示重试全部失败
     */
    private boolean tryLockWithRetry(String lockKey) {
        for (int attempt = 1; attempt <= MAX_RETRY_COUNT; attempt++) {
            if (tryLock(lockKey, DEFAULT_TIMEOUT_SECONDS)) {
                return true;
            }

            log.debug("获取分布式锁失败，准备第{}次重试: key={}", attempt, lockKey);

            if (attempt < MAX_RETRY_COUNT) {
                try {
                    Thread.sleep(RETRY_INTERVAL_MS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.warn("锁重试等待被中断: key={}", lockKey);
                    return false;
                }
            }
        }
        return false;
    }
}
