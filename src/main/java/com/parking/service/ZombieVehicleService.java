package com.parking.service;

import com.parking.dto.ZombieVehicleHandleRequest;
import com.parking.dto.ZombieVehicleQueryResponse;
import java.util.List;

/**
 * 僵尸车辆服务接口
 * Validates: Requirements 22.5, 22.9
 */
public interface ZombieVehicleService {

    /**
     * 查询僵尸车辆列表
     */
    List<ZombieVehicleQueryResponse> listZombieVehicles(Long communityId, String status);

    /**
     * 处理僵尸车辆
     */
    void handleZombieVehicle(Long zombieId, ZombieVehicleHandleRequest request, Long adminId);
}
