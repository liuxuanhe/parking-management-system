package com.parking.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.parking.dto.BatchAuditRequest;
import com.parking.dto.BatchAuditResponse;
import com.parking.mapper.OperationLogMapper;
import com.parking.mapper.OwnerMapper;
import com.parking.mapper.VisitorApplicationMapper;
import com.parking.mapper.VisitorAuthorizationMapper;
import com.parking.model.OperationLog;
import com.parking.model.Owner;
import com.parking.model.VisitorApplication;
import com.parking.service.IdempotencyService;
import com.parking.service.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 批量审核服务单元测试
 * Validates: Requirements 23.1, 23.2, 23.3, 23.4, 23.5, 23.6, 23.7
 */
@ExtendWith(MockitoExtension.class)
class BatchAuditServiceImplTest {

    @Mock
    private OwnerMapper ownerMapper;

    @Mock
    private VisitorApplicationMapper visitorApplicationMapper;

    @Mock
    private VisitorAuthorizationMapper visitorAuthorizationMapper;

    @Mock
    private OperationLogMapper operationLogMapper;

    @Mock
    private IdempotencyService idempotencyService;

    @Mock
    private NotificationService notificationService;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private BatchAuditServiceImpl batchAuditService;

    private static final Long ADMIN_ID = 100L;
    private static final Long COMMUNITY_ID = 1001L;

    @BeforeEach
    void setUp() {
        // RequestContext.getRequestId() 在非 HTTP 请求环境下返回 "N/A"，测试中无需设置
    }

    // ========== 批量审核业主测试 ==========

    @Test
    @DisplayName("批量审核业主 - 全部通过")
    void batchAuditOwners_allApproved() {
        // 准备数据
        BatchAuditRequest request = new BatchAuditRequest();
        request.setIds(Arrays.asList(1L, 2L, 3L));
        request.setAction("approve");
        request.setRequestId("req-001");

        // 模拟幂等键首次请求
        when(idempotencyService.generateKey(anyString(), eq(COMMUNITY_ID), eq(0L), anyString()))
                .thenReturn("batch_audit:1001:0:req-001");
        when(idempotencyService.checkAndSet(anyString(), anyString(), eq(300))).thenReturn(true);

        // 模拟查询业主
        List<Owner> owners = Arrays.asList(
                createOwner(1L, COMMUNITY_ID, "pending"),
                createOwner(2L, COMMUNITY_ID, "pending"),
                createOwner(3L, COMMUNITY_ID, "pending")
        );
        when(ownerMapper.selectByIdsForUpdate(request.getIds())).thenReturn(owners);

        // 执行
        BatchAuditResponse response = batchAuditService.batchAuditOwners(request, ADMIN_ID, COMMUNITY_ID);

        // 验证
        assertEquals(3, response.getSuccessCount());
        assertEquals(0, response.getFailedCount());
        assertTrue(response.getFailedItems().isEmpty());

        // 验证每条记录都调用了更新和日志
        verify(ownerMapper, times(3)).updateAuditStatus(anyLong(), eq("approved"), isNull(), eq(ADMIN_ID));
        verify(operationLogMapper, times(3)).insert(any(OperationLog.class));
    }

    @Test
    @DisplayName("批量审核业主 - 全部驳回")
    void batchAuditOwners_allRejected() {
        BatchAuditRequest request = new BatchAuditRequest();
        request.setIds(Arrays.asList(1L, 2L));
        request.setAction("reject");
        request.setRejectReason("信息不完整");
        request.setRequestId("req-002");

        when(idempotencyService.generateKey(anyString(), eq(COMMUNITY_ID), eq(0L), anyString()))
                .thenReturn("key");
        when(idempotencyService.checkAndSet(anyString(), anyString(), eq(300))).thenReturn(true);

        List<Owner> owners = Arrays.asList(
                createOwner(1L, COMMUNITY_ID, "pending"),
                createOwner(2L, COMMUNITY_ID, "pending")
        );
        when(ownerMapper.selectByIdsForUpdate(request.getIds())).thenReturn(owners);

        BatchAuditResponse response = batchAuditService.batchAuditOwners(request, ADMIN_ID, COMMUNITY_ID);

        assertEquals(2, response.getSuccessCount());
        assertEquals(0, response.getFailedCount());
        verify(ownerMapper, times(2)).updateAuditStatus(anyLong(), eq("rejected"), eq("信息不完整"), eq(ADMIN_ID));
    }

    @Test
    @DisplayName("批量审核业主 - 跳过非 pending 状态记录")
    void batchAuditOwners_skipNonPending() {
        BatchAuditRequest request = new BatchAuditRequest();
        request.setIds(Arrays.asList(1L, 2L, 3L));
        request.setAction("approve");
        request.setRequestId("req-003");

        when(idempotencyService.generateKey(anyString(), eq(COMMUNITY_ID), eq(0L), anyString()))
                .thenReturn("key");
        when(idempotencyService.checkAndSet(anyString(), anyString(), eq(300))).thenReturn(true);

        // ID=2 状态为 approved，应被跳过
        List<Owner> owners = Arrays.asList(
                createOwner(1L, COMMUNITY_ID, "pending"),
                createOwner(2L, COMMUNITY_ID, "approved"),
                createOwner(3L, COMMUNITY_ID, "pending")
        );
        when(ownerMapper.selectByIdsForUpdate(request.getIds())).thenReturn(owners);

        BatchAuditResponse response = batchAuditService.batchAuditOwners(request, ADMIN_ID, COMMUNITY_ID);

        assertEquals(2, response.getSuccessCount());
        assertEquals(1, response.getFailedCount());
        assertEquals(2L, response.getFailedItems().get(0).getId());
        assertTrue(response.getFailedItems().get(0).getReason().contains("approved"));
    }

    @Test
    @DisplayName("批量审核业主 - 跳过不存在的记录")
    void batchAuditOwners_skipNotFound() {
        BatchAuditRequest request = new BatchAuditRequest();
        request.setIds(Arrays.asList(1L, 999L));
        request.setAction("approve");
        request.setRequestId("req-004");

        when(idempotencyService.generateKey(anyString(), eq(COMMUNITY_ID), eq(0L), anyString()))
                .thenReturn("key");
        when(idempotencyService.checkAndSet(anyString(), anyString(), eq(300))).thenReturn(true);

        // 只返回 ID=1 的记录，ID=999 不存在
        List<Owner> owners = Collections.singletonList(createOwner(1L, COMMUNITY_ID, "pending"));
        when(ownerMapper.selectByIdsForUpdate(request.getIds())).thenReturn(owners);

        BatchAuditResponse response = batchAuditService.batchAuditOwners(request, ADMIN_ID, COMMUNITY_ID);

        assertEquals(1, response.getSuccessCount());
        assertEquals(1, response.getFailedCount());
        assertEquals(999L, response.getFailedItems().get(0).getId());
        assertTrue(response.getFailedItems().get(0).getReason().contains("不存在"));
    }

    @Test
    @DisplayName("批量审核业主 - 跳过其他小区的记录")
    void batchAuditOwners_skipOtherCommunity() {
        BatchAuditRequest request = new BatchAuditRequest();
        request.setIds(Arrays.asList(1L, 2L));
        request.setAction("approve");
        request.setRequestId("req-005");

        when(idempotencyService.generateKey(anyString(), eq(COMMUNITY_ID), eq(0L), anyString()))
                .thenReturn("key");
        when(idempotencyService.checkAndSet(anyString(), anyString(), eq(300))).thenReturn(true);

        // ID=2 属于其他小区
        List<Owner> owners = Arrays.asList(
                createOwner(1L, COMMUNITY_ID, "pending"),
                createOwner(2L, 9999L, "pending")
        );
        when(ownerMapper.selectByIdsForUpdate(request.getIds())).thenReturn(owners);

        BatchAuditResponse response = batchAuditService.batchAuditOwners(request, ADMIN_ID, COMMUNITY_ID);

        assertEquals(1, response.getSuccessCount());
        assertEquals(1, response.getFailedCount());
        assertEquals(2L, response.getFailedItems().get(0).getId());
    }

    @Test
    @DisplayName("批量审核业主 - 幂等键重复请求")
    void batchAuditOwners_idempotentDuplicate() {
        BatchAuditRequest request = new BatchAuditRequest();
        request.setIds(Arrays.asList(1L, 2L));
        request.setAction("approve");
        request.setRequestId("req-dup");

        when(idempotencyService.generateKey(anyString(), eq(COMMUNITY_ID), eq(0L), anyString()))
                .thenReturn("key");
        // 幂等键已存在，返回 false
        when(idempotencyService.checkAndSet(anyString(), anyString(), eq(300))).thenReturn(false);

        BatchAuditResponse response = batchAuditService.batchAuditOwners(request, ADMIN_ID, COMMUNITY_ID);

        assertEquals(0, response.getSuccessCount());
        assertEquals(0, response.getFailedCount());
        // 不应调用任何数据库操作
        verify(ownerMapper, never()).selectByIdsForUpdate(any());
    }

    // ========== 批量审批 Visitor 测试 ==========

    @Test
    @DisplayName("批量审批 Visitor - 全部通过")
    void batchAuditVisitors_allApproved() {
        BatchAuditRequest request = new BatchAuditRequest();
        request.setIds(Arrays.asList(10L, 20L));
        request.setAction("approve");
        request.setRequestId("req-v01");

        when(idempotencyService.generateKey(anyString(), eq(COMMUNITY_ID), eq(0L), anyString()))
                .thenReturn("key");
        when(idempotencyService.checkAndSet(anyString(), anyString(), eq(300))).thenReturn(true);

        List<VisitorApplication> apps = Arrays.asList(
                createVisitorApp(10L, COMMUNITY_ID, "submitted"),
                createVisitorApp(20L, COMMUNITY_ID, "submitted")
        );
        when(visitorApplicationMapper.selectByIdsForUpdate(request.getIds())).thenReturn(apps);

        BatchAuditResponse response = batchAuditService.batchAuditVisitors(request, ADMIN_ID, COMMUNITY_ID);

        assertEquals(2, response.getSuccessCount());
        assertEquals(0, response.getFailedCount());
        // 验证创建了授权记录
        verify(visitorAuthorizationMapper, times(2)).insert(any());
        verify(visitorApplicationMapper, times(2)).updateStatus(anyLong(), eq("approved_pending_activation"), isNull(), eq(ADMIN_ID));
    }

    @Test
    @DisplayName("批量审批 Visitor - 全部驳回")
    void batchAuditVisitors_allRejected() {
        BatchAuditRequest request = new BatchAuditRequest();
        request.setIds(Arrays.asList(10L, 20L));
        request.setAction("reject");
        request.setRejectReason("车位不足");
        request.setRequestId("req-v02");

        when(idempotencyService.generateKey(anyString(), eq(COMMUNITY_ID), eq(0L), anyString()))
                .thenReturn("key");
        when(idempotencyService.checkAndSet(anyString(), anyString(), eq(300))).thenReturn(true);

        List<VisitorApplication> apps = Arrays.asList(
                createVisitorApp(10L, COMMUNITY_ID, "submitted"),
                createVisitorApp(20L, COMMUNITY_ID, "submitted")
        );
        when(visitorApplicationMapper.selectByIdsForUpdate(request.getIds())).thenReturn(apps);

        BatchAuditResponse response = batchAuditService.batchAuditVisitors(request, ADMIN_ID, COMMUNITY_ID);

        assertEquals(2, response.getSuccessCount());
        assertEquals(0, response.getFailedCount());
        verify(visitorApplicationMapper, times(2)).updateStatus(anyLong(), eq("rejected"), eq("车位不足"), eq(ADMIN_ID));
        // 驳回不应创建授权记录
        verify(visitorAuthorizationMapper, never()).insert(any());
    }

    @Test
    @DisplayName("批量审批 Visitor - 跳过非 submitted 状态记录")
    void batchAuditVisitors_skipNonSubmitted() {
        BatchAuditRequest request = new BatchAuditRequest();
        request.setIds(Arrays.asList(10L, 20L));
        request.setAction("approve");
        request.setRequestId("req-v03");

        when(idempotencyService.generateKey(anyString(), eq(COMMUNITY_ID), eq(0L), anyString()))
                .thenReturn("key");
        when(idempotencyService.checkAndSet(anyString(), anyString(), eq(300))).thenReturn(true);

        // ID=20 状态为 rejected，应被跳过
        List<VisitorApplication> apps = Arrays.asList(
                createVisitorApp(10L, COMMUNITY_ID, "submitted"),
                createVisitorApp(20L, COMMUNITY_ID, "rejected")
        );
        when(visitorApplicationMapper.selectByIdsForUpdate(request.getIds())).thenReturn(apps);

        BatchAuditResponse response = batchAuditService.batchAuditVisitors(request, ADMIN_ID, COMMUNITY_ID);

        assertEquals(1, response.getSuccessCount());
        assertEquals(1, response.getFailedCount());
        assertEquals(20L, response.getFailedItems().get(0).getId());
    }

    @Test
    @DisplayName("批量审批 Visitor - 幂等键重复请求")
    void batchAuditVisitors_idempotentDuplicate() {
        BatchAuditRequest request = new BatchAuditRequest();
        request.setIds(Arrays.asList(10L));
        request.setAction("approve");
        request.setRequestId("req-v-dup");

        when(idempotencyService.generateKey(anyString(), eq(COMMUNITY_ID), eq(0L), anyString()))
                .thenReturn("key");
        when(idempotencyService.checkAndSet(anyString(), anyString(), eq(300))).thenReturn(false);

        BatchAuditResponse response = batchAuditService.batchAuditVisitors(request, ADMIN_ID, COMMUNITY_ID);

        assertEquals(0, response.getSuccessCount());
        verify(visitorApplicationMapper, never()).selectByIdsForUpdate(any());
    }

    // ========== 辅助方法 ==========

    private Owner createOwner(Long id, Long communityId, String status) {
        Owner owner = new Owner();
        owner.setId(id);
        owner.setCommunityId(communityId);
        owner.setStatus(status);
        owner.setHouseNo("1-101");
        owner.setPhoneNumber("13800138000");
        return owner;
    }

    private VisitorApplication createVisitorApp(Long id, Long communityId, String status) {
        VisitorApplication app = new VisitorApplication();
        app.setId(id);
        app.setCommunityId(communityId);
        app.setStatus(status);
        app.setHouseNo("1-101");
        app.setOwnerId(1L);
        app.setCarPlateId(1L);
        app.setCarNumber("京A12345");
        return app;
    }
}
