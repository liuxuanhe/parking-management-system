package com.parking.dto;

import lombok.Data;
import java.util.List;

/**
 * Visitor 申请列表分页响应
 */
@Data
public class VisitorListResponse {

    /** Visitor 申请记录列表 */
    private List<VisitorListItem> records;

    /** 总记录数 */
    private long total;
}
