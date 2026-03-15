package com.parking.service.impl;

import com.parking.common.BusinessException;
import com.parking.common.ErrorCode;
import com.parking.dto.ZombieVehicleHandleRequest;
import com.parking.dto.ZombieVehicleQueryResponse;
import com.parking.mapper.ZombieVehicleMapper;
import com.parking.model.ZombieVehicle;
import com.parking.service.MaskingService;
import com.parking.service.ZombieVehicleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 僵尸车辆服务实现
 * Validates: Requirements 22.5, 22.9
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ZombieVehicleServiceImpl implements ZombieVehicleService {

    private final ZombieVehicleMapper zombieVehicleMapper;
    private final MaskingService maskingService;

    private static final Set<String> VALID_HANDLE_TYPES = Set.of("contacted", "resolved", "ignored");

    @Override
    public List<ZombieVehicleQueryResponse> listZombieVehicles(Long communityId, String status) {
        List<ZombieVehicle> zombies = zombieVehicleMapper.selectByCommunityAndStatus(communityId, status);
        log.info("查询僵尸车辆列表: communityId={}, status={}, 数量={}", communityId, status, zombies.size());
        return zombies.stream().map(this::toResponse).collect(Collectors.toList());
    }

    @Override
    public void handleZombieVehicle(Long zombieId, ZombieVehicleHandleRequest request, Long adminId) {
        // 验证处理方式
        if (!VALID_HANDLE_TYPES.contains(request.getHandleType())) {
            throw new BusinessException(ErrorCode.PARKING_22003);
        }

        // 查询僵尸车辆记录
        ZombieVehicle zombie = zombieVehicleMapper.selectById(zombieId);
        if (zombie == null) {
            throw new BusinessException(ErrorCode.PARKING_22001);
        }

        // 已处理的记录（resolved/ignored）不允许重复操作
        if ("resolved".equals(zombie.getStatus()) || "ignored".equals(zombie.getStatus())) {
            throw new BusinessException(ErrorCode.PARKING_22002);
        }

        // 更新处理信息
        zombie.setStatus(request.getHandleType());
        zombie.setContactRecord(request.getContactRecord());
        zombie.setSolution(request.getSolution());
        zombie.setIgnoreReason(request.getIgnoreReason());
        zombie.setHandlerAdminId(adminId);
        zombie.setHandleTime(LocalDateTime.now());

        zombieVehicleMapper.updateHandle(zombie);
        log.info("处理僵尸车辆: zombieId={}, handleType={}, adminId={}", zombieId, request.getHandleType(), adminId);
    }

    private ZombieVehicleQueryResponse toResponse(ZombieVehicle zombie) {
        ZombieVehicleQueryResponse resp = new ZombieVehicleQueryResponse();
        resp.setId(zombie.getId());
        resp.setCommunityId(zombie.getCommunityId());
        resp.setHouseNo(zombie.getHouseNo());
        // 车牌号脱敏：保留前2位和后2位
        resp.setCarNumber(maskingService.mask(zombie.getCarNumber(), 2, 2));
        resp.setEnterTime(zombie.getEnterTime());
        resp.setContinuousDays(zombie.getContinuousDays());
        resp.setStatus(zombie.getStatus());
        resp.setContactRecord(zombie.getContactRecord());
        resp.setSolution(zombie.getSolution());
        resp.setIgnoreReason(zombie.getIgnoreReason());
        resp.setHandleTime(zombie.getHandleTime());
        resp.setCreateTime(zombie.getCreateTime());
        return resp;
    }
}
