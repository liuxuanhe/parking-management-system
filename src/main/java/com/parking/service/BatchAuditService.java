package com.parking.service;

import com.parking.dto.BatchAuditRequest;
import com.parking.dto.BatchAuditResponse;

/**
 * 批量审核服务接口
 * Validates: Requirements 23.1, 23.2, 23.3, 23.4, 23.5, 23.6, 23.7
 */
public interface BatchAuditService {

    /**
     * 批量审核业主
     * 限制每次最多50条，使用事务和幂等键
     *
     * @param request     批量审核请求
     * @param adminId     管理员ID
     * @param communityId 小区ID
     * @return 批量审核响应（成功/失败数量及详情）
     */
    BatchAuditResponse batchAuditOwners(BatchAuditRequest request, Long adminId, Long communityId);

    /**
     * 批量审批 Visitor
     * 限制每次最多50条，使用事务和幂等键
     *
     * @param request     批量审批请求
     * @param adminId     管理员ID
     * @param communityId 小区ID
     * @return 批量审批响应（成功/失败数量及详情）
     */
    BatchAuditResponse batchAuditVisitors(BatchAuditRequest request, Long adminId, Long communityId);
}
