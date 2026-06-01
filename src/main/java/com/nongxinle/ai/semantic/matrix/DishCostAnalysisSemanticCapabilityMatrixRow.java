package com.nongxinle.ai.semantic.matrix;

import lombok.Builder;
import lombok.Value;

/** 菜品成本+销售单菜分析 Matrix 单行（wire / planType / capability）。 */
@Value
@Builder
public class DishCostAnalysisSemanticCapabilityMatrixRow {

    String rowId;
    String capabilityId;
    String queryObject;
    String operation;
    String metric;
    String structuredIntentDetailWire;
    String targetAnswerPlanType;
    String knownGapCode;
}
