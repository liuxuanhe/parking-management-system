package com.parking.dto;

import lombok.Data;
import java.time.LocalDateTime;

/**
 * 僵尸车辆查询响应
 */
@Data
public class ZombieVehicleQueryResponse {
    private Long id;
    private Long communityId;
    private String houseNo;
    /** 车牌号（脱敏后） */
    private String carNumber;
    private LocalDateTime enterTime;
    private Integer continuousDays;
    private String status;
    private String contactRecord;
    private String solution;
    private String ignoreReason;
    private LocalDateTime handleTime;
    private LocalDateTime createTime;
}
