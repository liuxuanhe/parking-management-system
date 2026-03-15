package com.parking.service.scheduler;

import com.parking.mapper.VisitorSessionMapper;
import com.parking.model.VisitorSession;
import com.parking.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Visitor 超时检测定时任务
 * 每小时执行一次，检测累计停放时长 ≥ 1440 分钟（24小时）的会话
 * 发送超时提醒给业主和物业
 * Validates: Requirements 8.9, 8.10
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class VisitorTimeoutScheduler {

    private final VisitorSessionMapper visitorSessionMapper;
    private final NotificationService notificationService;

    /**
     * 每小时执行一次超时检测
     */
    @Scheduled(cron = "0 0 * * * ?")
    public void detectTimeoutSessions() {
        log.info("开始执行 Visitor 超时检测定时任务");

        List<VisitorSession> timeoutSessions = visitorSessionMapper.selectTimeoutSessions();
        if (timeoutSessions.isEmpty()) {
            log.info("未发现超时 Visitor 会话");
            return;
        }

        int successCount = 0;
        int failCount = 0;

        for (VisitorSession session : timeoutSessions) {
            try {
                handleTimeoutSession(session);
                successCount++;
            } catch (Exception e) {
                failCount++;
                log.error("处理超时会话失败: sessionId={}, carNumber={}",
                        session.getId(), session.getCarNumber(), e);
            }
        }

        log.info("Visitor 超时检测完成: 总数={}, 成功={}, 失败={}",
                timeoutSessions.size(), successCount, failCount);
    }

    /**
     * 处理单个超时会话：发送通知并标记已通知
     */
    void handleTimeoutSession(VisitorSession session) {
        // 1. 发送超时提醒给业主
        try {
            notificationService.sendSubscriptionMessage(
                    session.getCommunityId(),
                    "visitor_timeout",
                    Map.of(
                            "carNumber", session.getCarNumber(),
                            "houseNo", session.getHouseNo(),
                            "accumulatedDuration", String.valueOf(session.getAccumulatedDuration())
                    )
            );
        } catch (Exception e) {
            log.warn("发送 Visitor 超时通知失败: sessionId={}", session.getId(), e);
        }

        // 2. 标记已通知
        visitorSessionMapper.updateTimeoutNotified(session.getId(), 1);

        log.info("Visitor 超时提醒已发送: sessionId={}, carNumber={}, 累计时长={}分钟",
                session.getId(), session.getCarNumber(), session.getAccumulatedDuration());
    }
}
