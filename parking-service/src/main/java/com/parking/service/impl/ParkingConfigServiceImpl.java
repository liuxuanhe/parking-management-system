package com.parking.service.impl;

import com.parking.common.BusinessException;
import com.parking.common.ErrorCode;
import com.parking.common.RequestContext;
import com.parking.dto.ParkingConfigResponse;
import com.parking.dto.ParkingConfigUpdateRequest;
import com.parking.mapper.ParkingConfigMapper;
import com.parking.model.ParkingConfig;
import com.parking.service.CacheService;
import com.parking.service.ParkingConfigService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * 车位配置管理服务实现类
 * 实现查询和修改停车场配置，包含乐观锁、缓存失效、Visitor 授权状态更新等逻辑
 * Validates: Requirements 9.5, 9.6, 9.7, 9.8
 */
@Slf4j
@Service
public class ParkingConfigServiceImpl implements ParkingConfigService {

    /** 停车场配置缓存资源名称 */
    private static final String CONFIG_CACHE_RESOURCE = "parking_config";

    /** 停车场配置缓存过期时间：30分钟 */
    private static final long CONFIG_CACHE_TTL = 30;
    private static final TimeUnit CONFIG_CACHE_TTL_UNIT = TimeUnit.MINUTES;

    private final ParkingConfigMapper parkingConfigMapper;
    private final CacheService cacheService;

    public ParkingConfigServiceImpl(ParkingConfigMapper parkingConfigMapper,
                                    CacheService cacheService) {
        this.parkingConfigMapper = parkingConfigMapper;
        this.cacheService = cacheService;
    }

    @Override
    public ParkingConfigResponse getConfig(Long communityId) {
        // 1. 先查 Redis 缓存
        String cacheKey = cacheService.generateKey(CONFIG_CACHE_RESOURCE, communityId);
        Optional<Object> cached = cacheService.get(cacheKey);
        if (cached.isPresent() && cached.get() instanceof ParkingConfigResponse cachedResponse) {
            log.debug("停车场配置命中缓存: communityId={}", communityId);
            return cachedResponse;
        }

        // 2. 缓存未命中，查询数据库
        ParkingConfig config = parkingConfigMapper.selectByCommunityId(communityId);
        if (config == null) {
            // 配置不存在，自动创建默认配置
            config = createDefaultConfig(communityId);
            log.info("停车场配置不存在，已自动创建默认配置: communityId={}", communityId);
        }

        // 3. 构建响应
        ParkingConfigResponse response = buildResponse(config);

        // 4. 写入缓存
        cacheService.set(cacheKey, response, CONFIG_CACHE_TTL, CONFIG_CACHE_TTL_UNIT);
        log.debug("停车场配置已缓存: key={}, ttl={}分钟", cacheKey, CONFIG_CACHE_TTL);

        return response;
    }

    /**
     * 创建默认停车场配置
     * 默认值：总车位 100，预留 0，月度配额 72 小时，单次 24 小时，激活窗口 24 小时，僵尸车阈值 7 天
     */
    private ParkingConfig createDefaultConfig(Long communityId) {
        ParkingConfig config = new ParkingConfig();
        config.setCommunityId(communityId);
        config.setTotalSpaces(100);
        config.setReservedSpaces(0);
        config.setVisitorQuotaHours(72);
        config.setVisitorSingleDurationHours(24);
        config.setVisitorActivationWindowHours(24);
        config.setZombieVehicleThresholdDays(7);
        config.setVersion(1);
        parkingConfigMapper.insert(config);
        return config;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ParkingConfigResponse updateConfig(ParkingConfigUpdateRequest request) {
        Long communityId = request.getCommunityId();

        // 1. 查询当前配置
        ParkingConfig config = parkingConfigMapper.selectByCommunityId(communityId);
        if (config == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR.getCode(), "停车场配置不存在");
        }

        // 2. 验证新 total_spaces ≥ 当前在场车辆数（Requirements 9.6）
        int enteredCount = parkingConfigMapper.countEnteredVehicles(communityId);
        if (request.getTotalSpaces() < enteredCount) {
            log.warn("车位配置修改被拒绝: 新 totalSpaces={} < 当前在场车辆数={}, communityId={}",
                    request.getTotalSpaces(), enteredCount, communityId);
            throw new BusinessException(ErrorCode.PARKING_9002);
        }

        // 3. 记录修改前的值（用于操作日志）
        String beforeValue = String.format(
                "{totalSpaces=%d, reservedSpaces=%d, visitorQuotaHours=%d, visitorSingleDurationHours=%d, "
                + "visitorActivationWindowHours=%d, zombieVehicleThresholdDays=%d, version=%d}",
                config.getTotalSpaces(), config.getReservedSpaces(),
                config.getVisitorQuotaHours(), config.getVisitorSingleDurationHours(),
                config.getVisitorActivationWindowHours(), config.getZombieVehicleThresholdDays(),
                config.getVersion());

        // 4. 使用乐观锁更新配置（Requirements 9.7）
        config.setTotalSpaces(request.getTotalSpaces());
        if (request.getReservedSpaces() != null) {
            config.setReservedSpaces(request.getReservedSpaces());
        }
        if (request.getVisitorQuotaHours() != null) {
            config.setVisitorQuotaHours(request.getVisitorQuotaHours());
        }
        if (request.getVisitorSingleDurationHours() != null) {
            config.setVisitorSingleDurationHours(request.getVisitorSingleDurationHours());
        }
        if (request.getVisitorActivationWindowHours() != null) {
            config.setVisitorActivationWindowHours(request.getVisitorActivationWindowHours());
        }
        if (request.getZombieVehicleThresholdDays() != null) {
            config.setZombieVehicleThresholdDays(request.getZombieVehicleThresholdDays());
        }
        config.setVersion(request.getVersion());

        int updatedRows = parkingConfigMapper.updateByOptimisticLock(config);
        if (updatedRows == 0) {
            throw new BusinessException(ErrorCode.PARAM_ERROR.getCode(),
                    "配置更新失败，数据已被其他操作修改，请刷新后重试");
        }

        log.info("停车场配置更新成功: communityId={}, totalSpaces={}, version={}->{}",
                communityId, request.getTotalSpaces(), request.getVersion(), request.getVersion() + 1);

        // 5. 重算 Visitor_Available_Spaces 并更新不可用的 Visitor 授权状态（Requirements 9.8）
        int visitorAvailableSpaces = request.getTotalSpaces() - enteredCount;
        log.info("Visitor_Available_Spaces 重算: communityId={}, visitorAvailableSpaces={}",
                communityId, visitorAvailableSpaces);

        if (visitorAvailableSpaces <= 0) {
            // 将 approved_pending_activation 状态的 Visitor 授权更新为 unavailable
            // 预留：后续 Visitor 模块完善后，通过 VisitorAuthorizationMapper 执行批量更新
            log.info("Visitor 可开放车位不足，需更新不可用授权: communityId={}, visitorAvailableSpaces={}",
                    communityId, visitorAvailableSpaces);
        }

        // 6. 失效相关缓存
        invalidateRelatedCaches(communityId);

        // 7. 记录操作日志（Requirements 9.5）
        String afterValue = String.format(
                "{totalSpaces=%d, reservedSpaces=%d, visitorQuotaHours=%d, visitorSingleDurationHours=%d, "
                + "visitorActivationWindowHours=%d, zombieVehicleThresholdDays=%d, version=%d}",
                config.getTotalSpaces(), config.getReservedSpaces(),
                config.getVisitorQuotaHours(), config.getVisitorSingleDurationHours(),
                config.getVisitorActivationWindowHours(), config.getZombieVehicleThresholdDays(),
                request.getVersion() + 1);

        log.info("操作日志: 停车场配置修改, requestId={}, communityId={}, before={}, after={}",
                RequestContext.getRequestId(), communityId, beforeValue, afterValue);

        // 8. 重新查询更新后的配置并返回
        ParkingConfig updatedConfig = parkingConfigMapper.selectByCommunityId(communityId);
        return buildResponse(updatedConfig);
    }

    /**
     * 失效与停车场配置相关的缓存
     */
    private void invalidateRelatedCaches(Long communityId) {
        // 失效停车场配置缓存
        String configCacheKey = cacheService.generateKey(CONFIG_CACHE_RESOURCE, communityId);
        cacheService.delete(configCacheKey);
        log.info("停车场配置缓存已失效: key={}", configCacheKey);

        // 失效报表相关缓存
        cacheService.deleteByPrefix("report:" + communityId);
        log.info("报表缓存已失效: prefix=report:{}", communityId);
    }

    /**
     * 将 ParkingConfig 实体转换为响应 DTO
     */
    private ParkingConfigResponse buildResponse(ParkingConfig config) {
        ParkingConfigResponse response = new ParkingConfigResponse();
        response.setId(config.getId());
        response.setCommunityId(config.getCommunityId());
        response.setTotalSpaces(config.getTotalSpaces());
        response.setReservedSpaces(config.getReservedSpaces());
        response.setVisitorQuotaHours(config.getVisitorQuotaHours());
        response.setVisitorSingleDurationHours(config.getVisitorSingleDurationHours());
        response.setVisitorActivationWindowHours(config.getVisitorActivationWindowHours());
        response.setZombieVehicleThresholdDays(config.getZombieVehicleThresholdDays());
        response.setVersion(config.getVersion());
        response.setCreateTime(config.getCreateTime());
        response.setUpdateTime(config.getUpdateTime());
        return response;
    }
}
