package com.nongxinle.ai.semantic.matrix;

import lombok.Builder;
import lombok.Value;

/**
 * 菜品销量 Matrix 单行：wire / planType / capability 注册（完整问题）。
 * 契约见 {@code docs/ai/dish-sales-domain-capability-matrix.md}。
 */
@Value
@Builder
public class DishSalesSemanticCapabilityMatrixRow {

    String rowId;

    String queryObject;
    String operation;
    String metric;

    /** 销量侧切面：OVERVIEW / RANKING_HIGH / RANKING_LOW / SINGLE_DISH / TREND 等。 */
    String salesFacet;

    String structuredIntentDetailWire;
    String targetDishSalesPlanType;

    String knownGapCode;
}
