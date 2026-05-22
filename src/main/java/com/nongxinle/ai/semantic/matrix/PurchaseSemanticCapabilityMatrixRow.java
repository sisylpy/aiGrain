package com.nongxinle.ai.semantic.matrix;

import lombok.Builder;
import lombok.Value;

import java.util.Set;

/**
 * 采购 GOODS 锚 execution 矩阵单行（契约见 {@code docs/ai/purchase-answer-plan.md}）。
 * 不做 NL、不读用户原文、不调用 LLM。
 */
@Value
@Builder
public class PurchaseSemanticCapabilityMatrixRow {

    String capabilityId;
    /** 上一轮锚类型前提，Phase 1 GOODS 锚行均为 {@code GOODS}。 */
    String anchorType;
    Set<String> allowedQueryObjects;
    Set<String> allowedOperations;
    /** metric 槽须包含其中之一（子串匹配，已归一为大写）。 */
    Set<String> allowedMetricContains;
    String requiredSourceFacet;
    String requiredDetailWanted;
    String requiredStructuredIntentDetailWire;
    String targetPurchasePlanType;
    /** 合同内 operation 形状归一：如 DETAIL → BREAKDOWN；null 表示无 operation canonical mapping。 */
    String operationCanonicalFrom;
    String operationCanonicalTo;
    String canonicalDebugReason;
}
