package com.parking.service;

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
}
