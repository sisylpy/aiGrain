package com.nongxinle.ai.harness.followup;

import lombok.Builder;
import lombok.Value;

import java.util.Set;

/**
 * 菜品销量下钻矩阵单行（契约见 {@code docs/ai/dish-sales-drilldown-matrix-contract.md}）。
 */
@Value
@Builder
public class DishSalesDrilldownMatrixRow {

    String rowId;

    /**
     * {@code FIRST_TURN}、{@code TIME_FOLLOWUP}、{@code RANKING_FOLLOWUP}。
     */
    String rowKind;

    String queryObject;
    String operation;
    String metric;

    /** 销量侧切面：OVERVIEW / RANKING_HIGH / RANKING_LOW / SINGLE_DISH / TREND 等。 */
    String salesFacet;

    String structuredIntentDetailWire;
    String targetDishSalesPlanType;

    String resultAnchorStrategy;
    String knownGapCode;

    Set<String> allowedPriorPlanTypes;

    boolean rejectPriorRankingWire;
}
