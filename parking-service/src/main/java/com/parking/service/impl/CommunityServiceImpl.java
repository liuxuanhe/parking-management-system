package com.parking.service.impl;

import com.parking.common.BusinessException;
import com.parking.common.ErrorCode;
import com.parking.common.OperationLogAnnotation;
import com.parking.mapper.AdminMapper;
import com.parking.mapper.CommunityMapper;
import com.parking.model.Community;
import com.parking.service.CommunityService;
import com.parking.service.JwtTokenService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

/**
 * 小区服务实现
 * Validates: Requirements 12.2, 12.3
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CommunityServiceImpl implements CommunityService {

    private final CommunityMapper communityMapper;
    private final JwtTokenService jwtTokenService;
    private final AdminMapper adminMapper;

    @Override
    public List<Community> listCommunities(String role, Long communityId) {
        if ("super_admin".equals(role)) {
            // Super_Admin 返回所有小区
            return communityMapper.selectAll();
        }
        // Property_Admin 仅返回本小区
        Community community = communityMapper.selectById(communityId);
        if (community == null) {
            return Collections.emptyList();
        }
        return Collections.singletonList(community);
    }

    @Override
    @OperationLogAnnotation(operationType = "UPDATE", targetType = "COMMUNITY")
    public String switchCommunity(Long adminId, Long targetCommunityId) {
        // 验证目标小区存在
        Community target = communityMapper.selectById(targetCommunityId);
        if (target == null) {
            throw new BusinessException(ErrorCode.PARKING_12001);
        }

        log.info("Super_Admin 切换小区: adminId={}, targetCommunityId={}, communityName={}",
                adminId, targetCommunityId, target.getCommunityName());

        // 重新签发包含新 communityId 的 Access Token
        return jwtTokenService.generateAccessToken(adminId, "super_admin", targetCommunityId, null);
    }
}
