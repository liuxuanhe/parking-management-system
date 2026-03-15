package com.parking.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 管理员修改密码请求 DTO
 * Validates: Requirements 13.3, 13.4
 */
@Data
public class AdminChangePasswordRequest {

    /** 旧密码 */
    @NotBlank(message = "旧密码不能为空")
    private String oldPassword;

    /** 新密码 */
    @NotBlank(message = "新密码不能为空")
    private String newPassword;
}
