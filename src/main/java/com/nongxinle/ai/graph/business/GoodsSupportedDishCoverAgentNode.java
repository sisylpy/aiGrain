package com.nongxinle.ai.graph.business;

import com.nongxinle.ai.core.AiRunState;
import com.nongxinle.ai.graph.business.execution.ToolRequestContractExecutionParamSupport;
import com.nongxinle.ai.trace.AiSseEventPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class GoodsSupportedDishCoverAgentNode {

    private final AiSseEventPublisher publisher;

    public void aggregateIfApplicable(AiRunState state) {
        if (!shouldAggregate(state)) {
            return;
        }
        long rid = state.getRunId();
        publisher.publish(
                rid,
                "agent_started",
                Map.of(
                        "agent",
                        "GoodsSupportedDishCoverAgent",
                        "displayText",
                        "正在汇总库存与关联菜品…"));
        GoodsSupportedDishCoverAnswerPlanBuilder.attachIfApplicable(state);
        publisher.publish(
                rid,
                "agent_finished",
                Map.of(
                        "agent",
                        "GoodsSupportedDishCoverAgent",
                        "displayText",
                        "关联菜品明细已就绪"));
    }

    private static boolean shouldAggregate(AiRunState state) {
        return state != null
                && state.isWarehouseStockOverviewPath()
                && ToolRequestContractExecutionParamSupport.isGoodsSupportedDishCoverContract(
                        state.getResolvedQueryContext());
    }
}
