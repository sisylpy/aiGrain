package com.nongxinle.ai.inventory;

/**
 * 库存域查询时间口径（由 AnswerPlan / Tool / 合同矩阵归类，不做 NL 关键词判断）。
 */
public enum InventoryQueryTimeKind {

    /** 当前账面库存快照；数量查询不依赖 startDate/stopDate。 */
    CURRENT_SNAPSHOT,

    /**
     * 库存数量为当前快照，同时附带区间内流水/耗用/销量基线（如概览入库出库、风险可支撑天数、配料日均耗用）。
     */
    HYBRID_SNAPSHOT_WITH_PERIOD_BASELINE,

    /** 库存流水 / 期间统计（出库、消耗、采购入库等），必须有 startDate/stopDate。 */
    PERIOD_FLOW
}
