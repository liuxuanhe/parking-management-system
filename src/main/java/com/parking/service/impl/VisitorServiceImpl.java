package com.parking.service.impl;

import com.parking.common.BusinessException;
import com.parking.common.ErrorCode;
import com.parking.dto.VisitorApplyRequest;
import com.parking.dto.VisitorApplyResponse;
import com.parking.mapper.CarPlateMapper;
import com.parking.mapper.VisitorApplicationMapper;
import com.parking.model.CarPlate;
import com.parking.model.VisitorApplication;
import com.parking.service.ParkingSpaceCalculator;
import com.parking.service.VisitorQuotaManager;
import com.parking.service.VisitorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Visitor 权限服务实现
 * Validates: Requirements 7.1, 7.2, 7.3, 7.4, 9.2
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VisitorServiceImpl implements VisitorService {

    private final CarPlateMapper carPlateMapper;
    private final VisitorApplicationMapper visitorApplicationMapper;
    private final VisitorQuotaManager visitorQuotaManager;
    private final ParkingSpaceCalculator parkingSpaceCalculator;

    @Override
    public VisitorApplyResponse apply(VisitorApplyRequest request, Long ownerId,
                                       Long communityId, String houseNo) {
        // 1. 验证车牌已绑定到业主账号
        CarPlate carPlate = carPlateMapper.selectById(request.getCarPlateId());
        if (carPlate == null || !carPlate.getCommunityId().equals(communityId)
                || !carPlate.getHouseNo().equals(houseNo)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "车牌未绑定到当前业主账号");
        }

        // 2. 检查月度配额（< 72小时 = 4320分钟）
        if (!visitorQuotaManager.checkQuotaSufficient(communityId, houseNo)) {
            throw new BusinessException(ErrorCode.PARKING_7001);
        }

        // 3. 检查 Visitor 可开放车位数（> 0）
        int visitorSpaces = parkingSpaceCalculator.calculateVisitorAvailableSpaces(communityId);
        if (visitorSpaces <= 0) {
            throw new BusinessException(ErrorCode.PARKING_9001);
        }

        // 4. 创建申请记录，状态为 submitted
        VisitorApplication application = new VisitorApplication();
        application.setCommunityId(communityId);
        application.setHouseNo(houseNo);
        application.setOwnerId(ownerId);
        application.setCarPlateId(request.getCarPlateId());
        application.setCarNumber(request.getCarNumber());
        application.setApplyReason(request.getApplyReason());
        application.setStatus("submitted");

        visitorApplicationMapper.insert(application);

        log.info("Visitor 申请创建成功, applicationId={}, communityId={}, houseNo={}, carNumber={}",
                application.getId(), communityId, houseNo, request.getCarNumber());

        // 5. 构建响应
        VisitorApplyResponse response = new VisitorApplyResponse();
        response.setApplicationId(application.getId());
        response.setStatus("submitted");
        response.setCreateTime(application.getCreateTime());
        return response;
    }
}
