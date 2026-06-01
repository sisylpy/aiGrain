package com.nongxinle.ai.semantic.contract;

import com.nongxinle.ai.context.AiResolvedQueryIntent;
import com.nongxinle.ai.conversation.AiQuerySemanticLexicon;
import com.nongxinle.ai.semantic.matrix.DishSalesSemanticCapabilityMatrix;
import com.nongxinle.ai.semantic.matrix.DishSalesSemanticCapabilityMatrixRow;
import com.nongxinle.ai.tool.business.AiBusinessToolIds;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 菜品销量域 Step 2 小合同只读导出。
 * <p>ACTIVE / KNOWN_GAP：Matrix {@link DishSalesSemanticCapabilityMatrix#firstTurnRows()}。
 * <p>薄导出器：仅 Matrix → {@link MatrixBackedContractExporterSupport} 结构化字段。
 * NL 见 {@code semantic_intake.v1.md}、{@code query_semantic_parser.v2.md}、Harness。
 * 治理见 {@code docs/ai/semantic-contract-exporter-governance.md}。
 */
public final class DishSalesSemanticCapabilityContractExporter implements SemanticCapabilityContractExporter {

    public static final String DOMAIN_CODE = "DISH_SALES";

    public static final DishSalesSemanticCapabilityContractExporter INSTANCE =
            new DishSalesSemanticCapabilityContractExporter();

    private static final List<String> DISH_SALES_QUERY_TOOLS = List.of(AiBusinessToolIds.DISH_SALES_ANALYSIS_CARD);

    private DishSalesSemanticCapabilityContractExporter() {}

    @Override
    public String domain() {
        return DOMAIN_CODE;
    }

    @Override
    public List<SemanticCapabilityContract> exportActiveContracts() {
        List<SemanticCapabilityContract> out = new ArrayList<>();
        for (DishSalesSemanticCapabilityMatrixRow row : DishSalesSemanticCapabilityMatrix.firstTurnRows()) {
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
        List<SemanticCapabilityContract> out = new ArrayList<>();
        for (DishSalesSemanticCapabilityMatrixRow row : DishSalesSemanticCapabilityMatrix.firstTurnRows()) {
            if (row.getKnownGapCode() != null) {
                out.add(fromMatrixRow(row, SemanticCapabilityContractStatus.KNOWN_GAP, row.getKnownGapCode()));
            }
        }
        return List.copyOf(out);
    }

    @Override
    public SemanticCapabilityContractExportSummary exportSummary() {
        List<SemanticCapabilityContract> active = exportActiveContracts();
        List<SemanticCapabilityContract> gaps = exportKnownGapContracts();
        List<String> gapMarkers = new ArrayList<>();
        for (SemanticCapabilityContract c : gaps) {
            if (c.getGapMarker() != null) {
                gapMarkers.add(c.getGapMarker());
            }
        }
        return SemanticCapabilityContractExportSummary.builder()
                .domainCode(DOMAIN_CODE)
                .exportedContractCount(active.size() + gaps.size())
                .activeContractCount(active.size())
                .plannedContractCount(0)
                .knownGapContractCount(gaps.size())
                .knownGapMarkers(gapMarkers)
                .build();
    }

    private static SemanticCapabilityContract fromMatrixRow(
            DishSalesSemanticCapabilityMatrixRow row,
            SemanticCapabilityContractStatus status,
            String gapMarker) {
        MatrixBackedContractExporterSupport.MatrixContractExportSpec.MatrixContractExportSpecBuilder b =
                MatrixBackedContractExporterSupport.MatrixContractExportSpec.builder()
                        .contractId(contractIdForRow(row))
                        .domain(DOMAIN_CODE)
                        .intentCode(AiResolvedQueryIntent.DISH_SALES_QUERY)
                        .pathCode(AiResolvedQueryIntent.PATH_DISH_SALES_QUERY)
                        .wire(row.getStructuredIntentDetailWire())
                        .queryObject(row.getQueryObject())
                        .answerPlanType(row.getTargetDishSalesPlanType())
                        .requiresAnchor(row.isRequiresAnchor())
                        .anchorType(row.isRequiresAnchor() ? row.getAnchorType() : null)
                        .selectedTools(selectedToolsForRow(row))
                        .status(status)
                        .gapMarker(gapMarker);
        if (AiQuerySemanticLexicon.STRUCTURED_DISH_SALES_OVERVIEW.equals(
                row.getStructuredIntentDetailWire())) {
            b.operations(Set.of("OVERVIEW", "SUMMARY"));
        } else {
            b.operation(row.getOperation());
        }
        if (AiQuerySemanticLexicon.STRUCTURED_DISH_SALES_AMOUNT_RANKING_HIGH.equals(
                row.getStructuredIntentDetailWire())) {
            b.metric("SALES_AMOUNT").metric("LIST_PRICE_REVENUE");
        } else {
            b.metric(row.getMetric());
        }
        return MatrixBackedContractExporterSupport.build(b.build());
    }

    private static List<String> selectedToolsForRow(DishSalesSemanticCapabilityMatrixRow row) {
        return DISH_SALES_QUERY_TOOLS;
    }

    private static String contractIdForRow(DishSalesSemanticCapabilityMatrixRow row) {
        return switch (row.getRowId()) {
            case "DS-G" -> "dish_sales.overview";
            case "DS-A" -> "dish_sales.count_ranking_high";
            case "DS-B" -> "dish_sales.amount_ranking_high";
            case "DS-C" -> "dish_sales.count_ranking_low";
            case "DS-D" -> "dish_sales.single_dish";
            case "DS-E" -> "dish_sales.store_count_ranking";
            case "DS-F" -> "dish_sales.store_single_dish";
            case "DS-I" -> "dish_sales.cross_domain_profit";
            case "DS-J" -> "dish_sales.trend";
            default -> "dish_sales." + row.getRowId().toLowerCase();
        };
    }
}
