package com.parking.service.impl;

import com.parking.common.BusinessException;
import com.parking.common.CarPlateValidator;
import com.parking.common.ErrorCode;
import com.parking.common.RequestContext;
import com.parking.dto.VehicleAddRequest;
import com.parking.dto.VehicleAddResponse;
import com.parking.mapper.CarPlateMapper;
import com.parking.model.CarPlate;
import com.parking.service.CacheService;
import com.parking.service.VehicleService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 车辆管理服务实现类
 * 实现车牌添加流程：格式验证 → 数量验证 → 唯一性验证 → 创建记录 → 失效缓存 → 记录日志
 * Validates: Requirements 3.1, 3.2, 3.3, 3.4, 3.5, 3.9
 */
@Slf4j
@Service
public class VehicleServiceImpl implements VehicleService {

    /** 每个业主最多绑定的车牌数量 */
    private static final int MAX_CAR_PLATES_PER_OWNER = 5;

    private final CarPlateMapper carPlateMapper;
    private final CacheService cacheService;

    public VehicleServiceImpl(CarPlateMapper carPlateMapper,
                              CacheService cacheService) {
        this.carPlateMapper = carPlateMapper;
        this.cacheService = cacheService;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public VehicleAddResponse addVehicle(VehicleAddRequest request) {
        // 1. 验证车牌格式（Requirements 3.2）
        CarPlateValidator.validate(request.getCarNumber());

        // 2. 验证车牌数量 ≤ 5（Requirements 3.1, 3.5）
        int currentCount = carPlateMapper.countByOwner(
                request.getCommunityId(), request.getHouseNo(), request.getOwnerId());
        if (currentCount >= MAX_CAR_PLATES_PER_OWNER) {
            throw new BusinessException(ErrorCode.PARKING_3001);
        }

        // 3. 验证车牌在小区内未被其他业主绑定（Requirements 3.3）
        int boundCount = carPlateMapper.countByCarNumberInCommunity(
                request.getCommunityId(), request.getCarNumber(), request.getOwnerId());
        if (boundCount > 0) {
            throw new BusinessException(ErrorCode.PARAM_ERROR.getCode(),
                    "该车牌在当前小区内已被其他业主绑定");
        }

        // 4. 创建车牌记录，状态为 normal（Requirements 3.4）
        CarPlate carPlate = new CarPlate();
        carPlate.setCommunityId(request.getCommunityId());
        carPlate.setHouseNo(request.getHouseNo());
        carPlate.setOwnerId(request.getOwnerId());
        carPlate.setCarNumber(request.getCarNumber());
        carPlate.setCarBrand(request.getCarBrand());
        carPlate.setCarModel(request.getCarModel());
        carPlate.setCarColor(request.getCarColor());
        carPlate.setStatus("normal");
        carPlateMapper.insert(carPlate);

        log.info("车牌添加成功: vehicleId={}, carNumber={}, communityId={}, houseNo={}, ownerId={}",
                carPlate.getId(), request.getCarNumber(),
                request.getCommunityId(), request.getHouseNo(), request.getOwnerId());

        // 5. 失效缓存 vehicles:{communityId}:{houseNo}
        String cacheKey = cacheService.generateKey("vehicles",
                request.getCommunityId(), request.getHouseNo());
        cacheService.delete(cacheKey);
        log.info("缓存已失效: key={}", cacheKey);

        // 6. 记录操作日志预留（Requirements 3.9，后续在审计日志模块中完善）
        log.info("操作日志预留: 车牌添加, requestId={}, vehicleId={}, carNumber={}, communityId={}, houseNo={}",
                RequestContext.getRequestId(), carPlate.getId(), request.getCarNumber(),
                request.getCommunityId(), request.getHouseNo());

        // 7. 构建响应
        VehicleAddResponse response = new VehicleAddResponse();
        response.setVehicleId(carPlate.getId());
        response.setCarNumber(carPlate.getCarNumber());
        response.setStatus(carPlate.getStatus());
        response.setCreateTime(carPlate.getCreateTime());
        return response;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteVehicle(Long vehicleId) {
        // 1. 查询车牌记录
        CarPlate carPlate = carPlateMapper.selectById(vehicleId);
        if (carPlate == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR.getCode(), "车牌记录不存在");
        }

        // 2. 验证车辆当前不在场（Requirements 3.6, 3.7）
        // 简化实现：通过 status 判断，status 为 'entered' 视为在场
        // 后续入场模块完善后改为查询 parking_car_record 分表
        if ("entered".equals(carPlate.getStatus())) {
            throw new BusinessException(ErrorCode.PARKING_3002);
        }

        // 3. 执行逻辑删除（Requirements 3.8）
        int rows = carPlateMapper.logicalDelete(vehicleId);
        if (rows == 0) {
            throw new BusinessException(ErrorCode.PARAM_ERROR.getCode(), "车牌删除失败，记录可能已被删除");
        }

        log.info("车牌删除成功: vehicleId={}, carNumber={}, communityId={}, houseNo={}",
                vehicleId, carPlate.getCarNumber(),
                carPlate.getCommunityId(), carPlate.getHouseNo());

        // 4. 失效缓存 vehicles:{communityId}:{houseNo}（Requirements 3.9）
        String cacheKey = cacheService.generateKey("vehicles",
                carPlate.getCommunityId(), carPlate.getHouseNo());
        cacheService.delete(cacheKey);
        log.info("缓存已失效: key={}", cacheKey);

        // 5. 记录操作日志预留（Requirements 3.9，后续在审计日志模块中完善）
        log.info("操作日志预留: 车牌删除, requestId={}, vehicleId={}, carNumber={}, communityId={}, houseNo={}",
                RequestContext.getRequestId(), vehicleId, carPlate.getCarNumber(),
                carPlate.getCommunityId(), carPlate.getHouseNo());
    }
}
