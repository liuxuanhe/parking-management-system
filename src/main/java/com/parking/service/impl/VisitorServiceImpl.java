package com.parking.service.impl;

import com.parking.common.BusinessException;
import com.parking.common.ErrorCode;
import com.parking.common.RequestContext;
import com.parking.dto.VisitorApplyRequest;
import com.parking.dto.VisitorApplyResponse;
import com.parking.dto.VisitorAuditRequest;
import com.parking.dto.VisitorQueryResponse;
import com.parking.dto.VisitorQuotaResponse;
import com.parking.mapper.CarPlateMapper;
import com.parking.mapper.VisitorApplicationMapper;
import com.parking.mapper.VisitorAuthorizationMapper;
import com.parking.model.CarPlate;
import com.parking.model.VisitorApplication;
import com.parking.model.VisitorAuthorization;
import com.parking.service.IdempotencyService;
import com.parking.service.MaskingService;
import com.parking.service.NotificationService;
import com.parking.service.ParkingSpaceCalculator;
import com.parking.service.VisitorQuotaManager;
import com.parking.service.VisitorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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
    private final VisitorAuthorizationMapper visitorAuthorizationMapper;
    private final VisitorQuotaManager visitorQuotaManager;
    private final ParkingSpaceCalculator parkingSpaceCalculator;
    private final IdempotencyService idempotencyService;
    private final MaskingService maskingService;
    private final NotificationService notificationService;

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

    @Override
    @Transactional
    public void audit(Long visitorId, VisitorAuditRequest request, Long adminId, Long communityId) {
        // 1. 检查幂等键
        String requestId = request.getRequestId() != null ? request.getRequestId() : RequestContext.getRequestId();
        String idempotencyKey = idempotencyService.generateKey("visitor_audit", communityId, visitorId, requestId);
        if (!idempotencyService.checkAndSet(idempotencyKey, "{}", 300)) {
            log.info("Visitor 审批重复请求, visitorId={}, idempotencyKey={}", visitorId, idempotencyKey);
            return;
        }

        // 2. 行级锁查询申请记录
        VisitorApplication application = visitorApplicationMapper.selectByIdForUpdate(visitorId);
        if (application == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "Visitor 申请不存在");
        }

        // 3. 验证状态为 submitted
        if (!"submitted".equals(application.getStatus())) {
            throw new BusinessException(ErrorCode.PARKING_2001);
        }

        // 4. 验证小区权限
        if (!application.getCommunityId().equals(communityId)) {
            throw new BusinessException(ErrorCode.PARKING_12001);
        }

        boolean approved = "approve".equals(request.getAction());

        if (approved) {
            // 5a. 审批通过：更新状态为 approved_pending_activation
            visitorApplicationMapper.updateStatus(visitorId, "approved_pending_activation", null, adminId);

            // 创建授权记录，设置24小时激活窗口
            LocalDateTime now = LocalDateTime.now();
            VisitorAuthorization authorization = new VisitorAuthorization();
            authorization.setCommunityId(application.getCommunityId());
            authorization.setHouseNo(application.getHouseNo());
            authorization.setApplicationId(visitorId);
            authorization.setCarPlateId(application.getCarPlateId());
            authorization.setCarNumber(application.getCarNumber());
            authorization.setStatus("approved_pending_activation");
            authorization.setStartTime(now);
            authorization.setExpireTime(now.plusHours(24));
            visitorAuthorizationMapper.insert(authorization);

            log.info("Visitor 审批通过, visitorId={}, authorizationId={}, 激活窗口截止={}",
                    visitorId, authorization.getId(), authorization.getExpireTime());
        } else {
            // 5b. 审批驳回：更新状态为 rejected
            visitorApplicationMapper.updateStatus(visitorId, "rejected", request.getRejectReason(), adminId);
            log.info("Visitor 审批驳回, visitorId={}, 原因={}", visitorId, request.getRejectReason());
        }

        // 6. 发送订阅消息通知业主
        try {
            String resultText = approved ? "通过" : "驳回";
            notificationService.sendSubscriptionMessage(
                    application.getOwnerId(),
                    "visitor_audit_result",
                    Map.of("result", resultText, "carNumber", application.getCarNumber())
            );
        } catch (Exception e) {
            log.warn("Visitor 审批通知发送失败, visitorId={}", visitorId, e);
        }
    }

    @Override
    public List<VisitorQueryResponse> listVisitors(Long communityId, String houseNo) {
        // 1. 查询申请列表
        List<VisitorApplication> applications = visitorApplicationMapper.selectByHouse(communityId, houseNo);
        // 2. 查询授权列表
        List<VisitorAuthorization> authorizations = visitorAuthorizationMapper.selectByHouse(communityId, houseNo);

        // 3. 构建以 applicationId 为键的授权映射
        Map<Long, VisitorAuthorization> authMap = new java.util.HashMap<>();
        for (VisitorAuthorization auth : authorizations) {
            authMap.put(auth.getApplicationId(), auth);
        }

        // 4. 组装响应并执行数据脱敏
        List<VisitorQueryResponse> result = new ArrayList<>();
        for (VisitorApplication app : applications) {
            VisitorQueryResponse resp = new VisitorQueryResponse();
            resp.setApplicationId(app.getId());
            resp.setCarNumber(maskingService.mask(app.getCarNumber(), 2, 2));
            resp.setApplyReason(app.getApplyReason());
            resp.setApplicationStatus(app.getStatus());
            resp.setRejectReason(app.getRejectReason());
            resp.setApplyTime(app.getCreateTime());

            // 关联授权信息
            VisitorAuthorization auth = authMap.get(app.getId());
            if (auth != null) {
                resp.setAuthorizationId(auth.getId());
                resp.setAuthorizationStatus(auth.getStatus());
                resp.setStartTime(auth.getStartTime());
                resp.setExpireTime(auth.getExpireTime());
                resp.setActivationTime(auth.getActivationTime());
            }

            result.add(resp);
        }

        log.info("查询 Visitor 权限列表, communityId={}, houseNo={}, 数量={}", communityId, houseNo, result.size());
        return result;
    }

    /** 月度总配额：72小时 = 4320分钟 */
    private static final long MONTHLY_QUOTA_MINUTES = 4320L;
    /** 接近超限阈值：60小时 = 3600分钟 */
    private static final long NEAR_LIMIT_MINUTES = 3600L;

    @Override
    public VisitorQuotaResponse getQuota(Long communityId, String houseNo) {
        YearMonth currentMonth = YearMonth.now();
        long usedMinutes = visitorQuotaManager.calculateMonthlyUsage(communityId, houseNo, currentMonth);
        long remaining = Math.max(0, MONTHLY_QUOTA_MINUTES - usedMinutes);

        VisitorQuotaResponse response = new VisitorQuotaResponse();
        response.setTotalQuotaMinutes(MONTHLY_QUOTA_MINUTES);
        response.setUsedMinutes(usedMinutes);
        response.setRemainingMinutes(remaining);
        response.setNearLimit(usedMinutes >= NEAR_LIMIT_MINUTES);
        response.setMonth(currentMonth.toString());

        if (usedMinutes >= NEAR_LIMIT_MINUTES) {
            log.warn("Visitor 月度配额接近超限, communityId={}, houseNo={}, 已使用={}分钟",
                    communityId, houseNo, usedMinutes);
        }

        log.info("查询 Visitor 月度配额, communityId={}, houseNo={}, 已使用={}分钟, 剩余={}分钟",
                communityId, houseNo, usedMinutes, remaining);
        return response;
    }
}
