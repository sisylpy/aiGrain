package com.nongxinle.ai.harness.replay;

import org.springframework.util.StringUtils;

import java.util.Locale;

/**
 * Harness Replay 执行形态：仅 Resolver，或与生产一致的同步业务图。
 */
public enum AiHarnessReplayMode {
    RESOLVER_ONLY,
    GRAPH_RUN,
    /** PlannerExecutor 纯 mock：{@link AiHarnessBuiltinCases#PLANNER_EXECUTOR_MOCK_CORE} 等。 */
    PLANNER_EXECUTOR_MOCK,
    /**
     * PlannerExecutor {@link PlannerExecutorExecutionMode#ADAPTER} + {@link RevenuePlannerAgentAdapter}（半真实占位，无 readBridge）：
     * {@link AiHarnessBuiltinCases#PLANNER_EXECUTOR_REVENUE_ADAPTER_CORE}。
     */
    PLANNER_EXECUTOR_REVENUE_ADAPTER,
    /**
     * PlannerExecutor ADAPTER + {@link com.nongxinle.ai.planner.PurchasePlannerAgentAdapter}（C-16）：
 * {@link AiHarnessBuiltinCases#PLANNER_EXECUTOR_PURCHASE_ADAPTER_CORE} /
 * {@link AiHarnessBuiltinCases#PLANNER_EXECUTOR_PURCHASE_ADAPTER_FAKE_OK_CORE} /
 * {@link AiHarnessBuiltinCases#PLANNER_EXECUTOR_PURCHASE_ADAPTER_REAL_BRIDGE_CORE}。
     */
    PLANNER_EXECUTOR_PURCHASE_ADAPTER,

    /**
     * PlannerExecutor ADAPTER + {@link com.nongxinle.ai.planner.StockReducePlannerAgentAdapter}（C-21）：
     * {@link AiHarnessBuiltinCases#PLANNER_EXECUTOR_STOCK_REDUCE_ADAPTER_CORE} /
     * {@link AiHarnessBuiltinCases#PLANNER_EXECUTOR_STOCK_REDUCE_ADAPTER_FAKE_OK_CORE}。
     */
    PLANNER_EXECUTOR_STOCK_REDUCE_ADAPTER,

    /**
     * PlannerExecutor ADAPTER + {@link com.nongxinle.ai.planner.DishProfitPlannerAgentAdapter}（C-26 / C-27 / C-29）：
     * {@link AiHarnessBuiltinCases#PLANNER_EXECUTOR_DISH_PROFIT_ADAPTER_CORE} /
     * {@link AiHarnessBuiltinCases#PLANNER_EXECUTOR_DISH_PROFIT_ADAPTER_FAKE_OK_CORE} /
     * {@link AiHarnessBuiltinCases#PLANNER_EXECUTOR_DISH_PROFIT_ADAPTER_REAL_BRIDGE_CORE} /
     * {@link AiHarnessBuiltinCases#PLANNER_EXECUTOR_DISH_PROFIT_ADAPTER_REAL_BRIDGE_HYDRATED_CORE}。
     */
    PLANNER_EXECUTOR_DISH_PROFIT_ADAPTER,

    /**
     * C-54：{@link AiHarnessReplayCompositeGate} — 仅
     * {@link com.nongxinle.ai.planner.BusinessDiagnosisCompositeProductionGate}，不执行 {@code PlannerExecutor} / Tool。
     */
    BUSINESS_DIAGNOSIS_COMPOSITE_GATE;

    public static AiHarnessReplayMode fromApiString(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        return valueOf(raw.trim().toUpperCase(Locale.ROOT));
    }
}
