package com.nongxinle.ai.semantic.matrix;

import lombok.Builder;
import lombok.Value;

/**
 * 出库/核销 Matrix 单行：wire / planType 注册（完整问题）。
 * 契约见 {@code docs/ai/stock-reduce-answer-plan.md}。
 */
@Value
@Builder
public class StockReduceSemanticCapabilityMatrixRow {

    String rowId;

    String queryObject;
    String operation;
    String metric;
    String structuredIntentDetailWire;
    String targetStockReducePlanType;

    /** plan 层 reduceType 标签（ALL / TYPE1… / RANKING）。 */
    String reduceTypeLabel;

    String knownGapCode;

    /** facet 切换行：上一轮 wire 不得仍为出库排行 wire（契约 I/J；debug / harness）。 */
    @Builder.Default
    boolean rejectPriorRankingWire = false;
}
