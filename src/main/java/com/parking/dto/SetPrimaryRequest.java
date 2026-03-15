package com.parking.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 设置 Primary 车辆请求 DTO
 * Validates: Requirements 4.1, 4.4
 */
@Data
public class SetPrimaryRequest {

    /** 小区ID */
    @NotNull(message = "小区ID不能为空")
    private Long communityId;

    /** 房屋号 */
    @NotNull(message = "房屋号不能为空")
    private String houseNo;

    /** 二次确认 token（预留，后续完善） */
    private String confirmToken;
}
