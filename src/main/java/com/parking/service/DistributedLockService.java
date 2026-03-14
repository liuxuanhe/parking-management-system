package com.parking.service;

import java.util.function.Supplier;

/**
 * 分布式锁服务接口
 * 使用 Redis 实现分布式锁，确保并发场景下的数据一致性
 * - tryLock: 获取锁（超时时间5秒）
 * - unlock: 释放锁
 * - executeWithLock: 带锁执行业务逻辑
 * Validates: Requirements 4.10, 5.10
 */
public interface DistributedLockService {

    /**
     * 尝试获取分布式锁
     * 使用 Redis setIfAbsent 原子操作，value 为 UUID 防止误释放
     *
     * @param lockKey        锁的键名
     * @param timeoutSeconds 锁超时时间（秒）
     * @return true 表示获取锁成功，false 表示获取失败
     */
    boolean tryLock(String lockKey, int timeoutSeconds);

    /**
     * 释放分布式锁
     * 使用 Lua 脚本原子性地检查 value 并删除，防止释放其他线程的锁
     *
     * @param lockKey 锁的键名
     */
    void unlock(String lockKey);

    /**
     * 带锁执行业务逻辑
     * 封装 tryLock + 业务逻辑 + unlock 的模板方法
     * 锁获取失败时最多重试3次，每次间隔100ms
     * 重试全部失败后抛出 BusinessException
     *
     * @param lockKey  锁的键名
     * @param supplier 业务逻辑回调
     * @param <T>      返回值类型
     * @return 业务逻辑执行结果
     */
    <T> T executeWithLock(String lockKey, Supplier<T> supplier);
}
