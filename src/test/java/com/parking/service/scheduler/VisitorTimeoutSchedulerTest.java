package com.parking.service.scheduler;

import com.parking.mapper.VisitorSessionMapper;
import com.parking.model.VisitorSession;
import com.parking.service.NotificationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * VisitorTimeoutScheduler 单元测试
 * Validates: Requirements 8.9, 8.10
 */
@ExtendWith(MockitoExtension.class)
class VisitorTimeoutSchedulerTest {

    @Mock
    private VisitorSessionMapper visitorSessionMapper;

    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private VisitorTimeoutScheduler scheduler;

    private static final Long COMMUNITY_ID = 1001L;

    private VisitorSession createTimeoutSession(Long id, String carNumber) {
        VisitorSession session = new VisitorSession();
        session.setId(id);
        session.setCommunityId(COMMUNITY_ID);
        session.setHouseNo("1-101");
        session.setCarNumber(carNumber);
        session.setAccumulatedDuration(1500); // 超过1440分钟
        session.setStatus("in_park");
        session.setTimeoutNotified(0);
        return session;
    }

    @Test
    @DisplayName("检测到超时会话 - 发送通知并标记已通知")
    void detectTimeout_shouldNotifyAndMark() {
        VisitorSession session = createTimeoutSession(1L, "京A12345");
        when(visitorSessionMapper.selectTimeoutSessions()).thenReturn(List.of(session));

        scheduler.detectTimeoutSessions();

        verify(notificationService).sendSubscriptionMessage(
                eq(COMMUNITY_ID), eq("visitor_timeout"), any(Map.class));
        verify(visitorSessionMapper).updateTimeoutNotified(1L, 1);
    }

    @Test
    @DisplayName("无超时会话 - 不发送通知")
    void detectTimeout_shouldSkipWhenNoTimeout() {
        when(visitorSessionMapper.selectTimeoutSessions()).thenReturn(Collections.emptyList());

        scheduler.detectTimeoutSessions();

        verify(notificationService, never()).sendSubscriptionMessage(anyLong(), anyString(), any());
        verify(visitorSessionMapper, never()).updateTimeoutNotified(anyLong(), anyInt());
    }

    @Test
    @DisplayName("多个超时会话 - 逐个处理")
    void detectTimeout_shouldHandleMultipleSessions() {
        VisitorSession s1 = createTimeoutSession(1L, "京A11111");
        VisitorSession s2 = createTimeoutSession(2L, "京B22222");
        when(visitorSessionMapper.selectTimeoutSessions()).thenReturn(List.of(s1, s2));

        scheduler.detectTimeoutSessions();

        verify(notificationService, times(2)).sendSubscriptionMessage(
                eq(COMMUNITY_ID), eq("visitor_timeout"), any(Map.class));
        verify(visitorSessionMapper).updateTimeoutNotified(1L, 1);
        verify(visitorSessionMapper).updateTimeoutNotified(2L, 1);
    }

    @Test
    @DisplayName("通知发送失败 - 仍标记已通知并继续处理")
    void detectTimeout_shouldContinueOnNotificationFailure() {
        VisitorSession s1 = createTimeoutSession(1L, "京A11111");
        VisitorSession s2 = createTimeoutSession(2L, "京B22222");
        when(visitorSessionMapper.selectTimeoutSessions()).thenReturn(List.of(s1, s2));
        doThrow(new RuntimeException("通知失败")).when(notificationService)
                .sendSubscriptionMessage(eq(COMMUNITY_ID), eq("visitor_timeout"),
                        argThat(m -> "京A11111".equals(m.get("carNumber"))));

        scheduler.detectTimeoutSessions();

        // 第一个通知失败但仍标记
        verify(visitorSessionMapper).updateTimeoutNotified(1L, 1);
        // 第二个正常处理
        verify(visitorSessionMapper).updateTimeoutNotified(2L, 1);
    }
}
