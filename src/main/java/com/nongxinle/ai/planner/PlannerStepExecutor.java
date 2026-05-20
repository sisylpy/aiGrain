package com.nongxinle.ai.planner;

/**
 * Planner 单步执行适配端口：{@link PlannerExecutor} 在 {@link PlannerExecutorExecutionMode#ADAPTER} 下调用本接口，
 * <strong>不</strong>直接依赖 Domain Agent / Tool 实现类（C-5）。
 * <p>
 * 后续可由 Spring Bean 提供真实实现，内部再分派到各业务 Agent 与 Tool；v1 仅 {@link MockPlannerStepExecutor}。
 * 目标 Tool / Agent 键名对照见 {@code docs/ai/planner-executor-v1-design.md} §14.4（文档表格，无 Java 常量类）。
 * </p>
 */
@FunctionalInterface
public interface PlannerStepExecutor {

    /**
     * 执行一步；必须非 null。由调用方保证 {@code request} 与 {@code request#getStep()} 非空。
     */
    PlannerStepExecutionResponse execute(PlannerStepExecutionRequest request);
}
