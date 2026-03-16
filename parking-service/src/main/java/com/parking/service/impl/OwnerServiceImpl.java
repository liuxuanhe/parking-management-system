package com.parking.service.impl;

import com.parking.common.BusinessException;
import com.parking.common.ErrorCode;
import com.parking.dto.OwnerListItem;
import com.parking.dto.OwnerListResponse;
import com.parking.dto.OwnerLoginRequest;
import com.parking.dto.OwnerLoginResponse;
import com.parking.dto.OwnerRegisterRequest;
import com.parking.dto.OwnerRegisterResponse;
import com.parking.mapper.CarPlateMapper;
import com.parking.mapper.CommunityMapper;
import com.parking.mapper.HouseMapper;
import com.parking.mapper.OwnerHouseRelMapper;
import com.parking.mapper.OwnerMapper;
import com.parking.model.Community;
import com.parking.model.House;
import com.parking.model.Owner;
import com.parking.model.OwnerHouseRel;
import com.parking.service.JwtTokenService;
import com.parking.service.OwnerService;
import com.parking.service.VerificationCodeService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 业主服务实现类
 * 实现业主注册流程：验证码校验 → 房屋号验证 → 创建业主账号 → 绑定房屋号 → 记录操作日志
 * Validates: Requirements 1.1, 1.4, 1.5, 1.6, 1.7
 */
@Slf4j
@Service
public class OwnerServiceImpl implements OwnerService {

    private final OwnerMapper ownerMapper;
    private final OwnerHouseRelMapper ownerHouseRelMapper;
    private final HouseMapper houseMapper;
    private final CommunityMapper communityMapper;
    private final VerificationCodeService verificationCodeService;
    private final CarPlateMapper carPlateMapper;
    private final JwtTokenService jwtTokenService;

    /** 开发环境万能验证码，配置 verification.dev-code 即可启用 */
    @Value("${verification.dev-code:}")
    private String devCode;

    public OwnerServiceImpl(OwnerMapper ownerMapper,
                            OwnerHouseRelMapper ownerHouseRelMapper,
                            HouseMapper houseMapper,
                            CommunityMapper communityMapper,
                            VerificationCodeService verificationCodeService,
                            CarPlateMapper carPlateMapper,
                            JwtTokenService jwtTokenService) {
        this.ownerMapper = ownerMapper;
        this.ownerHouseRelMapper = ownerHouseRelMapper;
        this.houseMapper = houseMapper;
        this.communityMapper = communityMapper;
        this.verificationCodeService = verificationCodeService;
        this.carPlateMapper = carPlateMapper;
        this.jwtTokenService = jwtTokenService;
    }

    @Override
    public OwnerLoginResponse login(OwnerLoginRequest request) {
        // 1. 验证验证码（支持开发环境万能验证码）
        boolean isDevCode = devCode != null && !devCode.isEmpty()
                && devCode.equals(request.getVerificationCode());
        if (!isDevCode) {
            verificationCodeService.verify(request.getPhoneNumber(), request.getVerificationCode());
        }

        // 2. 根据手机号查询业主
        Owner owner = ownerMapper.selectByPhone(request.getPhoneNumber());
        if (owner == null) {
            log.warn("业主登录失败：手机号未注册, phone={}", request.getPhoneNumber());
            throw new BusinessException(ErrorCode.PARKING_13007);
        }

        // 3. 校验审核状态
        if (!"approved".equals(owner.getStatus())) {
            log.warn("业主登录失败：账号未通过审核, ownerId={}, status={}", owner.getId(), owner.getStatus());
            throw new BusinessException(ErrorCode.PARKING_13008);
        }

        // 4. 校验账号状态
        if (!"active".equals(owner.getAccountStatus())) {
            log.warn("业主登录失败：账号已被禁用, ownerId={}", owner.getId());
            throw new BusinessException(ErrorCode.PARKING_13009);
        }

        // 5. 生成 JWT Token
        String accessToken = jwtTokenService.generateAccessToken(
                owner.getId(), "owner", owner.getCommunityId(), owner.getHouseNo());
        String refreshToken = jwtTokenService.generateRefreshToken(owner.getId());

        // 6. 构建响应
        OwnerLoginResponse response = new OwnerLoginResponse();
        response.setAccessToken(accessToken);
        response.setRefreshToken(refreshToken);
        response.setOwnerId(owner.getId());
        response.setCommunityId(owner.getCommunityId());
        response.setHouseNo(owner.getHouseNo());
        response.setRealName(owner.getRealName());

        // 7. 查询小区名称
        Community community = communityMapper.selectById(owner.getCommunityId());
        if (community != null) {
            response.setCommunityName(community.getCommunityName());
        }

        log.info("业主登录成功: ownerId={}, communityId={}, houseNo={}",
                owner.getId(), owner.getCommunityId(), owner.getHouseNo());
        return response;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OwnerRegisterResponse register(OwnerRegisterRequest request) {
        // 1. 验证验证码
        verificationCodeService.verify(request.getPhoneNumber(), request.getVerificationCode());

        // 2. 验证房屋号是否存在
        House house = houseMapper.selectByCommunityAndHouseNo(
                request.getCommunityId(), request.getHouseNo());
        if (house == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR.getCode(), "房屋号不存在");
        }

        // 3. 创建业主账号，状态设置为 pending（Requirements 1.4）
        Owner owner = new Owner();
        owner.setCommunityId(request.getCommunityId());
        owner.setHouseNo(request.getHouseNo());
        owner.setPhoneNumber(request.getPhoneNumber());
        owner.setIdCardLast4(request.getIdCardLast4());
        owner.setStatus("pending");
        owner.setAccountStatus("active");
        ownerMapper.insert(owner);

        log.info("业主账号创建成功: ownerId={}, communityId={}, houseNo={}",
                owner.getId(), request.getCommunityId(), request.getHouseNo());

        // 4. 绑定到 community_id 和 house_no（Requirements 1.5）
        OwnerHouseRel rel = new OwnerHouseRel();
        rel.setCommunityId(request.getCommunityId());
        rel.setOwnerId(owner.getId());
        rel.setHouseNo(request.getHouseNo());
        rel.setRelationType("owner");
        ownerHouseRelMapper.insert(rel);

        log.info("业主房屋号绑定成功: ownerId={}, communityId={}, houseNo={}",
                owner.getId(), request.getCommunityId(), request.getHouseNo());

        // 5. 记录操作日志预留（Requirements 1.6，后续在审计日志模块中完善）
        log.info("操作日志预留: 业主注册, ownerId={}, communityId={}, houseNo={}",
                owner.getId(), request.getCommunityId(), request.getHouseNo());

        // 6. 构建响应
        OwnerRegisterResponse response = new OwnerRegisterResponse();
        response.setOwnerId(owner.getId());
        response.setStatus(owner.getStatus());
        response.setCreateTime(owner.getCreateTime());
        return response;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void disable(Long ownerId, String reason, Long operatorId) {
        // 1. 查询业主信息
        Owner owner = ownerMapper.selectById(ownerId);
        if (owner == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR.getCode(), "业主不存在");
        }

        // 2. 验证业主账号状态为 active
        if (!"active".equals(owner.getAccountStatus())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR.getCode(), "业主账号已被禁用");
        }

        // 3. 检查是否有车辆在场（Requirements 14.2, 14.3）
        int enteredCount = carPlateMapper.countEnteredByOwnerHouse(
                owner.getCommunityId(), owner.getHouseNo());
        if (enteredCount > 0) {
            throw new BusinessException(ErrorCode.PARKING_14001);
        }

        // 4. 更新业主账号状态为 disabled（Requirements 14.4）
        ownerMapper.updateAccountStatus(ownerId, "disabled");

        // 5. 批量禁用所有车牌（Requirements 14.5）
        carPlateMapper.disableByOwnerHouse(owner.getCommunityId(), owner.getHouseNo());

        // 6. 记录操作日志预留（Requirements 14.6）
        log.info("业主账号注销: ownerId={}, communityId={}, houseNo={}, reason={}, operatorId={}",
                ownerId, owner.getCommunityId(), owner.getHouseNo(), reason, operatorId);
    }

    @Override
    public OwnerListResponse listOwners(Long communityId, String status, int page, int pageSize) {
        // 计算偏移量
        int offset = (page - 1) * pageSize;

        // 查询业主列表
        List<Owner> owners = ownerMapper.selectByPage(communityId, status, offset, pageSize);

        // 查询总数
        long total = ownerMapper.countByCondition(communityId, status);

        // 转换为 DTO
        List<OwnerListItem> records = owners.stream()
                .map(this::convertToListItem)
                .collect(Collectors.toList());

        OwnerListResponse response = new OwnerListResponse();
        response.setRecords(records);
        response.setTotal(total);
        return response;
    }

    /**
     * 将 Owner 实体转换为 OwnerListItem DTO
     */
    private OwnerListItem convertToListItem(Owner owner) {
        OwnerListItem item = new OwnerListItem();
        item.setOwnerId(owner.getId());
        item.setCommunityId(owner.getCommunityId());
        item.setHouseNo(owner.getHouseNo());
        item.setPhoneNumber(owner.getPhoneNumber());
        item.setIdCardLast4(owner.getIdCardLast4());
        item.setRealName(owner.getRealName());
        item.setStatus(owner.getStatus());
        item.setRejectReason(owner.getRejectReason());
        item.setAccountStatus(owner.getAccountStatus());
        item.setCreateTime(owner.getCreateTime());
        return item;
    }
}
