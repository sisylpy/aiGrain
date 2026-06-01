package com.nongxinle.ai.graph.business;

/** Card 投影模式：由 contract/path 结构化字段决定，不读用户原文。 */
public enum BusinessStatusCardProjection {
    NONE,
    FULL_QUARTET,
    REVENUE_ONLY,
    PURCHASE_ONLY,
    STOCK_RECONCILE_ONLY,
    REORDER_ONLY
}
