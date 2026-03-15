package com.parking.service.scheduler;

import com.parking.mapper.ParkingCarRecordMapper;
import com.parking.mapper.ParkingConfigMapper;
import com.parking.mapper.ParkingStatDailyMapper;
import com.parking.model.ParkingStatDaily;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * StatReconciliationScheduler 单元测试
 * 验证每日统计回补逻辑的正确性
 */
@ExtendWith(MockitoExtension.class)
class StatReconciliationSchedulerTest {

    @Mock
    private ParkingCarRecordMapper parkingCarRecordMapper;

    @Mock
    private ParkingConfigMapper parkingConfigMapper;

    @Mock
    private ParkingStatDailyMapper parkingStatDailyMapper;

    @InjectMocks
    private StatReconciliationScheduler scheduler;

    private static final Long COMMUNITY_ID = 1001L;
    private static final LocalDate YESTERDAY = LocalDate.of(2026, 3, 14);

    @Test
    @DisplayName("回补任务 - 无小区配置时跳过")
    void reconcileDailyStat_noCommunities_skip() {
        when(parkingConfigMapper.selectAllCommunityIds()).thenReturn(Collections.emptyList());

        scheduler.reconcileDailyStat();

        verify(parkingStatDailyMapper, never()).upsertDailyStat(any());
    }

    @Test
    @DisplayName("回补单个小区 - 正常统计数据写入")
    void reconcileForCommunity_normalData() {
        String tableName = "parking_car_record_202603";
        LocalDateTime dayStart = YESTERDAY.atStartOfDay();
        LocalDateTime dayEnd = YESTERDAY.plusDays(1).atStartOfDay();

        // 模拟入场总数
        when(parkingCarRecordMapper.countEntryByDate(eq(tableName), eq(COMMUNITY_ID), eq(dayStart), eq(dayEnd)))
                .thenReturn(50);
        // 模拟 Primary 入场数
        when(parkingCarRecordMapper.countEntryByDateAndType(eq(tableName), eq(COMMUNITY_ID), eq(dayStart), eq(dayEnd), eq("primary")))
                .thenReturn(35);
        // 模拟 Visitor 入场数
        when(parkingCarRecordMapper.countEntryByDateAndType(eq(tableName), eq(COMMUNITY_ID), eq(dayStart), eq(dayEnd), eq("visitor")))
                .thenReturn(15);
        // 模拟出场总数
        when(parkingCarRecordMapper.countExitByDate(eq(tableName), eq(COMMUNITY_ID), eq(dayStart), eq(dayEnd)))
                .thenReturn(45);
        // 模拟峰值时段
        when(parkingCarRecordMapper.countEntryByHour(eq(tableName), eq(COMMUNITY_ID), eq(dayStart), eq(dayEnd)))
                .thenReturn(List.of(
                        Map.of("hour_val", 8, "cnt", 12),
                        Map.of("hour_val", 9, "cnt", 20),
                        Map.of("hour_val", 18, "cnt", 15)
                ));
        // 模拟平均停放时长
        when(parkingCarRecordMapper.avgDurationByDate(eq(tableName), eq(COMMUNITY_ID), eq(dayStart), eq(dayEnd)))
                .thenReturn(180);
        // 模拟异常出场数
        when(parkingCarRecordMapper.countExceptionExitByDate(eq(tableName), eq(COMMUNITY_ID), eq(dayStart), eq(dayEnd)))
                .thenReturn(2);

        scheduler.reconcileForCommunity(COMMUNITY_ID, YESTERDAY);

        // 验证 upsert 调用
        ArgumentCaptor<ParkingStatDaily> captor = ArgumentCaptor.forClass(ParkingStatDaily.class);
        verify(parkingStatDailyMapper).upsertDailyStat(captor.capture());

        ParkingStatDaily stat = captor.getValue();
        assertEquals(COMMUNITY_ID, stat.getCommunityId());
        assertEquals(YESTERDAY, stat.getStatDate());
        assertEquals(50, stat.getTotalEntryCount());
        assertEquals(45, stat.getTotalExitCount());
        assertEquals(35, stat.getPrimaryEntryCount());
        assertEquals(15, stat.getVisitorEntryCount());
        assertEquals(9, stat.getPeakHour()); // 9点入场最多(20辆)
        assertEquals(20, stat.getPeakCount());
        assertEquals(180, stat.getAvgParkingDuration());
        assertEquals(0, stat.getZombieVehicleCount());
        assertEquals(2, stat.getExceptionExitCount());
    }

    @Test
    @DisplayName("回补单个小区 - 无数据时写入零值")
    void reconcileForCommunity_noData() {
        String tableName = "parking_car_record_202603";
        LocalDateTime dayStart = YESTERDAY.atStartOfDay();
        LocalDateTime dayEnd = YESTERDAY.plusDays(1).atStartOfDay();

        when(parkingCarRecordMapper.countEntryByDate(eq(tableName), eq(COMMUNITY_ID), eq(dayStart), eq(dayEnd)))
                .thenReturn(0);
        when(parkingCarRecordMapper.countEntryByDateAndType(eq(tableName), eq(COMMUNITY_ID), eq(dayStart), eq(dayEnd), eq("primary")))
                .thenReturn(0);
        when(parkingCarRecordMapper.countEntryByDateAndType(eq(tableName), eq(COMMUNITY_ID), eq(dayStart), eq(dayEnd), eq("visitor")))
                .thenReturn(0);
        when(parkingCarRecordMapper.countExitByDate(eq(tableName), eq(COMMUNITY_ID), eq(dayStart), eq(dayEnd)))
                .thenReturn(0);
        when(parkingCarRecordMapper.countEntryByHour(eq(tableName), eq(COMMUNITY_ID), eq(dayStart), eq(dayEnd)))
                .thenReturn(Collections.emptyList());
        when(parkingCarRecordMapper.avgDurationByDate(eq(tableName), eq(COMMUNITY_ID), eq(dayStart), eq(dayEnd)))
                .thenReturn(null);
        when(parkingCarRecordMapper.countExceptionExitByDate(eq(tableName), eq(COMMUNITY_ID), eq(dayStart), eq(dayEnd)))
                .thenReturn(0);

        scheduler.reconcileForCommunity(COMMUNITY_ID, YESTERDAY);

        ArgumentCaptor<ParkingStatDaily> captor = ArgumentCaptor.forClass(ParkingStatDaily.class);
        verify(parkingStatDailyMapper).upsertDailyStat(captor.capture());

        ParkingStatDaily stat = captor.getValue();
        assertEquals(0, stat.getTotalEntryCount());
        assertEquals(0, stat.getTotalExitCount());
        assertEquals(0, stat.getPeakHour());
        assertEquals(0, stat.getPeakCount());
        assertEquals(0, stat.getAvgParkingDuration());
    }

    @Test
    @DisplayName("回补多个小区 - 单个失败不影响其他小区")
    void reconcileDailyStat_partialFailure() {
        Long communityId2 = 1002L;
        when(parkingConfigMapper.selectAllCommunityIds()).thenReturn(List.of(COMMUNITY_ID, communityId2));

        // 第一个小区抛异常
        when(parkingCarRecordMapper.countEntryByDate(anyString(), eq(COMMUNITY_ID), any(), any()))
                .thenThrow(new RuntimeException("数据库异常"));

        // 第二个小区正常
        when(parkingCarRecordMapper.countEntryByDate(anyString(), eq(communityId2), any(), any()))
                .thenReturn(10);
        when(parkingCarRecordMapper.countEntryByDateAndType(anyString(), eq(communityId2), any(), any(), eq("primary")))
                .thenReturn(8);
        when(parkingCarRecordMapper.countEntryByDateAndType(anyString(), eq(communityId2), any(), any(), eq("visitor")))
                .thenReturn(2);
        when(parkingCarRecordMapper.countExitByDate(anyString(), eq(communityId2), any(), any()))
                .thenReturn(9);
        when(parkingCarRecordMapper.countEntryByHour(anyString(), eq(communityId2), any(), any()))
                .thenReturn(Collections.emptyList());
        when(parkingCarRecordMapper.avgDurationByDate(anyString(), eq(communityId2), any(), any()))
                .thenReturn(60);
        when(parkingCarRecordMapper.countExceptionExitByDate(anyString(), eq(communityId2), any(), any()))
                .thenReturn(0);

        scheduler.reconcileDailyStat();

        // 第二个小区的统计应该正常写入
        verify(parkingStatDailyMapper, times(1)).upsertDailyStat(any());
    }

    @Test
    @DisplayName("峰值时段计算 - 多个时段相同入场数取第一个")
    void reconcileForCommunity_peakHourTieBreaker() {
        String tableName = "parking_car_record_202603";
        LocalDateTime dayStart = YESTERDAY.atStartOfDay();
        LocalDateTime dayEnd = YESTERDAY.plusDays(1).atStartOfDay();

        when(parkingCarRecordMapper.countEntryByDate(eq(tableName), eq(COMMUNITY_ID), eq(dayStart), eq(dayEnd)))
                .thenReturn(30);
        when(parkingCarRecordMapper.countEntryByDateAndType(anyString(), eq(COMMUNITY_ID), any(), any(), anyString()))
                .thenReturn(15);
        when(parkingCarRecordMapper.countExitByDate(eq(tableName), eq(COMMUNITY_ID), eq(dayStart), eq(dayEnd)))
                .thenReturn(28);
        // 8点和18点入场数相同
        when(parkingCarRecordMapper.countEntryByHour(eq(tableName), eq(COMMUNITY_ID), eq(dayStart), eq(dayEnd)))
                .thenReturn(List.of(
                        Map.of("hour_val", 8, "cnt", 15),
                        Map.of("hour_val", 18, "cnt", 15)
                ));
        when(parkingCarRecordMapper.avgDurationByDate(eq(tableName), eq(COMMUNITY_ID), eq(dayStart), eq(dayEnd)))
                .thenReturn(120);
        when(parkingCarRecordMapper.countExceptionExitByDate(eq(tableName), eq(COMMUNITY_ID), eq(dayStart), eq(dayEnd)))
                .thenReturn(0);

        scheduler.reconcileForCommunity(COMMUNITY_ID, YESTERDAY);

        ArgumentCaptor<ParkingStatDaily> captor = ArgumentCaptor.forClass(ParkingStatDaily.class);
        verify(parkingStatDailyMapper).upsertDailyStat(captor.capture());

        ParkingStatDaily stat = captor.getValue();
        // 第一个遇到的最大值时段为8点
        assertEquals(8, stat.getPeakHour());
        assertEquals(15, stat.getPeakCount());
    }
}
