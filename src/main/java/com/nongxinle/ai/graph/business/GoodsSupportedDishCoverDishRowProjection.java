package com.nongxinle.ai.graph.business;

import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.Map;

/** 原料视角：每道菜独立估算还能卖多少份 / 支撑几天（共享同一原料库存快照）。 */
final class GoodsSupportedDishCoverDishRowProjection {

    static final String REASON_NO_SALES = "no_sales_in_window_cannot_compute_cover_days";
    static final String REASON_NO_RECIPE = "recipe_unit_missing";
    static final String REASON_NO_STOCK = "inventory_rest_weight_missing";

    private GoodsSupportedDishCoverDishRowProjection() {}

    static Map<String, Object> project(
            Integer dishId,
            String dishName,
            BigDecimal recipeUnitPerDish,
            BigDecimal salesPortions,
            int baselineDays,
            BigDecimal ingredientStockQty) {
        LinkedHashMap<String, Object> out = new LinkedHashMap<>();
        out.put("dishId", dishId);
        out.put("dishName", StringUtils.hasText(dishName) ? dishName.trim() : null);
        if (recipeUnitPerDish != null && recipeUnitPerDish.compareTo(BigDecimal.ZERO) > 0) {
            out.put("recipeUnitPerDish", formatQty(recipeUnitPerDish));
        } else {
            out.put("recipeUnitPerDish", null);
            out.put("recipeUnitPerDishEmptyReason", REASON_NO_RECIPE);
        }

        BigDecimal dailySales = null;
        if (salesPortions != null
                && salesPortions.compareTo(BigDecimal.ZERO) > 0
                && baselineDays > 0) {
            dailySales =
                    salesPortions.divide(BigDecimal.valueOf(baselineDays), 4, RoundingMode.HALF_UP);
            out.put("salesPortionsInBaseline", formatQty(salesPortions));
            out.put("dailySalesPortions", formatQty(dailySales));
        } else {
            out.put("salesPortionsInBaseline", null);
            out.put("dailySalesPortions", null);
            out.put("dailySalesPortionsEmptyReason", REASON_NO_SALES);
        }

        BigDecimal supportedPortions = null;
        if (ingredientStockQty != null
                && ingredientStockQty.compareTo(BigDecimal.ZERO) > 0
                && recipeUnitPerDish != null
                && recipeUnitPerDish.compareTo(BigDecimal.ZERO) > 0) {
            supportedPortions =
                    ingredientStockQty.divide(recipeUnitPerDish, 2, RoundingMode.HALF_UP);
            out.put("supportedPortionsFromStock", formatQty(supportedPortions));
        } else {
            out.put("supportedPortionsFromStock", null);
            out.put(
                    "supportedPortionsFromStockEmptyReason",
                    ingredientStockQty == null ? REASON_NO_STOCK : REASON_NO_RECIPE);
        }

        BigDecimal coverDays = null;
        String coverReason = null;
        if (dailySales == null || dailySales.compareTo(BigDecimal.ZERO) <= 0) {
            coverReason = REASON_NO_SALES;
        } else if (supportedPortions == null) {
            coverReason = ingredientStockQty == null ? REASON_NO_STOCK : REASON_NO_RECIPE;
        } else {
            coverDays = supportedPortions.divide(dailySales, 2, RoundingMode.HALF_UP);
        }
        if (coverDays != null) {
            out.put("coverDays", formatQty(coverDays));
            out.put("coverDaysEmptyReason", null);
        } else {
            out.put("coverDays", null);
            out.put("coverDaysEmptyReason", coverReason);
        }
        out.put("_coverDaysSort", coverDays);
        return out;
    }

    private static String formatQty(BigDecimal v) {
        if (v == null) {
            return null;
        }
        return v.stripTrailingZeros().toPlainString();
    }
}
