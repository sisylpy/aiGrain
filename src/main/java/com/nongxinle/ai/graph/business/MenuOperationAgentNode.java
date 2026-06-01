package com.nongxinle.ai.graph.business;

import com.nongxinle.ai.core.AiRunState;
import com.nongxinle.ai.trace.AiSseEventPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * {@code menu_operation_path}：消费 {@link com.nongxinle.ai.tool.business.AiBusinessToolIds#DISH_PROFIT_ANALYSIS}
 * 快照，挂载 {@link com.nongxinle.ai.dto.business.MenuOperationAnswerPlan}（独立于 DishProfit）。
 */
@Component
@RequiredArgsConstructor
public class MenuOperationAgentNode {

    private final AiSseEventPublisher publisher;

    public void aggregateIfApplicable(AiRunState state) {
        if (!shouldAggregate(state)) {
            return;
        }
        long rid = state.getRunId();
        publisher.publish(rid, "agent_started", Map.of(
                "agent", "MenuOperationAgent",
                "displayText", "正在汇总菜单经营建议…"
        ));
        MenuOperationAnswerPlanBuilder.attachIfApplicable(state);
        publisher.publish(rid, "agent_finished", Map.of(
                "agent", "MenuOperationAgent",
                "displayText", "菜单经营建议已就绪"
        ));
    }

    private static boolean shouldAggregate(AiRunState state) {
        return state != null && state.isMenuOperationPath();
    }
}
