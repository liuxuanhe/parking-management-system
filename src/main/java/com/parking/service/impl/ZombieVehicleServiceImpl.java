package com.parking.service.impl;

import com.parking.dto.ZombieVehicleQueryResponse;
import com.parking.mapper.ZombieVehicleMapper;
import com.parking.model.ZombieVehicle;
import com.parking.service.MaskingService;
import com.parking.service.ZombieVehicleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
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

    @Override
    public List<ZombieVehicleQueryResponse> listZombieVehicles(Long communityId, String status) {
        List<ZombieVehicle> zombies = zombieVehicleMapper.selectByCommunityAndStatus(communityId, status);
        log.info("查询僵尸车辆列表: communityId={}, status={}, 数量={}", communityId, status, zombies.size());
        return zombies.stream().map(this::toResponse).collect(Collectors.toList());
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
