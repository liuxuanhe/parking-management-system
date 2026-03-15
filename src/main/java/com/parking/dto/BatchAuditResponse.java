package com.parking.dto;

import lombok.Data;

import java.util.List;

/**
 * 批量审核响应 DTO
 * Validates: Requirements 23.4, 23.5
 */
@Data
public class BatchAuditResponse {

    /**
     * 成功数量
     */
    private int successCount;

    /**
     * 失败数量
     */
    private int failedCount;

    /**
     * 失败详情列表
     */
    private List<FailedItem> failedItems;

    @Data
    public static class FailedItem {
        /**
         * 记录 ID
         */
        private Long id;

        /**
         * 失败原因
         */
        private String reason;

        public FailedItem(Long id, String reason) {
            this.id = id;
            this.reason = reason;
        }
    }
}
