package com.parking.dto;

import lombok.Data;
import java.time.LocalDate;
import java.util.List;

/**
 * 入场趋势报表响应
 */
@Data
public class EntryTrendResponse {

    /**
     * 趋势数据列表
     */
    private List<TrendItem> items;

    @Data
    public static class TrendItem {
        /** 统计日期 */
        private LocalDate date;
        /** 入场总数 */
        private int totalEntryCount;
        /** 出场总数 */
        private int totalExitCount;
        /** Primary 入场数 */
        private int primaryEntryCount;
        /** Visitor 入场数 */
        private int visitorEntryCount;
    }
}
