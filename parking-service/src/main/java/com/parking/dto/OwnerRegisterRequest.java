package com.parking.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * 业主注册请求 DTO
 * Validates: Requirements 1.1
 */
@Data
public class OwnerRegisterRequest {

    /** 手机号，11位数字 */
    @NotBlank(message = "手机号不能为空")
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
    private String phoneNumber;

    /** 验证码，6位数字 */
    @NotBlank(message = "验证码不能为空")
    @Pattern(regexp = "^\\d{6}$", message = "验证码格式不正确")
    private String verificationCode;

    /** 小区ID */
    @NotNull(message = "小区ID不能为空")
    private Long communityId;

    /** 房屋号 */
    @NotBlank(message = "房屋号不能为空")
    private String houseNo;

    /** 身份证后4位 */
    @NotBlank(message = "身份证后4位不能为空")
    @Pattern(regexp = "^[0-9Xx]{4}$", message = "身份证后4位格式不正确")
    private String idCardLast4;
}
