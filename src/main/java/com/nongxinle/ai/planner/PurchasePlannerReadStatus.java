package com.nongxinle.ai.planner;

/**
 * 采购只读桥接返回值状态（C-16）；由 {@link PurchasePlannerAgentAdapter} 映射为 {@link PlannerStepStatus}。
 */
public enum PurchasePlannerReadStatus {
    OK,
    DEGRADED,
    FAILED
}
