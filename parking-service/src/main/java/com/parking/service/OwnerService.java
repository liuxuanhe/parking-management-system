package com.parking.service;

import com.parking.dto.OwnerListResponse;
import com.parking.dto.OwnerRegisterRequest;
import com.parking.dto.OwnerRegisterResponse;

/**
 * 业主服务接口
 * Validates: Requirements 1.1, 1.4, 1.5, 1.6, 1.7
 */
public interface OwnerService {

    /**
     * 业主注册
     * 验证验证码 → 验证房屋号存在 → 创建业主账号(status=pending) → 绑定房屋号 → 记录操作日志
     *
     * @param request 注册请求
     * @return 注册响应
     */
    OwnerRegisterResponse register(OwnerRegisterRequest request);

    /**
     * 分页查询业主列表
     *
     * @param communityId 小区ID（可选）
     * @param status      审核状态（可选）
     * @param page        页码（从1开始）
     * @param pageSize    每页条数
     * @return 分页响应
     */
    OwnerListResponse listOwners(Long communityId, String status, int page, int pageSize);

    /**
     * 注销业主账号
     * 仅超级管理员可执行，验证所有车辆均不在场后，禁用账号和所有车牌
     * Validates: Requirements 14.1, 14.2, 14.3, 14.4, 14.5, 14.6, 14.7, 14.8
     *
     * @param ownerId 业主ID
     * @param reason 注销原因
     * @param operatorId 操作人ID
     */
    void disable(Long ownerId, String reason, Long operatorId);
}
