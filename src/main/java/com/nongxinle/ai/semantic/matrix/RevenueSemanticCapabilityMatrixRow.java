package com.nongxinle.ai.semantic.matrix;

import lombok.Builder;
import lombok.Value;

/**
 * 营业额/营收 Matrix 单行：wire / planType 注册（完整问题）。
 * 契约见 {@code docs/ai/revenue-answer-plan.md}。
 */
@Value
@Builder
public class RevenueSemanticCapabilityMatrixRow {

    String rowId;

    String queryObject;
    String operation;
    String metric;
    String structuredIntentDetailWire;
    String targetRevenuePlanType;

    String knownGapCode;
}
