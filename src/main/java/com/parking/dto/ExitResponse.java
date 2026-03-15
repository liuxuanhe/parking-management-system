package com.parking.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 车辆出场响应 DTO
 * Validates: Requirements 6.1, 6.2, 6.3
 */
@Data
public class ExitResponse {

    /** 入场记录ID */
    private Long recordId;

    /** 车牌号 */
    private String carNumber;

    /** 车辆类型（primary / visitor） */
    private String vehicleType;

    /** 入场时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime enterTime;

    /** 出场时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime exitTime;

    /** 停放时长（分钟） */
    private Integer duration;

    /** 出场记录状态（exited / exit_exception） */
    private String status;
}
