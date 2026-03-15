package com.parking.service.scheduler;

import com.parking.mapper.ParkingCarRecordMapper;
import com.parking.mapper.ParkingConfigMapper;
import com.parking.mapper.ParkingStatDailyMapper;
import com.parking.model.ParkingStatDaily;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * 预聚合表定时回补任务
 * 每日凌晨2点执行，回补前一日完整统计数据到 parking_stat_daily 表
 * Validates: Requirements 21.4, 21.5
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StatReconciliationScheduler {

    private final ParkingCarRecordMapper parkingCarRecordMapper;
    private final ParkingConfigMapper parkingConfigMapper;
    private final ParkingStatDailyMapper parkingStatDailyMapper;

    private static final DateTimeFormatter TABLE_NAME_FORMAT = DateTimeFormatter.ofPattern("yyyyMM");

    /**
     * 每日凌晨2点执行回补任务
     */
    @Scheduled(cron = "0 0 2 * * ?")
    public void reconcileDailyStat() {
        LocalDate yesterday = LocalDate.now().minusDays(1);
        log.info("开始执行每日统计回补任务，回补日期: {}", yesterday);

        List<Long> communityIds = parkingConfigMapper.selectAllCommunityIds();
        if (communityIds.isEmpty()) {
            log.info("未找到任何小区配置，跳过回补");
            return;
        }

        int successCount = 0;
        int failCount = 0;

        for (Long communityId : communityIds) {
            try {
                reconcileForCommunity(communityId, yesterday);
                successCount++;
            } catch (Exception e) {
                failCount++;
                log.error("回补小区统计数据失败: communityId={}, date={}", communityId, yesterday, e);
            }
        }

        log.info("每日统计回补任务完成: 总小区数={}, 成功={}, 失败={}", communityIds.size(), successCount, failCount);
    }

    /**
     * 回补指定小区指定日期的统计数据
     * 从分表中重新统计入场/出场/峰值等数据，覆盖写入 parking_stat_daily
     */
    void reconcileForCommunity(Long communityId, LocalDate date) {
        String tableName = "parking_car_record_" + date.format(TABLE_NAME_FORMAT);
        LocalDateTime dayStart = date.atStartOfDay();
        LocalDateTime dayEnd = date.plusDays(1).atStartOfDay();

        // 1. 统计入场总数
        int totalEntry = parkingCarRecordMapper.countEntryByDate(tableName, communityId, dayStart, dayEnd);

        // 2. 统计 Primary 入场数和 Visitor 入场数
        int primaryEntry = parkingCarRecordMapper.countEntryByDateAndType(tableName, communityId, dayStart, dayEnd, "primary");
        int visitorEntry = parkingCarRecordMapper.countEntryByDateAndType(tableName, communityId, dayStart, dayEnd, "visitor");

        // 3. 统计出场总数
        int totalExit = parkingCarRecordMapper.countExitByDate(tableName, communityId, dayStart, dayEnd);

        // 4. 计算峰值时段
        int peakHour = 0;
        int peakCount = 0;
        List<Map<String, Object>> hourlyStats = parkingCarRecordMapper.countEntryByHour(tableName, communityId, dayStart, dayEnd);
        for (Map<String, Object> hourStat : hourlyStats) {
            int hour = ((Number) hourStat.get("hour_val")).intValue();
            int count = ((Number) hourStat.get("cnt")).intValue();
            if (count > peakCount) {
                peakCount = count;
                peakHour = hour;
            }
        }

        // 5. 计算平均停放时长
        Integer avgDuration = parkingCarRecordMapper.avgDurationByDate(tableName, communityId, dayStart, dayEnd);
        int avgParkingDuration = (avgDuration != null) ? avgDuration : 0;

        // 6. 统计异常出场数
        int exceptionExitCount = parkingCarRecordMapper.countExceptionExitByDate(tableName, communityId, dayStart, dayEnd);

        // 7. 组装统计实体并 upsert
        ParkingStatDaily stat = new ParkingStatDaily();
        stat.setCommunityId(communityId);
        stat.setStatDate(date);
        stat.setTotalEntryCount(totalEntry);
        stat.setTotalExitCount(totalExit);
        stat.setPrimaryEntryCount(primaryEntry);
        stat.setVisitorEntryCount(visitorEntry);
        stat.setPeakHour(peakHour);
        stat.setPeakCount(peakCount);
        stat.setAvgParkingDuration(avgParkingDuration);
        stat.setZombieVehicleCount(0); // 僵尸车辆由独立定时任务统计
        stat.setExceptionExitCount(exceptionExitCount);

        parkingStatDailyMapper.upsertDailyStat(stat);

        log.info("小区统计回补完成: communityId={}, date={}, 入场={}, 出场={}, 峰值时段={}时({}辆), 平均时长={}分钟",
                communityId, date, totalEntry, totalExit, peakHour, peakCount, avgParkingDuration);
    }
}
