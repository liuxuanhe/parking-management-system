package com.parking.service.impl;

import com.parking.common.BusinessException;
import com.parking.common.ErrorCode;
import com.parking.service.AuthorizationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * 权限校验服务实现类
 * 实现小区访问权限、房屋号数据域权限、IP 白名单、角色权限的校验
 * Validates: Requirements 12.5, 12.6, 12.7, 20.2, 20.3
 */
@Slf4j
@Service
public class AuthorizationServiceImpl implements AuthorizationService {

    /** Redis 中 IP 白名单缓存键前缀 */
    private static final String IP_WHITELIST_CACHE_PREFIX = "ip_whitelist:";

    /** IP 白名单缓存过期时间（1小时） */
    private static final long IP_WHITELIST_CACHE_TTL = 1;

    /** 角色：超级管理员 */
    private static final String ROLE_SUPER_ADMIN = "super_admin";

    /** 角色：物业管理员 */
    private static final String ROLE_PROPERTY_ADMIN = "property_admin";

    /** 角色：业主 */
    private static final String ROLE_OWNER = "owner";

    /**
     * 角色-操作权限映射表
     * key: 角色名称, value: 该角色允许执行的操作集合
     */
    private static final Map<String, Set<String>> ROLE_PERMISSIONS;

    static {
        ROLE_PERMISSIONS = new HashMap<>();

        // 超级管理员：拥有所有操作权限
        ROLE_PERMISSIONS.put(ROLE_SUPER_ADMIN, Set.of(
                "modify_parking_config", "disable_owner", "export_raw_data",
                "audit_owner", "audit_visitor", "batch_audit",
                "manage_ip_whitelist", "view_audit_log", "export_audit_log",
                "manage_vehicle", "handle_zombie_vehicle", "handle_exit_exception",
                "manage_owner", "manage_visitor", "view_report"
        ));

        // 物业管理员：本小区范围内的审批、配置、报表等操作
        ROLE_PERMISSIONS.put(ROLE_PROPERTY_ADMIN, Set.of(
                "modify_parking_config", "audit_owner", "audit_visitor",
                "batch_audit", "manage_vehicle", "handle_zombie_vehicle",
                "handle_exit_exception", "view_audit_log", "view_report",
                "manage_owner", "manage_visitor"
        ));

        // 业主：仅限本人/本房屋号数据域的操作
        ROLE_PERMISSIONS.put(ROLE_OWNER, Set.of(
                "manage_own_vehicle", "set_primary", "apply_visitor",
                "view_own_records", "view_own_quota", "modify_own_info"
        ));
    }

    private final RedisTemplate<String, Object> redisTemplate;

    public AuthorizationServiceImpl(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public void checkCommunityAccess(Long userCommunityId, Long requestCommunityId) {
        if (userCommunityId == null || requestCommunityId == null) {
            log.warn("小区访问权限校验失败: userCommunityId={}, requestCommunityId={}",
                    userCommunityId, requestCommunityId);
            throw new BusinessException(ErrorCode.PARKING_12001);
        }
        if (!userCommunityId.equals(requestCommunityId)) {
            log.warn("跨小区访问被拒绝: userCommunityId={}, requestCommunityId={}",
                    userCommunityId, requestCommunityId);
            throw new BusinessException(ErrorCode.PARKING_12001);
        }
    }

    @Override
    public void checkHouseNoAccess(Long userCommunityId, String userHouseNo,
                                   Long requestCommunityId, String requestHouseNo) {
        // 先校验小区访问权限
        checkCommunityAccess(userCommunityId, requestCommunityId);

        if (userHouseNo == null || requestHouseNo == null) {
            log.warn("房屋号数据域权限校验失败: userHouseNo={}, requestHouseNo={}",
                    userHouseNo, requestHouseNo);
            throw new BusinessException(ErrorCode.PARKING_12001);
        }
        if (!userHouseNo.equals(requestHouseNo)) {
            log.warn("跨房屋号访问被拒绝: userHouseNo={}, requestHouseNo={}",
                    userHouseNo, requestHouseNo);
            throw new BusinessException(ErrorCode.PARKING_12001);
        }
    }

    @Override
    public void checkIpWhitelist(String ip, String operation) {
        if (ip == null || ip.isBlank()) {
            log.warn("IP 白名单校验失败: IP 为空, operation={}", operation);
            throw new BusinessException(ErrorCode.PARKING_20001);
        }

        // 从 Redis 缓存读取白名单
        String cacheKey = IP_WHITELIST_CACHE_PREFIX + operation;
        List<String> whitelist = getWhitelistFromCache(cacheKey);

        if (whitelist == null || whitelist.isEmpty()) {
            // 缓存未命中，尝试从数据库加载（预留接口，当前返回空列表）
            whitelist = loadWhitelistFromDatabase(operation);
            // 写入缓存（即使为空也缓存，防止缓存穿透）
            cacheWhitelist(cacheKey, whitelist);
        }

        if (whitelist.isEmpty()) {
            // 白名单为空，拒绝所有请求
            log.warn("IP 白名单为空, 拒绝操作: ip={}, operation={}", ip, operation);
            throw new BusinessException(ErrorCode.PARKING_20001);
        }

        // 逐条匹配白名单规则（支持精确 IP 和 CIDR 格式）
        for (String rule : whitelist) {
            if (isIpMatchRule(ip, rule)) {
                log.debug("IP 白名单校验通过: ip={}, operation={}, matchedRule={}", ip, operation, rule);
                return;
            }
        }

        log.warn("IP 不在白名单中: ip={}, operation={}", ip, operation);
        throw new BusinessException(ErrorCode.PARKING_20001);
    }

    @Override
    public void checkRolePermission(String role, String operation) {
        if (role == null || operation == null) {
            log.warn("角色权限校验失败: role={}, operation={}", role, operation);
            throw new BusinessException(ErrorCode.PARKING_12001);
        }

        // 超级管理员拥有所有权限
        if (ROLE_SUPER_ADMIN.equals(role)) {
            return;
        }

        Set<String> permissions = ROLE_PERMISSIONS.get(role);
        if (permissions == null || !permissions.contains(operation)) {
            log.warn("角色权限不足: role={}, operation={}", role, operation);
            throw new BusinessException(ErrorCode.PARKING_12001);
        }
    }

    /**
     * 从 Redis 缓存读取 IP 白名单
     */
    @SuppressWarnings("unchecked")
    private List<String> getWhitelistFromCache(String cacheKey) {
        try {
            Object cached = redisTemplate.opsForValue().get(cacheKey);
            if (cached instanceof List) {
                return (List<String>) cached;
            }
        } catch (Exception e) {
            log.error("读取 IP 白名单缓存失败: cacheKey={}", cacheKey, e);
        }
        return null;
    }

    /**
     * 预留：从数据库加载 IP 白名单
     * 当前返回空列表，后续接入 Mapper 后实现数据库查询
     */
    protected List<String> loadWhitelistFromDatabase(String operation) {
        // 预留接口，后续实现数据库查询
        // 示例: return ipWhitelistMapper.selectByOperation(operation);
        return Collections.emptyList();
    }

    /**
     * 将 IP 白名单写入 Redis 缓存
     */
    private void cacheWhitelist(String cacheKey, List<String> whitelist) {
        try {
            redisTemplate.opsForValue().set(cacheKey, whitelist, IP_WHITELIST_CACHE_TTL, TimeUnit.HOURS);
        } catch (Exception e) {
            log.error("写入 IP 白名单缓存失败: cacheKey={}", cacheKey, e);
        }
    }

    /**
     * 判断 IP 是否匹配白名单规则
     * 支持精确 IP 匹配和 CIDR 格式匹配（如 192.168.1.0/24）
     *
     * @param ip   请求 IP
     * @param rule 白名单规则（精确 IP 或 CIDR 格式）
     * @return 是否匹配
     */
    protected boolean isIpMatchRule(String ip, String rule) {
        if (ip == null || rule == null) {
            return false;
        }

        String trimmedRule = rule.trim();
        String trimmedIp = ip.trim();

        // 精确匹配
        if (!trimmedRule.contains("/")) {
            return trimmedIp.equals(trimmedRule);
        }

        // CIDR 格式匹配
        return isIpInCidr(trimmedIp, trimmedRule);
    }

    /**
     * 判断 IP 是否在 CIDR 网段内
     *
     * @param ip   请求 IP（如 192.168.1.100）
     * @param cidr CIDR 格式网段（如 192.168.1.0/24）
     * @return 是否在网段内
     */
    private boolean isIpInCidr(String ip, String cidr) {
        try {
            String[] parts = cidr.split("/");
            if (parts.length != 2) {
                return false;
            }

            String networkAddress = parts[0];
            int prefixLength = Integer.parseInt(parts[1]);

            if (prefixLength < 0 || prefixLength > 32) {
                return false;
            }

            byte[] ipBytes = InetAddress.getByName(ip).getAddress();
            byte[] networkBytes = InetAddress.getByName(networkAddress).getAddress();

            // 仅支持 IPv4
            if (ipBytes.length != 4 || networkBytes.length != 4) {
                return false;
            }

            int ipInt = bytesToInt(ipBytes);
            int networkInt = bytesToInt(networkBytes);

            // 计算子网掩码
            int mask = prefixLength == 0 ? 0 : (-1 << (32 - prefixLength));

            return (ipInt & mask) == (networkInt & mask);
        } catch (UnknownHostException | NumberFormatException e) {
            log.warn("CIDR 格式解析失败: ip={}, cidr={}", ip, cidr, e);
            return false;
        }
    }

    /**
     * 将4字节数组转换为 int
     */
    private int bytesToInt(byte[] bytes) {
        return ((bytes[0] & 0xFF) << 24)
                | ((bytes[1] & 0xFF) << 16)
                | ((bytes[2] & 0xFF) << 8)
                | (bytes[3] & 0xFF);
    }
}
