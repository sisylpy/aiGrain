package com.nongxinle.ai.planner;

/**
 * {@link PlannerExecutor} 单步执行策略（C-5）。
 * <ul>
 *   <li>{@link #MOCK}：仅根据 {@link PlannerStep} 上 {@code mock*} 字段合成结果，不经过可注入适配器。</li>
 *   <li>{@link #ADAPTER}：每一步委托 {@link PlannerStepExecutor}（v1 可注入 {@link MockPlannerStepExecutor}，不接真实 Agent）。</li>
 * </ul>
 */
public enum PlannerExecutorExecutionMode {

    MOCK,
    ADAPTER
}
