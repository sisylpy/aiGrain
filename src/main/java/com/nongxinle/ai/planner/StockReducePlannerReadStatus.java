package com.nongxinle.ai.planner;

/**
 * 出库/核销只读桥接返回值状态（C-21）；由 {@link StockReducePlannerAgentAdapter} 映射为 {@link PlannerStepStatus}。
 */
public enum StockReducePlannerReadStatus {
    OK,
    DEGRADED,
    FAILED
}
