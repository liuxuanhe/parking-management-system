package com.parking.service;

import com.parking.dto.ParkingRecordQueryRequest;
import com.parking.dto.ParkingRecordQueryResponse;

/**
 * 入场记录查询服务接口
 * 支持跨月分表查询、游标分页、数据脱敏
 * Validates: Requirements 11.2, 15.3, 15.4, 15.5, 16.1, 16.2, 16.3
 */
public interface ParkingRecordService {

    /**
     * 查询入场记录
     * 根据时间范围计算涉及的月份分表，使用 UNION ALL 合并跨月查询结果，
     * 基于 (enter_time, id) 游标分页，返回脱敏后的记录列表
     *
     * @param request 查询请求参数
     * @return 查询响应（包含 records、nextCursor、hasMore）
     */
    ParkingRecordQueryResponse queryRecords(ParkingRecordQueryRequest request);
}
