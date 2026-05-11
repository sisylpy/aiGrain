package com.nongxinle.ai.graph.business;

import com.nongxinle.ai.core.AgentNode;
import com.nongxinle.ai.core.AiRunState;
import com.nongxinle.ai.scope.AiRunScopeIntersectService;
import com.nongxinle.ai.scope.AiQueryScope;
import com.nongxinle.ai.trace.AiSseEventPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class BusinessScopeIntersectNode implements AgentNode {

    private final AiRunScopeIntersectService runScopeIntersectService;
    private final AiSseEventPublisher publisher;

    @Override
    public String name() {
        return "ScopeIntersect";
    }

    @Override
    public boolean shouldRun(AiRunState state) {
        return true;
    }

    @Override
    public AiRunState run(AiRunState state) {
        long rid = state.getRunId();
        publisher.publish(rid, "agent_started", Map.of(
                "agent", "ScopeIntersectNode",
                "displayText", "正在校准组织查询范围…"
        ));
        runScopeIntersectService.applyIntersection(state, null);
        AiQueryScope sc = state.getScope();
        Map<String, Object> fin = new LinkedHashMap<>();
        fin.put("agent", "ScopeIntersectNode");
        String note = state.getScopeConvergenceNote();
        fin.put("displayText", note != null && !note.isBlank() ? note : "组织查询范围校准完成");
        if (sc != null && sc.getResolvedDepartmentIds() != null) {
            fin.put("resolvedDepartmentCount", sc.getResolvedDepartmentIds().size());
        }
        publisher.publish(rid, "agent_finished", fin);
        return state;
    }
}
