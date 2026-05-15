package com.nongxinle.ai.planner;

/**
 * PlannerExecutor 单步状态（与 {@code docs/ai/planner-executor-v1-design.md} §4 对齐）。
 */
public enum PlannerStepStatus {

    PENDING,
    RUNNING,
    SUCCESS,
    FAILED,
    SKIPPED,
    DEGRADED
}
