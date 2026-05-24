package com.nongxinle.ai.semantic;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * v2 parser 输入：Step 2 ACTIVE 能力合同摘要（不含 PLANNED / KNOWN_GAP wire）。
 * <p>P4-J2：主 prompt 约束以 {@link #allowedContracts} 为准；散装 union 字段仅 debug。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SemanticParserAllowedOutputContract {

    private String selectedDomain;

    /** P4-J2：ACTIVE 合同 entry 列表；Parser 须从中精确选择 {@code selectedContractId}。 */
    private List<AllowedContractEntry> allowedContracts;

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
        private String detailWanted;
        private String answerPlanType;
        private Boolean requiresAnchor;
        private String anchorType;
        private List<String> selectedTools;
        /** 只读 execution metadata（Catalog 导出；Parser 不得改写）。 */
        private String intentCode;
        private String pathCode;
        private String description;
        private List<String> examples;
    }
}
