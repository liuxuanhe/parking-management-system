package com.parking.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.parking.model.SubscriptionMessage;
import com.parking.service.NotificationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 通知服务实现类
 * 实现订阅消息推送功能，包含失败重试机制和定时补偿推送
 *
 * 重试策略：
 * - 最多重试3次
 * - 重试间隔：第1次 1分钟后，第2次 5分钟后，第3次 15分钟后
 * - 超过3次重试仍失败的消息记录日志后丢弃
 *
 * 定时补偿：
 * - 每小时扫描失败队列，对到达重试时间的消息进行补偿推送
 *
 * Validates: Requirements 2.5, 2.6, 7.8
 */
@Slf4j
@Service
public class NotificationServiceImpl implements NotificationService {

    /** Redis 失败消息队列键 */
    public static final String FAILED_MESSAGE_QUEUE_KEY = "notification:failed:queue";

    /** 最大重试次数 */
    public static final int MAX_RETRY_COUNT = 3;

    /** 重试间隔（分钟），分别对应第1、2、3次重试 */
    public static final int[] RETRY_INTERVALS_MINUTES = {1, 5, 15};

    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;

    public NotificationServiceImpl(RedisTemplate<String, Object> redisTemplate,
                                   ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public void sendSubscriptionMessage(Long userId, String templateId, Map<String, String> data) {
        if (userId == null || templateId == null || templateId.isBlank()) {
            log.warn("发送订阅消息参数无效: userId={}, templateId={}", userId, templateId);
            return;
        }

        log.info("发送订阅消息: userId={}, templateId={}", userId, templateId);

        boolean success = doSend(userId, templateId, data);

        if (!success) {
            // 发送失败，构建消息对象并存入 Redis 失败队列
            SubscriptionMessage message = SubscriptionMessage.builder()
                    .messageId(UUID.randomUUID().toString())
                    .targetUserId(userId)
                    .templateId(templateId)
                    .data(data)
                    .retryCount(0)
                    .nextRetryTime(LocalDateTime.now().plusMinutes(RETRY_INTERVALS_MINUTES[0]))
                    .createTime(LocalDateTime.now())
                    .build();

            enqueueFailedMessage(message);
            log.info("消息已加入失败队列等待重试: messageId={}", message.getMessageId());
        }
    }

    @Override
    @Scheduled(fixedRate = 3600000) // 每小时执行一次
    public void retryFailedMessages() {
        log.info("开始执行失败消息补偿推送任务");

        Long queueSize = redisTemplate.opsForList().size(FAILED_MESSAGE_QUEUE_KEY);
        if (queueSize == null || queueSize == 0) {
            log.debug("失败消息队列为空，跳过补偿推送");
            return;
        }

        log.info("失败消息队列中有 {} 条消息待处理", queueSize);

        // 取出所有消息进行处理
        List<SubscriptionMessage> retryLater = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();

        for (long i = 0; i < queueSize; i++) {
            Object raw = redisTemplate.opsForList().leftPop(FAILED_MESSAGE_QUEUE_KEY);
            if (raw == null) {
                break;
            }

            SubscriptionMessage message = deserializeMessage(raw);
            if (message == null) {
                log.warn("反序列化失败消息失败，丢弃该消息");
                continue;
            }

            processRetryMessage(message, now, retryLater);
        }

        // 将未到重试时间的消息重新放回队列
        for (SubscriptionMessage msg : retryLater) {
            enqueueFailedMessage(msg);
        }

        log.info("补偿推送任务完成，重新入队 {} 条消息", retryLater.size());
    }

    /**
     * 处理单条重试消息
     * 判断是否到达重试时间，到达则尝试发送，未到达则放回队列
     */
    private void processRetryMessage(SubscriptionMessage message, LocalDateTime now,
                                     List<SubscriptionMessage> retryLater) {
        // 未到重试时间，放回队列
        if (message.getNextRetryTime() != null && message.getNextRetryTime().isAfter(now)) {
            retryLater.add(message);
            return;
        }

        // 到达重试时间，尝试发送
        int currentRetry = message.getRetryCount() + 1;
        log.info("重试发送消息: messageId={}, 第{}次重试", message.getMessageId(), currentRetry);

        boolean success = doSend(message.getTargetUserId(), message.getTemplateId(), message.getData());

        if (success) {
            log.info("重试发送成功: messageId={}", message.getMessageId());
            return;
        }

        // 发送仍然失败
        if (currentRetry >= MAX_RETRY_COUNT) {
            // 超过最大重试次数，记录日志后丢弃
            log.error("消息发送失败已达最大重试次数({}次)，丢弃消息: messageId={}, userId={}, templateId={}",
                    MAX_RETRY_COUNT, message.getMessageId(),
                    message.getTargetUserId(), message.getTemplateId());
            return;
        }

        // 更新重试次数和下次重试时间，放回队列
        message.setRetryCount(currentRetry);
        message.setNextRetryTime(now.plusMinutes(RETRY_INTERVALS_MINUTES[currentRetry]));
        retryLater.add(message);
        log.info("消息重试失败，将在 {} 分钟后再次重试: messageId={}",
                RETRY_INTERVALS_MINUTES[currentRetry], message.getMessageId());
    }

    /**
     * 实际发送订阅消息（预留实现）
     * 当前仅记录日志，后续对接微信订阅消息 API 时替换此方法
     *
     * @return true 表示发送成功，false 表示发送失败
     */
    public boolean doSend(Long userId, String templateId, Map<String, String> data) {
        try {
            // 预留：实际对接微信订阅消息 API
            // 当前以日志记录模拟发送
            log.info("【模拟发送】订阅消息: userId={}, templateId={}, data={}", userId, templateId, data);
            return true;
        } catch (Exception e) {
            log.error("发送订阅消息异常: userId={}, templateId={}, error={}", userId, templateId, e.getMessage(), e);
            return false;
        }
    }

    /**
     * 将失败消息序列化后存入 Redis 队列
     */
    private void enqueueFailedMessage(SubscriptionMessage message) {
        try {
            String json = objectMapper.writeValueAsString(message);
            redisTemplate.opsForList().rightPush(FAILED_MESSAGE_QUEUE_KEY, json);
        } catch (JsonProcessingException e) {
            log.error("序列化失败消息异常: messageId={}", message.getMessageId(), e);
        }
    }

    /**
     * 从 Redis 队列中反序列化消息
     */
    public SubscriptionMessage deserializeMessage(Object raw) {
        try {
            if (raw instanceof String jsonStr) {
                return objectMapper.readValue(jsonStr, SubscriptionMessage.class);
            }
            // 如果 RedisTemplate 自动反序列化了，尝试转换
            String json = objectMapper.writeValueAsString(raw);
            return objectMapper.readValue(json, SubscriptionMessage.class);
        } catch (JsonProcessingException e) {
            log.error("反序列化消息失败: {}", e.getMessage(), e);
            return null;
        }
    }
}
