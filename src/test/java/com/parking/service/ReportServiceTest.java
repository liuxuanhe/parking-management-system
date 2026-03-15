package com.parking.service;

import com.parking.dto.EntryTrendResponse;
import com.parking.dto.PeakHoursResponse;
import com.parking.dto.SpaceUsageResponse;
import com.parking.mapper.ParkingConfigMapper;
import com.parking.mapper.ParkingStatDailyMapper;
import com.parking.model.ParkingConfig;
import com.parking.model.ParkingStatDaily;
import com.parking.service.impl.ReportServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * ReportServiceImpl 单元测试
 */
@ExtendWith(MockitoExtension.class)
class ReportServiceTest {

    @Mock
    private ParkingStatDailyMapper parkingStatDailyMapper;

    @Mock
    private ParkingConfigMapper parkingConfigMapper;

    @Mock
    private CacheService cacheService;

    @InjectMocks
    private ReportServiceImpl reportService;

    private static final Long COMMUNITY_ID = 1001L;
    private static final LocalDate START_DATE = LocalDate.of(2026, 1, 1);
    private static final LocalDate END_DATE = LocalDate.of(2026, 1, 31);

    @Test
    @DisplayName("入场趋势 - 缓存命中直接返回")
    void getEntryTrend_cacheHit() {
        EntryTrendResponse cached = new EntryTrendResponse();
        cached.setItems(Collections.emptyList());
        when(cacheService.get(anyString())).thenReturn(Optional.of(cached));

        EntryTrendResponse result = reportService.getEntryTrend(COMMUNITY_ID, START_DATE, END_DATE);

        assertSame(cached, result);
        verify(parkingStatDailyMapper, never()).selectByDateRange(any(), any(), any());
    }

    @Test
    @DisplayName("入场趋势 - 缓存未命中从数据库查询并写入缓存")
    void getEntryTrend_cacheMiss() {
        when(cacheService.get(anyString())).thenReturn(Optional.empty());

        ParkingStatDaily stat1 = buildStat(LocalDate.of(2026, 1, 1), 50, 45, 35, 15);
        ParkingStatDaily stat2 = buildStat(LocalDate.of(2026, 1, 2), 60, 55, 40, 20);
        when(parkingStatDailyMapper.selectByDateRange(COMMUNITY_ID, START_DATE, END_DATE))
                .thenReturn(List.of(stat1, stat2));

        EntryTrendResponse result = reportService.getEntryTrend(COMMUNITY_ID, START_DATE, END_DATE);

        assertNotNull(result);
        assertEquals(2, result.getItems().size());

        EntryTrendResponse.TrendItem item1 = result.getItems().get(0);
        assertEquals(LocalDate.of(2026, 1, 1), item1.getDate());
        assertEquals(50, item1.getTotalEntryCount());
        assertEquals(45, item1.getTotalExitCount());
        assertEquals(35, item1.getPrimaryEntryCount());
        assertEquals(15, item1.getVisitorEntryCount());

        // 验证写入缓存
        verify(cacheService).set(anyString(), eq(result), eq(1L), any());
    }

    @Test
    @DisplayName("入场趋势 - 无数据返回空列表")
    void getEntryTrend_noData() {
        when(cacheService.get(anyString())).thenReturn(Optional.empty());
        when(parkingStatDailyMapper.selectByDateRange(COMMUNITY_ID, START_DATE, END_DATE))
                .thenReturn(Collections.emptyList());

        EntryTrendResponse result = reportService.getEntryTrend(COMMUNITY_ID, START_DATE, END_DATE);

        assertNotNull(result);
        assertTrue(result.getItems().isEmpty());
        verify(cacheService).set(anyString(), eq(result), eq(1L), any());
    }

    @Test
    @DisplayName("入场趋势 - null 字段转换为0")
    void getEntryTrend_nullFieldsConvertToZero() {
        when(cacheService.get(anyString())).thenReturn(Optional.empty());

        ParkingStatDaily stat = new ParkingStatDaily();
        stat.setStatDate(LocalDate.of(2026, 1, 1));
        stat.setCommunityId(COMMUNITY_ID);
        // 所有计数字段为 null
        when(parkingStatDailyMapper.selectByDateRange(COMMUNITY_ID, START_DATE, END_DATE))
                .thenReturn(List.of(stat));

        EntryTrendResponse result = reportService.getEntryTrend(COMMUNITY_ID, START_DATE, END_DATE);

        assertEquals(1, result.getItems().size());
        EntryTrendResponse.TrendItem item = result.getItems().get(0);
        assertEquals(0, item.getTotalEntryCount());
        assertEquals(0, item.getTotalExitCount());
        assertEquals(0, item.getPrimaryEntryCount());
        assertEquals(0, item.getVisitorEntryCount());
    }

    private ParkingStatDaily buildStat(LocalDate date, int entry, int exit, int primary, int visitor) {
        ParkingStatDaily stat = new ParkingStatDaily();
        stat.setCommunityId(COMMUNITY_ID);
        stat.setStatDate(date);
        stat.setTotalEntryCount(entry);
        stat.setTotalExitCount(exit);
        stat.setPrimaryEntryCount(primary);
        stat.setVisitorEntryCount(visitor);
        return stat;
    }

    // ========== getSpaceUsage 测试 ==========

    @Test
    @DisplayName("车位使用率 - 缓存命中直接返回")
    void getSpaceUsage_cacheHit() {
        SpaceUsageResponse cached = new SpaceUsageResponse();
        cached.setTotalSpaces(100);
        when(cacheService.get(anyString())).thenReturn(Optional.of(cached));

        SpaceUsageResponse result = reportService.getSpaceUsage(COMMUNITY_ID, START_DATE, END_DATE);

        assertSame(cached, result);
        verify(parkingConfigMapper, never()).selectByCommunityId(any());
        verify(parkingStatDailyMapper, never()).selectByDateRange(any(), any(), any());
    }

    @Test
    @DisplayName("车位使用率 - 缓存未命中正常计算")
    void getSpaceUsage_cacheMiss() {
        when(cacheService.get(anyString())).thenReturn(Optional.empty());

        ParkingConfig config = new ParkingConfig();
        config.setTotalSpaces(200);
        when(parkingConfigMapper.selectByCommunityId(COMMUNITY_ID)).thenReturn(config);

        ParkingStatDaily stat1 = buildStatWithDuration(LocalDate.of(2026, 1, 1), 100, 80, 120);
        ParkingStatDaily stat2 = buildStatWithDuration(LocalDate.of(2026, 1, 2), 150, 140, 90);
        when(parkingStatDailyMapper.selectByDateRange(COMMUNITY_ID, START_DATE, END_DATE))
                .thenReturn(List.of(stat1, stat2));

        SpaceUsageResponse result = reportService.getSpaceUsage(COMMUNITY_ID, START_DATE, END_DATE);

        assertNotNull(result);
        assertEquals(200, result.getTotalSpaces());
        assertEquals(2, result.getItems().size());

        // stat1: avg = (100+80)/2 = 90, rate = 90/200*100 = 45.0
        SpaceUsageResponse.UsageItem item1 = result.getItems().get(0);
        assertEquals(100, item1.getTotalEntryCount());
        assertEquals(80, item1.getTotalExitCount());
        assertEquals(120, item1.getAvgParkingDuration());
        assertEquals(45.0, item1.getUsageRate(), 0.01);

        // stat2: avg = (150+140)/2 = 145, rate = 145/200*100 = 72.5
        SpaceUsageResponse.UsageItem item2 = result.getItems().get(1);
        assertEquals(72.5, item2.getUsageRate(), 0.01);

        verify(cacheService).set(anyString(), eq(result), eq(1L), any());
    }

    @Test
    @DisplayName("车位使用率 - totalSpaces 为 0 时使用率为 0")
    void getSpaceUsage_zeroTotalSpaces() {
        when(cacheService.get(anyString())).thenReturn(Optional.empty());

        ParkingConfig config = new ParkingConfig();
        config.setTotalSpaces(0);
        when(parkingConfigMapper.selectByCommunityId(COMMUNITY_ID)).thenReturn(config);

        ParkingStatDaily stat = buildStatWithDuration(LocalDate.of(2026, 1, 1), 50, 40, 60);
        when(parkingStatDailyMapper.selectByDateRange(COMMUNITY_ID, START_DATE, END_DATE))
                .thenReturn(List.of(stat));

        SpaceUsageResponse result = reportService.getSpaceUsage(COMMUNITY_ID, START_DATE, END_DATE);

        assertEquals(0, result.getTotalSpaces());
        assertEquals(0.0, result.getItems().get(0).getUsageRate(), 0.01);
    }

    @Test
    @DisplayName("车位使用率 - config 为 null 时 totalSpaces 默认 0")
    void getSpaceUsage_nullConfig() {
        when(cacheService.get(anyString())).thenReturn(Optional.empty());
        when(parkingConfigMapper.selectByCommunityId(COMMUNITY_ID)).thenReturn(null);
        when(parkingStatDailyMapper.selectByDateRange(COMMUNITY_ID, START_DATE, END_DATE))
                .thenReturn(List.of(buildStatWithDuration(LocalDate.of(2026, 1, 1), 30, 20, 45)));

        SpaceUsageResponse result = reportService.getSpaceUsage(COMMUNITY_ID, START_DATE, END_DATE);

        assertEquals(0, result.getTotalSpaces());
        assertEquals(0.0, result.getItems().get(0).getUsageRate(), 0.01);
    }

    @Test
    @DisplayName("车位使用率 - 使用率上限为 100%")
    void getSpaceUsage_rateCapAt100() {
        when(cacheService.get(anyString())).thenReturn(Optional.empty());

        ParkingConfig config = new ParkingConfig();
        config.setTotalSpaces(10);
        when(parkingConfigMapper.selectByCommunityId(COMMUNITY_ID)).thenReturn(config);

        // avg = (500+400)/2 = 450, rate = 450/10*100 = 4500 → 应被截断为 100
        ParkingStatDaily stat = buildStatWithDuration(LocalDate.of(2026, 1, 1), 500, 400, 60);
        when(parkingStatDailyMapper.selectByDateRange(COMMUNITY_ID, START_DATE, END_DATE))
                .thenReturn(List.of(stat));

        SpaceUsageResponse result = reportService.getSpaceUsage(COMMUNITY_ID, START_DATE, END_DATE);

        assertEquals(100.0, result.getItems().get(0).getUsageRate(), 0.01);
    }

    private ParkingStatDaily buildStatWithDuration(LocalDate date, int entry, int exit, int avgDuration) {
        ParkingStatDaily stat = new ParkingStatDaily();
        stat.setCommunityId(COMMUNITY_ID);
        stat.setStatDate(date);
        stat.setTotalEntryCount(entry);
        stat.setTotalExitCount(exit);
        stat.setAvgParkingDuration(avgDuration);
        return stat;
    }

    // ========== getPeakHours 测试 ==========

    @Test
    @DisplayName("峰值时段 - 缓存命中直接返回")
    void getPeakHours_cacheHit() {
        PeakHoursResponse cached = new PeakHoursResponse();
        cached.setItems(Collections.emptyList());
        when(cacheService.get(anyString())).thenReturn(Optional.of(cached));

        PeakHoursResponse result = reportService.getPeakHours(COMMUNITY_ID, START_DATE, END_DATE);

        assertSame(cached, result);
        verify(parkingStatDailyMapper, never()).selectByDateRange(any(), any(), any());
    }

    @Test
    @DisplayName("峰值时段 - 缓存未命中正常查询")
    void getPeakHours_cacheMiss() {
        when(cacheService.get(anyString())).thenReturn(Optional.empty());

        ParkingStatDaily stat1 = buildStatWithPeak(LocalDate.of(2026, 1, 1), 8, 120);
        ParkingStatDaily stat2 = buildStatWithPeak(LocalDate.of(2026, 1, 2), 18, 95);
        when(parkingStatDailyMapper.selectByDateRange(COMMUNITY_ID, START_DATE, END_DATE))
                .thenReturn(List.of(stat1, stat2));

        PeakHoursResponse result = reportService.getPeakHours(COMMUNITY_ID, START_DATE, END_DATE);

        assertNotNull(result);
        assertEquals(2, result.getItems().size());
        assertEquals(8, result.getItems().get(0).getPeakHour());
        assertEquals(120, result.getItems().get(0).getPeakCount());
        assertEquals(18, result.getItems().get(1).getPeakHour());
        assertEquals(95, result.getItems().get(1).getPeakCount());

        verify(cacheService).set(anyString(), eq(result), eq(1L), any());
    }

    @Test
    @DisplayName("峰值时段 - null 字段转换为 0")
    void getPeakHours_nullFields() {
        when(cacheService.get(anyString())).thenReturn(Optional.empty());

        ParkingStatDaily stat = new ParkingStatDaily();
        stat.setStatDate(LocalDate.of(2026, 1, 1));
        stat.setCommunityId(COMMUNITY_ID);
        // peakHour 和 peakCount 为 null
        when(parkingStatDailyMapper.selectByDateRange(COMMUNITY_ID, START_DATE, END_DATE))
                .thenReturn(List.of(stat));

        PeakHoursResponse result = reportService.getPeakHours(COMMUNITY_ID, START_DATE, END_DATE);

        assertEquals(1, result.getItems().size());
        assertEquals(0, result.getItems().get(0).getPeakHour());
        assertEquals(0, result.getItems().get(0).getPeakCount());
    }

    @Test
    @DisplayName("峰值时段 - 无数据返回空列表")
    void getPeakHours_noData() {
        when(cacheService.get(anyString())).thenReturn(Optional.empty());
        when(parkingStatDailyMapper.selectByDateRange(COMMUNITY_ID, START_DATE, END_DATE))
                .thenReturn(Collections.emptyList());

        PeakHoursResponse result = reportService.getPeakHours(COMMUNITY_ID, START_DATE, END_DATE);

        assertNotNull(result);
        assertTrue(result.getItems().isEmpty());
    }

    private ParkingStatDaily buildStatWithPeak(LocalDate date, int peakHour, int peakCount) {
        ParkingStatDaily stat = new ParkingStatDaily();
        stat.setCommunityId(COMMUNITY_ID);
        stat.setStatDate(date);
        stat.setPeakHour(peakHour);
        stat.setPeakCount(peakCount);
        return stat;
    }
}
