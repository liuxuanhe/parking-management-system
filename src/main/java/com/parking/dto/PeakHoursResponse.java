package com.parking.dto;

import lombok.Data;
import java.time.LocalDate;
import java.util.List;

/**
 * 峰值时段报表响应
 */
@Data
public class PeakHoursResponse {

    /**
     * 峰值时段数据列表
     */
    private List<PeakItem> items;

    @Data
    public static class PeakItem {
        /** 统计日期 */
        private LocalDate date;
        /** 峰值小时（0-23） */
        private int peakHour;
        /** 峰值时段入场数 */
        private int peakCount;
    }
}
