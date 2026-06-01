package com.nongxinle.ai.semantic.contract;

import com.nongxinle.ai.context.AiResolvedQueryIntent;
import com.nongxinle.ai.conversation.AiQuerySemanticLexicon;
import com.nongxinle.ai.dto.business.WarehouseAnswerPlan;
import com.nongxinle.ai.semantic.matrix.WarehouseSemanticCapabilityMatrix;
import com.nongxinle.ai.semantic.matrix.WarehouseSemanticCapabilityMatrixRow;
import com.nongxinle.ai.tool.business.AiBusinessToolIds;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 库房库存域 Step 2 小合同只读导出。
 * <p>ACTIVE / KNOWN_GAP 均来自 {@link WarehouseSemanticCapabilityMatrix#firstTurnRows()}；
 * Lexicon 已登记但未纳入 Matrix 首轮的 wire 以 PLANNED 导出（不进入 allowedContracts）。
 * <p>薄导出器：仅 Matrix → {@link MatrixBackedContractExporterSupport} 结构化字段。
 * NL 见 {@code semantic_intake.v1.md}、{@code query_semantic_parser.v2.md}、Harness。
 * 治理见 {@code docs/ai/semantic-contract-exporter-governance.md}。
 */
public final class WarehouseSemanticCapabilityContractExporter implements SemanticCapabilityContractExporter {

    public static final String DOMAIN_CODE = "WAREHOUSE";

    public static final WarehouseSemanticCapabilityContractExporter INSTANCE =
            new WarehouseSemanticCapabilityContractExporter();

    private static List<String> selectedToolsForMatrixRow(WarehouseSemanticCapabilityMatrixRow row) {
        if (row != null && "WH-F".equals(row.getRowId())) {
            return AiBusinessToolIds.DEFAULT_WAREHOUSE_INVENTORY_RISK_TOOLS;
        }
        if (row != null && "WH-H".equals(row.getRowId())) {
            return AiBusinessToolIds.DEFAULT_WAREHOUSE_GOODS_SUPPORTED_DISH_COVER_TOOLS;
        }
        return List.of(AiBusinessToolIds.WAREHOUSE_STOCK_OVERVIEW);
    }

    /** Lexicon 有 wire、Matrix 无 ACTIVE 行：仅 Catalog 观测。 */
    public static final String PLANNED_REPLENISHMENT_NOT_IN_MATRIX =
            "WAREHOUSE_STOCK_REPLENISHMENT_NOT_IN_MATRIX";
    public static final String PLANNED_OVERSTOCK_RISK_NOT_IN_MATRIX =
            "WAREHOUSE_STOCK_OVERSTOCK_RISK_NOT_IN_MATRIX";
    public static final String PLANNED_STORE_ITEM_COUNT_RANKING_NOT_IN_MATRIX =
            "WAREHOUSE_STORE_STOCK_ITEM_COUNT_RANKING_NOT_IN_MATRIX";
    public static final String PLANNED_WAREHOUSE_ITEM_COUNT_RANKING_NOT_IN_MATRIX =
            "WAREHOUSE_WAREHOUSE_STOCK_ITEM_COUNT_RANKING_NOT_IN_MATRIX";

    private WarehouseSemanticCapabilityContractExporter() {
    }

    @Override
    public String domain() {
        return DOMAIN_CODE;
    }

    @Override
    public List<SemanticCapabilityContract> exportActiveContracts() {
        List<SemanticCapabilityContract> out = new ArrayList<>();
        for (WarehouseSemanticCapabilityMatrixRow row : WarehouseSemanticCapabilityMatrix.firstTurnRows()) {
            if (row.getKnownGapCode() == null) {
                out.add(fromMatrixRow(row, SemanticCapabilityContractStatus.ACTIVE, null));
            }
        }
        return List.copyOf(out);
    }

    @Override
    public List<SemanticCapabilityContract> exportPlannedContracts() {
        return List.of(
                plannedContract(
                        "warehouse.stock_replenishment_needed",
                        AiQuerySemanticLexicon.STRUCTURED_WAREHOUSE_STOCK_REPLENISHMENT_NEEDED,
                        WarehouseAnswerPlan.TYPE_WAREHOUSE_STOCK_OVERVIEW,
                        "GOODS",
                        Set.of("RISK"),
                        Set.of("LOW_STOCK"),
                        PLANNED_REPLENISHMENT_NOT_IN_MATRIX),
                plannedContract(
                        "warehouse.stock_overstock_risk",
                        AiQuerySemanticLexicon.STRUCTURED_WAREHOUSE_STOCK_OVERSTOCK_RISK,
                        WarehouseAnswerPlan.TYPE_WAREHOUSE_STOCK_OVERVIEW,
                        "GOODS",
                        Set.of("RISK"),
                        Set.of("STOCK_AMOUNT"),
                        PLANNED_OVERSTOCK_RISK_NOT_IN_MATRIX),
                plannedContract(
                        "warehouse.store_stock_item_count_ranking",
                        AiQuerySemanticLexicon.STRUCTURED_STORE_STOCK_ITEM_COUNT_RANKING,
                        WarehouseAnswerPlan.TYPE_WAREHOUSE_STORE_AMOUNT_RANKING,
                        "STORE",
                        Set.of("RANKING"),
                        Set.of("STOCK_ITEM_COUNT"),
                        PLANNED_STORE_ITEM_COUNT_RANKING_NOT_IN_MATRIX),
                plannedContract(
                        "warehouse.warehouse_stock_item_count_ranking",
                        AiQuerySemanticLexicon.STRUCTURED_WAREHOUSE_STOCK_ITEM_COUNT_RANKING,
                        WarehouseAnswerPlan.TYPE_WAREHOUSE_STOCK_OVERVIEW,
                        "ALL",
                        Set.of("RANKING"),
                        Set.of("STOCK_ITEM_COUNT"),
                        PLANNED_WAREHOUSE_ITEM_COUNT_RANKING_NOT_IN_MATRIX));
    }

    @Override
    public List<SemanticCapabilityContract> exportKnownGapContracts() {
        List<SemanticCapabilityContract> out = new ArrayList<>();
        for (WarehouseSemanticCapabilityMatrixRow row : WarehouseSemanticCapabilityMatrix.firstTurnRows()) {
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
            WarehouseSemanticCapabilityMatrixRow row,
            SemanticCapabilityContractStatus status,
            String gapMarker) {
        String contractId = contractIdForRow(row);
        boolean requiresGoodsAnchor = "WH-H".equals(row.getRowId());
        return MatrixBackedContractExporterSupport.build(
                MatrixBackedContractExporterSupport.MatrixContractExportSpec.builder()
                        .contractId(contractId)
                        .domain(DOMAIN_CODE)
                        .intentCode(AiResolvedQueryIntent.WAREHOUSE_STOCK_OVERVIEW)
                        .pathCode(AiResolvedQueryIntent.PATH_WAREHOUSE_STOCK)
                        .wire(row.getStructuredIntentDetailWire())
                        .queryObject(row.getQueryObject())
                        .operation(row.getOperation())
                        .metric(row.getMetric())
                        .sourceFacet(row.getStockFacet())
                        .answerPlanType(row.getTargetWarehousePlanType())
                        .requiresAnchor(requiresGoodsAnchor)
                        .anchorType(requiresGoodsAnchor ? "GOODS" : null)
                        .selectedTools(selectedToolsForMatrixRow(row))
                        .status(status)
                        .gapMarker(gapMarker)
                        .build());
    }

    private static SemanticCapabilityContract plannedContract(
            String contractId,
            String wire,
            String answerPlanType,
            String queryObject,
            Set<String> operations,
            Set<String> metrics,
            String gapMarker) {
        return MatrixBackedContractExporterSupport.build(
                MatrixBackedContractExporterSupport.MatrixContractExportSpec.builder()
                        .contractId(contractId)
                        .domain(DOMAIN_CODE)
                        .intentCode(AiResolvedQueryIntent.WAREHOUSE_STOCK_OVERVIEW)
                        .pathCode(AiResolvedQueryIntent.PATH_WAREHOUSE_STOCK)
                        .wire(wire)
                        .queryObject(queryObject)
                        .operations(operations)
                        .metrics(metrics)
                        .answerPlanType(answerPlanType)
                        .selectedTools(List.of(AiBusinessToolIds.WAREHOUSE_STOCK_OVERVIEW))
                        .status(SemanticCapabilityContractStatus.PLANNED)
                        .gapMarker(gapMarker)
                        .build());
    }

    private static String contractIdForRow(WarehouseSemanticCapabilityMatrixRow row) {
        return switch (row.getRowId()) {
            case "WH-A" -> "warehouse.overview";
            case "WH-B" -> "warehouse.goods_amount_ranking_high";
            case "WH-C" -> "warehouse.goods_amount_ranking_low";
            case "WH-D" -> "warehouse.store_amount_ranking";
            case "WH-E" -> "warehouse.single_store_overview";
            case "WH-F" -> "warehouse.inventory_risk_list";
            case "WH-G" -> "warehouse.near_expiry";
            case "WH-H" -> "warehouse.goods_supported_dish_cover.v1";
            default -> "warehouse." + row.getRowId().toLowerCase();
        };
    }
}
