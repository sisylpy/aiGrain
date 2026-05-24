package com.nongxinle.ai.semantic.contract;

import com.nongxinle.ai.context.AiResolvedQueryIntent;
import com.nongxinle.ai.conversation.AiQuerySemanticLexicon;
import com.nongxinle.ai.semantic.AiQuerySemanticSlotMerge;
import com.nongxinle.ai.semantic.matrix.DishProfitSemanticCapabilityMatrix;
import com.nongxinle.ai.semantic.matrix.DishProfitSemanticCapabilityMatrixRow;
import com.nongxinle.ai.tool.business.AiBusinessToolIds;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.springframework.util.StringUtils;

/**
 * 菜品毛利域 Step 2 小合同只读导出。
 * <p>ACTIVE：Matrix 首轮无 knownGap 行（P2G 主流程：概览 / 排行 / 单菜毛利率）。
 * KNOWN_GAP：扩展排行、诊断、原料构成等复杂下钻。
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
        for (DishProfitSemanticCapabilityMatrixRow row : DishProfitSemanticCapabilityMatrix.firstTurnRows()) {
            if (row.getKnownGapCode() != null) {
                out.add(fromMatrixRow(row, SemanticCapabilityContractStatus.KNOWN_GAP, row.getKnownGapCode()));
            }
        }
        for (DishProfitSemanticCapabilityMatrixRow row :
                DishProfitSemanticCapabilityMatrix.dishAnchorFollowUpRows()) {
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
        SemanticCapabilityContract.SemanticCapabilityContractBuilder builder =
                SemanticCapabilityContract.builder()
                        .contractId(contractId)
                        .domain(DOMAIN_CODE)
                        .intentCode(AiResolvedQueryIntent.DISH_PROFIT)
                        .pathCode(AiResolvedQueryIntent.PATH_DISH_PROFIT)
                        .wire(row.getStructuredIntentDetailWire())
                        .queryObject(row.getQueryObject())
                        .metric(row.getMetric())
                        .sourceFacet(null)
                        .detailWanted(row.getDetailWanted())
                        .answerPlanType(row.getTargetDishProfitPlanType())
                        .requiresAnchor(requiresAnchor)
                        .anchorType(requiresAnchor ? DishProfitSemanticCapabilityMatrix.ANCHOR_TYPE_DISH : null)
                        .selectedTools(tools)
                        .status(status)
                        .gapMarker(gapMarker);
        if (AiQuerySemanticLexicon.STRUCTURED_DISH_PROFIT_OVERVIEW.equals(
                row.getStructuredIntentDetailWire())) {
            builder.operations(Set.of("OVERVIEW", "SUMMARY"));
        } else {
            builder.operation(row.getOperation());
        }
        return builder.build();
    }

    private static String contractIdForRow(DishProfitSemanticCapabilityMatrixRow row) {
        return switch (row.getRowId()) {
            case "DP-R0k" -> "dish_profit.overview";
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
