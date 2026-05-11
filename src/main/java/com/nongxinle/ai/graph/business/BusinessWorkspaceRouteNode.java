package com.nongxinle.ai.graph.business;

import com.nongxinle.ai.core.AgentNode;
import com.nongxinle.ai.core.AiRunState;
import com.nongxinle.ai.security.AiWorkspaceAccessGuard;
import com.nongxinle.ai.trace.AiSseEventPublisher;
import com.nongxinle.ai.workspace.WorkspaceRouterService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class BusinessWorkspaceRouteNode implements AgentNode {

    private final WorkspaceRouterService workspaceRouterService;
    private final AiWorkspaceAccessGuard workspaceAccessGuard;
    private final AiSseEventPublisher publisher;

    @Override
    public String name() {
        return "WorkspaceRoute";
    }

    @Override
    public boolean shouldRun(AiRunState state) {
        return true;
    }

    @Override
    public AiRunState run(AiRunState state) {
        long rid = state.getRunId();
        publisher.publish(rid, "agent_started", Map.of(
                "agent", "WorkspaceRouterAgent",
                "displayText", "正在识别任务类型…"
        ));
        workspaceRouterService.route(state);
        workspaceAccessGuard.clampAfterWorkspaceRoute(state);
        publisher.publish(rid, "agent_finished", Map.of(
                "agent", "WorkspaceRouterAgent",
                "displayText", "工作空间路由完成",
                "workspaceMode", state.getWorkspaceMode() == null ? "" : state.getWorkspaceMode().name()
        ));
        return state;
    }
}
