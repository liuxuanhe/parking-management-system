package com.parking.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 车牌查询响应 DTO
 * 返回指定 Data_Domain（community_id + house_no）下所有车牌信息
 * 敏感字段（如车主手机号）已执行脱敏处理
 * Validates: Requirements 11.1, 11.5
 */
@Data
public class VehicleQueryResponse {

    /** 车牌列表 */
    private List<VehicleItem> vehicles;

    /** 总数量 */
    private int total;

    /**
     * 单条车牌信息
     */
    @Data
    public static class VehicleItem {
        /** 车牌记录ID */
        private Long vehicleId;

        /** 车牌号 */
        private String carNumber;

        /** 车辆品牌 */
        private String carBrand;

        /** 车辆型号 */
        private String carModel;

        /** 车辆颜色 */
        private String carColor;

        /** 车牌状态（normal / primary） */
        private String status;

        /** 业主ID */
        private Long ownerId;

        /** 业主手机号（已脱敏） */
        private String ownerPhone;

        /** 创建时间 */
        private LocalDateTime createTime;
    }
}
