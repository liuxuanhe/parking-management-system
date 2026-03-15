package com.parking.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 入场记录查询响应 DTO
 * 包含记录列表、游标分页信息
 * Validates: Requirements 11.2, 16.2, 16.3
 */
@Data
public class ParkingRecordQueryResponse {

    /** 入场记录列表 */
    private List<RecordItem> records;

    /** 下一页游标，格式: "{enter_time}_{id}"；无更多数据时为 null */
    private String nextCursor;

    /** 是否还有更多数据 */
    private boolean hasMore;

    /**
     * 单条入场记录
     */
    @Data
    public static class RecordItem {
        /** 记录ID */
        private Long id;

        /** 车牌号（已脱敏） */
        private String carNumber;

        /** 车辆类型 */
        private String vehicleType;

        /** 入场时间 */
        private LocalDateTime enterTime;

        /** 出场时间 */
        private LocalDateTime exitTime;

        /** 停放时长（分钟） */
        private Integer duration;

        /** 记录状态 */
        private String status;
    }
}
