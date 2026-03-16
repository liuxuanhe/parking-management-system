package com.parking.service.impl;

import com.parking.common.BusinessException;
import com.parking.common.ErrorCode;
import com.parking.dto.ParkingRecordQueryRequest;
import com.parking.dto.ParkingRecordQueryResponse;
import com.parking.mapper.ParkingCarRecordMapper;
import com.parking.model.ParkingCarRecord;
import com.parking.service.MaskingService;
import com.parking.service.ParkingRecordService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 入场记录查询服务实现类
 * 根据时间范围计算涉及的月份分表，使用 UNION ALL 合并跨月查询，
 * 基于 (enter_time, id) 游标分页，对响应执行数据脱敏
 * Validates: Requirements 11.2, 15.3, 15.4, 15.5, 16.1, 16.2, 16.3
 */
@Slf4j
@Service
public class ParkingRecordServiceImpl implements ParkingRecordService {

    /** 分表名称前缀 */
    private static final String TABLE_PREFIX = "parking_car_record_";

    /** 分表月份格式 */
    private static final DateTimeFormatter YEAR_MONTH_FORMATTER = DateTimeFormatter.ofPattern("yyyyMM");

    /** 游标中时间的格式 */
    private static final DateTimeFormatter CURSOR_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** 游标分隔符 */
    private static final String CURSOR_SEPARATOR = "_";

    private final ParkingCarRecordMapper parkingCarRecordMapper;
    private final MaskingService maskingService;

    public ParkingRecordServiceImpl(ParkingCarRecordMapper parkingCarRecordMapper,
                                     MaskingService maskingService) {
        this.parkingCarRecordMapper = parkingCarRecordMapper;
        this.maskingService = maskingService;
    }

    @Override
    public ParkingRecordQueryResponse queryRecords(ParkingRecordQueryRequest request) {
        // 1. 校验时间范围
        validateTimeRange(request.getStartTime(), request.getEndTime());

        // 2. 计算涉及的月份分表列表，并过滤掉不存在的分表
        List<String> allTableNames = resolveTableNames(request.getStartTime(), request.getEndTime());
        List<String> tableNames = allTableNames.stream()
                .filter(tbl -> parkingCarRecordMapper.checkTableExists(tbl) > 0)
                .collect(Collectors.toList());
        log.info("入场记录查询: communityId={}, houseNo={}, 涉及分表={}, 实际存在={}",
                request.getCommunityId(), request.getHouseNo(), allTableNames, tableNames);

        // 所有分表都不存在时，直接返回空结果
        if (tableNames.isEmpty()) {
            ParkingRecordQueryResponse emptyResponse = new ParkingRecordQueryResponse();
            emptyResponse.setRecords(new ArrayList<>());
            emptyResponse.setHasMore(false);
            return emptyResponse;
        }

        // 3. 解析游标
        LocalDateTime cursorTime = null;
        Long cursorId = null;
        if (request.getCursor() != null && !request.getCursor().isEmpty()) {
            String[] parts = parseCursor(request.getCursor());
            cursorTime = LocalDateTime.parse(parts[0], CURSOR_TIME_FORMATTER);
            cursorId = Long.parseLong(parts[1]);
        }

        // 4. 执行跨月查询（多查一条用于判断 hasMore）
        int effectivePageSize = request.getEffectivePageSize();
        int queryLimit = effectivePageSize + 1;

        List<ParkingCarRecord> records = parkingCarRecordMapper.selectRecordsByUnionAll(
                tableNames,
                request.getCommunityId(),
                request.getHouseNo(),
                request.getStartTime(),
                request.getEndTime(),
                cursorTime,
                cursorId,
                queryLimit
        );

        // 5. 判断是否有更多数据
        boolean hasMore = records.size() > effectivePageSize;
        if (hasMore) {
            records = records.subList(0, effectivePageSize);
        }

        // 6. 构建响应（含脱敏）
        ParkingRecordQueryResponse response = new ParkingRecordQueryResponse();
        response.setRecords(records.stream().map(this::toRecordItem).collect(Collectors.toList()));
        response.setHasMore(hasMore);

        // 7. 生成下一页游标
        if (!records.isEmpty() && hasMore) {
            ParkingCarRecord lastRecord = records.get(records.size() - 1);
            response.setNextCursor(buildCursor(lastRecord.getEnterTime(), lastRecord.getId()));
        }

        log.info("入场记录查询完成: communityId={}, houseNo={}, 返回记录数={}, hasMore={}",
                request.getCommunityId(), request.getHouseNo(), response.getRecords().size(), hasMore);

        return response;
    }

    /**
     * 校验时间范围有效性
     */
    public void validateTimeRange(LocalDateTime startTime, LocalDateTime endTime) {
        if (startTime.isAfter(endTime)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR.getCode(), "startTime 不能晚于 endTime");
        }
    }

    /**
     * 根据时间范围计算涉及的月份分表列表
     * 例如 2025-01-15 到 2025-03-20 → [parking_car_record_202501, parking_car_record_202502, parking_car_record_202503]
     */
    public List<String> resolveTableNames(LocalDateTime startTime, LocalDateTime endTime) {
        List<String> tableNames = new ArrayList<>();
        YearMonth startMonth = YearMonth.from(startTime);
        YearMonth endMonth = YearMonth.from(endTime);

        YearMonth current = startMonth;
        while (!current.isAfter(endMonth)) {
            String tableName = TABLE_PREFIX + current.format(YEAR_MONTH_FORMATTER);
            tableNames.add(tableName);
            current = current.plusMonths(1);
        }

        return tableNames;
    }

    /**
     * 解析游标字符串
     * 格式: "{enter_time}_{id}"，其中 enter_time 格式为 "yyyy-MM-dd HH:mm:ss"
     * 由于时间中包含空格，使用最后一个 "_" 作为分隔符
     */
    public String[] parseCursor(String cursor) {
        int lastUnderscoreIndex = cursor.lastIndexOf(CURSOR_SEPARATOR);
        if (lastUnderscoreIndex <= 0) {
            throw new BusinessException(ErrorCode.PARAM_ERROR.getCode(), "游标格式无效");
        }
        String timePart = cursor.substring(0, lastUnderscoreIndex);
        String idPart = cursor.substring(lastUnderscoreIndex + 1);
        try {
            LocalDateTime.parse(timePart, CURSOR_TIME_FORMATTER);
            Long.parseLong(idPart);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.PARAM_ERROR.getCode(), "游标格式无效");
        }
        return new String[]{timePart, idPart};
    }

    /**
     * 构建游标字符串
     */
    public String buildCursor(LocalDateTime enterTime, Long id) {
        return enterTime.format(CURSOR_TIME_FORMATTER) + CURSOR_SEPARATOR + id;
    }

    /**
     * 将实体转换为响应 DTO，并执行数据脱敏
     */
    private ParkingRecordQueryResponse.RecordItem toRecordItem(ParkingCarRecord record) {
        ParkingRecordQueryResponse.RecordItem item = new ParkingRecordQueryResponse.RecordItem();
        item.setId(record.getId());
        // 车牌号脱敏：保留前2位和后2位
        item.setCarNumber(maskingService.mask(record.getCarNumber(), 2, 2));
        item.setVehicleType(record.getVehicleType());
        item.setEnterTime(record.getEnterTime());
        item.setExitTime(record.getExitTime());
        item.setDuration(record.getDuration());
        item.setStatus(record.getStatus());
        return item;
    }
}
