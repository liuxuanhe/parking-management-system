package com.parking.service.impl;

import com.parking.common.BusinessException;
import com.parking.common.CarPlateValidator;
import com.parking.common.ErrorCode;
import com.parking.common.RequestContext;
import com.parking.dto.VehicleAddRequest;
import com.parking.dto.VehicleAddResponse;
import com.parking.dto.VehicleQueryResponse;
import com.parking.mapper.CarPlateMapper;
import com.parking.mapper.OwnerMapper;
import com.parking.model.CarPlate;
import com.parking.model.Owner;
import com.parking.service.CacheService;
import com.parking.service.DistributedLockService;
import com.parking.service.MaskingService;
import com.parking.service.VehicleService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

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

    /** 车牌缓存资源名称 */
    private static final String VEHICLES_CACHE_RESOURCE = "vehicles";

    /** 车牌缓存过期时间：30分钟 */
    private static final long VEHICLES_CACHE_TTL = 30;
    private static final TimeUnit VEHICLES_CACHE_TTL_UNIT = TimeUnit.MINUTES;

    private final CarPlateMapper carPlateMapper;
    private final OwnerMapper ownerMapper;
    private final CacheService cacheService;
    private final MaskingService maskingService;
    private final DistributedLockService distributedLockService;

    public VehicleServiceImpl(CarPlateMapper carPlateMapper,
                              OwnerMapper ownerMapper,
                              CacheService cacheService,
                              MaskingService maskingService,
                              DistributedLockService distributedLockService) {
        this.carPlateMapper = carPlateMapper;
        this.ownerMapper = ownerMapper;
        this.cacheService = cacheService;
        this.maskingService = maskingService;
        this.distributedLockService = distributedLockService;
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

    @SuppressWarnings("unchecked")
    @Override
    public VehicleQueryResponse listVehicles(Long communityId, String houseNo) {
        // 1. 先查 Redis 缓存（Requirements 11.1, 11.5）
        String cacheKey = cacheService.generateKey(VEHICLES_CACHE_RESOURCE, communityId, houseNo);
        Optional<Object> cached = cacheService.get(cacheKey);
        if (cached.isPresent() && cached.get() instanceof VehicleQueryResponse cachedResponse) {
            log.debug("车牌查询命中缓存: communityId={}, houseNo={}", communityId, houseNo);
            return cachedResponse;
        }

        // 2. 缓存未命中，查询数据库
        List<CarPlate> carPlates = carPlateMapper.selectByHouse(communityId, houseNo);
        log.info("车牌查询数据库: communityId={}, houseNo={}, 数量={}", communityId, houseNo, carPlates.size());

        // 3. 构建响应并执行脱敏
        List<VehicleQueryResponse.VehicleItem> items = new ArrayList<>();
        for (CarPlate cp : carPlates) {
            VehicleQueryResponse.VehicleItem item = new VehicleQueryResponse.VehicleItem();
            item.setVehicleId(cp.getId());
            item.setCarNumber(cp.getCarNumber());
            item.setCarBrand(cp.getCarBrand());
            item.setCarModel(cp.getCarModel());
            item.setCarColor(cp.getCarColor());
            item.setStatus(cp.getStatus());
            item.setOwnerId(cp.getOwnerId());
            item.setCreateTime(cp.getCreateTime());

            // 查询业主手机号并脱敏（Requirements 17.1, 17.8）
            Owner owner = ownerMapper.selectById(cp.getOwnerId());
            if (owner != null && owner.getPhoneNumber() != null) {
                item.setOwnerPhone(maskingService.maskPhoneNumber(owner.getPhoneNumber()));
            }

            items.add(item);
        }

        VehicleQueryResponse response = new VehicleQueryResponse();
        response.setVehicles(items);
        response.setTotal(items.size());

        // 4. 将结果写入缓存（30分钟过期）
        cacheService.set(cacheKey, response, VEHICLES_CACHE_TTL, VEHICLES_CACHE_TTL_UNIT);
        log.debug("车牌查询结果已缓存: key={}, ttl={}分钟", cacheKey, VEHICLES_CACHE_TTL);

        return response;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void setPrimaryVehicle(Long vehicleId, Long communityId, String houseNo) {
        // 使用分布式锁 + 行级锁双重保护（Requirements 4.10）
        String lockKey = "lock:primary:" + communityId + ":" + houseNo;
        distributedLockService.executeWithLock(lockKey, () -> {
            doSetPrimary(vehicleId, communityId, houseNo);
            return null;
        });
    }

    /**
     * 在分布式锁保护下执行 Primary 切换的核心逻辑
     */
    private void doSetPrimary(Long vehicleId, Long communityId, String houseNo) {
        // 1. 行级锁查询该 Data_Domain 下所有车辆（Requirements 4.10）
        List<CarPlate> vehicles = carPlateMapper.selectForUpdate(communityId, houseNo);
        if (vehicles.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR.getCode(), "该房屋号下无车牌记录");
        }

        // 2. 验证目标车辆存在且属于该 Data_Domain
        CarPlate targetVehicle = vehicles.stream()
                .filter(v -> v.getId().equals(vehicleId))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.PARAM_ERROR.getCode(),
                        "目标车辆不存在或不属于该房屋号"));

        // 3. 如果目标车辆已经是 Primary，直接返回（幂等处理）
        if ("primary".equals(targetVehicle.getStatus())) {
            log.info("目标车辆已是 Primary，无需切换: vehicleId={}", vehicleId);
            return;
        }

        // 4. 验证所有车辆均不在场（Requirements 4.2, 4.3）
        // 简化实现：status 为 'entered' 视为在场
        // 后续入场模块完善后改为查询 parking_car_record 分表
        for (CarPlate v : vehicles) {
            if ("entered".equals(v.getStatus())) {
                throw new BusinessException(ErrorCode.PARKING_4001);
            }
        }

        // 5. 验证原 Primary 车辆无未完成入场申请（Requirements 4.5, 4.6）
        // 预留：后续入场模块完善后，查询 visitor_application 表中 status='submitted' 的记录
        // 当前简化为日志记录
        log.info("未完成入场申请检查预留: communityId={}, houseNo={}", communityId, houseNo);

        // 6. 将旧 Primary 车辆状态更新为 normal（Requirements 4.8）
        int normalizedCount = carPlateMapper.updatePrimaryToNormal(communityId, houseNo);
        log.info("旧 Primary 车辆已更新为 normal: communityId={}, houseNo={}, 更新数量={}",
                communityId, houseNo, normalizedCount);

        // 7. 将目标车辆状态更新为 primary（Requirements 4.8）
        int updatedCount = carPlateMapper.updateStatusToPrimary(vehicleId);
        if (updatedCount == 0) {
            throw new BusinessException(ErrorCode.PARAM_ERROR.getCode(), "设置 Primary 车辆失败");
        }

        log.info("Primary 车辆设置成功: vehicleId={}, carNumber={}, communityId={}, houseNo={}",
                vehicleId, targetVehicle.getCarNumber(), communityId, houseNo);

        // 8. 失效缓存 vehicles:{communityId}:{houseNo}（Requirements 4.9）
        String cacheKey = cacheService.generateKey(VEHICLES_CACHE_RESOURCE, communityId, houseNo);
        cacheService.delete(cacheKey);
        log.info("缓存已失效: key={}", cacheKey);

        // 9. 记录操作日志预留（后续在审计日志模块中完善）
        log.info("操作日志预留: Primary 车辆设置, requestId={}, vehicleId={}, carNumber={}, communityId={}, houseNo={}",
                RequestContext.getRequestId(), vehicleId, targetVehicle.getCarNumber(),
                communityId, houseNo);
    }
}
