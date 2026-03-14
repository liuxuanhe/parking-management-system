package com.parking.service.impl;

import com.parking.common.BusinessException;
import com.parking.common.ErrorCode;
import com.parking.dto.OwnerRegisterRequest;
import com.parking.dto.OwnerRegisterResponse;
import com.parking.mapper.HouseMapper;
import com.parking.mapper.OwnerHouseRelMapper;
import com.parking.mapper.OwnerMapper;
import com.parking.model.House;
import com.parking.model.Owner;
import com.parking.model.OwnerHouseRel;
import com.parking.service.OwnerService;
import com.parking.service.VerificationCodeService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    private final VerificationCodeService verificationCodeService;

    public OwnerServiceImpl(OwnerMapper ownerMapper,
                            OwnerHouseRelMapper ownerHouseRelMapper,
                            HouseMapper houseMapper,
                            VerificationCodeService verificationCodeService) {
        this.ownerMapper = ownerMapper;
        this.ownerHouseRelMapper = ownerHouseRelMapper;
        this.houseMapper = houseMapper;
        this.verificationCodeService = verificationCodeService;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OwnerRegisterResponse register(OwnerRegisterRequest request) {
        // 1. 验证验证码
        verificationCodeService.verify(request.getPhone(), request.getVerificationCode());

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
        owner.setPhoneNumber(request.getPhone());
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
}
