package com.parking.dto;

import lombok.Data;

/**
 * 创建管理员响应 DTO
 */
@Data
public class AdminCreateResponse {

    /** 管理员ID */
    private Long adminId;

    /** 用户名 */
    private String username;

    /** 初始密码（仅创建时返回一次） */
    private String initialPassword;

    /** 角色 */
    private String role;

    /** 所属小区ID */
    private Long communityId;
}
