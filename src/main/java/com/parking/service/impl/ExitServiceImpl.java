package com.parking.service.impl;

import com.parking.dto.ExitRequest;
import com.parking.dto.ExitResponse;
import com.parking.mapper.ParkingCarRecordMapper;
import com.parking.model.ParkingCarRecord;
import com.parking.service.CacheService;
import com.parking.service.DistributedLockService;
import com.parking.service.ExitService;
import com.parking.service.NotificationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/**
 * 车辆出场服务实现类
 * 处理车辆出场逻辑：查找入场记录 → 正常出场/异常出场 → 分布式锁更新车位 → 失效缓存 → 记录日志
 * Validates: Requirements 6.1, 6.2, 6.3, 6.4, 6.7, 6.8
 */
@Slf4j
@Service
public class ExitServiceImpl implements ExitService {

    /** 分布式锁键前缀 */
    private static final String LOCK_KEY_PREFIX = "lock:space:";

    /** 分表名称格式 */
    private static final String TABLE_NAME_FORMAT = "parking_car_record_%s";

    /** 分表月份格式 */
    private static final DateTimeFormatter YEAR_MONTH_FORMATTER = DateTimeFormatter.ofPattern("yyyyMM");

    private final ParkingCarRecordMapper parkingCarRecordMapper;
    private final DistributedLockService distributedLockService;
    private final CacheService cacheService;
    private final NotificationService notificationService;

    public ExitServiceImpl(ParkingCarRecordMapper parkingCarRecordMapper,
                           DistributedLockService distributedLockService,
                           CacheService cacheService,
                           NotificationService notificationService) {
        this.parkingCarRecordMapper = parkingCarRecordMapper;
        this.distributedLockService = distributedLockService;
        this.cacheService = cacheService;
        this.notificationService = notificationService;
    }

    @Override
    public ExitResponse vehicleExit(ExitRequest request) {
        Long communityId = request.getCommunityId();
        String carNumber = request.getCarNumber();
        LocalDateTime now = LocalDateTime.now();

        log.info("车辆出场请求: communityId={}, carNumber={}", communityId, carNumber);

        // 1. 查找入场记录（当前月和上个月分表）
        ParkingCarRecord entryRecord = findEnteredRecord(communityId, carNumber, now);

        // 2. 在分布式锁内处理出场逻辑
        String lockKey = LOCK_KEY_PREFIX + communityId;
        ExitResponse response = distributedLockService.executeWithLock(lockKey, () -> {
            if (entryRecord != null) {
                // 正常出场：更新入场记录
                return handleNormalExit(entryRecord, now);
            } else {
                // 异常出场：创建异常出场记录
                return handleExceptionExit(communityId, carNumber, now);
            }
        });

        // 3. 失效报表缓存
        invalidateReportCache(communityId);

        // 4. 记录操作日志
        log.info("车辆出场完成: communityId={}, carNumber={}, status={}, recordId={}",
                communityId, carNumber, response.getStatus(), response.getRecordId());

        return response;
    }

    /**
     * 查找在场入场记录
     * 先查当前月分表，未找到则查上个月分表（跨月场景）
     */
    ParkingCarRecord findEnteredRecord(Long communityId, String carNumber, LocalDateTime now) {
        // 查当前月分表
        String currentTable = resolveTableName(now);
        ParkingCarRecord record = parkingCarRecordMapper.selectEnteredRecord(currentTable, communityId, carNumber);
        if (record != null) {
            return record;
        }

        // 查上个月分表（跨月入场场景）
        LocalDateTime lastMonth = now.minusMonths(1);
        String lastMonthTable = resolveTableName(lastMonth);
        // 避免重复查询同一张表（月初时 now 和 lastMonth 可能同月）
        if (!lastMonthTable.equals(currentTable)) {
            record = parkingCarRecordMapper.selectEnteredRecord(lastMonthTable, communityId, carNumber);
        }

        return record;
    }

    /**
     * 正常出场处理
     * 更新入场记录：status='exited'，记录 exit_time，计算停放时长
     */
    ExitResponse handleNormalExit(ParkingCarRecord entryRecord, LocalDateTime exitTime) {
        // 计算停放时长（分钟）
        int duration = (int) Duration.between(entryRecord.getEnterTime(), exitTime).toMinutes();

        // 更新入场记录
        entryRecord.setExitTime(exitTime);
        entryRecord.setDuration(duration);
        entryRecord.setStatus("exited");

        String tableName = resolveTableName(entryRecord.getEnterTime());
        parkingCarRecordMapper.updateExitRecord(tableName, entryRecord);

        log.info("正常出场: recordId={}, carNumber={}, duration={}分钟",
                entryRecord.getId(), entryRecord.getCarNumber(), duration);

        // Visitor 车辆出场时累计停放时长（预留接口，当前仅打印日志）
        if ("visitor".equals(entryRecord.getVehicleType())) {
            log.info("Visitor 车辆出场，待累计停放时长: carNumber={}, duration={}分钟",
                    entryRecord.getCarNumber(), duration);
        }

        return buildResponse(entryRecord);
    }

    /**
     * 异常出场处理
     * 无入场记录时创建 exit_exception 记录，并通知物业管理员
     */
    ExitResponse handleExceptionExit(Long communityId, String carNumber, LocalDateTime exitTime) {
        // 创建异常出场记录
        ParkingCarRecord exceptionRecord = new ParkingCarRecord();
        exceptionRecord.setCommunityId(communityId);
        exceptionRecord.setCarNumber(carNumber);
        exceptionRecord.setExitTime(exitTime);
        exceptionRecord.setStatus("exit_exception");
        exceptionRecord.setExceptionReason("无对应入场记录");

        String tableName = resolveTableName(exitTime);
        parkingCarRecordMapper.insertToTable(tableName, exceptionRecord);

        log.warn("异常出场: communityId={}, carNumber={}, recordId={}",
                communityId, carNumber, exceptionRecord.getId());

        // 通知物业管理员处理异常
        notifyPropertyAdmin(communityId, carNumber, exceptionRecord.getId());

        return buildExceptionResponse(exceptionRecord);
    }

    /**
     * 通知物业管理员处理异常出场
     */
    void notifyPropertyAdmin(Long communityId, String carNumber, Long recordId) {
        try {
            Map<String, String> data = Map.of(
                    "carNumber", carNumber,
                    "communityId", String.valueOf(communityId),
                    "recordId", String.valueOf(recordId),
                    "reason", "无对应入场记录，需人工处理"
            );
            // 使用 communityId 作为通知目标（物业管理员），后续可扩展为查询具体管理员ID
            notificationService.sendSubscriptionMessage(communityId, "exit_exception", data);
            log.info("已通知物业管理员处理异常出场: communityId={}, carNumber={}", communityId, carNumber);
        } catch (Exception e) {
            // 通知失败不影响主流程
            log.warn("通知物业管理员失败: communityId={}, carNumber={}", communityId, carNumber, e);
        }
    }

    /**
     * 根据时间计算分表名称
     */
    String resolveTableName(LocalDateTime time) {
        String yearMonth = time.format(YEAR_MONTH_FORMATTER);
        return String.format(TABLE_NAME_FORMAT, yearMonth);
    }

    /**
     * 构建正常出场响应
     */
    private ExitResponse buildResponse(ParkingCarRecord record) {
        ExitResponse response = new ExitResponse();
        response.setRecordId(record.getId());
        response.setCarNumber(record.getCarNumber());
        response.setVehicleType(record.getVehicleType());
        response.setEnterTime(record.getEnterTime());
        response.setExitTime(record.getExitTime());
        response.setDuration(record.getDuration());
        response.setStatus(record.getStatus());
        return response;
    }

    /**
     * 构建异常出场响应
     */
    private ExitResponse buildExceptionResponse(ParkingCarRecord record) {
        ExitResponse response = new ExitResponse();
        response.setRecordId(record.getId());
        response.setCarNumber(record.getCarNumber());
        response.setExitTime(record.getExitTime());
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
}
