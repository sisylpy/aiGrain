package com.nongxinle.ai.planner;

/**
 * C-26：菜品毛利只读桥接。<strong>禁止</strong>解析用户聊天原文、禁止直接拼接 SQL。
 * 未来 {@link DishProfitPlannerRealReadBridge} 应经 {@link com.nongxinle.ai.graph.business.DishProfitQueryToolExecutor}（见设计文档）。
 */
@FunctionalInterface
public interface DishProfitPlannerReadBridge {

    DishProfitPlannerReadResponse readDishProfit(DishProfitPlannerReadRequest request);
}
