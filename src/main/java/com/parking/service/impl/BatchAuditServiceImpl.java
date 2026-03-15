package com.parking.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.parking.common.RequestContext;
import com.parking.dto.BatchAuditRequest;
import com.parking.dto.BatchAuditResponse;
import com.parking.mapper.OperationLogMapper;
import com.parking.mapper.OwnerMapper;
import com.parking.mapper.VisitorApplicationMapper;
import com.parking.mapper.VisitorAuthorizationMapper;
import com.parking.model.OperationLog;
import com.parking.model.Owner;
import com.parking.model.VisitorApplication;
import com.parking.model.VisitorAuthorization;
import com.parking.service.BatchAuditService;
import com.parking.service.IdempotencyService;
import com.parking.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 批量审核服务实现
 * Validates: Requirements 23.1, 23.2, 23.3, 23.4, 23.5, 23.6, 23.7
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BatchAuditServiceImpl implements BatchAuditService {

    private final OwnerMapper ownerMapper;
    private final VisitorApplicationMapper visitorApplicationMapper;
    private final VisitorAuthorizationMapper visitorAuthorizationMapper;
    private final OperationLogMapper operationLogMapper;
    private final IdempotencyService idempotencyService;
    private final NotificationService notificationService;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BatchAuditResponse batchAuditOwners(BatchAuditRequest request, Long adminId, Long communityId) {
        // 1. 幂等键检查（Requirements 23.7）
        String requestId = request.getRequestId() != null ? request.getRequestId() : RequestContext.getRequestId();
        String idempotencyKey = idempotencyService.generateKey("batch_audit", communityId, 0L, requestId);
        if (!idempotencyService.checkAndSet(idempotencyKey, "{}", 300)) {
            log.info("批量审核业主重复请求, idempotencyKey={}", idempotencyKey);
            BatchAuditResponse resp = new BatchAuditResponse();
            resp.setSuccessCount(0);
            resp.setFailedCount(0);
            resp.setFailedItems(new ArrayList<>());
            return resp;
        }

        // 2. 行级锁批量查询（Requirements 23.6 事务原子性）
        List<Owner> owners = ownerMapper.selectByIdsForUpdate(request.getIds());
        Map<Long, Owner> ownerMap = owners.stream()
                .collect(Collectors.toMap(Owner::getId, Function.identity()));

        boolean approved = "approve".equals(request.getAction());
        int successCount = 0;
        List<BatchAuditResponse.FailedItem> failedItems = new ArrayList<>();

        // 3. 逐条处理（Requirements 23.2, 23.3）
        for (Long ownerId : request.getIds()) {
            Owner owner = ownerMap.get(ownerId);
            if (owner == null) {
                failedItems.add(new BatchAuditResponse.FailedItem(ownerId, "记录不存在"));
                continue;
            }

            // 验证小区权限
            if (!owner.getCommunityId().equals(communityId)) {
                failedItems.add(new BatchAuditResponse.FailedItem(ownerId, "无权操作该小区数据"));
                continue;
            }

            // 验证状态为 pending（Requirements 23.2）
            if (!"pending".equals(owner.getStatus())) {
                failedItems.add(new BatchAuditResponse.FailedItem(ownerId, "状态不是待审核，当前状态: " + owner.getStatus()));
                continue;
            }

            // 执行审核
            String newStatus = approved ? "approved" : "rejected";
            String rejectReason = approved ? null : request.getRejectReason();
            ownerMapper.updateAuditStatus(ownerId, newStatus, rejectReason, adminId);

            // 记录操作日志（Requirements 23.5）
            recordOperationLog(communityId, adminId, "OWNER_AUDIT", "OWNER", ownerId,
                    owner.getStatus(), newStatus);

            // 发送通知
            sendAuditNotification(owner.getId(), approved, owner.getPhoneNumber());

            successCount++;
        }

        // 4. 构建响应（Requirements 23.4）
        BatchAuditResponse response = new BatchAuditResponse();
        response.setSuccessCount(successCount);
        response.setFailedCount(failedItems.size());
        response.setFailedItems(failedItems);

        log.info("批量审核业主完成: communityId={}, 成功={}, 失败={}", communityId, successCount, failedItems.size());
        return response;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BatchAuditResponse batchAuditVisitors(BatchAuditRequest request, Long adminId, Long communityId) {
        // 1. 幂等键检查（Requirements 23.7）
        String requestId = request.getRequestId() != null ? request.getRequestId() : RequestContext.getRequestId();
        String idempotencyKey = idempotencyService.generateKey("batch_visitor_audit", communityId, 0L, requestId);
        if (!idempotencyService.checkAndSet(idempotencyKey, "{}", 300)) {
            log.info("批量审批 Visitor 重复请求, idempotencyKey={}", idempotencyKey);
            BatchAuditResponse resp = new BatchAuditResponse();
            resp.setSuccessCount(0);
            resp.setFailedCount(0);
            resp.setFailedItems(new ArrayList<>());
            return resp;
        }

        // 2. 行级锁批量查询（Requirements 23.6 事务原子性）
        List<VisitorApplication> applications = visitorApplicationMapper.selectByIdsForUpdate(request.getIds());
        Map<Long, VisitorApplication> appMap = applications.stream()
                .collect(Collectors.toMap(VisitorApplication::getId, Function.identity()));

        boolean approved = "approve".equals(request.getAction());
        int successCount = 0;
        List<BatchAuditResponse.FailedItem> failedItems = new ArrayList<>();

        // 3. 逐条处理（Requirements 23.2, 23.3）
        for (Long visitorId : request.getIds()) {
            VisitorApplication app = appMap.get(visitorId);
            if (app == null) {
                failedItems.add(new BatchAuditResponse.FailedItem(visitorId, "记录不存在"));
                continue;
            }

            // 验证小区权限
            if (!app.getCommunityId().equals(communityId)) {
                failedItems.add(new BatchAuditResponse.FailedItem(visitorId, "无权操作该小区数据"));
                continue;
            }

            // 验证状态为 submitted（Requirements 23.2）
            if (!"submitted".equals(app.getStatus())) {
                failedItems.add(new BatchAuditResponse.FailedItem(visitorId, "状态不是待审批，当前状态: " + app.getStatus()));
                continue;
            }

            if (approved) {
                // 审批通过：更新状态为 approved_pending_activation
                visitorApplicationMapper.updateStatus(visitorId, "approved_pending_activation", null, adminId);

                // 创建授权记录，设置24小时激活窗口
                LocalDateTime now = LocalDateTime.now();
                VisitorAuthorization authorization = new VisitorAuthorization();
                authorization.setCommunityId(app.getCommunityId());
                authorization.setHouseNo(app.getHouseNo());
                authorization.setApplicationId(visitorId);
                authorization.setCarPlateId(app.getCarPlateId());
                authorization.setCarNumber(app.getCarNumber());
                authorization.setStatus("approved_pending_activation");
                authorization.setStartTime(now);
                authorization.setExpireTime(now.plusHours(24));
                visitorAuthorizationMapper.insert(authorization);
            } else {
                // 审批驳回：更新状态为 rejected
                visitorApplicationMapper.updateStatus(visitorId, "rejected", request.getRejectReason(), adminId);
            }

            // 记录操作日志（Requirements 23.5）
            String newStatus = approved ? "approved_pending_activation" : "rejected";
            recordOperationLog(communityId, adminId, "VISITOR_AUDIT", "VISITOR_APPLICATION", visitorId,
                    app.getStatus(), newStatus);

            // 发送通知
            sendVisitorAuditNotification(app.getOwnerId(), approved, app.getCarNumber());

            successCount++;
        }

        // 4. 构建响应（Requirements 23.4）
        BatchAuditResponse response = new BatchAuditResponse();
        response.setSuccessCount(successCount);
        response.setFailedCount(failedItems.size());
        response.setFailedItems(failedItems);

        log.info("批量审批 Visitor 完成: communityId={}, 成功={}, 失败={}", communityId, successCount, failedItems.size());
        return response;
    }

    /**
     * 记录操作日志
     */
    private void recordOperationLog(Long communityId, Long adminId, String operationType,
                                     String targetType, Long targetId,
                                     String beforeStatus, String afterStatus) {
        try {
            OperationLog opLog = new OperationLog();
            opLog.setRequestId(RequestContext.getRequestId());
            opLog.setCommunityId(communityId);
            opLog.setOperatorId(adminId);
            opLog.setOperationType(operationType);
            opLog.setOperationTime(LocalDateTime.now());
            opLog.setTargetType(targetType);
            opLog.setTargetId(targetId);
            opLog.setBeforeValue("{\"status\":\"" + beforeStatus + "\"}");
            opLog.setAfterValue("{\"status\":\"" + afterStatus + "\"}");
            opLog.setOperationResult("SUCCESS");
            operationLogMapper.insert(opLog);
        } catch (Exception e) {
            log.error("记录操作日志失败: targetType={}, targetId={}", targetType, targetId, e);
        }
    }

    /**
     * 发送业主审核通知
     */
    private void sendAuditNotification(Long ownerId, boolean approved, String phone) {
        try {
            String resultText = approved ? "通过" : "驳回";
            notificationService.sendSubscriptionMessage(
                    ownerId,
                    "owner_audit_result",
                    Map.of("result", resultText)
            );
        } catch (Exception e) {
            log.warn("业主审核通知发送失败, ownerId={}", ownerId, e);
        }
    }

    /**
     * 发送 Visitor 审批通知
     */
    private void sendVisitorAuditNotification(Long ownerId, boolean approved, String carNumber) {
        try {
            String resultText = approved ? "通过" : "驳回";
            notificationService.sendSubscriptionMessage(
                    ownerId,
                    "visitor_audit_result",
                    Map.of("result", resultText, "carNumber", carNumber)
            );
        } catch (Exception e) {
            log.warn("Visitor 审批通知发送失败, ownerId={}", ownerId, e);
        }
    }
}
