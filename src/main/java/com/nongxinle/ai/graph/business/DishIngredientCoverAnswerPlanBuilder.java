package com.nongxinle.ai.graph.business;

import com.nongxinle.ai.context.AiResolvedQueryContext;
import com.nongxinle.ai.core.AiRunState;
import com.nongxinle.ai.dto.business.DishIngredientCoverAnswerPlan;
import com.nongxinle.ai.graph.business.execution.ToolRequestContractExecutionParamSupport;
import com.nongxinle.ai.inventory.CoverDaysSalesBaselinePresentationSupport;
import com.nongxinle.ai.inventory.InventoryPresentationTimeSupport;
import com.nongxinle.ai.tool.business.AiBusinessToolIds;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * {@code dish.ingredient_cover_days.v1}：只读 {@link AiBusinessToolIds#DISH_COST_ANALYSIS} 快照。
 */
@Slf4j
public final class DishIngredientCoverAnswerPlanBuilder {

    private DishIngredientCoverAnswerPlanBuilder() {}

    public static void attachIfApplicable(AiRunState state) {
        if (state == null || !state.isDishCostAnalysisPath()) {
            return;
        }
        AiResolvedQueryContext rq = state.getResolvedQueryContext();
        if (!ToolRequestContractExecutionParamSupport.isDishIngredientCoverDaysContract(rq)) {
            return;
        }
        state.setDishIngredientCoverAnswerPlan(null);

        LinkedHashMap<String, Object> debug = new LinkedHashMap<>();
        debug.put("contractId", DishIngredientCoverAnswerPlan.CONTRACT_ID);
        debug.put("planType", DishIngredientCoverAnswerPlan.TYPE);

        Map<String, Object> costData = toolData(state, AiBusinessToolIds.DISH_COST_ANALYSIS);
        String costStatus = resolveToolStatus(state, AiBusinessToolIds.DISH_COST_ANALYSIS, costData);
        if ("NEED_CLARIFICATION".equalsIgnoreCase(costStatus)) {
            attachFailed(state, debug, DishIngredientCoverAnswerPlan.STATUS_FAILED, "需要澄清具体菜品");
            return;
        }
        if (costData == null || costData.isEmpty()) {
            attachFailed(state, debug, DishIngredientCoverAnswerPlan.STATUS_FAILED, "缺少菜品成本分析数据");
            return;
        }

        try {
            DishIngredientCoverAnswerPlan plan = buildPlan(state, rq, costData, debug);
            state.setDishIngredientCoverAnswerPlan(plan);
            log.info(
                    "[DishIngredientCover] runId={} dish={} status={} coverDays={}",
                    state.getRunId(),
                    plan.getDishName(),
                    plan.getStatus(),
                    plan.getDishCoverDays());
        } catch (Exception ex) {
            log.warn("[DishIngredientCover] attach failed runId={}", state.getRunId(), ex);
            debug.put("failureReason", ex.getMessage());
            attachFailed(state, debug, DishIngredientCoverAnswerPlan.STATUS_FAILED, ex.getMessage());
        }
    }

    private static DishIngredientCoverAnswerPlan buildPlan(
            AiRunState state,
            AiResolvedQueryContext rq,
            Map<String, Object> costData,
            LinkedHashMap<String, Object> debug) {
        String dishName = DishEntityDisplayNameSupport.resolveDisplayDishName(rq, costData);
        Integer dishId = DishEntityDisplayNameSupport.resolveDisplayFoodId(rq, costData);
        String reasonCode = str(costData.get("reasonCode"));
        debug.put("costReasonCode", StringUtils.hasText(reasonCode) ? reasonCode : null);
        BigDecimal salesPortions = parseDecimal(costData.get("salesPortions"));

        DishIngredientCoverSalesBaseline salesBaseline =
                DishIngredientCoverSalesBaselineSupport.fromCostData(costData);
        if (salesBaseline == null) {
            salesBaseline = DishIngredientCoverSalesBaselineSupport.resolve(state, rq);
        }
        int baselineDays = Math.max(1, salesBaseline.getBaselineDays());
        BigDecimal dailySales =
                salesPortions != null
                        ? salesPortions.divide(BigDecimal.valueOf(baselineDays), 4, RoundingMode.HALF_UP)
                        : null;

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> ingredientRows =
                costData.get("ingredientRows") instanceof List
                        ? (List<Map<String, Object>>) costData.get("ingredientRows")
                        : List.of();

        List<Map<String, Object>> enrichedRows = new ArrayList<>();
        BigDecimal minCoverDays = null;
        BigDecimal minSupportedFromStock = null;
        String bottleneckName = null;
        BigDecimal bottleneckCover = null;

        boolean hasPositiveDailySales = dailySales != null && dailySales.compareTo(BigDecimal.ZERO) > 0;

        for (Map<String, Object> row : ingredientRows) {
            Map<String, Object> draft =
                    DishIngredientCoverIngredientRowProjection.project(row, dailySales, baselineDays, null);
            if (hasPositiveDailySales) {
                BigDecimal coverDays = DishIngredientCoverIngredientRowProjection.coverDaysForRanking(draft);
                String ingredientName = str(draft.get("ingredientName"));
                if (coverDays != null && (minCoverDays == null || coverDays.compareTo(minCoverDays) < 0)) {
                    minCoverDays = coverDays;
                    bottleneckName = ingredientName;
                    bottleneckCover = coverDays;
                }
            } else {
                BigDecimal supported =
                        DishIngredientCoverIngredientRowProjection.supportedPortionsFromStockForRanking(draft);
                String ingredientName = str(draft.get("ingredientName"));
                if (supported != null
                        && (minSupportedFromStock == null || supported.compareTo(minSupportedFromStock) < 0)) {
                    minSupportedFromStock = supported;
                    bottleneckName = ingredientName;
                }
            }
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> bottle =
                costData.get("bottle") instanceof Map
                        ? (Map<String, Object>) costData.get("bottle")
                        : null;
        if (bottleneckName == null && bottle != null) {
            bottleneckName = str(bottle.get("goodsName"));
            if (bottleneckName.isEmpty()) {
                bottleneckName = str(bottle.get("gbDgGoodsName"));
            }
        }

        String finalBottleneckName = bottleneckName;
        for (Map<String, Object> row : ingredientRows) {
            enrichedRows.add(
                    DishIngredientCoverIngredientRowProjection.project(
                            row, dailySales, baselineDays, finalBottleneckName));
        }

        LinkedHashMap<String, Object> summary = new LinkedHashMap<>();
        summary.put("salesPortions", salesPortions == null ? null : salesPortions.toPlainString());
        summary.put("salesBaselineStartDate", salesBaseline.getStartDateIso());
        summary.put("salesBaselineStopDate", salesBaseline.getStopDateIso());
        summary.put("salesBaselineDays", baselineDays);
        summary.put("salesBaselineSource", salesBaseline.getBaselineSource());
        summary.put("salesBaselineLabel", salesBaseline.getDisplayLabel());
        summary.put(
                "salesBaselinePeriodPhrase",
                CoverDaysSalesBaselinePresentationSupport.formatPeriodPhrase(rq, salesBaseline));
        summary.put("windowDays", baselineDays);
        if (dailySales != null) {
            summary.put("dailySalesPortions", dailySales.setScale(2, RoundingMode.HALF_UP).toPlainString());
        }
        summary.put(
                "methodNote",
                "当前库存为实时快照（截至当前），不按问句时间窗过滤；"
                        + "日均销量/用量来自销量基线（"
                        + salesBaseline.getDisplayLabel()
                        + "）；"
                        + "可支撑天数 = 当前库存 ÷ 每日预计用量（每日预计用量 = 单份配方用量 × 日均销量）；"
                        + "基线期内无销量时不推算天数，但仍展示配方与当前库存。");
        if (dailySales == null || dailySales.compareTo(BigDecimal.ZERO) <= 0) {
            summary.put(
                    "noSalesBaselineNote",
                    CoverDaysSalesBaselinePresentationSupport.composeNoSalesCannotEstimateNote(
                            dishName, rq, salesBaseline));
        }
        Object inventoryDebug = costData.get("dishIngredientCoverInventoryDebug");
        if (inventoryDebug != null) {
            summary.put("inventoryDataSource", inventoryDebug);
        }

        List<String> gaps = new ArrayList<>();
        gaps.add("shelf_life_batch_not_in_cover_days_p1");
        if (enrichedRows.isEmpty() && StringUtils.hasText(dishName)) {
            gaps.add("no_recipe_for_dish");
        }
        if (dailySales == null || dailySales.compareTo(BigDecimal.ZERO) <= 0) {
            gaps.add("no_sales_in_window_cannot_compute_cover_days");
            debug.put("coverDaysGap", "no_sales_in_window_cannot_compute_cover_days");
        }
        boolean noRecipeGap = enrichedRows.isEmpty() && StringUtils.hasText(dishName);
        debug.put("dishIngredientCoverNoRecipeGap", noRecipeGap);

        String status;
        if (!StringUtils.hasText(dishName)) {
            status = DishIngredientCoverAnswerPlan.STATUS_PARTIAL;
        } else if (enrichedRows.isEmpty()) {
            status = DishIngredientCoverAnswerPlan.STATUS_PARTIAL;
        } else if (minCoverDays != null) {
            status = DishIngredientCoverAnswerPlan.STATUS_SUCCESS;
        } else {
            status = DishIngredientCoverAnswerPlan.STATUS_SUCCESS;
        }

        debug.put("salesBaselineSource", salesBaseline.getBaselineSource());
        debug.put("salesBaselineDays", baselineDays);

        DishIngredientCoverAnswerPlan.DishIngredientCoverAnswerPlanBuilder planBuilder =
                DishIngredientCoverAnswerPlan.builder()
                .planType(DishIngredientCoverAnswerPlan.TYPE)
                .contractId(DishIngredientCoverAnswerPlan.CONTRACT_ID)
                .status(status)
                .dishName(dishName)
                .dishId(dishId)
                .scopeLabel(resolveScopeLabel(rq))
                .dishCoverDays(minCoverDays == null ? null : minCoverDays.stripTrailingZeros().toPlainString())
                .bottleneckIngredientName(bottleneckName)
                .bottleneckCoverDays(
                        bottleneckCover == null ? null : bottleneckCover.stripTrailingZeros().toPlainString())
                .ingredientRows(enrichedRows)
                .summary(summary)
                .knownGaps(gaps)
                .debug(debug);
        InventoryPresentationTimeSupport.applyToDishIngredientCoverPlanBuilder(
                planBuilder, state, rq, salesBaseline);
        return planBuilder.build();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> toolData(AiRunState state, String toolId) {
        Object raw = state.getToolResults() == null ? null : state.getToolResults().get(toolId);
        if (!(raw instanceof Map<?, ?> env)) {
            return Map.of();
        }
        if (!Boolean.TRUE.equals(env.get("success"))) {
            return Map.of();
        }
        Object data = env.get("data");
        if (data instanceof Map<?, ?> dm) {
            return (Map<String, Object>) dm;
        }
        return Map.of();
    }

    private static String resolveToolStatus(AiRunState state, String toolId, Map<String, Object> data) {
        Object raw = state.getToolResults() == null ? null : state.getToolResults().get(toolId);
        if (raw instanceof Map<?, ?> env && env.get("message") != null) {
            return env.get("message").toString();
        }
        if (data != null && Boolean.TRUE.equals(data.get("needClarification"))) {
            return "NEED_CLARIFICATION";
        }
        return data == null || data.isEmpty() ? "FAILED" : str(data.get("status"));
    }

    private static void attachFailed(
            AiRunState state, Map<String, Object> debug, String status, String message) {
        debug.put("failureDetail", message);
        state.setDishIngredientCoverAnswerPlan(
                DishIngredientCoverAnswerPlan.builder()
                        .planType(DishIngredientCoverAnswerPlan.TYPE)
                        .contractId(DishIngredientCoverAnswerPlan.CONTRACT_ID)
                        .status(status)
                        .dishName(
                                DishEntityDisplayNameSupport.resolveDisplayDishName(
                                        state.getResolvedQueryContext(), Map.of()))
                        .summary(Map.of("message", message == null ? "" : message))
                        .debug(debug)
                        .build());
    }

    private static String resolveScopeLabel(AiResolvedQueryContext rq) {
        if (rq != null && StringUtils.hasText(rq.getQueryScopeBanner())) {
            return rq.getQueryScopeBanner().trim();
        }
        return "当前范围";
    }

    private static BigDecimal parseDecimal(Object o) {
        if (o == null) {
            return null;
        }
        if (o instanceof BigDecimal bd) {
            return bd;
        }
        if (o instanceof Number n) {
            return BigDecimal.valueOf(n.doubleValue());
        }
        String s = o.toString().trim();
        if (!StringUtils.hasText(s)) {
            return null;
        }
        try {
            return new BigDecimal(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String str(Object o) {
        return o == null ? "" : o.toString().trim();
    }
}
