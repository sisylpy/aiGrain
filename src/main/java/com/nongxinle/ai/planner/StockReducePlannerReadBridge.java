package com.nongxinle.ai.planner;

/**
 * C-21/C-22：出库/核销只读桥接。<strong>禁止</strong>解析用户聊天原文、禁止直接拼接 SQL。
 * {@link StockReducePlannerRealReadBridge} 的真实入口为 {@link StockReducePlannerRealReadBridge#readWithExecutionContext}；
 * 生产链路后续应经 {@link com.nongxinle.ai.graph.business.StockReduceQueryToolExecutor} 等（见 RealBridge 文档）。
 */
@FunctionalInterface
public interface StockReducePlannerReadBridge {

    StockReducePlannerReadResponse readStockReduce(StockReducePlannerReadRequest request);
}
