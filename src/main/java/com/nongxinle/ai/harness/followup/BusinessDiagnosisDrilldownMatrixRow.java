package com.nongxinle.ai.harness.followup;

import lombok.Builder;
import lombok.Value;

/**
 * 经营诊断内下钻矩阵单行（契约见 {@code docs/ai/business-diagnosis-drilldown-matrix-contract.md}）。
 * 不做 NL、不读用户原文、不调用 LLM。
 */
@Value
@Builder
public class BusinessDiagnosisDrilldownMatrixRow {

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

    /**
     * {@code NONE} / {@code EMIT_STORE} / {@code CONSUME_STORE}。
     */
    String resultAnchorStrategy;

    /** BD-D：当前句显式点名门店（非仅继承上轮 STORE 锚）。 */
    boolean requiresExplicitStoreNameInTurn;

    /** BD-C：消费上一轮 STORE resultAnchor / followUp 目标。 */
    boolean consumesPriorStoreAnchor;

    String knownGapCode;
}
