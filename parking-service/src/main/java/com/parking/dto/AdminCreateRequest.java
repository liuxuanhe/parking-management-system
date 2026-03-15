package com.parking.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 创建管理员请求 DTO
 * Validates: Requirements 13.1, 13.2
 */
@Data
public class AdminCreateRequest {

    /** 用户名 */
    @NotBlank(message = "用户名不能为空")
    private String username;

    /** 真实姓名 */
    @NotBlank(message = "真实姓名不能为空")
    private String realName;

    /** 手机号 */
    @NotBlank(message = "手机号不能为空")
    private String phoneNumber;

    /** 所属小区ID */
    @NotNull(message = "小区ID不能为空")
    private Long communityId;

    /** 角色（仅允许 property_admin） */
    @NotBlank(message = "角色不能为空")
    private String role;
}
