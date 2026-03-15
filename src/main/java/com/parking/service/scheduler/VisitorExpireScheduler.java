package com.parking.service.scheduler;

import com.parking.mapper.VisitorAuthorizationMapper;
import com.parking.model.VisitorAuthorization;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Visitor 24小时未入场自动取消定时任务
 * 每小时执行一次，查询 status='approved_pending_activation' 且超过 expire_time 的授权
 * 更新状态为 canceled_no_entry
 * Validates: Requirements 8.3
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class VisitorExpireScheduler {

    private final VisitorAuthorizationMapper visitorAuthorizationMapper;

    /**
     * 每小时执行一次过期检测
     */
    @Scheduled(cron = "0 30 * * * ?")
    public void cancelExpiredAuthorizations() {
        log.info("开始执行 Visitor 未入场自动取消定时任务");

        List<VisitorAuthorization> expired =
                visitorAuthorizationMapper.selectExpiredPendingActivation();

        if (expired.isEmpty()) {
            log.info("未发现过期的 Visitor 授权");
            return;
        }

        int successCount = 0;
        int failCount = 0;

        for (VisitorAuthorization auth : expired) {
            try {
                visitorAuthorizationMapper.updateStatus(auth.getId(), "canceled_no_entry");
                successCount++;
                log.info("Visitor 授权已自动取消: authorizationId={}, carNumber={}",
                        auth.getId(), auth.getCarNumber());
            } catch (Exception e) {
                failCount++;
                log.error("取消过期授权失败: authorizationId={}", auth.getId(), e);
            }
        }

        log.info("Visitor 未入场自动取消完成: 总数={}, 成功={}, 失败={}",
                expired.size(), successCount, failCount);
    }
}
