package com.nongxinle.ai.semantic.contract;

import com.nongxinle.ai.context.AiResolvedQueryIntent;
import com.nongxinle.ai.semantic.AiQuerySemanticSlotMerge;
import com.nongxinle.ai.semantic.matrix.DishProfitSemanticCapabilityMatrix;
import com.nongxinle.ai.semantic.matrix.DishProfitSemanticCapabilityMatrixRow;
import com.nongxinle.ai.tool.business.AiBusinessToolIds;

import java.util.ArrayList;
import java.util.List;

import org.springframework.util.StringUtils;

/**
 * 菜品毛利域 Step 2 小合同只读导出。
 * <p>ACTIVE：Matrix 首轮行 + DISH 锚追问 capability 行（无 knownGap）。
 */
public final class DishProfitSemanticCapabilityContractExporter implements SemanticCapabilityContractExporter {

    public static final String DOMAIN_CODE = "DISH_PROFIT";

    public static final DishProfitSemanticCapabilityContractExporter INSTANCE =
            new DishProfitSemanticCapabilityContractExporter();

    private static final List<String> DISH_PROFIT_TOOLS = AiBusinessToolIds.DEFAULT_DISH_PROFIT_TOOLS;

    private DishProfitSemanticCapabilityContractExporter() {
    }

    @Override
    public String domain() {
        return DOMAIN_CODE;
    }

    @Override
    public List<SemanticCapabilityContract> exportActiveContracts() {
        List<SemanticCapabilityContract> out = new ArrayList<>();
        for (DishProfitSemanticCapabilityMatrixRow row : DishProfitSemanticCapabilityMatrix.firstTurnRows()) {
            out.add(fromMatrixRow(row, SemanticCapabilityContractStatus.ACTIVE, null));
        }
        for (DishProfitSemanticCapabilityMatrixRow row :
                DishProfitSemanticCapabilityMatrix.dishAnchorFollowUpRows()) {
            out.add(fromMatrixRow(row, SemanticCapabilityContractStatus.ACTIVE, null));
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
            DishProfitSemanticCapabilityMatrixRow row,
            SemanticCapabilityContractStatus status,
            String gapMarker) {
        boolean requiresAnchor =
                DishProfitSemanticCapabilityMatrix.ANCHOR_TYPE_DISH.equals(row.getAnchorType())
                        && AiQuerySemanticSlotMerge.ANCHOR_USE_PREVIOUS.equals(row.getAnchorPolicy());
        List<String> tools =
                requiresAnchor
                        ? List.of(
                                AiBusinessToolIds.DISH_INGREDIENT_COST_BREAKDOWN,
                                AiBusinessToolIds.DISH_PROFIT_ANALYSIS)
                        : DISH_PROFIT_TOOLS;
        String contractId =
                StringUtils.hasText(row.getCapabilityId())
                        ? row.getCapabilityId()
                        : contractIdForRow(row);
        return SemanticCapabilityContract.builder()
                .contractId(contractId)
                .domain(DOMAIN_CODE)
                .intentCode(AiResolvedQueryIntent.DISH_PROFIT)
                .pathCode(AiResolvedQueryIntent.PATH_DISH_PROFIT)
                .wire(row.getStructuredIntentDetailWire())
                .queryObject(row.getQueryObject())
                .operation(row.getOperation())
                .metric(row.getMetric())
                .sourceFacet(null)
                .detailWanted(row.getDetailWanted())
                .answerPlanType(row.getTargetDishProfitPlanType())
                .requiresAnchor(requiresAnchor)
                .anchorType(requiresAnchor ? DishProfitSemanticCapabilityMatrix.ANCHOR_TYPE_DISH : null)
                .selectedTools(tools)
                .status(status)
                .gapMarker(gapMarker)
                .build();
    }

    private static String contractIdForRow(DishProfitSemanticCapabilityMatrixRow row) {
        return switch (row.getRowId()) {
            case "DP-R0a" -> "dish_profit.ranking_low_margin";
            case "DP-R0b" -> "dish_profit.ranking_high_margin";
            case "DP-R0c" -> "dish_profit.ranking_high_actual_cost";
            case "DP-R0d" -> "dish_profit.ranking_max_cost_gap";
            case "DP-R0e" -> "dish_profit.low_profit_reason";
            case "DP-R0f" -> "dish_profit.theoretical_cost";
            case "DP-R0g" -> "dish_profit.actual_outbound_cost";
            case "DP-R0h" -> "dish_profit.gross_margin_rate";
            case "DP-R0i" -> "dish_profit.cost_gap";
            case "DP-R0j" -> "dish_profit.ingredient_cost_breakdown_first_turn";
            case "DP-R1" -> DishProfitSemanticCapabilityMatrix.CAPABILITY_DISH_ANCHOR_INGREDIENT_BREAKDOWN;
            default -> "dish_profit." + row.getRowId().toLowerCase();
        };
    }
}
