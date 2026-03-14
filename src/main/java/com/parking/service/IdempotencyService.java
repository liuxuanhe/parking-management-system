package com.parking.service;

import java.util.Optional;

/**
 * 幂等性服务接口
 * 通过 Redis 存储幂等键，防止并发重复操作
 * 幂等键格式: {operationType}:{communityId}:{targetId}:{requestId}
 * Validates: Requirements 2.8, 5.7, 7.10
 */
public interface IdempotencyService {

    /**
     * 检查并设置幂等键（原子操作）
     * 如果键不存在则设置并返回 true（首次请求），否则返回 false（重复请求）
     *
     * @param idempotencyKey 幂等键
     * @param result         操作结果（JSON 序列化值）
     * @param expireSeconds  过期时间（秒）
     * @return true 表示首次请求（键设置成功），false 表示重复请求（键已存在）
     */
    boolean checkAndSet(String idempotencyKey, String result, int expireSeconds);

    /**
     * 获取幂等键对应的操作结果
     * 用于重复请求时返回之前的结果
     *
     * @param idempotencyKey 幂等键
     * @return 操作结果（JSON 字符串），如果键不存在则返回 empty
     */
    Optional<String> getResult(String idempotencyKey);

    /**
     * 生成幂等键
     * 格式: {operationType}:{communityId}:{targetId}:{requestId}
     *
     * @param operationType 操作类型（如 owner_audit, visitor_audit, vehicle_entry）
     * @param communityId   小区 ID
     * @param targetId      目标 ID
     * @param requestId     请求 ID
     * @return 生成的幂等键
     */
    String generateKey(String operationType, Long communityId, Long targetId, String requestId);
}
