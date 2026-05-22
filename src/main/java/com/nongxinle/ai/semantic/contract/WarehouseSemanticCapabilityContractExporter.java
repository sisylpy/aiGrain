package com.nongxinle.ai.semantic.contract;

import com.nongxinle.ai.context.AiResolvedQueryIntent;
import com.nongxinle.ai.semantic.matrix.WarehouseSemanticCapabilityMatrix;
import com.nongxinle.ai.semantic.matrix.WarehouseSemanticCapabilityMatrixRow;
import com.nongxinle.ai.tool.business.AiBusinessToolIds;

import java.util.ArrayList;
import java.util.List;

/**
 * 库房库存域 Step 2 小合同只读导出。
 * <p>ACTIVE / KNOWN_GAP 均来自 {@link WarehouseSemanticCapabilityMatrix#firstTurnRows()}。
 */
public final class WarehouseSemanticCapabilityContractExporter implements SemanticCapabilityContractExporter {

    public static final String DOMAIN_CODE = "WAREHOUSE";

    public static final WarehouseSemanticCapabilityContractExporter INSTANCE =
            new WarehouseSemanticCapabilityContractExporter();

    private static final List<String> WAREHOUSE_TOOLS =
            List.of(AiBusinessToolIds.WAREHOUSE_STOCK_OVERVIEW);

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
        return List.of();
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
        return SemanticCapabilityContract.builder()
                .contractId(contractIdForRow(row))
                .domain(DOMAIN_CODE)
                .intentCode(AiResolvedQueryIntent.WAREHOUSE_STOCK_OVERVIEW)
                .pathCode(AiResolvedQueryIntent.PATH_WAREHOUSE_STOCK)
                .wire(row.getStructuredIntentDetailWire())
                .queryObject(row.getQueryObject())
                .operation(row.getOperation())
                .metric(row.getMetric())
                .sourceFacet(row.getStockFacet())
                .detailWanted(null)
                .answerPlanType(row.getTargetWarehousePlanType())
                .requiresAnchor(false)
                .anchorType(null)
                .selectedTools(WAREHOUSE_TOOLS)
                .status(status)
                .gapMarker(gapMarker)
                .build();
    }

    private static String contractIdForRow(WarehouseSemanticCapabilityMatrixRow row) {
        return switch (row.getRowId()) {
            case "WH-A" -> "warehouse.overview";
            case "WH-B" -> "warehouse.goods_amount_ranking_high";
            case "WH-C" -> "warehouse.goods_amount_ranking_low";
            case "WH-D" -> "warehouse.store_amount_ranking";
            case "WH-E" -> "warehouse.single_store_overview";
            case "WH-F" -> "warehouse.out_of_stock";
            case "WH-G" -> "warehouse.near_expiry";
            default -> "warehouse." + row.getRowId().toLowerCase();
        };
    }
}
