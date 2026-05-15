package com.nongxinle.ai.planner;

/**
 * 营收只读桥接返回值状态（C-8）；与 {@link PlannerStepStatus} 对应但保留业务细分，由 {@link RevenuePlannerAgentAdapter} 映射。
 */
public enum RevenuePlannerReadStatus {
    OK,
    DEGRADED,
    FAILED
}
