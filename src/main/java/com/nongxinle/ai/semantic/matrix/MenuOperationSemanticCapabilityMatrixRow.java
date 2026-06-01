package com.nongxinle.ai.semantic.matrix;

import lombok.Builder;
import lombok.Value;

/** MenuOperation Matrix 单行：wire / planType / contract 注册。 */
@Value
@Builder
public class MenuOperationSemanticCapabilityMatrixRow {

    String rowId;
    String queryObject;
    String operation;
    String metric;
    String menuFacet;
    String structuredIntentDetailWire;
    String targetMenuOperationPlanType;
    String knownGapCode;
}
