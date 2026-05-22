package com.nongxinle.ai.semantic;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * v2 parser 输入：Step 2 ACTIVE 能力合同摘要（不含 PLANNED / KNOWN_GAP wire）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SemanticParserAllowedOutputContract {

    private String selectedDomain;
    private List<String> allowedWires;
    private List<String> allowedQueryObjects;
    private List<String> allowedOperations;
    private List<String> allowedMetrics;
    private List<String> allowedSourceFacets;
    private List<String> allowedDetailWanted;
    private List<String> allowedAnswerPlanTypes;
}
