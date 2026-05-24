package com.nongxinle.ai.semantic.contract;

import com.nongxinle.ai.context.AiResolvedQueryIntent;
import com.nongxinle.ai.conversation.AiQuerySemanticLexicon;
import com.nongxinle.ai.dto.business.DishSalesAnswerPlan;
import com.nongxinle.ai.semantic.matrix.DishSalesSemanticCapabilityMatrix;
import com.nongxinle.ai.semantic.matrix.DishSalesSemanticCapabilityMatrixRow;
import com.nongxinle.ai.tool.business.AiBusinessToolIds;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 菜品销量域 Step 2 小合同只读导出。
 * <p>ACTIVE：Matrix {@link DishSalesSemanticCapabilityMatrix#firstTurnRows()} 无 knownGap 行。
 * KNOWN_GAP：Matrix 缺口行。
 */
public final class DishSalesSemanticCapabilityContractExporter implements SemanticCapabilityContractExporter {

    public static final String DOMAIN_CODE = "DISH_SALES";

    public static final DishSalesSemanticCapabilityContractExporter INSTANCE =
            new DishSalesSemanticCapabilityContractExporter();

    private static final List<String> DISH_SALES_TOOLS = List.of(AiBusinessToolIds.DISH_PROFIT_ANALYSIS);

    private DishSalesSemanticCapabilityContractExporter() {
    }

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
        List<SemanticCapabilityContract> planned = exportPlannedContracts();
        List<SemanticCapabilityContract> gaps = exportKnownGapContracts();
        List<String> gapMarkers = new ArrayList<>();
        for (SemanticCapabilityContract c : gaps) {
            if (c.getGapMarker() != null) {
                gapMarkers.add(c.getGapMarker());
            }
        }
        for (SemanticCapabilityContract c : planned) {
            if (c.getGapMarker() != null) {
                gapMarkers.add(c.getGapMarker());
            }
        }
        return SemanticCapabilityContractExportSummary.builder()
                .domainCode(DOMAIN_CODE)
                .exportedContractCount(active.size() + planned.size() + gaps.size())
                .activeContractCount(active.size())
                .plannedContractCount(planned.size())
                .knownGapContractCount(gaps.size())
                .knownGapMarkers(gapMarkers)
                .build();
    }

    private static SemanticCapabilityContract fromMatrixRow(
            DishSalesSemanticCapabilityMatrixRow row,
            SemanticCapabilityContractStatus status,
            String gapMarker) {
        SemanticCapabilityContract.SemanticCapabilityContractBuilder builder =
                SemanticCapabilityContract.builder()
                        .contractId(contractIdForRow(row))
                        .domain(DOMAIN_CODE)
                        .intentCode(AiResolvedQueryIntent.DISH_SALES_QUERY)
                        .pathCode(AiResolvedQueryIntent.PATH_DISH_SALES_QUERY)
                        .wire(row.getStructuredIntentDetailWire())
                        .queryObject(row.getQueryObject());
        if (AiQuerySemanticLexicon.STRUCTURED_DISH_SALES_OVERVIEW.equals(
                row.getStructuredIntentDetailWire())) {
            builder.operations(Set.of("OVERVIEW", "SUMMARY")).operation(null);
        } else {
            builder.operation(row.getOperation());
        }
        if (AiQuerySemanticLexicon.STRUCTURED_DISH_SALES_AMOUNT_RANKING_HIGH.equals(
                row.getStructuredIntentDetailWire())) {
            builder.metric("SALES_AMOUNT").metric("LIST_PRICE_REVENUE");
        } else {
            builder.metric(row.getMetric());
        }
        return builder
                .sourceFacet(null)
                .detailWanted(null)
                .answerPlanType(row.getTargetDishSalesPlanType())
                .requiresAnchor(false)
                .anchorType(null)
                .selectedTools(DISH_SALES_TOOLS)
                .status(status)
                .gapMarker(gapMarker)
                .build();
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

    private static SemanticCapabilityContract plannedContract(
            String contractId,
            String wire,
            String answerPlanType,
            String queryObject,
            Set<String> operations,
            Set<String> metrics,
            String gapMarker) {
        return SemanticCapabilityContract.builder()
                .contractId(contractId)
                .domain(DOMAIN_CODE)
                .intentCode(AiResolvedQueryIntent.DISH_SALES_QUERY)
                .pathCode(AiResolvedQueryIntent.PATH_DISH_SALES_QUERY)
                .wire(wire)
                .queryObject(queryObject)
                .operations(operations)
                .metrics(metrics)
                .sourceFacet(null)
                .detailWanted(null)
                .answerPlanType(answerPlanType)
                .requiresAnchor(false)
                .anchorType(null)
                .selectedTools(DISH_SALES_TOOLS)
                .status(SemanticCapabilityContractStatus.PLANNED)
                .gapMarker(gapMarker)
                .build();
    }
}
