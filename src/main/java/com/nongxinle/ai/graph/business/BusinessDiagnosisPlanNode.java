package com.nongxinle.ai.graph.business;

import com.nongxinle.ai.core.AgentNode;
import com.nongxinle.ai.core.AiRunState;
import com.nongxinle.ai.trace.AiSseEventPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 经营诊断：在采购/出库 Tool 与菜品毛利 Agent 之后汇总 {@link com.nongxinle.ai.dto.business.BusinessDiagnosisPlan}。
 */
@Component
@RequiredArgsConstructor
public class BusinessDiagnosisPlanNode implements AgentNode {

    private final AiSseEventPublisher publisher;

    @Override
    public String name() {
        return "BusinessDiagnosisPlan";
    }

    @Override
    public boolean shouldRun(AiRunState state) {
        return state != null && state.isBusinessDiagnosisPath();
    }

    @Override
    public AiRunState run(AiRunState state) {
        long rid = state.getRunId();
        publisher.publish(rid, "agent_started", Map.of(
                "agent", "BusinessDiagnosisPlanNode",
                "displayText", "正在汇总经营诊断计划…"
        ));
        state.setBusinessDiagnosisPlan(BusinessDiagnosisPlanBuilder.build(state));
        publisher.publish(rid, "agent_finished", Map.of(
                "agent", "BusinessDiagnosisPlanNode",
                "displayText", "经营诊断计划已就绪"
        ));
        return state;
    }
}
