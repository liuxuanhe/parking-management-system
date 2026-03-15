package com.parking.controller;

import com.parking.common.ApiResponse;
import com.parking.common.RequestContext;
import com.parking.dto.EntryTrendResponse;
import com.parking.dto.PeakHoursResponse;
import com.parking.dto.SpaceUsageResponse;
import com.parking.service.ReportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * 报表控制器
 * 提供入场趋势、车位使用率、峰值时段、僵尸车辆等报表查询接口
 * Validates: Requirements 21.1, 21.2, 21.7
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;

    /**
     * 查询入场趋势报表
     * GET /api/v1/reports/entry-trend?communityId={}&startDate={}&endDate={}
     *
     * @param communityId 小区ID
     * @param startDate   开始日期
     * @param endDate     结束日期
     * @return 入场趋势数据
     */
    @GetMapping("/entry-trend")
    public ApiResponse<EntryTrendResponse> getEntryTrend(
            @RequestParam Long communityId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        log.info("查询入场趋势报表: communityId={}, {} ~ {}", communityId, startDate, endDate);
        EntryTrendResponse response = reportService.getEntryTrend(communityId, startDate, endDate);
        return ApiResponse.success(response, RequestContext.getRequestId());
    }

    /**
     * 查询车位使用率报表
     * GET /api/v1/reports/space-usage?communityId={}&startDate={}&endDate={}
     *
     * @param communityId 小区ID
     * @param startDate   开始日期
     * @param endDate     结束日期
     * @return 车位使用率数据
     */
    @GetMapping("/space-usage")
    public ApiResponse<SpaceUsageResponse> getSpaceUsage(
            @RequestParam Long communityId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        log.info("查询车位使用率报表: communityId={}, {} ~ {}", communityId, startDate, endDate);
        SpaceUsageResponse response = reportService.getSpaceUsage(communityId, startDate, endDate);
        return ApiResponse.success(response, RequestContext.getRequestId());
    }

    /**
     * 查询峰值时段报表
     * GET /api/v1/reports/peak-hours?communityId={}&startDate={}&endDate={}
     *
     * @param communityId 小区ID
     * @param startDate   开始日期
     * @param endDate     结束日期
     * @return 峰值时段数据
     */
    @GetMapping("/peak-hours")
    public ApiResponse<PeakHoursResponse> getPeakHours(
            @RequestParam Long communityId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        log.info("查询峰值时段报表: communityId={}, {} ~ {}", communityId, startDate, endDate);
        PeakHoursResponse response = reportService.getPeakHours(communityId, startDate, endDate);
        return ApiResponse.success(response, RequestContext.getRequestId());
    }
}
