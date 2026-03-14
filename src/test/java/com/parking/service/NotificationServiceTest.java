package com.parking.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.parking.model.SubscriptionMessage;
import com.parking.service.impl.NotificationServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.RedisTemplate;

import java.time.LocalDateTime;
import java.util.Map;

import static com.parking.service.impl.NotificationServiceImpl.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * NotificationService 单元测试
 * Validates: Requirements 2.5, 2.6, 7.8
 */
@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private ListOperations<String, Object> listOperations;

    private ObjectMapper objectMapper;
    private NotificationServiceImpl notificationService;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        notificationService = spy(new NotificationServiceImpl(redisTemplate, objectMapper));
    }

    @Nested
    @DisplayName("sendSubscriptionMessage - 发送订阅消息")
    class SendSubscriptionMessageTests {

        @Test
        @DisplayName("发送成功时不应将消息加入失败队列")
        void send_success_shouldNotEnqueue() {
            doReturn(true).when(notificationService).doSend(anyLong(), anyString(), any());

            notificationService.sendSubscriptionMessage(1001L, "template_001", Map.of("key", "value"));

            verify(notificationService).doSend(1001L, "template_001", Map.of("key", "value"));
            verify(redisTemplate, never()).opsForList();
        }

        @Test
        @DisplayName("发送失败时应将消息加入 Redis 失败队列")
        void send_failure_shouldEnqueueToRedis() {
            doReturn(false).when(notificationService).doSend(anyLong(), anyString(), any());
            when(redisTemplate.opsForList()).thenReturn(listOperations);

            notificationService.sendSubscriptionMessage(2001L, "template_002", Map.of("result", "approved"));

            verify(redisTemplate.opsForList()).rightPush(eq(FAILED_MESSAGE_QUEUE_KEY), anyString());
        }

        @Test
        @DisplayName("发送失败时入队消息应包含正确的字段")
        void send_failure_enqueuedMessageShouldHaveCorrectFields() throws Exception {
            doReturn(false).when(notificationService).doSend(anyLong(), anyString(), any());
            when(redisTemplate.opsForList()).thenReturn(listOperations);

            ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);

            notificationService.sendSubscriptionMessage(3001L, "template_003", Map.of("status", "rejected"));

            verify(listOperations).rightPush(eq(FAILED_MESSAGE_QUEUE_KEY), captor.capture());

            String json = captor.getValue();
            SubscriptionMessage message = objectMapper.readValue(json, SubscriptionMessage.class);

            assertNotNull(message.getMessageId());
            assertEquals(3001L, message.getTargetUserId());
            assertEquals("template_003", message.getTemplateId());
            assertEquals("rejected", message.getData().get("status"));
            assertEquals(0, message.getRetryCount());
            assertNotNull(message.getNextRetryTime());
            assertNotNull(message.getCreateTime());
        }

        @Test
        @DisplayName("userId 为 null 时应跳过发送")
        void send_nullUserId_shouldSkip() {
            notificationService.sendSubscriptionMessage(null, "template_001", Map.of("key", "value"));

            verify(notificationService, never()).doSend(anyLong(), anyString(), any());
            verify(redisTemplate, never()).opsForList();
        }

        @Test
        @DisplayName("templateId 为 null 时应跳过发送")
        void send_nullTemplateId_shouldSkip() {
            notificationService.sendSubscriptionMessage(1001L, null, Map.of("key", "value"));

            verify(notificationService, never()).doSend(anyLong(), anyString(), any());
        }

        @Test
        @DisplayName("templateId 为空字符串时应跳过发送")
        void send_emptyTemplateId_shouldSkip() {
            notificationService.sendSubscriptionMessage(1001L, "", Map.of("key", "value"));

            verify(notificationService, never()).doSend(anyLong(), anyString(), any());
        }
    }

    @Nested
    @DisplayName("retryFailedMessages - 定时补偿推送")
    class RetryFailedMessagesTests {

        @Test
        @DisplayName("队列为空时应直接返回")
        void retry_emptyQueue_shouldReturn() {
            when(redisTemplate.opsForList()).thenReturn(listOperations);
            when(listOperations.size(FAILED_MESSAGE_QUEUE_KEY)).thenReturn(0L);

            notificationService.retryFailedMessages();

            verify(listOperations, never()).leftPop(anyString());
        }

        @Test
        @DisplayName("队列 size 为 null 时应直接返回")
        void retry_nullQueueSize_shouldReturn() {
            when(redisTemplate.opsForList()).thenReturn(listOperations);
            when(listOperations.size(FAILED_MESSAGE_QUEUE_KEY)).thenReturn(null);

            notificationService.retryFailedMessages();

            verify(listOperations, never()).leftPop(anyString());
        }

        @Test
        @DisplayName("到达重试时间且发送成功时不应重新入队")
        void retry_sendSuccess_shouldNotReEnqueue() throws Exception {
            when(redisTemplate.opsForList()).thenReturn(listOperations);
            when(listOperations.size(FAILED_MESSAGE_QUEUE_KEY)).thenReturn(1L);

            SubscriptionMessage message = SubscriptionMessage.builder()
                    .messageId("msg-001")
                    .targetUserId(1001L)
                    .templateId("template_001")
                    .data(Map.of("key", "value"))
                    .retryCount(0)
                    .nextRetryTime(LocalDateTime.now().minusMinutes(1))
                    .createTime(LocalDateTime.now().minusMinutes(2))
                    .build();
            String json = objectMapper.writeValueAsString(message);

            when(listOperations.leftPop(FAILED_MESSAGE_QUEUE_KEY)).thenReturn(json);
            doReturn(true).when(notificationService).doSend(anyLong(), anyString(), any());

            notificationService.retryFailedMessages();

            // 发送成功，不应重新入队（rightPush 只在 size 查询时调用过）
            verify(listOperations, never()).rightPush(eq(FAILED_MESSAGE_QUEUE_KEY), anyString());
        }

        @Test
        @DisplayName("到达重试时间但发送失败且未超过最大重试次数时应重新入队")
        void retry_sendFailure_belowMaxRetry_shouldReEnqueue() throws Exception {
            when(redisTemplate.opsForList()).thenReturn(listOperations);
            when(listOperations.size(FAILED_MESSAGE_QUEUE_KEY)).thenReturn(1L);

            SubscriptionMessage message = SubscriptionMessage.builder()
                    .messageId("msg-002")
                    .targetUserId(2001L)
                    .templateId("template_002")
                    .data(Map.of("status", "approved"))
                    .retryCount(0)
                    .nextRetryTime(LocalDateTime.now().minusMinutes(1))
                    .createTime(LocalDateTime.now().minusMinutes(2))
                    .build();
            String json = objectMapper.writeValueAsString(message);

            when(listOperations.leftPop(FAILED_MESSAGE_QUEUE_KEY)).thenReturn(json);
            doReturn(false).when(notificationService).doSend(anyLong(), anyString(), any());

            notificationService.retryFailedMessages();

            // 发送失败但未超过最大重试次数，应重新入队
            verify(listOperations).rightPush(eq(FAILED_MESSAGE_QUEUE_KEY), anyString());
        }

        @Test
        @DisplayName("到达重试时间但发送失败且已达最大重试次数时应丢弃消息")
        void retry_sendFailure_atMaxRetry_shouldDiscard() throws Exception {
            when(redisTemplate.opsForList()).thenReturn(listOperations);
            when(listOperations.size(FAILED_MESSAGE_QUEUE_KEY)).thenReturn(1L);

            // retryCount=2 表示已重试2次，本次是第3次（最后一次）
            SubscriptionMessage message = SubscriptionMessage.builder()
                    .messageId("msg-003")
                    .targetUserId(3001L)
                    .templateId("template_003")
                    .data(Map.of("status", "rejected"))
                    .retryCount(2)
                    .nextRetryTime(LocalDateTime.now().minusMinutes(1))
                    .createTime(LocalDateTime.now().minusMinutes(20))
                    .build();
            String json = objectMapper.writeValueAsString(message);

            when(listOperations.leftPop(FAILED_MESSAGE_QUEUE_KEY)).thenReturn(json);
            doReturn(false).when(notificationService).doSend(anyLong(), anyString(), any());

            notificationService.retryFailedMessages();

            // 已达最大重试次数，不应重新入队
            verify(listOperations, never()).rightPush(eq(FAILED_MESSAGE_QUEUE_KEY), anyString());
        }

        @Test
        @DisplayName("未到重试时间的消息应重新入队")
        void retry_notYetRetryTime_shouldReEnqueue() throws Exception {
            when(redisTemplate.opsForList()).thenReturn(listOperations);
            when(listOperations.size(FAILED_MESSAGE_QUEUE_KEY)).thenReturn(1L);

            SubscriptionMessage message = SubscriptionMessage.builder()
                    .messageId("msg-004")
                    .targetUserId(4001L)
                    .templateId("template_004")
                    .data(Map.of("key", "value"))
                    .retryCount(0)
                    .nextRetryTime(LocalDateTime.now().plusMinutes(10))
                    .createTime(LocalDateTime.now())
                    .build();
            String json = objectMapper.writeValueAsString(message);

            when(listOperations.leftPop(FAILED_MESSAGE_QUEUE_KEY)).thenReturn(json);

            notificationService.retryFailedMessages();

            // 未到重试时间，应重新入队，且不应尝试发送
            verify(listOperations).rightPush(eq(FAILED_MESSAGE_QUEUE_KEY), anyString());
            verify(notificationService, never()).doSend(anyLong(), anyString(), any());
        }
    }

    @Nested
    @DisplayName("重试策略常量验证")
    class RetryStrategyConstantsTests {

        @Test
        @DisplayName("最大重试次数应为3次")
        void maxRetryCount_shouldBeThree() {
            assertEquals(3, MAX_RETRY_COUNT);
        }

        @Test
        @DisplayName("重试间隔应为1分钟、5分钟、15分钟")
        void retryIntervals_shouldBeCorrect() {
            assertArrayEquals(new int[]{1, 5, 15}, RETRY_INTERVALS_MINUTES);
        }

        @Test
        @DisplayName("Redis 失败队列键应正确")
        void failedQueueKey_shouldBeCorrect() {
            assertEquals("notification:failed:queue", FAILED_MESSAGE_QUEUE_KEY);
        }
    }

    @Nested
    @DisplayName("deserializeMessage - 消息反序列化")
    class DeserializeMessageTests {

        @Test
        @DisplayName("有效 JSON 字符串应正确反序列化")
        void deserialize_validJson_shouldReturnMessage() throws Exception {
            SubscriptionMessage original = SubscriptionMessage.builder()
                    .messageId("msg-test")
                    .targetUserId(1001L)
                    .templateId("tpl-001")
                    .data(Map.of("key", "value"))
                    .retryCount(1)
                    .nextRetryTime(LocalDateTime.of(2024, 6, 15, 10, 30, 0))
                    .createTime(LocalDateTime.of(2024, 6, 15, 10, 0, 0))
                    .build();
            String json = objectMapper.writeValueAsString(original);

            SubscriptionMessage result = notificationService.deserializeMessage(json);

            assertNotNull(result);
            assertEquals("msg-test", result.getMessageId());
            assertEquals(1001L, result.getTargetUserId());
            assertEquals("tpl-001", result.getTemplateId());
            assertEquals(1, result.getRetryCount());
        }

        @Test
        @DisplayName("无效 JSON 应返回 null")
        void deserialize_invalidJson_shouldReturnNull() {
            SubscriptionMessage result = notificationService.deserializeMessage("not-a-json");
            assertNull(result);
        }
    }
}
