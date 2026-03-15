package com.parking.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 批量审核请求 DTO
 * 适用于业主批量审核和 Visitor 批量审批
 * Validates: Requirements 23.1, 23.2, 23.7
 */
@Data
public class BatchAuditRequest {

    /**
     * 待审核的记录 ID 列表，最多50条
     */
    @NotEmpty(message = "审核记录列表不能为空")
    @Size(max = 50, message = "每次最多处理50条记录")
    private List<Long> ids;

    /**
     * 审核动作: "approve" 或 "reject"
     */
    @NotBlank(message = "审批结果不能为空")
    private String action;

    /**
     * 驳回原因（驳回时必填）
     */
    private String rejectReason;

    /**
     * 请求ID，用于幂等键
     */
    private String requestId;
}
