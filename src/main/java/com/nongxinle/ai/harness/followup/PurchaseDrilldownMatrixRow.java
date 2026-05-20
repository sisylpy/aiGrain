package com.nongxinle.ai.harness.followup;

import lombok.Builder;
import lombok.Value;

import java.util.Set;

/**
 * 采购 GOODS 锚点下钻矩阵单行（契约见 {@code docs/ai/purchase-drilldown-matrix-contract.md}）。
 * 不做 NL、不读用户原文、不调用 LLM。
 */
@Value
@Builder
public class PurchaseDrilldownMatrixRow {

    String capabilityId;
    /** 上一轮锚类型前提，Phase 1 GOODS 锚行均为 {@code GOODS}。 */
    String anchorType;
    Set<String> allowedPriorFramePlanTypes;
    Set<String> allowedQueryObjects;
    Set<String> allowedOperations;
    /** metric 槽须包含其中之一（子串匹配，已归一为大写）。 */
    Set<String> allowedMetricContains;
    String requiredSourceFacet;
    String requiredDetailWanted;
    String requiredStructuredIntentDetailWire;
    String targetPurchasePlanType;
    /** 合同内 operation 同义归一：如 DETAIL → BREAKDOWN；null 表示无 alias。 */
    String operationCanonicalFrom;
    String operationCanonicalTo;
    String canonicalDebugReason;
}
