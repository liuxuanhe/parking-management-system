package com.parking.dto;

import lombok.Data;
import java.time.LocalDateTime;

/**
 * 业主列表项 DTO（返回给前端）
 */
@Data
public class OwnerListItem {

    private Long ownerId;
    private Long communityId;
    private String houseNo;
    private String phoneNumber;
    private String idCardLast4;
    private String realName;
    private String status;
    private String rejectReason;
    private String accountStatus;
    private LocalDateTime createTime;
}
