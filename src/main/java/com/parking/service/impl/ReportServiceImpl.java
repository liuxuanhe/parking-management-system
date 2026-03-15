package com.parking.service.impl;

import com.parking.dto.EntryTrendResponse;
import com.parking.dto.PeakHoursResponse;
import com.parking.dto.SpaceUsageResponse;
import com.parking.mapper.ParkingConfigMapper;
import com.parking.mapper.ParkingStatDailyMapper;
import com.parking.model.ParkingConfig;
import com.parking.model.ParkingStatDaily;
import com.parking.service.CacheService;
import com.parking.service.ReportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 报表服务实现
 * 查询 parking_stat_daily 预聚合表，使用 Redis 缓存（1小时过期）
 * Validates: Requirements 21.1, 21.2, 21.7
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReportServiceImpl implements ReportService {

    private final ParkingStatDailyMapper parkingStatDailyMapper;
    private final ParkingConfigMapper parkingConfigMapper;
    private final CacheService cacheService;

    /** 报表缓存过期时间：1小时 */
    private static final long CACHE_EXPIRE_HOURS = 1;

    @Override
    public EntryTrendResponse getEntryTrend(Long communityId, LocalDate startDate, LocalDate endDate) {
        // 1. 尝试从缓存获取
        String cacheKey = buildCacheKey(communityId, startDate, endDate);
        Optional<Object> cached = cacheService.get(cacheKey);
        if (cached.isPresent() && cached.get() instanceof EntryTrendResponse) {
            log.debug("入场趋势报表命中缓存: communityId={}", communityId);
            return (EntryTrendResponse) cached.get();
        }

        // 2. 从预聚合表查询
        List<ParkingStatDaily> dailyStats = parkingStatDailyMapper.selectByDateRange(
                communityId, startDate, endDate);

        // 3. 转换为响应 DTO
        EntryTrendResponse response = new EntryTrendResponse();
        List<EntryTrendResponse.TrendItem> items = dailyStats.stream()
                .map(this::toTrendItem)
                .collect(Collectors.toList());
        response.setItems(items);

        // 4. 写入缓存
        cacheService.set(cacheKey, response, CACHE_EXPIRE_HOURS, TimeUnit.HOURS);

        log.info("入场趋势报表查询完成: communityId={}, 日期范围={} ~ {}, 数据条数={}",
                communityId, startDate, endDate, items.size());
        return response;
    }

    /**
     * 将预聚合实体转换为趋势项
     */
    private EntryTrendResponse.TrendItem toTrendItem(ParkingStatDaily stat) {
        EntryTrendResponse.TrendItem item = new EntryTrendResponse.TrendItem();
        item.setDate(stat.getStatDate());
        item.setTotalEntryCount(stat.getTotalEntryCount() != null ? stat.getTotalEntryCount() : 0);
        item.setTotalExitCount(stat.getTotalExitCount() != null ? stat.getTotalExitCount() : 0);
        item.setPrimaryEntryCount(stat.getPrimaryEntryCount() != null ? stat.getPrimaryEntryCount() : 0);
        item.setVisitorEntryCount(stat.getVisitorEntryCount() != null ? stat.getVisitorEntryCount() : 0);
        return item;
    }

    /**
     * 构建报表缓存键
     */
    private String buildCacheKey(Long communityId, LocalDate startDate, LocalDate endDate) {
        return "report:entry_trend:" + communityId + ":" + startDate + ":" + endDate;
    }

    @Override
    public SpaceUsageResponse getSpaceUsage(Long communityId, LocalDate startDate, LocalDate endDate) {
        // 1. 尝试从缓存获取
        String cacheKey = "report:space_usage:" + communityId + ":" + startDate + ":" + endDate;
        Optional<Object> cached = cacheService.get(cacheKey);
        if (cached.isPresent() && cached.get() instanceof SpaceUsageResponse) {
            log.debug("车位使用率报表命中缓存: communityId={}", communityId);
            return (SpaceUsageResponse) cached.get();
        }

        // 2. 查询总车位数
        ParkingConfig config = parkingConfigMapper.selectByCommunityId(communityId);
        int totalSpaces = (config != null && config.getTotalSpaces() != null) ? config.getTotalSpaces() : 0;

        // 3. 从预聚合表查询
        List<ParkingStatDaily> dailyStats = parkingStatDailyMapper.selectByDateRange(
                communityId, startDate, endDate);

        // 4. 转换为响应 DTO
        SpaceUsageResponse response = new SpaceUsageResponse();
        response.setTotalSpaces(totalSpaces);
        List<SpaceUsageResponse.UsageItem> items = dailyStats.stream()
                .map(stat -> toUsageItem(stat, totalSpaces))
                .collect(Collectors.toList());
        response.setItems(items);

        // 5. 写入缓存
        cacheService.set(cacheKey, response, CACHE_EXPIRE_HOURS, TimeUnit.HOURS);

        log.info("车位使用率报表查询完成: communityId={}, 日期范围={} ~ {}, 数据条数={}",
                communityId, startDate, endDate, items.size());
        return response;
    }

    /**
     * 将预聚合实体转换为使用率项
     * 使用率估算：(入场数 + 出场数) / 2 / total_spaces * 100
     */
    private SpaceUsageResponse.UsageItem toUsageItem(ParkingStatDaily stat, int totalSpaces) {
        SpaceUsageResponse.UsageItem item = new SpaceUsageResponse.UsageItem();
        item.setDate(stat.getStatDate());
        int entry = stat.getTotalEntryCount() != null ? stat.getTotalEntryCount() : 0;
        int exit = stat.getTotalExitCount() != null ? stat.getTotalExitCount() : 0;
        int avgDuration = stat.getAvgParkingDuration() != null ? stat.getAvgParkingDuration() : 0;
        item.setTotalEntryCount(entry);
        item.setTotalExitCount(exit);
        item.setAvgParkingDuration(avgDuration);

        // 使用率：平均在场车辆数 / 总车位数
        // 平均在场车辆数近似为 (入场数 + 出场数) / 2
        if (totalSpaces > 0) {
            double avgInPark = (entry + exit) / 2.0;
            double rate = Math.min(avgInPark / totalSpaces * 100, 100.0);
            item.setUsageRate(Math.round(rate * 100.0) / 100.0);
        } else {
            item.setUsageRate(0.0);
        }
        return item;
    }

    @Override
    public PeakHoursResponse getPeakHours(Long communityId, LocalDate startDate, LocalDate endDate) {
        // 1. 尝试从缓存获取
        String cacheKey = "report:peak_hours:" + communityId + ":" + startDate + ":" + endDate;
        Optional<Object> cached = cacheService.get(cacheKey);
        if (cached.isPresent() && cached.get() instanceof PeakHoursResponse) {
            log.debug("峰值时段报表命中缓存: communityId={}", communityId);
            return (PeakHoursResponse) cached.get();
        }

        // 2. 从预聚合表查询
        List<ParkingStatDaily> dailyStats = parkingStatDailyMapper.selectByDateRange(
                communityId, startDate, endDate);

        // 3. 转换为响应 DTO
        PeakHoursResponse response = new PeakHoursResponse();
        List<PeakHoursResponse.PeakItem> items = dailyStats.stream()
                .map(this::toPeakItem)
                .collect(Collectors.toList());
        response.setItems(items);

        // 4. 写入缓存
        cacheService.set(cacheKey, response, CACHE_EXPIRE_HOURS, TimeUnit.HOURS);

        log.info("峰值时段报表查询完成: communityId={}, 日期范围={} ~ {}, 数据条数={}",
                communityId, startDate, endDate, items.size());
        return response;
    }

    /**
     * 将预聚合实体转换为峰值时段项
     */
    private PeakHoursResponse.PeakItem toPeakItem(ParkingStatDaily stat) {
        PeakHoursResponse.PeakItem item = new PeakHoursResponse.PeakItem();
        item.setDate(stat.getStatDate());
        item.setPeakHour(stat.getPeakHour() != null ? stat.getPeakHour() : 0);
        item.setPeakCount(stat.getPeakCount() != null ? stat.getPeakCount() : 0);
        return item;
    }
}
