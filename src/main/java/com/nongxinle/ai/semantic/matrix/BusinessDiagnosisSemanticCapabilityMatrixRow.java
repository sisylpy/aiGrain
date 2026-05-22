package com.nongxinle.ai.semantic.matrix;

import lombok.Builder;
import lombok.Value;

/**
 * 经营诊断 Matrix 单行：wire / planType / facet 注册（完整问题）。
 * 契约见 {@code docs/ai/business-overview-diagnosis-domain-capability-matrix.md}。
 */
@Value
@Builder
public class BusinessDiagnosisSemanticCapabilityMatrixRow {

    String rowId;

    String queryObject;
    String operation;
    String structuredIntentDetailWire;

    /** 与 {@link com.nongxinle.ai.agent.business.BusinessDiagnosisAgentV1} debug {@code diagnosisFacet} 对齐。 */
    String diagnosisFacet;

    /**
     * 与 {@link com.nongxinle.ai.agent.business.BusinessDiagnosisAgentV1} debug {@code diagnosisQuestionType} 对齐；
     * 为空时默认等于 {@link #diagnosisFacet}。
     */
    String diagnosisQuestionType;

    /** P1 子域归因：{@code PURCHASE} / {@code STOCK_REDUCE} / {@code DISH_PROFIT}；其它行为 null。 */
    String childDomain;

    String knownGapCode;
}
