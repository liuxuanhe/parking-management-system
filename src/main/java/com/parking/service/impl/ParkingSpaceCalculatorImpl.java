package com.parking.service.impl;

import com.parking.common.BusinessException;
import com.parking.common.ErrorCode;
import com.parking.mapper.ParkingConfigMapper;
import com.parking.model.ParkingConfig;
import com.parking.service.DistributedLockService;
import com.parking.service.ParkingSpaceCalculator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 车位数量计算器实现类
 * 使用分布式锁确保并发场景下车位计算的一致性
 * Primary 车辆与 Visitor 车辆共享车位池
 * Validates: Requirements 5.2, 5.3, 5.4, 9.1, 9.3, 9.4
 */
@Slf4j
@Service
public class ParkingSpaceCalculatorImpl implements ParkingSpaceCalculator {

    /** 车位计算分布式锁前缀 */
    private static final String LOCK_KEY_PREFIX = "lock:space:";

    private final ParkingConfigMapper parkingConfigMapper;
    private final DistributedLockService distributedLockService;

    public ParkingSpaceCalculatorImpl(ParkingConfigMapper parkingConfigMapper,
                                      DistributedLockService distributedLockService) {
        this.parkingConfigMapper = parkingConfigMapper;
        this.distributedLockService = distributedLockService;
    }

    @Override
    public int calculateAvailableSpaces(Long communityId) {
        // 获取停车场配置
        ParkingConfig config = getConfigOrThrow(communityId);
        int totalSpaces = config.getTotalSpaces();

        // 查询当前在场车辆数
        int enteredCount = parkingConfigMapper.countEnteredVehicles(communityId);

        // 计算可用车位数，确保不为负数
        int availableSpaces = Math.max(0, totalSpaces - enteredCount);

        log.debug("计算可用车位数: communityId={}, totalSpaces={}, enteredCount={}, availableSpaces={}",
                communityId, totalSpaces, enteredCount, availableSpaces);

        return availableSpaces;
    }

    @Override
    public int calculateVisitorAvailableSpaces(Long communityId) {
        // Visitor 可开放车位数与 Available_Spaces 使用相同公式
        // Primary 车辆与 Visitor 车辆共享车位池
        ParkingConfig config = getConfigOrThrow(communityId);
        int totalSpaces = config.getTotalSpaces();

        int enteredCount = parkingConfigMapper.countEnteredVehicles(communityId);

        int visitorAvailableSpaces = Math.max(0, totalSpaces - enteredCount);

        log.debug("计算 Visitor 可开放车位数: communityId={}, totalSpaces={}, enteredCount={}, visitorAvailableSpaces={}",
                communityId, totalSpaces, enteredCount, visitorAvailableSpaces);

        return visitorAvailableSpaces;
    }

    @Override
    public boolean checkSpaceAvailable(Long communityId, String vehicleType) {
        String lockKey = LOCK_KEY_PREFIX + communityId;

        // 使用分布式锁确保车位计算一致性
        return distributedLockService.executeWithLock(lockKey, () -> {
            int availableSpaces;

            if ("visitor".equalsIgnoreCase(vehicleType)) {
                // Visitor 车辆使用 Visitor 可开放车位数校验
                availableSpaces = calculateVisitorAvailableSpaces(communityId);
                if (availableSpaces <= 0) {
                    log.warn("Visitor 可开放车位不足: communityId={}, availableSpaces={}",
                            communityId, availableSpaces);
                    return false;
                }
            } else {
                // Primary 车辆及其他类型使用 Available_Spaces 校验
                availableSpaces = calculateAvailableSpaces(communityId);
                if (availableSpaces <= 0) {
                    log.warn("车位不足: communityId={}, vehicleType={}, availableSpaces={}",
                            communityId, vehicleType, availableSpaces);
                    return false;
                }
            }

            log.info("车位充足: communityId={}, vehicleType={}, availableSpaces={}",
                    communityId, vehicleType, availableSpaces);
            return true;
        });
    }

    /**
     * 获取停车场配置，不存在则抛出异常
     */
    private ParkingConfig getConfigOrThrow(Long communityId) {
        ParkingConfig config = parkingConfigMapper.selectByCommunityId(communityId);
        if (config == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR.getCode(), "停车场配置不存在");
        }
        return config;
    }
}
