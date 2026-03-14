package com.parking.service;

/**
 * 权限校验服务接口
 * 负责小区访问权限、房屋号数据域权限、IP 白名单、角色权限的校验
 * Validates: Requirements 12.5, 12.6, 12.7, 20.2, 20.3
 */
public interface AuthorizationService {

    /**
     * 验证用户的 community_id 访问权限
     * 校验用户绑定的 communityId 与请求的 communityId 是否一致
     * 不一致时抛出 BusinessException（PARKING_12001）
     *
     * @param userCommunityId    用户绑定的小区 ID
     * @param requestCommunityId 请求访问的小区 ID
     */
    void checkCommunityAccess(Long userCommunityId, Long requestCommunityId);

    /**
     * 验证业主的房屋号数据域权限（Data_Domain 隔离）
     * 校验业主的 houseNo 与请求的 houseNo 是否一致
     * 不一致时抛出 BusinessException（PARKING_12001）
     *
     * @param userCommunityId    用户绑定的小区 ID
     * @param userHouseNo        用户绑定的房屋号
     * @param requestCommunityId 请求访问的小区 ID
     * @param requestHouseNo     请求访问的房屋号
     */
    void checkHouseNoAccess(Long userCommunityId, String userHouseNo,
                            Long requestCommunityId, String requestHouseNo);

    /**
     * 验证请求 IP 是否在白名单中（支持 CIDR 格式）
     * 先从 Redis 缓存读取白名单，缓存未命中时预留数据库查询接口
     * IP 不在白名单中时抛出 BusinessException（PARKING_20001）
     *
     * @param ip        请求来源 IP 地址
     * @param operation 操作类型（如 modify_parking_config、disable_owner、export_raw_data）
     */
    void checkIpWhitelist(String ip, String operation);

    /**
     * 验证用户角色是否有权限执行指定操作
     * 权限不足时抛出 BusinessException（PARKING_12001）
     *
     * @param role      用户角色（super_admin、property_admin、owner）
     * @param operation 操作类型
     */
    void checkRolePermission(String role, String operation);
}
