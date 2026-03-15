package com.parking.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * Visitor 申请请求 DTO
 */
@Data
public class VisitorApplyRequest {

    @NotNull(message = "车牌ID不能为空")
    private Long carPlateId;

    @NotBlank(message = "车牌号不能为空")
    private String carNumber;

    private String applyReason;
}
