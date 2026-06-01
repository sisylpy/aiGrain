package com.nongxinle.ai.semantic.contract;

import com.nongxinle.ai.context.AiResolvedQueryIntent;
import com.nongxinle.ai.semantic.matrix.DishCostAnalysisSemanticCapabilityMatrix;
import com.nongxinle.ai.semantic.matrix.DishCostAnalysisSemanticCapabilityMatrixRow;
import com.nongxinle.ai.tool.business.AiBusinessToolIds;

import java.util.ArrayList;
import java.util.List;

/**
 * 菜品成本+销售单菜分析域 Step 2 小合同只读导出。
 * <p>ACTIVE：Matrix {@link DishCostAnalysisSemanticCapabilityMatrix#firstTurnRows()}（三条单菜合同）。
 * <p>薄导出器：仅 Matrix → {@link MatrixBackedContractExporterSupport} 结构化字段。
 * NL 见 {@code semantic_intake.v1.md}、{@code query_semantic_parser.v2.md}、Harness。
 * 治理见 {@code docs/ai/semantic-contract-exporter-governance.md}。
 */
public final class DishCostAnalysisSemanticCapabilityContractExporter implements SemanticCapabilityContractExporter {

    public static final String DOMAIN_CODE = "DISH_COST";

    public static final DishCostAnalysisSemanticCapabilityContractExporter INSTANCE =
            new DishCostAnalysisSemanticCapabilityContractExporter();

    private DishCostAnalysisSemanticCapabilityContractExporter() {}

    @Override
    public String domain() {
        return DOMAIN_CODE;
    }

    @Override
    public List<SemanticCapabilityContract> exportActiveContracts() {
        List<SemanticCapabilityContract> out = new ArrayList<>();
        for (DishCostAnalysisSemanticCapabilityMatrixRow row :
                DishCostAnalysisSemanticCapabilityMatrix.firstTurnRows()) {
            if (row.getKnownGapCode() == null) {
                out.add(fromMatrixRow(row, SemanticCapabilityContractStatus.ACTIVE, null));
            }
        }
        return List.copyOf(out);
    }

    @Override
    public List<SemanticCapabilityContract> exportPlannedContracts() {
        return List.of();
    }

    @Override
    public List<SemanticCapabilityContract> exportKnownGapContracts() {
        return List.of();
    }

    @Override
    public SemanticCapabilityContractExportSummary exportSummary() {
        List<SemanticCapabilityContract> active = exportActiveContracts();
        return SemanticCapabilityContractExportSummary.builder()
                .domainCode(DOMAIN_CODE)
                .exportedContractCount(active.size())
                .activeContractCount(active.size())
                .plannedContractCount(0)
                .knownGapContractCount(0)
                .knownGapMarkers(List.of())
                .build();
    }

    private static SemanticCapabilityContract fromMatrixRow(
            DishCostAnalysisSemanticCapabilityMatrixRow row,
            SemanticCapabilityContractStatus status,
            String gapMarker) {
        String contractId =
                row.getCapabilityId() != null && !row.getCapabilityId().isBlank()
                        ? row.getCapabilityId()
                        : "dish_cost." + row.getRowId().toLowerCase();
        return MatrixBackedContractExporterSupport.build(
                MatrixBackedContractExporterSupport.MatrixContractExportSpec.builder()
                        .contractId(contractId)
                        .domain(DOMAIN_CODE)
                        .intentCode(AiResolvedQueryIntent.DISH_COST_ANALYSIS)
                        .pathCode(AiResolvedQueryIntent.PATH_DISH_COST_ANALYSIS)
                        .wire(row.getStructuredIntentDetailWire())
                        .queryObject(row.getQueryObject())
                        .operation(row.getOperation())
                        .metric(row.getMetric())
                        .answerPlanType(row.getTargetAnswerPlanType())
                        .requiresAnchor(true)
                        .anchorType("DISH")
                        .selectedTools(selectedToolsForContract(contractId))
                        .status(status)
                        .gapMarker(gapMarker)
                        .build());
    }

    private static List<String> selectedToolsForContract(String contractId) {
        if (DishCostAnalysisSemanticCapabilityMatrix.CONTRACT_DISH_PROFIT_PRESCRIPTION.equals(contractId)) {
            return AiBusinessToolIds.DEFAULT_DISH_PROFIT_PRESCRIPTION_TOOLS;
        }
        if (DishCostAnalysisSemanticCapabilityMatrix.CONTRACT_DISH_INGREDIENT_COVER_DAYS.equals(contractId)) {
            return AiBusinessToolIds.DEFAULT_DISH_INGREDIENT_COVER_DAYS_TOOLS;
        }
        return AiBusinessToolIds.DEFAULT_DISH_COST_ANALYSIS_TOOLS;
    }
}
