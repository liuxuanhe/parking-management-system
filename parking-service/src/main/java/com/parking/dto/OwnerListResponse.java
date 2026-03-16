package com.parking.dto;

import lombok.Data;
import java.util.List;

/**
 * 业主列表分页响应
 */
@Data
public class OwnerListResponse {

    /** 业主记录列表 */
    private List<OwnerListItem> records;

    /** 总记录数 */
    private long total;
}
