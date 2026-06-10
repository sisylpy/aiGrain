package com.nongxinle.ai.semantic;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * v2 parser 输入：Step 2 能力合同摘要。
 * <p>P4-J2：{@code selectedContractId} 只能从 {@link #allowedContracts}（ACTIVE）精确选择；
 * {@link #knownGapContracts} 仅作边界观测，禁止选为 selectedContractId。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SemanticParserAllowedOutputContract {

    private String selectedDomain;

    /** P4-J2：ACTIVE 合同 entry 列表；Parser 须从中精确选择 {@code selectedContractId}。 */
    private List<AllowedContractEntry> allowedContracts;

    /**
     * Catalog KNOWN_GAP / 未开放能力（只读边界）；Parser 须对照 {@code selectionHint}/{@code negativeHint}，
     * 问法命中缺口且 allowed 内无对应 ACTIVE 合同时输出 {@code needClarification=true}，禁止用相近 ACTIVE 合同凑合。
     */
    private List<AllowedContractEntry> knownGapContracts;

    /**
     * 域级合同选择边界（P4 起 Java 主链恒为 null；边界见 {@code query_semantic_parser.v2.md} 与 {@code knownGapContracts}）。
     */
    private List<String> contractSelectionBoundaryHints;

    /** debug：allowedContracts 扁平化 wire 集合。 */
    private List<String> allowedWires;
    private List<String> allowedQueryObjects;
    private List<String> allowedOperations;
    private List<String> allowedMetrics;
    private List<String> allowedSourceFacets;
    private List<String> allowedDetailWanted;
    private List<String> allowedAnswerPlanTypes;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AllowedContractEntry {
        private String contractId;
        private String wire;
        private List<String> queryObjects;
        /** 单值便捷字段（合同仅一个 operation 时）。 */
        private String operation;
        private List<String> operations;
        /** 单值便捷字段（合同仅一个 queryObject 时）。 */
        private String queryObject;
        /** 单值便捷字段（合同仅一个 metric 时）。 */
        private String metric;
        private List<String> metrics;
        private String sourceFacet;
        /** 非空时 {@code sourceFacet} 为默认值，Parser 可选集合内任一值。 */
        private List<String> allowedSourceFacets;
        private String detailWanted;
        private String answerPlanType;
        private Boolean requiresAnchor;
        private String anchorType;
        private List<String> selectedTools;
        /** 只读 execution metadata（Catalog 导出；Parser 不得改写）。 */
        private String intentCode;
        private String pathCode;
        /** 合同用途简述。 */
        private String description;
        /** 何时应选本合同。 */
        private String selectionHint;
        /** 何时不应选本合同。 */
        private String negativeHint;
        /** 应选本合同的问法示例。 */
        private List<String> positiveExamples;
        /** 不应选本合同的问法示例。 */
        private List<String> negativeExamples;
        /** ACTIVE / KNOWN_GAP / PLANNED（knownGapContracts 条目为 KNOWN_GAP 或 PLANNED）。 */
        private String capabilityStatus;
        /** Catalog gap marker（KNOWN_GAP 时）。 */
        private String gapMarker;
        /** @deprecated 使用 {@link #positiveExamples} */
        @Deprecated
        private List<String> examples;
    }
}
