package com.parking.dto;

import lombok.Data;

/**
 * Visitor 月度配额查询响应
 * Validates: Requirements 10.5, 10.6, 10.7
 */
@Data
public class VisitorQuotaResponse {
    /** 月度总配额（分钟） */
    private long totalQuotaMinutes;
    /** 已使用时长（分钟） */
    private long usedMinutes;
    /** 剩余时长（分钟） */
    private long remainingMinutes;
    /** 是否接近超限（已使用 ≥ 60小时 = 3600分钟） */
    private boolean nearLimit;
    /** 查询月份（格式 yyyy-MM） */
    private String month;
}
