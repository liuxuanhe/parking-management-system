package com.parking.service;

/**
 * 车位数量计算器接口
 * 负责计算 Available_Spaces、Visitor_Available_Spaces，以及检查车位是否充足
 * Primary 车辆与 Visitor 车辆共享车位池
 * Validates: Requirements 5.2, 5.3, 5.4, 9.1, 9.3, 9.4
 */
public interface ParkingSpaceCalculator {

    /**
     * 计算可用车位数
     * 公式: Available_Spaces = total_spaces - COUNT(status='entered')
     *
     * @param communityId 小区ID
     * @return 可用车位数（不会小于0）
     */
    int calculateAvailableSpaces(Long communityId);

    /**
     * 计算 Visitor 可开放车位数
     * 公式: Visitor_Available_Spaces = total_spaces - COUNT(status='entered')
     * 用于 Visitor 申请校验
     *
     * @param communityId 小区ID
     * @return Visitor 可开放车位数（不会小于0）
     */
    int calculateVisitorAvailableSpaces(Long communityId);

    /**
     * 检查车位是否充足
     * 使用分布式锁 lock:space:{communityId} 确保计算一致性
     *
     * @param communityId 小区ID
     * @param vehicleType 车辆类型（"primary" 或 "visitor"）
     * @return true 表示车位充足，false 表示车位不足
     */
    boolean checkSpaceAvailable(Long communityId, String vehicleType);
}
