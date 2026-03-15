package com.parking.controller;

import com.parking.common.ApiResponse;
import com.parking.common.RequestContext;
import com.parking.dto.EntryRequest;
import com.parking.dto.EntryResponse;
import com.parking.dto.ParkingRecordQueryRequest;
import com.parking.dto.ParkingRecordQueryResponse;
import com.parking.service.EntryService;
import com.parking.service.ParkingRecordService;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 停车场控制器
 * 处理入场和入场记录查询接口
 * Validates: Requirements 5.1, 5.5, 5.8, 11.2, 15.3, 16.1, 16.2, 16.3
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/parking")
public class EntryController {

    private final EntryService entryService;
    private final ParkingRecordService parkingRecordService;

    public EntryController(EntryService entryService,
                           ParkingRecordService parkingRecordService) {
        this.entryService = entryService;
        this.parkingRecordService = parkingRecordService;
    }

    /**
     * 车辆入场接口
     * POST /api/v1/parking/entry
     *
     * @param request 入场请求（包含 communityId 和 carNumber）
     * @return 入场响应
     */
    @PostMapping("/entry")
    public ApiResponse<EntryResponse> vehicleEntry(@Valid @RequestBody EntryRequest request) {
        log.info("车辆入场请求: communityId={}, carNumber={}",
                request.getCommunityId(), request.getCarNumber());
        EntryResponse response = entryService.vehicleEntry(request);
        return ApiResponse.success(response, RequestContext.getRequestId());
    }

    /**
     * 入场记录查询接口
     * GET /api/v1/parking/records
     * 支持跨月分表查询、游标分页、数据脱敏
     *
     * @param request 查询请求参数（communityId, houseNo, startTime, endTime, cursor, pageSize）
     * @return 查询响应（records, nextCursor, hasMore）
     */
    @GetMapping("/records")
    public ApiResponse<ParkingRecordQueryResponse> queryRecords(@Valid ParkingRecordQueryRequest request) {
        log.info("入场记录查询请求: communityId={}, houseNo={}, startTime={}, endTime={}",
                request.getCommunityId(), request.getHouseNo(),
                request.getStartTime(), request.getEndTime());
        ParkingRecordQueryResponse response = parkingRecordService.queryRecords(request);
        return ApiResponse.success(response, RequestContext.getRequestId());
    }
}
