package com.parking.dto;

import lombok.Data;

/**
 * 管理员登录响应 DTO
 * Validates: Requirements 13.4, 13.5, 13.6
 */
@Data
public class AdminLoginResponse {

    /** Access Token */
    private String accessToken;

    /** Refresh Token */
    private String refreshToken;

    /** 是否必须修改初始密码 */
    private Boolean mustChangePassword;

    /** 管理员ID */
    private Long adminId;

    /** 角色 */
    private String role;

    /** 小区ID */
    private Long communityId;
}
