package com.parking.service;

import com.parking.dto.ZombieVehicleQueryResponse;
import java.util.List;

/**
 * 僵尸车辆服务接口
 * Validates: Requirements 22.5, 22.9
 */
public interface ZombieVehicleService {

    /**
     * 查询僵尸车辆列表
     *
     * @param communityId 小区ID
     * @param status      状态筛选（可选，null 表示全部）
     * @return 僵尸车辆列表
     */
    List<ZombieVehicleQueryResponse> listZombieVehicles(Long communityId, String status);
}
