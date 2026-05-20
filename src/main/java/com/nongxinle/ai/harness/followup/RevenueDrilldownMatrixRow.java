package com.nongxinle.ai.harness.followup;

import lombok.Builder;
import lombok.Value;

import java.util.Set;

/**
 * 营业额/营收下钻矩阵单行（契约见 {@code docs/ai/revenue-drilldown-matrix-contract.md}）。
 * 不做 NL、不读用户原文、不调用 LLM。
 */
@Value
@Builder
public class RevenueDrilldownMatrixRow {

    String rowId;

    /**
     * {@code FIRST_TURN}、{@code TIME_FOLLOWUP}（继承范围切时间）、{@code RANKING_FOLLOWUP}（继承时间切排行）。
     */
    String rowKind;

    String queryObject;
    String operation;
    String metric;
    String structuredIntentDetailWire;
    String targetRevenuePlanType;

    /** {@code NONE} / {@code STORE} / {@code STORE_PAIR}（矩阵契约；P1 AnswerPlan 尚无 resultAnchor 字段）。 */
    String resultAnchorStrategy;

    String knownGapCode;

    Set<String> allowedPriorPlanTypes;

    /** 追问切排行/总览时，上一轮不得仍为 compare / 另一排行 wire。 */
    boolean rejectPriorCompareOrRankingWire;
}
