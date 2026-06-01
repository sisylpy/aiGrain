package com.nongxinle.ai.graph.business;

import com.nongxinle.ai.core.AiRunState;
import com.nongxinle.ai.graph.business.execution.ToolRequestContractExecutionParamSupport;
import com.nongxinle.ai.trace.AiSseEventPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class DishIngredientCoverAgentNode {

    private final AiSseEventPublisher publisher;
    private final DishIngredientCoverCostDataEnricher costDataEnricher;

    public void aggregateIfApplicable(AiRunState state) {
        if (!shouldAggregate(state)) {
            return;
        }
        long rid = state.getRunId();
        publisher.publish(rid, "agent_started", Map.of(
                "agent", "DishIngredientCoverAgent",
                "displayText", "正在汇总单菜配料可支撑天数…"
        ));
        costDataEnricher.enrichIfApplicable(state);
        DishIngredientCoverAnswerPlanBuilder.attachIfApplicable(state);
        publisher.publish(rid, "agent_finished", Map.of(
                "agent", "DishIngredientCoverAgent",
                "displayText", "配料可支撑天数已就绪"
        ));
    }

    private static boolean shouldAggregate(AiRunState state) {
        return state != null
                && state.isDishCostAnalysisPath()
                && ToolRequestContractExecutionParamSupport.isDishIngredientCoverDaysContract(
                        state.getResolvedQueryContext());
    }
}
