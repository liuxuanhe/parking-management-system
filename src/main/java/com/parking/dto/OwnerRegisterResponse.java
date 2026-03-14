package com.parking.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 业主注册响应 DTO
 */
@Data
public class OwnerRegisterResponse {

    /** 业主ID */
    private Long ownerId;

    /** 审核状态 */
    private String status;

    /** 创建时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
}
