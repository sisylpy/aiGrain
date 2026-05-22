package com.nongxinle.ai.semantic.matrix;

import lombok.Builder;
import lombok.Value;

/**
 * 经营概览四域 MULTI_AGENT 矩阵单行（wire → 槽位形状 + 聚合 planType 提示）。
 */
@Value
@Builder
public class BusinessOverviewSemanticCapabilityMatrixRow {

    String rowId;

    String queryObject;
    String operation;
    String metric;

    String structuredIntentDetailWire;

    /** 与 {@link com.nongxinle.ai.dto.business.BusinessOverviewAnswerPlan} planType 对齐；观测用。 */
    String targetOverviewPlanType;

    String knownGapCode;
}
