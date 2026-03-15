package com.parking.service.scheduler;

import com.parking.mapper.VisitorAuthorizationMapper;
import com.parking.model.VisitorAuthorization;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.mockito.Mockito.*;

/**
 * VisitorExpireScheduler 单元测试
 * Validates: Requirements 8.3
 */
@ExtendWith(MockitoExtension.class)
class VisitorExpireSchedulerTest {

    @Mock
    private VisitorAuthorizationMapper visitorAuthorizationMapper;

    @InjectMocks
    private VisitorExpireScheduler scheduler;

    private VisitorAuthorization createExpiredAuth(Long id, String carNumber) {
        VisitorAuthorization auth = new VisitorAuthorization();
        auth.setId(id);
        auth.setCommunityId(1001L);
        auth.setCarNumber(carNumber);
        auth.setStatus("approved_pending_activation");
        auth.setExpireTime(LocalDateTime.now().minusHours(1));
        return auth;
    }

    @Test
    @DisplayName("检测到过期授权 - 更新状态为 canceled_no_entry")
    void cancelExpired_shouldUpdateStatus() {
        VisitorAuthorization auth = createExpiredAuth(1L, "京A12345");
        when(visitorAuthorizationMapper.selectExpiredPendingActivation())
                .thenReturn(List.of(auth));

        scheduler.cancelExpiredAuthorizations();

        verify(visitorAuthorizationMapper).updateStatus(1L, "canceled_no_entry");
    }

    @Test
    @DisplayName("无过期授权 - 不执行更新")
    void cancelExpired_shouldSkipWhenNoExpired() {
        when(visitorAuthorizationMapper.selectExpiredPendingActivation())
                .thenReturn(Collections.emptyList());

        scheduler.cancelExpiredAuthorizations();

        verify(visitorAuthorizationMapper, never()).updateStatus(anyLong(), anyString());
    }

    @Test
    @DisplayName("多个过期授权 - 逐个取消")
    void cancelExpired_shouldHandleMultiple() {
        VisitorAuthorization a1 = createExpiredAuth(1L, "京A11111");
        VisitorAuthorization a2 = createExpiredAuth(2L, "京B22222");
        when(visitorAuthorizationMapper.selectExpiredPendingActivation())
                .thenReturn(List.of(a1, a2));

        scheduler.cancelExpiredAuthorizations();

        verify(visitorAuthorizationMapper).updateStatus(1L, "canceled_no_entry");
        verify(visitorAuthorizationMapper).updateStatus(2L, "canceled_no_entry");
    }
}
