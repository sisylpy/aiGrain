package com.nongxinle.ai.semantic.contract;

import com.nongxinle.ai.context.AiResolvedQueryIntent;
import com.nongxinle.ai.conversation.AiQuerySemanticLexicon;
import com.nongxinle.ai.dto.business.StockReduceAnswerPlan;
import com.nongxinle.ai.semantic.matrix.StockReduceSemanticCapabilityMatrix;
import com.nongxinle.ai.semantic.matrix.StockReduceSemanticCapabilityMatrixRow;
import com.nongxinle.ai.tool.business.AiBusinessToolIds;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 出库/核销域 Step 2 小合同只读导出。
 * <p>ACTIVE：Matrix {@link StockReduceSemanticCapabilityMatrix#firstTurnRows()} 无 knownGap 行。
 * KNOWN_GAP：Matrix 缺口行；PLANNED：PlanBuilder 已挂载但 Matrix 首轮未登记。
 */
public final class StockReduceSemanticCapabilityContractExporter implements SemanticCapabilityContractExporter {

    public static final String DOMAIN_CODE = "STOCK_REDUCE";

    public static final StockReduceSemanticCapabilityContractExporter INSTANCE =
            new StockReduceSemanticCapabilityContractExporter();

    private static final List<String> STOCK_REDUCE_TOOLS = List.of(AiBusinessToolIds.STOCK_REDUCE_QUERY);

    private StockReduceSemanticCapabilityContractExporter() {
    }

    @Override
    public String domain() {
        return DOMAIN_CODE;
    }

    @Override
    public List<SemanticCapabilityContract> exportActiveContracts() {
        List<SemanticCapabilityContract> out = new ArrayList<>();
        for (StockReduceSemanticCapabilityMatrixRow row : StockReduceSemanticCapabilityMatrix.firstTurnRows()) {
            out.add(fromMatrixRow(row, SemanticCapabilityContractStatus.ACTIVE, null));
        }
        return List.copyOf(out);
    }

    @Override
    public List<SemanticCapabilityContract> exportPlannedContracts() {
        return List.of(
                plannedContract(
                        "stock_reduce.produce_output",
                        AiQuerySemanticLexicon.STRUCTURED_PRODUCE_OUTPUT,
                        StockReduceAnswerPlan.TYPE_STOCK_REDUCE_OUTPUT_OVERVIEW,
                        "ALL",
                        Set.of("SUMMARY"),
                        Set.of("PRODUCTION_OUTPUT"),
                        StockReduceAnswerPlan.REDUCE_TYPE_TYPE1,
                        "produce_output_not_in_matrix_first_turn"),
                plannedContract(
                        "stock_reduce.goods_count_ranking",
                        AiQuerySemanticLexicon.STRUCTURED_GOODS_OUTBOUND_COUNT_RANKING,
                        StockReduceAnswerPlan.TYPE_STOCK_REDUCE_GOODS_COUNT_RANKING,
                        "GOODS",
                        Set.of("RANKING"),
                        Set.of("OUTBOUND_COUNT"),
                        "RANKING",
                        "goods_outbound_count_ranking_not_in_matrix_first_turn"));
    }

    @Override
    public List<SemanticCapabilityContract> exportKnownGapContracts() {
        StockReduceSemanticCapabilityMatrixRow row = StockReduceSemanticCapabilityMatrix.GOODS_WASTE_AMOUNT_RANKING;
        return List.of(
                fromMatrixRow(row, SemanticCapabilityContractStatus.KNOWN_GAP, row.getKnownGapCode()));
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
            StockReduceSemanticCapabilityMatrixRow row,
            SemanticCapabilityContractStatus status,
            String gapMarker) {
        return SemanticCapabilityContract.builder()
                .contractId(contractIdForRow(row))
                .domain(DOMAIN_CODE)
                .intentCode(AiResolvedQueryIntent.STOCK_REDUCE_QUERY)
                .pathCode(AiResolvedQueryIntent.PATH_STOCK_REDUCE_QUERY)
                .wire(row.getStructuredIntentDetailWire())
                .queryObject(row.getQueryObject())
                .operation(row.getOperation())
                .metric(row.getMetric())
                .sourceFacet(semanticSourceFacet(row))
                .detailWanted(null)
                .answerPlanType(row.getTargetStockReducePlanType())
                .requiresAnchor(false)
                .anchorType(null)
                .selectedTools(STOCK_REDUCE_TOOLS)
                .status(status)
                .gapMarker(gapMarker)
                .build();
    }

    /**
     * 出库域 v2 semanticSlots 不使用 {@code sourceFacet}；Matrix {@code reduceTypeLabel} 仅用于 Plan 层。
     */
    private static String semanticSourceFacet(StockReduceSemanticCapabilityMatrixRow row) {
        return null;
    }

    private static String contractIdForRow(StockReduceSemanticCapabilityMatrixRow row) {
        return switch (row.getRowId()) {
            case "SR-A" -> "stock_reduce.overview";
            case "SR-B" -> "stock_reduce.store_amount_ranking";
            case "SR-C" -> "stock_reduce.production_overview";
            case "SR-D" -> "stock_reduce.waste_overview";
            case "SR-E" -> "stock_reduce.loss_overview";
            case "SR-F" -> "stock_reduce.return_overview";
            case "SR-G" -> "stock_reduce.goods_amount_ranking";
            case "SR-GW" -> "stock_reduce.goods_waste_ranking";
            default -> "stock_reduce." + row.getRowId().toLowerCase();
        };
    }

    private static SemanticCapabilityContract plannedContract(
            String contractId,
            String wire,
            String answerPlanType,
            String queryObject,
            Set<String> operations,
            Set<String> metrics,
            String reduceTypeLabel,
            String gapMarker) {
        return SemanticCapabilityContract.builder()
                .contractId(contractId)
                .domain(DOMAIN_CODE)
                .intentCode(AiResolvedQueryIntent.STOCK_REDUCE_QUERY)
                .pathCode(AiResolvedQueryIntent.PATH_STOCK_REDUCE_QUERY)
                .wire(wire)
                .queryObject(queryObject)
                .operations(operations)
                .metrics(metrics)
                .sourceFacet(null)
                .detailWanted(null)
                .answerPlanType(answerPlanType)
                .requiresAnchor(false)
                .anchorType(null)
                .selectedTools(STOCK_REDUCE_TOOLS)
                .status(SemanticCapabilityContractStatus.PLANNED)
                .gapMarker(gapMarker)
                .build();
    }
}
