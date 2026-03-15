package com.parking.service;

import com.parking.dto.EntryTrendResponse;
import com.parking.mapper.ParkingStatDailyMapper;
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
}
