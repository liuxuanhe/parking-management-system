package com.parking.dto;

import lombok.Data;
import java.time.LocalDateTime;

/**
 * Visitor 申请列表项 DTO（返回给 Admin_Portal 前端）
 */
@Data
public class VisitorListItem {

    /** 申请ID（前端用作 visitorId） */
    private Long visitorId;
    /** 车牌号 */
    private String carNumber;
    /** 房屋号 */
    private String houseNo;
    /** 业主手机号（脱敏后） */
    private String ownerPhone;
    /** 申请原因 */
    private String applyReason;
    /** 申请状态 */
    private String status;
    /** 驳回原因 */
    private String rejectReason;
    /** 申请时间 */
    private LocalDateTime createTime;
}
