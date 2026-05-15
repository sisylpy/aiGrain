package com.nongxinle.ai.planner;

/**
 * 将单一 {@link PlannerStep}（按 {@code targetAgent}/{@code targetTool}）映射到未来真实业务 Agent 的端口（C-6）。
 * <p>
 * {@link PlannerExecutor} 与编排层<strong>不得</strong>直接依赖 Domain Agent 实现类；仅通过 {@link PlannerStepExecutor}
 * → {@link PlannerAgentAdapterRegistry} → 本接口扩展。
 * </p>
 * <p>本轮：无任何实现调用真实 Agent / Tool / SQL。</p>
 */
public interface PlannerAgentAdapter {

    /**
     * 是否处理该 agent 与 tool 组合；注册表按列表顺序<strong>首个</strong>匹配的适配器执行。
     */
    boolean supports(String targetAgent, String targetTool);

    /**
     * 执行一步；仅当 {@link #supports(String, String)} 为 true 时由 {@link PlannerAgentAdapterRegistry} 调用。
     * 必须返回非 null 的 {@link PlannerStepExecutionResponse}。
     */
    PlannerStepExecutionResponse invoke(PlannerAgentAdapterRequest request);
}
