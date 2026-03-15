package com.parking.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 僵尸车辆处理请求
 */
@Data
public class ZombieVehicleHandleRequest {
    @NotNull(message = "小区ID不能为空")
    private Long communityId;

    /**
     * 处理方式：contacted（已联系车主）、resolved（已解决）、ignored（忽略）
     */
    @NotBlank(message = "处理方式不能为空")
    private String handleType;

    /** 联系记录（handleType=contacted 时填写） */
    private String contactRecord;

    /** 解决方案（handleType=resolved 时填写） */
    private String solution;

    /** 忽略原因（handleType=ignored 时填写） */
    private String ignoreReason;
}
