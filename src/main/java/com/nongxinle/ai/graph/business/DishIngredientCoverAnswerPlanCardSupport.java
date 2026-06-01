package com.nongxinle.ai.graph.business;

import com.nongxinle.ai.dto.business.DishIngredientCoverAnswerPlan;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class DishIngredientCoverAnswerPlanCardSupport {

    private DishIngredientCoverAnswerPlanCardSupport() {}

    public static List<Map<String, Object>> buildRunCards(DishIngredientCoverAnswerPlan plan) {
        if (plan == null || !DishIngredientCoverAnswerPlan.TYPE.equals(plan.getPlanType())) {
            return List.of();
        }
        Map<String, Object> card = new LinkedHashMap<>();
        card.put("cardType", DishIngredientCoverAnswerPlan.CARD_TYPE);
        String title = plan.getDishName() == null || plan.getDishName().isBlank()
                ? "单菜配料可支撑天数"
                : plan.getDishName() + " · 配料可支撑天数";
        card.put("title", title);
        String subtitle = plan.getStockSnapshotLabel();
        if (subtitle == null || subtitle.isBlank()) {
            subtitle = plan.getTimeLabel();
        }
        card.put("subtitle", subtitle == null ? "" : subtitle);
        card.put("sourceAnswerPlanType", plan.getPlanType());
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("dishName", plan.getDishName());
        payload.put("dishId", plan.getDishId());
        payload.put("stockSnapshotLabel", plan.getStockSnapshotLabel());
        payload.put("salesBaselineLabel", readSummaryString(plan, "salesBaselineLabel"));
        payload.put("salesBaselineStartDate", readSummaryString(plan, "salesBaselineStartDate"));
        payload.put("salesBaselineStopDate", readSummaryString(plan, "salesBaselineStopDate"));
        payload.put("salesBaselineDays", readSummaryInt(plan, "salesBaselineDays"));
        payload.put("salesBaselineSource", readSummaryString(plan, "salesBaselineSource"));
        payload.put("periodFlowLabel", plan.getPeriodFlowLabel());
        payload.put("asOfDate", plan.getAsOfDate());
        payload.put("inventoryQueryTimeKind", plan.getInventoryQueryTimeKind());
        payload.put("timeLabel", plan.getTimeLabel());
        payload.put("dishCoverDays", plan.getDishCoverDays());
        payload.put("dishCoverDaysEmptyReason", resolveDishCoverDaysEmptyReason(plan));
        payload.put("bottleneckIngredientName", plan.getBottleneckIngredientName());
        payload.put("bottleneckCoverDays", plan.getBottleneckCoverDays());
        payload.put(
                "ingredientRowFields",
                List.of(
                        "ingredientName",
                        "recipeUnitPerDish",
                        "currentStockQty",
                        "currentStockQtyEmptyReason",
                        "dailyExpectedUsageQty",
                        "dailyExpectedUsageQtyEmptyReason",
                        "coverDays",
                        "coverDaysEmptyReason",
                        "isBottleneck"));
        payload.put("ingredientRows", projectRowsForCard(plan.getIngredientRows()));
        payload.put("summary", plan.getSummary());
        payload.put("knownGaps", plan.getKnownGaps());
        if (plan.getDebug() != null && plan.getDebug().get("dishIngredientCoverNoRecipeGap") != null) {
            payload.put("dishIngredientCoverNoRecipeGap", plan.getDebug().get("dishIngredientCoverNoRecipeGap"));
        } else {
            payload.put(
                    "dishIngredientCoverNoRecipeGap",
                    plan.getKnownGaps() != null && plan.getKnownGaps().contains("no_recipe_for_dish"));
        }
        card.put("payload", payload);
        return List.of(card);
    }

    private static String resolveDishCoverDaysEmptyReason(DishIngredientCoverAnswerPlan plan) {
        if (plan.getDishCoverDays() != null && !plan.getDishCoverDays().isBlank()) {
            return null;
        }
        if (plan.getKnownGaps() != null
                && plan.getKnownGaps().contains("no_sales_in_window_cannot_compute_cover_days")) {
            return "no_sales_in_window_cannot_compute_cover_days";
        }
        if (plan.getKnownGaps() != null && plan.getKnownGaps().contains("no_recipe_for_dish")) {
            return "no_recipe_for_dish";
        }
        return null;
    }

    /** 卡片层再次约束：只输出老板向字段，禁止成本分析原始键泄漏。 */
    static List<Map<String, Object>> projectRowsForCard(List<Map<String, Object>> rows) {
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        List<Map<String, Object>> out = new ArrayList<>(rows.size());
        for (Map<String, Object> row : rows) {
            if (row == null || row.isEmpty()) {
                continue;
            }
            LinkedHashMap<String, Object> projected = new LinkedHashMap<>();
            copyIfPresent(projected, row, "ingredientName");
            copyIfPresent(projected, row, "recipeUnitPerDish");
            copyIfPresent(projected, row, "currentStockQty");
            copyIfPresent(projected, row, "currentStockQtyEmptyReason");
            copyIfPresent(projected, row, "dailyExpectedUsageQty");
            copyIfPresent(projected, row, "dailyExpectedUsageQtyEmptyReason");
            copyIfPresent(projected, row, "coverDays");
            copyIfPresent(projected, row, "coverDaysEmptyReason");
            if (row.containsKey("isBottleneck")) {
                projected.put("isBottleneck", row.get("isBottleneck"));
            }
            out.add(projected);
        }
        return out;
    }

    private static void copyIfPresent(Map<String, Object> target, Map<String, Object> source, String key) {
        if (source.containsKey(key)) {
            target.put(key, source.get(key));
        }
    }

    private static String readSummaryString(DishIngredientCoverAnswerPlan plan, String key) {
        if (plan == null || plan.getSummary() == null || key == null) {
            return null;
        }
        Object raw = plan.getSummary().get(key);
        return raw == null ? null : raw.toString();
    }

    private static Integer readSummaryInt(DishIngredientCoverAnswerPlan plan, String key) {
        if (plan == null || plan.getSummary() == null || key == null) {
            return null;
        }
        Object raw = plan.getSummary().get(key);
        if (raw instanceof Number n) {
            return n.intValue();
        }
        if (raw == null) {
            return null;
        }
        try {
            return Integer.parseInt(raw.toString().trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
