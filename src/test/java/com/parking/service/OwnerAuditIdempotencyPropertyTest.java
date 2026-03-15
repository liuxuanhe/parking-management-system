package com.parking.service;

import com.parking.common.BusinessException;
import com.parking.common.ErrorCode;
import com.parking.dto.InfoModifyAuditRequest;
import com.parking.mapper.OwnerInfoModifyMapper;
import com.parking.mapper.OwnerMapper;
import com.parking.model.OwnerInfoModifyApplication;
import com.parking.service.impl.OwnerInfoModifyServiceImpl;
import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.constraints.LongRange;
import net.jqwik.api.constraints.StringLength;
import org.mockito.Mockito;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Property 4: 审批操作幂等性
 * Validates: Requirements 2.8
 * 使用 jqwik 属性测试验证：
 * - 重复审批请求返回相同结果（不会重复执行审批逻辑）
 * - 并发审批只有一个成功执行
 */
class OwnerAuditIdempotencyPropertyTest {

    /**
     * Property 4.1: 重复审批请求应被幂等处理
     * 对于任意 applyId 和 requestId，第二次相同请求不应执行审批逻辑
     */
    @Property(tries = 100)
    void duplicateAuditRequestShouldBeIdempotent(
            @ForAll @LongRange(min = 1, max = 100000) Long applyId,
            @ForAll @LongRange(min = 1, max = 100000) Long communityId,
            @ForAll @LongRange(min = 1, max = 100000) Long adminId,
            @ForAll("requestIds") String requestId) {

        // 准备 mock
        OwnerInfoModifyMapper modifyMapper = mock(OwnerInfoModifyMapper.class);
        OwnerMapper ownerMapper = mock(OwnerMapper.class);
        IdempotencyService idempotencyService = mock(IdempotencyService.class);
        NotificationService notificationService = mock(NotificationService.class);

        OwnerInfoModifyServiceImpl service = new OwnerInfoModifyServiceImpl(
                modifyMapper, ownerMapper, idempotencyService, notificationService);

        // 模拟申请记录存在
        OwnerInfoModifyApplication application = new OwnerInfoModifyApplication();
        application.setId(applyId);
        application.setCommunityId(communityId);
        application.setOwnerId(1L);
        application.setModifyType("phone_number");
        application.setOldValue("13800000000");
        application.setNewValue("13900000000");
        application.setStatus("pending");
        when(modifyMapper.selectById(applyId)).thenReturn(application);

        String idempotencyKey = "info_modify_audit:" + communityId + ":" + applyId + ":" + requestId;
        when(idempotencyService.generateKey("info_modify_audit", communityId, applyId, requestId))
                .thenReturn(idempotencyKey);

        // 第二次请求：幂等键已存在，返回 false
        when(idempotencyService.checkAndSet(eq(idempotencyKey), anyString(), anyInt()))
                .thenReturn(false);

        InfoModifyAuditRequest request = new InfoModifyAuditRequest();
        request.setApproved(true);

        // 执行：重复请求应直接返回，不执行审批逻辑
        service.audit(applyId, request, adminId, requestId);

        // 验证：不应调用 selectByIdForUpdate（行级锁查询）和 updateStatus
        verify(modifyMapper, never()).selectByIdForUpdate(anyLong());
        verify(modifyMapper, never()).updateStatus(anyLong(), anyString(), anyString(), anyLong());
        verify(ownerMapper, never()).updatePhoneNumber(anyLong(), anyString());
    }

    /**
     * Property 4.2: 首次审批请求应正常执行审批逻辑
     * 对于任意 applyId，首次请求（幂等键不存在）应执行完整审批流程
     */
    @Property(tries = 100)
    void firstAuditRequestShouldExecuteAuditLogic(
            @ForAll @LongRange(min = 1, max = 100000) Long applyId,
            @ForAll @LongRange(min = 1, max = 100000) Long communityId,
            @ForAll @LongRange(min = 1, max = 100000) Long adminId,
            @ForAll("requestIds") String requestId,
            @ForAll boolean approved) {

        // 准备 mock
        OwnerInfoModifyMapper modifyMapper = mock(OwnerInfoModifyMapper.class);
        OwnerMapper ownerMapper = mock(OwnerMapper.class);
        IdempotencyService idempotencyService = mock(IdempotencyService.class);
        NotificationService notificationService = mock(NotificationService.class);

        OwnerInfoModifyServiceImpl service = new OwnerInfoModifyServiceImpl(
                modifyMapper, ownerMapper, idempotencyService, notificationService);

        // 模拟申请记录
        OwnerInfoModifyApplication application = new OwnerInfoModifyApplication();
        application.setId(applyId);
        application.setCommunityId(communityId);
        application.setOwnerId(1L);
        application.setModifyType("phone_number");
        application.setOldValue("13800000000");
        application.setNewValue("13900000000");
        application.setStatus("pending");
        when(modifyMapper.selectById(applyId)).thenReturn(application);
        when(modifyMapper.selectByIdForUpdate(applyId)).thenReturn(application);

        String idempotencyKey = "info_modify_audit:" + communityId + ":" + applyId + ":" + requestId;
        when(idempotencyService.generateKey("info_modify_audit", communityId, applyId, requestId))
                .thenReturn(idempotencyKey);

        // 首次请求：幂等键不存在，返回 true
        when(idempotencyService.checkAndSet(eq(idempotencyKey), anyString(), anyInt()))
                .thenReturn(true);

        InfoModifyAuditRequest request = new InfoModifyAuditRequest();
        request.setApproved(approved);
        if (!approved) {
            request.setRejectReason("信息不符");
        }

        // 执行
        service.audit(applyId, request, adminId, requestId);

        // 验证：应调用 selectByIdForUpdate 和 updateStatus
        verify(modifyMapper).selectByIdForUpdate(applyId);
        verify(modifyMapper).updateStatus(eq(applyId), eq(approved ? "approved" : "rejected"),
                any(), eq(adminId));

        if (approved) {
            verify(ownerMapper).updatePhoneNumber(1L, "13900000000");
        }
    }

    /**
     * Property 4.3: 并发审批场景下，幂等键确保只有一个请求执行审批
     * 模拟 N 个并发请求，只有第一个（checkAndSet 返回 true）执行审批逻辑
     */
    @Property(tries = 50)
    void concurrentAuditShouldOnlyExecuteOnce(
            @ForAll @LongRange(min = 1, max = 100000) Long applyId,
            @ForAll @LongRange(min = 1, max = 100000) Long communityId,
            @ForAll @IntRange(min = 2, max = 5) int concurrentCount) {

        // 准备 mock
        OwnerInfoModifyMapper modifyMapper = mock(OwnerInfoModifyMapper.class);
        OwnerMapper ownerMapper = mock(OwnerMapper.class);
        IdempotencyService idempotencyService = mock(IdempotencyService.class);
        NotificationService notificationService = mock(NotificationService.class);

        OwnerInfoModifyServiceImpl service = new OwnerInfoModifyServiceImpl(
                modifyMapper, ownerMapper, idempotencyService, notificationService);

        // 为每次调用创建新的申请记录实例（避免状态共享问题）
        when(modifyMapper.selectById(anyLong())).thenAnswer(inv -> {
            OwnerInfoModifyApplication app = new OwnerInfoModifyApplication();
            app.setId(applyId);
            app.setCommunityId(communityId);
            app.setOwnerId(1L);
            app.setModifyType("phone_number");
            app.setOldValue("13800000000");
            app.setNewValue("13900000000");
            app.setStatus("pending");
            return app;
        });
        when(modifyMapper.selectByIdForUpdate(anyLong())).thenAnswer(inv -> {
            OwnerInfoModifyApplication app = new OwnerInfoModifyApplication();
            app.setId(applyId);
            app.setCommunityId(communityId);
            app.setOwnerId(1L);
            app.setModifyType("phone_number");
            app.setOldValue("13800000000");
            app.setNewValue("13900000000");
            app.setStatus("pending");
            return app;
        });

        // 使用计数器模拟：只有第一次 checkAndSet 返回 true
        AtomicInteger callCount = new AtomicInteger(0);
        when(idempotencyService.generateKey(eq("info_modify_audit"), anyLong(), anyLong(), anyString()))
                .thenAnswer(inv -> "key:" + inv.getArgument(3));
        when(idempotencyService.checkAndSet(anyString(), anyString(), anyInt()))
                .thenAnswer(inv -> callCount.getAndIncrement() == 0);

        InfoModifyAuditRequest request = new InfoModifyAuditRequest();
        request.setApproved(true);

        // 模拟 N 个请求
        for (int i = 0; i < concurrentCount; i++) {
            service.audit(applyId, request, 1L, "req_" + i);
        }

        // 验证：updateStatus 只被调用1次（只有第一个请求执行了审批）
        verify(modifyMapper, times(1)).updateStatus(eq(applyId), eq("approved"), any(), eq(1L));
        verify(ownerMapper, times(1)).updatePhoneNumber(1L, "13900000000");
    }

    /**
     * 自定义 requestId 生成器
     */
    @Provide
    Arbitrary<String> requestIds() {
        return Arbitraries.strings()
                .withCharRange('a', 'z')
                .ofMinLength(8)
                .ofMaxLength(32)
                .map(s -> "req_" + s);
    }
}
