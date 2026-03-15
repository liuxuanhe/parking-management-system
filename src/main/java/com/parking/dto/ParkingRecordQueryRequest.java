package com.parking.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

/**
 * 入场记录查询请求 DTO
 * 支持游标分页和时间范围查询
 * Validates: Requirements 11.2, 15.3, 15.4, 15.5, 16.1, 16.2, 16.3
 */
@Data
public class ParkingRecordQueryRequest {

    /** 小区ID（必填） */
    @NotNull(message = "communityId 不能为空")
    private Long communityId;

    /** 房屋号（必填） */
    @NotBlank(message = "houseNo 不能为空")
    private String houseNo;

    /** 查询开始时间（必填，防止全表扫描） */
    @NotNull(message = "startTime 不能为空")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime startTime;

    /** 查询结束时间（必填，防止全表扫描） */
    @NotNull(message = "endTime 不能为空")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime endTime;

    /** 游标（可选），格式: "{enter_time}_{id}" */
    private String cursor;

    /** 每页大小（可选，默认20，最大100） */
    @Min(value = 1, message = "pageSize 最小为 1")
    @Max(value = 100, message = "pageSize 最大为 100")
    private Integer pageSize;

    /**
     * 获取每页大小，默认20
     */
    public int getEffectivePageSize() {
        return (pageSize != null && pageSize > 0) ? Math.min(pageSize, 100) : 20;
    }
}
