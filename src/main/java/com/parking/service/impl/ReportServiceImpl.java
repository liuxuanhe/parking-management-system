package com.parking.service.impl;

import com.parking.dto.EntryTrendResponse;
import com.parking.mapper.ParkingStatDailyMapper;
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
}
