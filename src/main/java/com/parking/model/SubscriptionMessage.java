package com.parking.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 订阅消息模型
 * 用于封装微信订阅消息的发送信息，支持失败重试机制
 * Validates: Requirements 2.5, 2.6, 7.8
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubscriptionMessage {

    /**
     * 消息唯一标识
     */
    private String messageId;

    /**
     * 目标用户 ID
     */
    private Long targetUserId;

    /**
     * 消息模板 ID
     */
    private String templateId;

    /**
     * 消息数据（模板变量键值对）
     */
    private Map<String, String> data;

    /**
     * 已重试次数（初始为0）
     */
    private int retryCount;

    /**
     * 下次重试时间
     */
    private LocalDateTime nextRetryTime;

    /**
     * 消息创建时间
     */
    private LocalDateTime createTime;
}
