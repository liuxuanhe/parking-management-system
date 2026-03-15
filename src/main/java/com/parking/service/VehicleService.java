package com.parking.service;

import com.parking.dto.VehicleAddRequest;
import com.parking.dto.VehicleAddResponse;
import com.parking.dto.VehicleQueryResponse;

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

    /**
     * 查询车牌列表
     * 根据 Data_Domain（community_id + house_no）查询所有未删除车牌
     * 支持同房屋号多业主场景，返回该房屋号下所有业主的车牌
     * 使用 Redis 缓存（30分钟过期），对敏感信息执行脱敏处理
     * Validates: Requirements 11.1, 11.5
     *
     * @param communityId 小区ID
     * @param houseNo 房屋号
     * @return 车牌查询响应
     */
    VehicleQueryResponse listVehicles(Long communityId, String houseNo);

    /**
     * 设置 Primary 车辆
     * 获取分布式锁 → 行级锁查询 → 验证所有车辆不在场 → 验证无未完成入场申请 →
     * 旧 Primary 改为 normal → 新车辆设为 primary → 失效缓存 → 记录操作日志
     * Validates: Requirements 4.1, 4.2, 4.3, 4.4, 4.5, 4.6, 4.7, 4.8, 4.9, 4.10
     *
     * @param vehicleId   目标车牌记录ID
     * @param communityId 小区ID
     * @param houseNo     房屋号
     */
    void setPrimaryVehicle(Long vehicleId, Long communityId, String houseNo);
}
