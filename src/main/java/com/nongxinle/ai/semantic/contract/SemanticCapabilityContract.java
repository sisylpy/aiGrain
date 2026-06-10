package com.nongxinle.ai.semantic.contract;

import lombok.Builder;
import lombok.Singular;
import lombok.Value;

import java.util.List;
import java.util.Set;

/**
 * 单条语义能力合同（P1-A 最小字段集；由 Matrix 行只读导出，不含 compat alias）。
 */
@Value
@Builder
public class SemanticCapabilityContract {

    String contractId;
    String domain;
    String intentCode;
    String pathCode;
    /** canonical {@code structuredIntentDetailWire}。 */
    String wire;
    @Singular("queryObject")
    Set<String> queryObjects;
    @Singular("operation")
    Set<String> operations;
    /** metric 须包含其中之一（与 Matrix {@code allowedMetricContains} 对齐）。 */
    @Singular("metric")
    Set<String> metrics;
    String sourceFacet;
    /** 非空时 {@code sourceFacet} 为 completion 默认值，LLM 槽位须落在此集合内。 */
    @Singular("allowedSourceFacet")
    Set<String> allowedSourceFacets;
    String detailWanted;
    String answerPlanType;
    /**
     * 本轮应产出的 AnswerPlan 类型键（可多值，如 bundle 双卡）；空则执行层回退 {@link #answerPlanType}。
     */
    @Singular("planOutput")
    List<String> planOutputs;
    boolean requiresAnchor;
    String anchorType;
    @Singular("selectedTool")
    List<String> selectedTools;
    SemanticCapabilityContractStatus status;
    /**
     * 缺口标记（仅 {@link SemanticCapabilityContractStatus#KNOWN_GAP} / {@link SemanticCapabilityContractStatus#PLANNED}）。
     * 例如 {@code first_turn_purchase_goods_amount_ranking_missing_contract}。
     */
    String gapMarker;
    /**
     * @deprecated 勿在 Exporter 新增；NL 放 Intake/V2/Harness。存量域 P2–P3 瘦身删除。
     */
    @Deprecated
    String description;
    /**
     * @deprecated 勿在 Exporter 新增；NL 放 Intake/V2/Harness。存量域 P2–P3 瘦身删除。
     */
    @Deprecated
    String selectionHint;
    /**
     * @deprecated 勿在 Exporter 新增；NL 放 Intake/V2/Harness。存量域 P2–P3 瘦身删除。
     */
    @Deprecated
    String negativeHint;
    /**
     * @deprecated 勿在 Exporter 新增；回归问句放 Harness。存量域 P2–P3 瘦身删除。
     */
    @Deprecated
    @Singular("positiveExample")
    List<String> positiveExamples;
    /**
     * @deprecated 勿在 Exporter 新增；互斥用 contractId 指针放 V2。存量域 P2–P3 瘦身删除。
     */
    @Deprecated
    @Singular("negativeExample")
    List<String> negativeExamples;
}
