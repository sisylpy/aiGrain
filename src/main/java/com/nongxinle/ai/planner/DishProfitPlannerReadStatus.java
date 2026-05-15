package com.nongxinle.ai.planner;

/**
 * 菜品毛利只读桥接返回值状态（C-26）；由 {@link DishProfitPlannerAgentAdapter} 映射为 {@link PlannerStepStatus}。
 */
public enum DishProfitPlannerReadStatus {
    OK,
    DEGRADED,
    FAILED
}
