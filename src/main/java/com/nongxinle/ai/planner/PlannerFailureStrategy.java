package com.nongxinle.ai.planner;

/**
 * 计划级 / 步骤级失败策略（设计文档 §5）。
 */
public enum PlannerFailureStrategy {

    FAIL_FAST,
    CONTINUE_WITH_DEGRADED,
    ASK_CLARIFICATION
}
