package com.nongxinle.ai.graph.business;

import com.nongxinle.ai.core.AiRunState;
import com.nongxinle.ai.trace.AiSseEventPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * {@code dish_cost_analysis_path} + contract {@code dish.profit.prescription.v1}：
 * 挂载 {@link com.nongxinle.ai.dto.business.DishProfitPrescriptionAnswerPlan}。
 */
@Component
@RequiredArgsConstructor
public class DishProfitPrescriptionAgentNode {

    private final AiSseEventPublisher publisher;

    public void aggregateIfApplicable(AiRunState state) {
        if (!shouldAggregate(state)) {
            return;
        }
        long rid = state.getRunId();
        publisher.publish(rid, "agent_started", Map.of(
                "agent", "DishProfitPrescriptionAgent",
                "displayText", "正在汇总单菜利润处方…"
        ));
        DishProfitPrescriptionAnswerPlanBuilder.attachIfApplicable(state);
        publisher.publish(rid, "agent_finished", Map.of(
                "agent", "DishProfitPrescriptionAgent",
                "displayText", "单菜利润处方已就绪"
        ));
    }

    private static boolean shouldAggregate(AiRunState state) {
        return state != null && state.isDishCostAnalysisPath();
    }
}
