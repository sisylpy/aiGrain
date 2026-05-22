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
    String detailWanted;
    String answerPlanType;
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
}
