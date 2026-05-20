package com.nongxinle.ai.harness.followup;

import lombok.Builder;
import lombok.Value;

import java.util.Set;

/**
 * 库房库存现量下钻矩阵单行（契约见 {@code docs/ai/warehouse-drilldown-matrix-contract.md}）。
 */
@Value
@Builder
public class WarehouseDrilldownMatrixRow {

    String rowId;

    /**
     * {@code FIRST_TURN}、{@code GOODS_RANKING_FOLLOWUP}、{@code STORE_FOLLOWUP}。
     */
    String rowKind;

    String queryObject;
    String operation;
    String metric;

    /** 库存侧切面：OVERVIEW / STORE_RANKING / GOODS_RANKING / LOW_STOCK / NEAR_EXPIRY 等。 */
    String stockFacet;

    String structuredIntentDetailWire;
    String targetWarehousePlanType;

    String resultAnchorStrategy;
    String knownGapCode;

    Set<String> allowedPriorPlanTypes;

    boolean rejectPriorRankingWire;
}
