package com.parking.dto;

import lombok.Data;

/**
 * 业主登录响应 DTO
 */
@Data
public class OwnerLoginResponse {

    /** 访问令牌 */
    private String accessToken;

    /** 刷新令牌 */
    private String refreshToken;

    /** 业主ID */
    private Long ownerId;

    /** 小区ID */
    private Long communityId;

    /** 房屋号 */
    private String houseNo;

    /** 真实姓名 */
    private String realName;

    /** 小区名称 */
    private String communityName;
}
