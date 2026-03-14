package com.parking.service;

import java.util.Map;

/**
 * 通知服务接口
 * 提供订阅消息推送功能，支持失败重试和定时补偿推送
 * 重试策略：最多3次，间隔分别为1分钟、5分钟、15分钟
 * Validates: Requirements 2.5, 2.6, 7.8
 */
public interface NotificationService {

    /**
     * 发送订阅消息
     * 如果发送失败，将消息存入 Redis 失败队列等待重试
     *
     * @param userId     目标用户 ID
     * @param templateId 消息模板 ID
     * @param data       消息数据（模板变量键值对）
     */
    void sendSubscriptionMessage(Long userId, String templateId, Map<String, String> data);

    /**
     * 重试失败的消息
     * 作为定时任务执行，定期检查 Redis 失败队列并重试发送
     * 超过最大重试次数（3次）的消息将被记录日志后丢弃
     */
    void retryFailedMessages();
}
