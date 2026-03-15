package com.parking.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 车辆出场请求 DTO
 * Validates: Requirements 6.1
 */
@Data
public class ExitRequest {

    /** 小区ID */
    @NotNull(message = "小区ID不能为空")
    private Long communityId;

    /** 车牌号 */
    @NotBlank(message = "车牌号不能为空")
    private String carNumber;
}
