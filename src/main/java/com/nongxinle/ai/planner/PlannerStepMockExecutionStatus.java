package com.nongxinle.ai.planner;

/**
 * 测试 / Harness：声明某步在 <strong>{@link PlannerExecutor} mock 路径</strong>上应落成的语义（非生产运行时状态机）。
 */
public enum PlannerStepMockExecutionStatus {

    SUCCESS,
    SKIPPED,
    DEGRADED,
    FAILED
}
