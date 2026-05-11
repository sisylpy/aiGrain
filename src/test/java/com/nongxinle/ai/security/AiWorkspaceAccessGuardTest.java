package com.nongxinle.ai.security;

import com.nongxinle.ai.context.AiUserContext;
import com.nongxinle.ai.core.AiRunState;
import com.nongxinle.ai.core.AiWorkspaceMode;
import com.nongxinle.ai.trace.AiSseEventPublisher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AiWorkspaceAccessGuardTest {

    @Mock
    AiSseEventPublisher publisher;

    @InjectMocks
    AiWorkspaceAccessGuard guard;

    @Test
    void marketingWorkspace_withPermission_keepsMode() {
        AiUserContext ctx = AiUserContext.builder()
                .permissions(new ArrayList<>(List.of(AiPermissions.ACCESS_MARKETING_WORKSPACE)))
                .build();
        AiRunState st = AiRunState.builder().runId(9001L).workspaceMode(AiWorkspaceMode.MARKETING_GROWTH)
                .aiUserContext(ctx).build();

        guard.clampAfterWorkspaceRoute(st);

        assertThat(st.getWorkspaceMode()).isEqualTo(AiWorkspaceMode.MARKETING_GROWTH);
        assertThat(st.getPermissionDenials()).isEmpty();
    }

    @Test
    void marketingWorkspace_purchaseManager_fallbacksToBusinessChat() {
        AiUserContext ctx = AiUserContext.builder()
                .permissions(new ArrayList<>(List.of(AiPermissions.VIEW_PURCHASE)))
                .build();
        AiRunState st = AiRunState.builder().runId(9002L).workspaceMode(AiWorkspaceMode.MARKETING_GROWTH)
                .aiUserContext(ctx).build();

        guard.clampAfterWorkspaceRoute(st);

        assertThat(st.getWorkspaceMode()).isEqualTo(AiWorkspaceMode.BUSINESS_CHAT);
        assertThat(st.getPermissionDenials()).isNotEmpty();
        verify(publisher).publishError(
                eq(9002L),
                org.mockito.ArgumentMatchers.anyString(),
                eq("workspace access denied"),
                eq("WORKSPACE_ACCESS_DENIED"),
                eq("BusinessError"),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(AiPermissionDenied.class));
    }

    @Test
    void businessChat_unchangedWhenNoMarketing() {
        AiUserContext ctx = AiUserContext.builder()
                .permissions(new ArrayList<>(List.of(AiPermissions.VIEW_PURCHASE)))
                .build();
        AiRunState st = AiRunState.builder().workspaceMode(AiWorkspaceMode.BUSINESS_CHAT).aiUserContext(ctx).build();

        guard.clampAfterWorkspaceRoute(st);

        assertThat(st.getWorkspaceMode()).isEqualTo(AiWorkspaceMode.BUSINESS_CHAT);
        assertThat(st.getPermissionDenials()).isEmpty();
    }
}
