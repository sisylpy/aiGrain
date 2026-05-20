package com.nongxinle.ai.harness.followup;

import lombok.Builder;
import lombok.Value;

import java.util.Set;

/**
 * 出库/核销下钻矩阵单行（契约见 {@code docs/ai/stock-reduce-drilldown-matrix-contract.md}，实现期）。
 * 不做 NL、不读用户原文、不调用 LLM。
 */
@Value
@Builder
public class StockReduceDrilldownMatrixRow {

    /** 文档行号，如 {@code SR-A}；Harness 对照用。 */
    String rowId;

    /**
     * {@code FIRST_TURN}：首轮 structuredIntentDetailWire → planType；
     * {@code FACET_SWITCH}：继承时间/范围，切换子口径 wire（废弃/损失等）；
     * {@code GOODS_WASTE_RANKING}：商品金额排行 + TYPE2/WASTE facet（SQL 未过滤时标 knownGap）。
     */
    String rowKind;

    String queryObject;
    String operation;
    String metric;
    String structuredIntentDetailWire;
    String targetStockReducePlanType;

    /** plan 层 reduceType 标签（ALL / TYPE1… / RANKING）。 */
    String reduceTypeLabel;

    /**
     * 非 null 时写入 summary/plan debug {@code stockReduceKnownGap}（如商品废弃排行未按 TYPE2 过滤）。
     */
    String knownGapCode;

    /** {@code FACET_SWITCH}：允许的上一轮 planType；首轮为空。 */
    Set<String> allowedPriorPlanTypes;

    /** 上一轮 wire 不得为出库排行 wire（防「那废弃呢」仍继承 goods/store 排行）。 */
    boolean rejectPriorRankingWire;
}
