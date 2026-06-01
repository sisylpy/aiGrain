package com.nongxinle.ai.semantic.contract;

import com.nongxinle.ai.context.AiResolvedQueryIntent;
import com.nongxinle.ai.conversation.AiQuerySemanticLexicon;
import com.nongxinle.ai.semantic.matrix.MenuOperationSemanticCapabilityMatrix;
import com.nongxinle.ai.semantic.matrix.MenuOperationSemanticCapabilityMatrixRow;
import com.nongxinle.ai.tool.business.AiBusinessToolIds;

import java.util.ArrayList;
import java.util.List;

/**
 * MenuOperation 域 Step 2 能力合同只读导出。
 * <p>薄导出器：仅 Matrix → {@link MatrixBackedContractExporterSupport} 结构化字段。
 * NL 见 {@code semantic_intake.v1.md}、{@code query_semantic_parser.v2.md}、Harness。
 * 治理见 {@code docs/ai/semantic-contract-exporter-governance.md}。
 */
public final class MenuOperationSemanticCapabilityContractExporter
        implements SemanticCapabilityContractExporter {

    public static final String DOMAIN_CODE = "MENU_OPERATION";

    public static final MenuOperationSemanticCapabilityContractExporter INSTANCE =
            new MenuOperationSemanticCapabilityContractExporter();

    private static final List<String> MENU_OPERATION_TOOLS =
            List.of(AiBusinessToolIds.DISH_PROFIT_ANALYSIS);

    private MenuOperationSemanticCapabilityContractExporter() {}

    @Override
    public String domain() {
        return DOMAIN_CODE;
    }

    @Override
    public List<SemanticCapabilityContract> exportActiveContracts() {
        List<SemanticCapabilityContract> out = new ArrayList<>();
        for (MenuOperationSemanticCapabilityMatrixRow row :
                MenuOperationSemanticCapabilityMatrix.firstTurnRows()) {
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
            MenuOperationSemanticCapabilityMatrixRow row,
            SemanticCapabilityContractStatus status,
            String gapMarker) {
        return MatrixBackedContractExporterSupport.build(
                MatrixBackedContractExporterSupport.MatrixContractExportSpec.builder()
                        .contractId(contractIdForRow(row))
                        .domain(DOMAIN_CODE)
                        .intentCode(AiResolvedQueryIntent.MENU_OPERATION)
                        .pathCode(AiResolvedQueryIntent.PATH_MENU_OPERATION)
                        .wire(row.getStructuredIntentDetailWire())
                        .queryObject(row.getQueryObject())
                        .operation(row.getOperation())
                        .metric(row.getMetric())
                        .sourceFacet(row.getMenuFacet())
                        .answerPlanType(row.getTargetMenuOperationPlanType())
                        .selectedTools(MENU_OPERATION_TOOLS)
                        .status(status)
                        .gapMarker(gapMarker)
                        .build());
    }

    static String contractIdForRow(MenuOperationSemanticCapabilityMatrixRow row) {
        if (row == null || row.getStructuredIntentDetailWire() == null) {
            return "menu.unknown";
        }
        return switch (row.getStructuredIntentDetailWire()) {
            case AiQuerySemanticLexicon.STRUCTURED_MENU_OPERATION_OVERVIEW -> "menu.operation.overview.v1";
            case AiQuerySemanticLexicon.STRUCTURED_MENU_DISH_HIGH_SALES_LOW_PROFIT ->
                    "menu.dish.high_sales_low_profit.v1";
            case AiQuerySemanticLexicon.STRUCTURED_MENU_ACTION_RECOMMENDATION ->
                    "menu.action.recommendation.v1";
            default -> "menu." + row.getStructuredIntentDetailWire().replace('_', '.') + ".v1";
        };
    }
}
