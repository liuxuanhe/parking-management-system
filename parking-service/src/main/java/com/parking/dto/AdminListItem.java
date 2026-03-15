package com.parking.dto;

import lombok.Data;
import java.time.LocalDateTime;

/**
 * 管理员列表项 DTO（脱敏后返回给前端）
 */
@Data
public class AdminListItem {

    private Long id;
    private String username;
    private String realName;
    private String role;
    private Long communityId;
    private String communityName;
    private String status;
    private String phoneNumber;
    private LocalDateTime lastLoginTime;
    private LocalDateTime createTime;
}
