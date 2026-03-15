package com.parking.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * Visitor 申请响应 DTO
 */
@Data
public class VisitorApplyResponse {
    private Long applicationId;
    private String status;
    private LocalDateTime createTime;
}
