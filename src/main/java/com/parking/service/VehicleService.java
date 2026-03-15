package com.parking.service;

import com.parking.dto.VehicleAddRequest;
import com.parking.dto.VehicleAddResponse;

/**
 * 车辆管理服务接口
 * Validates: Requirements 3.1, 3.2, 3.3, 3.4, 3.5, 3.9
 */
public interface VehicleService {

    /**
     * 添加车牌
     * 验证车牌格式 → 验证车牌数量 ≤ 5 → 验证车牌在小区内未被其他业主绑定 → 创建车牌记录 → 失效缓存 → 记录操作日志
     *
     * @param request 添加车牌请求
     * @return 添加车牌响应
     */
    VehicleAddResponse addVehicle(VehicleAddRequest request);

    /**
     * 删除车牌（逻辑删除）
     * 查询车牌 → 验证车辆不在场 → 逻辑删除 → 失效缓存 → 记录操作日志
     * Validates: Requirements 3.6, 3.7, 3.8, 3.9
     *
     * @param vehicleId 车牌记录ID
     */
    void deleteVehicle(Long vehicleId);
}
