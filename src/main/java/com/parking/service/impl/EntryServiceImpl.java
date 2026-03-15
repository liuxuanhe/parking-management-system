package com.parking.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.parking.common.BusinessException;
import com.parking.common.ErrorCode;
import com.parking.dto.EntryRequest;
import com.parking.dto.EntryResponse;
import com.parking.mapper.CarPlateMapper;
import com.parking.mapper.ParkingCarRecordMapper;
import com.parking.mapper.VisitorAuthorizationMapper;
import com.parking.mapper.VisitorSessionMapper;
import com.parking.model.CarPlate;
import com.parking.model.ParkingCarRecord;
import com.parking.model.VisitorAuthorization;
import com.parking.model.VisitorSession;
import com.parking.service.CacheService;
import com.parking.service.DistributedLockService;
import com.parking.service.EntryService;
import com.parking.service.IdempotencyService;
import com.parking.service.ParkingSpaceCalculator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

/**
 * 车辆入场服务实现类
 * 处理 Primary 车辆自动入场：幂等检查 → 车牌查询 → 分布式锁 → 车位校验 → 创建入场记录
 * Validates: Requirements 5.1, 5.2, 5.3, 5.4, 5.5, 5.6, 5.7, 5.8, 5.9, 5.10, 15.2
 */
@Slf4j
@Service
public class EntryServiceImpl implements EntryService {

    /** 幂等键过期时间（秒），5分钟 */
    private static final int IDEMPOTENCY_EXPIRE_SECONDS = 300;

    /** 分布式锁键前缀 */
    private static final String LOCK_KEY_PREFIX = "lock:space:";

    /** 分表名称格式 */
    private static final String TABLE_NAME_FORMAT = "parking_car_record_%s";

    /** 分表月份格式 */
    private static final DateTimeFormatter YEAR_MONTH_FORMATTER = DateTimeFormatter.ofPattern("yyyyMM");

    /** 幂等键中的分钟格式 */
    private static final DateTimeFormatter MINUTE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmm");

    private final IdempotencyService idempotencyService;
    private final DistributedLockService distributedLockService;
    private final ParkingSpaceCalculator parkingSpaceCalculator;
    private final CarPlateMapper carPlateMapper;
    private final ParkingCarRecordMapper parkingCarRecordMapper;
    private final VisitorAuthorizationMapper visitorAuthorizationMapper;
    private final VisitorSessionMapper visitorSessionMapper;
    private final CacheService cacheService;
    private final ObjectMapper objectMapper;

    public EntryServiceImpl(IdempotencyService idempotencyService,
                            DistributedLockService distributedLockService,
                            ParkingSpaceCalculator parkingSpaceCalculator,
                            CarPlateMapper carPlateMapper,
                            ParkingCarRecordMapper parkingCarRecordMapper,
                            VisitorAuthorizationMapper visitorAuthorizationMapper,
                            VisitorSessionMapper visitorSessionMapper,
                            CacheService cacheService,
                            ObjectMapper objectMapper) {
        this.idempotencyService = idempotencyService;
        this.distributedLockService = distributedLockService;
        this.parkingSpaceCalculator = parkingSpaceCalculator;
        this.carPlateMapper = carPlateMapper;
        this.parkingCarRecordMapper = parkingCarRecordMapper;
        this.visitorAuthorizationMapper = visitorAuthorizationMapper;
        this.visitorSessionMapper = visitorSessionMapper;
        this.cacheService = cacheService;
        this.objectMapper = objectMapper;
    }

    @Override
    public EntryResponse vehicleEntry(EntryRequest request) {
        Long communityId = request.getCommunityId();
        String carNumber = request.getCarNumber();
        LocalDateTime now = LocalDateTime.now();

        // 1. 生成幂等键并检查：vehicle_entry:{communityId}:{carNumber}:{minute}
        String idempotencyKey = buildIdempotencyKey(communityId, carNumber, now);
        Optional<String> existingResult = idempotencyService.getResult(idempotencyKey);
        if (existingResult.isPresent()) {
            log.info("重复入场请求，返回已有结果: idempotencyKey={}", idempotencyKey);
            return deserializeResponse(existingResult.get());
        }

        // 2. 查询车牌状态（primary 或 visitor）
        CarPlate carPlate = findCarPlate(communityId, carNumber);
        String vehicleType = carPlate.getStatus();

        log.info("车辆入场: communityId={}, carNumber={}, vehicleType={}", communityId, carNumber, vehicleType);

        // 3. 在分布式锁内执行车位检查和入场记录创建
        String lockKey = LOCK_KEY_PREFIX + communityId;
        EntryResponse response = distributedLockService.executeWithLock(lockKey, () -> {
            // 4. 计算可用车位数，验证 Available_Spaces > 0
            boolean spaceAvailable = parkingSpaceCalculator.calculateAvailableSpaces(communityId) > 0;
            if (!spaceAvailable) {
                log.warn("车位已满，拒绝入场: communityId={}, carNumber={}", communityId, carNumber);
                throw new BusinessException(ErrorCode.PARKING_5001);
            }

            // 4.5 Visitor 车辆入场激活处理
            if ("visitor".equals(vehicleType)) {
                handleVisitorEntry(communityId, carPlate, now);
            }

            // 5. 创建入场记录，路由到对应月份分表
            ParkingCarRecord record = buildEntryRecord(communityId, carPlate, vehicleType, now);
            String tableName = resolveTableName(now);
            parkingCarRecordMapper.insertToTable(tableName, record);

            log.info("入场记录已创建: recordId={}, tableName={}, carNumber={}",
                    record.getId(), tableName, carNumber);

            // 6. 构建响应
            return buildResponse(record);
        });

        // 7. 设置幂等键（5分钟过期）
        String resultJson = serializeResponse(response);
        idempotencyService.checkAndSet(idempotencyKey, resultJson, IDEMPOTENCY_EXPIRE_SECONDS);

        // 8. 失效报表缓存
        invalidateReportCache(communityId);

        // 9. 记录操作日志（通过 AOP 切面自动记录，此处仅打印日志）
        log.info("车辆入场成功: communityId={}, carNumber={}, recordId={}",
                communityId, carNumber, response.getRecordId());

        return response;
    }

    /**
     * 构建幂等键
     * 格式: vehicle_entry:{communityId}:{carNumber}:{minute}
     */
    String buildIdempotencyKey(Long communityId, String carNumber, LocalDateTime time) {
        String minute = time.format(MINUTE_FORMATTER);
        return String.format("vehicle_entry:%d:%s:%s", communityId, carNumber, minute);
    }

    /**
     * 查询车牌信息，验证车牌存在且状态为 primary 或 visitor
     */
    CarPlate findCarPlate(Long communityId, String carNumber) {
        CarPlate carPlate = carPlateMapper.selectByCommunityAndCarNumber(communityId, carNumber);

        if (carPlate == null) {
            log.warn("车牌未找到: communityId={}, carNumber={}", communityId, carNumber);
            throw new BusinessException(ErrorCode.PARAM_ERROR.getCode(), "车牌未注册或无入场权限");
        }

        String status = carPlate.getStatus();
        if (!"primary".equals(status) && !"visitor".equals(status)) {
            log.warn("车牌状态不允许入场: communityId={}, carNumber={}, status={}",
                    communityId, carNumber, status);
            throw new BusinessException(ErrorCode.PARAM_ERROR.getCode(), "车辆无入场权限，需设置为 Primary 或申请 Visitor 权限");
        }

        return carPlate;
    }

    /**
     * 构建入场记录实体
     */
    private ParkingCarRecord buildEntryRecord(Long communityId, CarPlate carPlate,
                                               String vehicleType, LocalDateTime enterTime) {
        ParkingCarRecord record = new ParkingCarRecord();
        record.setCommunityId(communityId);
        record.setHouseNo(carPlate.getHouseNo());
        record.setCarNumber(carPlate.getCarNumber());
        record.setVehicleType(vehicleType);
        record.setEnterTime(enterTime);
        record.setStatus("entered");
        return record;
    }

    /**
     * 根据入场时间计算分表名称
     * 格式: parking_car_record_yyyyMM
     */
    String resolveTableName(LocalDateTime enterTime) {
        String yearMonth = enterTime.format(YEAR_MONTH_FORMATTER);
        return String.format(TABLE_NAME_FORMAT, yearMonth);
    }

    /**
     * 构建入场响应
     */
    private EntryResponse buildResponse(ParkingCarRecord record) {
        EntryResponse response = new EntryResponse();
        response.setRecordId(record.getId());
        response.setCarNumber(record.getCarNumber());
        response.setVehicleType(record.getVehicleType());
        response.setEnterTime(record.getEnterTime());
        response.setStatus(record.getStatus());
        return response;
    }

    /**
     * 失效报表缓存
     */
    private void invalidateReportCache(Long communityId) {
        try {
            cacheService.deleteByPrefix("report:" + communityId);
            log.debug("报表缓存已失效: communityId={}", communityId);
        } catch (Exception e) {
            // 缓存失效失败不影响主流程
            log.warn("报表缓存失效失败: communityId={}", communityId, e);
        }
    }

    /**
     * 序列化响应为 JSON
     */
    private String serializeResponse(EntryResponse response) {
        try {
            return objectMapper.writeValueAsString(response);
        } catch (JsonProcessingException e) {
            log.error("序列化入场响应失败", e);
            return "";
        }
    }

    /**
     * 反序列化 JSON 为响应
     */
    private EntryResponse deserializeResponse(String json) {
        try {
            return objectMapper.readValue(json, EntryResponse.class);
        } catch (JsonProcessingException e) {
            log.error("反序列化入场响应失败", e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR);
        }
    }

    /**
     * 处理 Visitor 车辆入场激活逻辑
     * 1. 查询待激活授权记录（status='approved_pending_activation'）
     * 2. 验证当前时间在24小时激活窗口内
     * 3. 超过窗口 → 更新状态为 canceled_no_entry，拒绝入场
     * 4. 窗口内 → 首次激活：更新状态为 activated，创建 visitor_session
     *          → 再次入场：更新 visitor_session 状态为 in_park
     */
    void handleVisitorEntry(Long communityId, CarPlate carPlate, LocalDateTime now) {
        String carNumber = carPlate.getCarNumber();

        // 1. 查询待激活的授权记录
        VisitorAuthorization authorization = visitorAuthorizationMapper
                .selectPendingActivation(communityId, carNumber);

        if (authorization != null) {
            // 有待激活授权 → 首次入场激活流程
            if (now.isAfter(authorization.getExpireTime())) {
                // 2a. 超过24小时激活窗口，自动取消
                visitorAuthorizationMapper.updateStatus(authorization.getId(), "canceled_no_entry");
                log.warn("Visitor 激活窗口已过期, authorizationId={}, expireTime={}, carNumber={}",
                        authorization.getId(), authorization.getExpireTime(), carNumber);
                throw new BusinessException(ErrorCode.PARKING_8001);
            }

            // 2b. 在窗口内，激活授权
            visitorAuthorizationMapper.updateActivation(
                    authorization.getId(), "activated", now);

            // 3. 创建 visitor_session 记录
            VisitorSession session = new VisitorSession();
            session.setCommunityId(communityId);
            session.setHouseNo(carPlate.getHouseNo());
            session.setAuthorizationId(authorization.getId());
            session.setCarNumber(carNumber);
            session.setSessionStart(now);
            session.setLastEntryTime(now);
            session.setAccumulatedDuration(0);
            session.setStatus("in_park");
            session.setTimeoutNotified(0);
            visitorSessionMapper.insert(session);

            log.info("Visitor 首次入场激活成功, authorizationId={}, sessionId={}, carNumber={}",
                    authorization.getId(), session.getId(), carNumber);
            return;
        }

        // 4. 无待激活授权 → 检查是否有已激活的会话（再次入场场景）
        VisitorSession existingSession = visitorSessionMapper.selectActiveByCarNumber(communityId, carNumber);
        if (existingSession == null) {
            // 查找 out_of_park 状态的会话（Visitor 再次入场）
            existingSession = visitorSessionMapper.selectOutOfParkByCarNumber(communityId, carNumber);
        }
        if (existingSession != null && "out_of_park".equals(existingSession.getStatus())) {
            // 再次入场：更新会话状态为 in_park，记录 last_entry_time
            visitorSessionMapper.updateStatusAndEntryTime(existingSession.getId(), "in_park", now);
            log.info("Visitor 再次入场: sessionId={}, carNumber={}", existingSession.getId(), carNumber);
            return;
        }

        // 如果既无待激活授权也无已有会话，仅记录日志（可能是数据异常）
        log.warn("Visitor 车辆入场但无有效授权或会话: communityId={}, carNumber={}", communityId, carNumber);
    }
}
