package com.nongxinle.ai.graph.business;

import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * {@code dish.ingredient_cover_days.v1} 配料行：从 {@code dish_cost_analysis} 原料行投影为老板向字段。
 * <p>库存口径：{@code inventoryRestWeightQty}（部门库存剩余重量汇总），禁止用出库分摊字段冒充库存。
 */
final class DishIngredientCoverIngredientRowProjection {

    static final String REASON_NO_SALES_DAILY_USAGE = "no_sales_in_window_cannot_compute_daily_usage";
    static final String REASON_NO_SALES_COVER_DAYS = "no_sales_in_window_cannot_compute_cover_days";
    static final String REASON_INVENTORY_REST_WEIGHT_MISSING = "inventory_rest_weight_missing";
    static final String REASON_RECIPE_UNIT_MISSING = "recipe_unit_missing";
    static final String REASON_DAILY_USAGE_MISSING = "daily_expected_usage_missing";

    private DishIngredientCoverIngredientRowProjection() {}

    static Map<String, Object> project(
            Map<String, Object> rawRow,
            BigDecimal dailySales,
            int windowDays,
            String bottleneckIngredientName) {
        LinkedHashMap<String, Object> out = new LinkedHashMap<>();

        String ingredientName = resolveIngredientName(rawRow);
        out.put("ingredientName", StringUtils.hasText(ingredientName) ? ingredientName : null);

        BigDecimal recipeUnit = parseDecimal(rawRow.get("recipeUnitPerDish"));
        if (recipeUnit != null && recipeUnit.compareTo(BigDecimal.ZERO) > 0) {
            out.put("recipeUnitPerDish", formatQty(recipeUnit));
        } else {
            out.put("recipeUnitPerDish", null);
        }

        BigDecimal stockQty = resolveCurrentStockQty(rawRow);
        if (stockQty != null) {
            out.put("currentStockQty", formatQty(stockQty));
            out.put("currentStockQtyEmptyReason", null);
        } else {
            out.put("currentStockQty", null);
            out.put("currentStockQtyEmptyReason", REASON_INVENTORY_REST_WEIGHT_MISSING);
        }

        BigDecimal dailyUsage = null;
        String dailyUsageReason = null;
        if (dailySales != null && dailySales.compareTo(BigDecimal.ZERO) > 0) {
            if (recipeUnit != null && recipeUnit.compareTo(BigDecimal.ZERO) > 0) {
                dailyUsage = recipeUnit.multiply(dailySales).setScale(4, RoundingMode.HALF_UP);
            } else {
                BigDecimal theoryQty = parseDecimal(rawRow.get("theoryQtyFromSales"));
                if (theoryQty == null) {
                    theoryQty = parseDecimal(rawRow.get("salesUsageFromOrders"));
                }
                if (theoryQty != null && windowDays > 0) {
                    dailyUsage = theoryQty.divide(BigDecimal.valueOf(windowDays), 4, RoundingMode.HALF_UP);
                } else {
                    dailyUsageReason = REASON_RECIPE_UNIT_MISSING;
                }
            }
        } else {
            dailyUsageReason = REASON_NO_SALES_DAILY_USAGE;
        }
        if (dailyUsage != null) {
            out.put("dailyExpectedUsageQty", formatQty(dailyUsage));
            out.put("dailyExpectedUsageQtyEmptyReason", null);
        } else {
            out.put("dailyExpectedUsageQty", null);
            out.put("dailyExpectedUsageQtyEmptyReason", dailyUsageReason);
        }

        BigDecimal coverDays = null;
        String coverDaysReason = null;
        if (dailySales != null && dailySales.compareTo(BigDecimal.ZERO) > 0) {
            if (stockQty == null) {
                coverDaysReason = REASON_INVENTORY_REST_WEIGHT_MISSING;
            } else if (dailyUsage == null || dailyUsage.compareTo(BigDecimal.ZERO) <= 0) {
                coverDaysReason = REASON_DAILY_USAGE_MISSING;
            } else {
                coverDays = stockQty.divide(dailyUsage, 2, RoundingMode.HALF_UP);
            }
        } else {
            coverDaysReason = REASON_NO_SALES_COVER_DAYS;
        }
        if (coverDays != null) {
            out.put("coverDays", formatQty(coverDays));
            out.put("coverDaysEmptyReason", null);
        } else {
            out.put("coverDays", null);
            out.put("coverDaysEmptyReason", coverDaysReason);
        }

        BigDecimal supportedFromStock = resolveSupportedPortionsFromStock(stockQty, recipeUnit);
        if (supportedFromStock != null) {
            out.put("supportedPortionsFromStock", formatQty(supportedFromStock));
        }

        boolean isBottleneck =
                StringUtils.hasText(bottleneckIngredientName)
                        && StringUtils.hasText(ingredientName)
                        && bottleneckIngredientName.equals(ingredientName);
        out.put("isBottleneck", isBottleneck);
        return out;
    }

    static BigDecimal supportedPortionsFromStockForRanking(Map<String, Object> projectedRow) {
        if (projectedRow == null) {
            return null;
        }
        return parseDecimal(projectedRow.get("supportedPortionsFromStock"));
    }

    static BigDecimal coverDaysForRanking(Map<String, Object> projectedRow) {
        if (projectedRow == null) {
            return null;
        }
        return parseDecimal(projectedRow.get("coverDays"));
    }

    private static String resolveIngredientName(Map<String, Object> rawRow) {
        if (rawRow == null) {
            return "";
        }
        String name = str(rawRow.get("goodsName"));
        if (!StringUtils.hasText(name)) {
            name = str(rawRow.get("gbDgGoodsName"));
        }
        if (!StringUtils.hasText(name)) {
            name = str(rawRow.get("ingredientName"));
        }
        return name;
    }

    /** 真实库存：{@code inventoryRestWeightQty}（由 {@link DishIngredientCoverCostDataEnricher} 写入）。 */
    private static BigDecimal resolveCurrentStockQty(Map<String, Object> rawRow) {
        return parseDecimal(rawRow.get("inventoryRestWeightQty"));
    }

    private static BigDecimal resolveSupportedPortionsFromStock(BigDecimal stockQty, BigDecimal recipeUnit) {
        if (stockQty == null || recipeUnit == null || recipeUnit.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }
        return stockQty.divide(recipeUnit, 4, RoundingMode.HALF_UP);
    }

    private static String formatQty(BigDecimal value) {
        if (value == null) {
            return null;
        }
        return value.stripTrailingZeros().toPlainString();
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
