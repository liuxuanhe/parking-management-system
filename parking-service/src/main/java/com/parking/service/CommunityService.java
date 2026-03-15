package com.parking.service;

import com.parking.model.Community;

import java.util.List;

/**
 * 小区服务接口
 * 提供小区列表查询与切换功能
 * Validates: Requirements 12.2, 12.3
 */
public interface CommunityService {

    /**
     * 查询小区列表
     * Super_Admin 返回所有小区，Property_Admin 仅返回本小区
     *
     * @param role        当前用户角色
     * @param communityId 当前用户所属小区ID
     * @return 小区列表
     */
    List<Community> listCommunities(String role, Long communityId);

    /**
     * Super_Admin 切换当前操作小区
     * 验证目标小区存在后，重新签发包含新 communityId 的 Access Token
     *
     * @param adminId           当前管理员ID
     * @param targetCommunityId 目标小区ID
     * @return 新的 Access Token
     */
    String switchCommunity(Long adminId, Long targetCommunityId);
}
