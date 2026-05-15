package com.nongxinle.ai.graph.business;

import com.nongxinle.ai.core.AiRunState;
import com.nongxinle.ai.dto.business.DishProfitAnswerPlan;
import com.nongxinle.ai.tool.business.AiBusinessToolIds;

import java.util.Map;

/**
 * 挂载 {@link DishProfitAnswerPlan}：对齐采购等子域「工具成功 → AnswerPlanAttach」契约；
 * AnswerPlan 的字段仍由 Tool 快照与 Overview 推导；子 Agent 填入 Master trace 的 {@code answerPlan}／{@code resultType}。
 */
public final class DishProfitAnswerPlanBuilder {

    private DishProfitAnswerPlanBuilder() {
    }

    /**
     * 菜品子 Agent 在工具成功写入快照后调用：与 {@link DishProfitAgentNode#deriveOverview} 同源挂载 AnswerPlan，
     * {@code orchestrationEnvelope} 仅影响 debug 标记（Master 聚合 trace）。
     */
    public static void attachForAgentEnvelope(AiRunState state, boolean orchestrationEnvelope) {
        if (state == null || !toolEnvelopeSuccessQuick(state)) {
            return;
        }
        state.setDishProfitAnswerPlan(null);
        DishProfitAgentNode.computeOverviewAndAttachPlans(state, orchestrationEnvelope);
    }

    private static boolean toolEnvelopeSuccessQuick(AiRunState state) {
        if (state == null || state.getToolResults() == null) {
            return false;
        }
        Object env = state.getToolResults().get(AiBusinessToolIds.DISH_PROFIT_ANALYSIS);
        if (!(env instanceof Map<?, ?> m)) {
            return false;
        }
        return Boolean.TRUE.equals(m.get("success"));
    }
}
