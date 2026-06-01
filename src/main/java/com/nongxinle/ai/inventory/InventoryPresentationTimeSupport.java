package com.nongxinle.ai.inventory;

import com.nongxinle.ai.context.AiResolvedQueryContext;
import com.nongxinle.ai.context.AiResolvedTimeWindow;
import com.nongxinle.ai.core.AiRunState;
import com.nongxinle.ai.dto.business.DishIngredientCoverAnswerPlan;
import com.nongxinle.ai.dto.business.GoodsSupportedDishCoverAnswerPlan;
import com.nongxinle.ai.graph.business.DishIngredientCoverSalesBaseline;
import com.nongxinle.ai.graph.business.DishIngredientCoverSalesBaselineSupport;
import com.nongxinle.ai.dto.business.WarehouseAnswerPlan;
import com.nongxinle.ai.semantic.matrix.DishCostAnalysisSemanticCapabilityMatrix;
import com.nongxinle.ai.tool.business.AiBusinessToolIds;
import com.nongxinle.ai.util.AiTimeWindowTextFormatter;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.util.Locale;
import java.util.Map;

/**
 * 库存域快照 vs 期间流水口径：唯一归类与文案入口（按 PlanType / Tool / Contract，不读 rawMessage）。
 */
public final class InventoryPresentationTimeSupport {

    private InventoryPresentationTimeSupport() {}

    public static InventoryQueryTimeKind resolveWarehousePlanKind(String planType) {
        if (planType == null || planType.isBlank()) {
            return InventoryQueryTimeKind.CURRENT_SNAPSHOT;
        }
        return switch (planType.trim()) {
            case WarehouseAnswerPlan.TYPE_WAREHOUSE_STOCK_OVERVIEW,
                    WarehouseAnswerPlan.TYPE_WAREHOUSE_LOW_STOCK_RISK ->
                    InventoryQueryTimeKind.HYBRID_SNAPSHOT_WITH_PERIOD_BASELINE;
            case WarehouseAnswerPlan.TYPE_WAREHOUSE_STORE_AMOUNT_RANKING,
                    WarehouseAnswerPlan.TYPE_WAREHOUSE_GOODS_AMOUNT_RANKING_HIGH,
                    WarehouseAnswerPlan.TYPE_WAREHOUSE_GOODS_AMOUNT_RANKING_LOW ->
                    InventoryQueryTimeKind.CURRENT_SNAPSHOT;
            case GoodsSupportedDishCoverAnswerPlan.TYPE ->
                    InventoryQueryTimeKind.HYBRID_SNAPSHOT_WITH_PERIOD_BASELINE;
            default -> InventoryQueryTimeKind.CURRENT_SNAPSHOT;
        };
    }

    public static InventoryQueryTimeKind resolveWarehouseToolKind(String toolId) {
        if (AiBusinessToolIds.WAREHOUSE_INVENTORY_RISK_LIST.equals(toolId)) {
            return InventoryQueryTimeKind.HYBRID_SNAPSHOT_WITH_PERIOD_BASELINE;
        }
        if (AiBusinessToolIds.WAREHOUSE_STOCK_OVERVIEW.equals(toolId)) {
            return InventoryQueryTimeKind.HYBRID_SNAPSHOT_WITH_PERIOD_BASELINE;
        }
        if (AiBusinessToolIds.WAREHOUSE_GOODS_SUPPORTED_DISH_COVER.equals(toolId)) {
            return InventoryQueryTimeKind.HYBRID_SNAPSHOT_WITH_PERIOD_BASELINE;
        }
        if (AiBusinessToolIds.STOCK_REDUCE_QUERY.equals(toolId)) {
            return InventoryQueryTimeKind.PERIOD_FLOW;
        }
        return InventoryQueryTimeKind.CURRENT_SNAPSHOT;
    }

    public static InventoryQueryTimeKind resolveDishIngredientCoverKind() {
        return InventoryQueryTimeKind.HYBRID_SNAPSHOT_WITH_PERIOD_BASELINE;
    }

    public static InventoryPlanTimeFields buildForWarehousePlan(
            String planType, AiRunState state, AiResolvedQueryContext rq) {
        return buildFields(resolveWarehousePlanKind(planType), state, rq);
    }

    public static InventoryPlanTimeFields buildForGoodsSupportedDishCover(
            AiRunState state, AiResolvedQueryContext rq) {
        DishIngredientCoverSalesBaseline baseline = DishIngredientCoverSalesBaselineSupport.resolve(state, rq);
        return InventoryPlanTimeFields.builder()
                .inventoryQueryTimeKind(InventoryQueryTimeKind.HYBRID_SNAPSHOT_WITH_PERIOD_BASELINE)
                .asOfDate(resolveAsOfDateIso(state, rq))
                .stockSnapshotLabel(formatStockSnapshotLabel(resolveAsOfDateIso(state, rq)))
                .periodFlowLabel(blankToNull(baseline.getDisplayLabel()))
                .timeLabel(formatStockSnapshotLabel(resolveAsOfDateIso(state, rq)))
                .build();
    }

    public static InventoryPlanTimeFields buildForDishIngredientCover(AiRunState state, AiResolvedQueryContext rq) {
        DishIngredientCoverSalesBaseline baseline = DishIngredientCoverSalesBaselineSupport.resolve(state, rq);
        return InventoryPlanTimeFields.builder()
                .inventoryQueryTimeKind(resolveDishIngredientCoverKind())
                .asOfDate(LocalDate.now().toString())
                .stockSnapshotLabel(formatDishCoverStockSnapshotLabel())
                .periodFlowLabel(blankToNull(baseline.getDisplayLabel()))
                .timeLabel(formatDishCoverStockSnapshotLabel())
                .build();
    }

    public static InventoryPlanTimeFields buildFields(
            InventoryQueryTimeKind kind, AiRunState state, AiResolvedQueryContext rq) {
        String asOf = resolveAsOfDateIso(state, rq);
        String snapshotLabel = formatStockSnapshotLabel(asOf);
        String periodLabel = kind == InventoryQueryTimeKind.HYBRID_SNAPSHOT_WITH_PERIOD_BASELINE
                || kind == InventoryQueryTimeKind.PERIOD_FLOW
                ? resolvePeriodFlowLabel(state, rq)
                : null;
        return InventoryPlanTimeFields.builder()
                .inventoryQueryTimeKind(kind)
                .asOfDate(asOf)
                .stockSnapshotLabel(snapshotLabel)
                .periodFlowLabel(blankToNull(periodLabel))
                .timeLabel(snapshotLabel)
                .build();
    }

    public static String resolveAsOfDateIso(AiRunState state, AiResolvedQueryContext rq) {
        if (state != null && StringUtils.hasText(state.getStatEndDate())) {
            return state.getStatEndDate().trim();
        }
        AiResolvedTimeWindow tw = rq == null ? null : rq.getTimeWindow();
        if (tw != null && tw.getEndDate() != null) {
            return tw.getEndDate().toString();
        }
        if (tw != null && tw.getStartDate() != null) {
            return tw.getStartDate().toString();
        }
        return LocalDate.now().toString();
    }

    public static String resolvePeriodFlowLabel(AiRunState state, AiResolvedQueryContext rq) {
        if (rq != null && StringUtils.hasText(rq.getTimeWindowLabel())) {
            return rq.getTimeWindowLabel().trim();
        }
        AiTimeWindowTextFormatter.UserPhrases phrases = AiTimeWindowTextFormatter.forAnswer(state);
        if (phrases != null && StringUtils.hasText(phrases.getDisplayTimeRange())) {
            String range = phrases.getDisplayTimeRange().trim();
            if (!"该统计区间".equals(range)) {
                return range;
            }
        }
        if (state != null
                && StringUtils.hasText(state.getStatStartDate())
                && StringUtils.hasText(state.getStatEndDate())) {
            return state.getStatStartDate().trim() + " 至 " + state.getStatEndDate().trim();
        }
        return null;
    }

    public static String formatStockSnapshotLabel(String asOfDateIso) {
        if (StringUtils.hasText(asOfDateIso)) {
            return String.format(Locale.CHINA, "当前库存（截至 %s）", asOfDateIso.trim());
        }
        return "当前库存";
    }

    public static String bracketStockSnapshotLine(String stockSnapshotLabel) {
        String label = StringUtils.hasText(stockSnapshotLabel) ? stockSnapshotLabel.trim() : "当前库存";
        return "【" + label + "】";
    }

    public static String bracketPeriodBaselineLine(String periodFlowLabel) {
        if (!StringUtils.hasText(periodFlowLabel)) {
            return null;
        }
        return "【耗用/流水基线】" + periodFlowLabel.trim();
    }

    public static void applyToWarehousePlanBuilder(
            WarehouseAnswerPlan.WarehouseAnswerPlanBuilder builder,
            String planType,
            AiRunState state,
            AiResolvedQueryContext rq) {
        InventoryPlanTimeFields fields = buildForWarehousePlan(planType, state, rq);
        builder.inventoryQueryTimeKind(fields.getInventoryQueryTimeKind().name())
                .asOfDate(fields.getAsOfDate())
                .stockSnapshotLabel(fields.getStockSnapshotLabel())
                .periodFlowLabel(fields.getPeriodFlowLabel())
                .timeLabel(fields.getTimeLabel());
    }

    public static void applyToDishIngredientCoverPlanBuilder(
            DishIngredientCoverAnswerPlan.DishIngredientCoverAnswerPlanBuilder builder,
            AiRunState state,
            AiResolvedQueryContext rq) {
        applyToDishIngredientCoverPlanBuilder(builder, state, rq, null);
    }

    public static void applyToDishIngredientCoverPlanBuilder(
            DishIngredientCoverAnswerPlan.DishIngredientCoverAnswerPlanBuilder builder,
            AiRunState state,
            AiResolvedQueryContext rq,
            DishIngredientCoverSalesBaseline salesBaseline) {
        builder.inventoryQueryTimeKind(resolveDishIngredientCoverKind().name())
                .asOfDate(LocalDate.now().toString())
                .stockSnapshotLabel(formatDishCoverStockSnapshotLabel())
                .periodFlowLabel(
                        salesBaseline != null && StringUtils.hasText(salesBaseline.getDisplayLabel())
                                ? salesBaseline.getDisplayLabel().trim()
                                : null)
                .timeLabel(formatDishCoverStockSnapshotLabel());
    }

    /** 配料可支撑天数：库存为实时快照，文案固定「截至当前」。 */
    public static String formatDishCoverStockSnapshotLabel() {
        return "当前库存（截至当前）";
    }

    public static boolean isPeriodFlowRequired(InventoryQueryTimeKind kind) {
        return kind == InventoryQueryTimeKind.PERIOD_FLOW;
    }

    public static boolean hasPeriodBaseline(InventoryQueryTimeKind kind) {
        return kind == InventoryQueryTimeKind.HYBRID_SNAPSHOT_WITH_PERIOD_BASELINE
                || kind == InventoryQueryTimeKind.PERIOD_FLOW;
    }

    public static String contractIdForDishIngredientCover() {
        return DishCostAnalysisSemanticCapabilityMatrix.CONTRACT_DISH_INGREDIENT_COVER_DAYS;
    }

    public static String resolveStockAsOfFromToolArgs(Map<String, Object> args, String start, String stop) {
        if (args != null && args.get(com.nongxinle.ai.tool.business.AiBusinessToolIds.ARG_STOCK_AS_OF_DATE) != null) {
            String fromArgs =
                    args.get(com.nongxinle.ai.tool.business.AiBusinessToolIds.ARG_STOCK_AS_OF_DATE).toString().trim();
            if (!fromArgs.isEmpty()) {
                return fromArgs;
            }
        }
        if (StringUtils.hasText(stop)) {
            return stop.trim();
        }
        if (StringUtils.hasText(start)) {
            return start.trim();
        }
        return LocalDate.now().toString();
    }

    public static boolean hasPeriodFlowDates(String start, String stop) {
        return StringUtils.hasText(start) && StringUtils.hasText(stop);
    }

    public static void applySnapshotMetadataToPayload(
            Map<String, Object> payload, String start, String stop, String stockAsOf) {
        if (payload == null) {
            return;
        }
        payload.put("stockAsOfDate", stockAsOf);
        InventoryQueryTimeKind kind = hasPeriodFlowDates(start, stop)
                ? InventoryQueryTimeKind.HYBRID_SNAPSHOT_WITH_PERIOD_BASELINE
                : InventoryQueryTimeKind.CURRENT_SNAPSHOT;
        payload.put("inventoryQueryTimeKind", kind.name());
        if (hasPeriodFlowDates(start, stop)) {
            payload.put("periodFlowStartDate", start.trim());
            payload.put("periodFlowStopDate", stop.trim());
        }
    }

    private static String blankToNull(String s) {
        return StringUtils.hasText(s) ? s.trim() : null;
    }
}
