package com.nongxinle.ai.security;

import com.nongxinle.ai.context.AiUserContext;
import com.nongxinle.ai.core.AiRunState;
import com.nongxinle.ai.core.AiWorkspaceMode;
import com.nongxinle.ai.trace.AiSseEventPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;

/**
 * 路由层「入口鉴权」：命中某 {@link AiWorkspaceMode} 时需具备对应能力码，
 * 否则软拒绝——落 {@link AiPermissionDenied}、降级到 {@link AiWorkspaceMode#BUSINESS_CHAT}，
 * 并发 {@code error} 帧便于前端弹出权限文案（Run 仍可整体 {@code completed}）。
 */
@Component
@RequiredArgsConstructor
public class AiWorkspaceAccessGuard {

    private final AiSseEventPublisher publisher;

    public void clampAfterWorkspaceRoute(AiRunState state) {
        if (state == null || state.getWorkspaceMode() != AiWorkspaceMode.MARKETING_GROWTH) {
            return;
        }
        AiUserContext ctx = state.getAiUserContext();
        if (ctx != null && ctx.getPermissions() != null
                && ctx.getPermissions().contains(AiPermissions.ACCESS_MARKETING_WORKSPACE)) {
            return;
        }
        AiPermissionDenied denial = AiAnswerBoundary.forMarketingWorkspaceDenied();
        state.getPermissionDenials().add(denial);
        Long runId = state.getRunId();
        if (runId != null) {
            LinkedHashMap<String, Object> ex = new LinkedHashMap<>(2);
            ex.put("workspaceMode", AiWorkspaceMode.MARKETING_GROWTH.name());
            publisher.publishError(
                    runId,
                    denial.getReason(),
                    "workspace access denied",
                    "WORKSPACE_ACCESS_DENIED",
                    "BusinessError",
                    ex,
                    denial);
        }
        state.setWorkspaceMode(AiWorkspaceMode.BUSINESS_CHAT);
    }
}
