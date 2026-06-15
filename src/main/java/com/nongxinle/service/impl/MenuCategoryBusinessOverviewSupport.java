package com.nongxinle.service.impl;

import com.nongxinle.entity.GbDistributerFoodEntity;
import com.nongxinle.utils.GbDepartmentGoodsStockReduceSupport;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 菜单类别经营概览：菜品行合并、四象限计数、展示格式化（确定性规则，不接 LLM）。
 */
final class MenuCategoryBusinessOverviewSupport {

    private MenuCategoryBusinessOverviewSupport() {
    }

    static Map<String, LocalDate> currentPeriodRange(LocalDate today, int days) {
        LocalDate end = today;
        LocalDate start = today.minusDays(days - 1L);
        Map<String, LocalDate> m = new LinkedHashMap<>();
        m.put("start", start);
        m.put("end", end);
        return m;
    }

    static Map<String, LocalDate> comparePeriodRange(LocalDate today, int days) {
        LocalDate currentStart = today.minusDays(days - 1L);
        LocalDate end = currentStart.minusDays(1);
        LocalDate start = end.minusDays(days - 1L);
        Map<String, LocalDate> m = new LinkedHashMap<>();
        m.put("start", start);
        m.put("end", end);
        return m;
    }

    static Map<String, Object> periodMeta(LocalDate start, LocalDate end, int days) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("startDate", start.toString());
        m.put("endDate", end.toString());
        m.put("days", days);
        return m;
    }

    static List<Map<String, Object>> aggregateDishRowsByFoodId(List<Map<String, Object>> dishRows) {
        if (dishRows == null || dishRows.isEmpty()) {
            return List.of();
        }
        LinkedHashMap<String, LinkedHashMap<String, Object>> acc = new LinkedHashMap<>();
        int anonymousIdx = 0;
        for (Map<String, Object> row : dishRows) {
            if (row == null) {
                continue;
            }
            String foodKey = resolveFoodKey(row);
            if (foodKey == null) {
                foodKey = "anon:" + anonymousIdx++;
            }
            acc.compute(
                    foodKey,
                    (k, existing) ->
                            existing == null
                                    ? new LinkedHashMap<>(row)
                                    : mergeDishRow(existing, row));
        }
        return new ArrayList<>(acc.values());
    }

    private static String resolveFoodKey(Map<String, Object> row) {
        Object fid = row.get("foodId");
        if (fid == null) {
            fid = row.get("dishId");
        }
        if (fid == null) {
            return null;
        }
        String s = String.valueOf(fid).trim();
        return s.isEmpty() ? null : "food:" + s;
    }

    private static LinkedHashMap<String, Object> mergeDishRow(
            LinkedHashMap<String, Object> acc, Map<String, Object> row) {
        BigDecimal qty =
                coerce(acc.get("soldPortionsTotal")).add(coerce(row.get("soldPortionsTotal")));
        BigDecimal rev = coerce(acc.get("actualRevenue")).add(coerce(row.get("actualRevenue")));
        BigDecimal cost123 =
                coerce(acc.get("actualCostTotalAmount123"))
                        .add(coerce(row.get("actualCostTotalAmount123")));
        if (cost123.compareTo(BigDecimal.ZERO) == 0) {
            cost123 =
                    coerce(acc.get("actualCostTotalAmount"))
                            .add(coerce(row.get("actualCostTotalAmount")));
        }
        BigDecimal costType1 = coerce(acc.get("actualCostAmount")).add(coerce(row.get("actualCostAmount")));
        BigDecimal theoryTotal =
                coerce(acc.get("theoreticalCostTotalAmount"))
                        .add(coerce(row.get("theoreticalCostTotalAmount")));

        acc.put("soldPortionsTotal", moneyPlain(qty));
        acc.put("actualRevenue", moneyPlain(rev));
        acc.put("actualCostTotalAmount123", moneyPlain(cost123));
        acc.put("theoreticalCostTotalAmount", moneyPlain(theoryTotal));
        if (costType1.compareTo(BigDecimal.ZERO) != 0) {
            acc.put("actualCostAmount", moneyPlain(costType1));
        }
        acc.put("blendedGrossMarginRateOnListPrice", blendedMarginPercent(rev, cost123));
        acc.put("theoreticalGrossMarginRateOnListPrice", blendedMarginPercent(rev, theoryTotal));
        if (qty.compareTo(BigDecimal.ZERO) > 0) {
            acc.put("actualCostPerPortion", moneyPlain(cost123.divide(qty, 8, RoundingMode.HALF_UP)));
            acc.put("theoryCostPerPortion", moneyPlain(theoryTotal.divide(qty, 8, RoundingMode.HALF_UP)));
            acc.put(
                    "diffCostPerPortion",
                    moneyPlain(
                            cost123.subtract(theoryTotal).divide(qty, 8, RoundingMode.HALF_UP)));
        }
        preferNonEmpty(acc, row, "dishName");
        preferNonEmpty(acc, row, "foodName");
        preferNonEmpty(acc, row, "salesUnitPrice");
        preferNonEmpty(acc, row, "listPricePerPortion");
        return acc;
    }

    private static void preferNonEmpty(Map<String, Object> acc, Map<String, Object> row, String field) {
        Object existing = acc.get(field);
        if (existing != null && !String.valueOf(existing).trim().isEmpty()) {
            return;
        }
        Object v = row.get(field);
        if (v != null && !String.valueOf(v).trim().isEmpty()) {
            acc.put(field, v);
        }
    }

    static PeriodRollup rollupRows(List<Map<String, Object>> rows) {
        BigDecimal sales = BigDecimal.ZERO;
        BigDecimal portions = BigDecimal.ZERO;
        BigDecimal cost = BigDecimal.ZERO;
        if (rows != null) {
            for (Map<String, Object> row : rows) {
                if (row == null) {
                    continue;
                }
                BigDecimal rev = coerce(row.get("actualRevenue"));
                BigDecimal qty = coerce(row.get("soldPortionsTotal"));
                BigDecimal c123 = coerce(row.get("actualCostTotalAmount123"));
                if (c123.compareTo(BigDecimal.ZERO) == 0) {
                    c123 = coerce(row.get("actualCostTotalAmount"));
                }
                sales = sales.add(rev);
                portions = portions.add(qty);
                cost = cost.add(c123);
            }
        }
        BigDecimal profit = sales.subtract(cost);
        BigDecimal marginRatio = BigDecimal.ZERO;
        if (sales.compareTo(BigDecimal.ZERO) > 0) {
            marginRatio = profit.divide(sales, 8, RoundingMode.HALF_UP);
        }
        return new PeriodRollup(sales, portions, cost, profit, marginRatio);
    }

    static Map<String, Object> periodPayload(PeriodRollup rollup) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("salesAmount", moneyDisplay(rollup.sales()));
        m.put("soldPortions", moneyDisplay(rollup.portions()));
        m.put("actualCostAmount", moneyDisplay(rollup.cost()));
        m.put("actualProfitAmount", moneyDisplay(rollup.profit()));
        m.put("averageGrossMarginRate", percentDisplayFromRatio(rollup.marginRatio()));
        return m;
    }

    static Map<String, Object> portfolioCounts(List<Map<String, Object>> categoryDishRows) {
        PortfolioCounts counts = classifyPortfolio(categoryDishRows);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("starCount", counts.star);
        m.put("trafficCount", counts.traffic);
        m.put("potentialCount", counts.potential);
        m.put("watchCount", counts.watch);
        m.put("totalDishCount", counts.total);
        return m;
    }

    static String buildBusinessHint(
            PortfolioCounts counts, PeriodRollup current, BigDecimal averageDishTargetGrossMarginPercent) {
        if (counts.total <= 0) {
            return "该分类暂无经营样本，建议补充销量后再评估。";
        }
        if (counts.traffic >= counts.star && counts.traffic > 0) {
            return "引流菜偏多，建议优先复核两道低毛利畅销菜";
        }
        if (counts.watch >= 2 && counts.watch > counts.potential) {
            return "观察档菜品偏多，建议先跟踪一个周期再调整";
        }
        if (averageDishTargetGrossMarginPercent != null
                && current.marginRatio().compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal target =
                    averageDishTargetGrossMarginPercent
                            .divide(BigDecimal.valueOf(100), 8, RoundingMode.HALF_UP);
            if (current.marginRatio().compareTo(target) < 0) {
                return "实际毛利率低于该分类菜品目标均值，建议复核成本结构与定价";
            }
        }
        if (counts.star > 0) {
            return "明星菜结构尚可，可继续稳定供应与推荐位";
        }
        return "建议结合销量与毛利结构，优先优化潜力菜曝光";
    }

    static List<GbDistributerFoodEntity> dishesUnderCategory(
            List<GbDistributerFoodEntity> dishes, Integer categoryId) {
        List<GbDistributerFoodEntity> out = new ArrayList<>();
        if (dishes == null || categoryId == null) {
            return out;
        }
        for (GbDistributerFoodEntity dish : dishes) {
            if (dish != null && categoryId.equals(dish.getGbDfFoodFatherId())) {
                out.add(dish);
            }
        }
        return out;
    }

    /** 分类下已配置目标毛利率的菜品，目标毛利率(%) 的算术平均；无配置时 null。 */
    static BigDecimal averageTargetGrossMarginRatePercent(List<GbDistributerFoodEntity> dishesUnderCategory) {
        if (dishesUnderCategory == null || dishesUnderCategory.isEmpty()) {
            return null;
        }
        BigDecimal sum = BigDecimal.ZERO;
        int count = 0;
        for (GbDistributerFoodEntity dish : dishesUnderCategory) {
            if (dish == null || dish.getGbDfTargetGrossMarginRate() == null) {
                continue;
            }
            sum = sum.add(dish.getGbDfTargetGrossMarginRate());
            count++;
        }
        if (count == 0) {
            return null;
        }
        return sum.divide(BigDecimal.valueOf(count), 8, RoundingMode.HALF_UP);
    }

    static String targetGrossMarginRateTextFromDishes(List<GbDistributerFoodEntity> dishesUnderCategory) {
        BigDecimal avg = averageTargetGrossMarginRatePercent(dishesUnderCategory);
        if (avg == null) {
            return null;
        }
        return percentDisplayFromNumber(avg);
    }

    static String marginChangePoints(PeriodRollup current, PeriodRollup compare) {
        BigDecimal cur = current.marginRatio().multiply(BigDecimal.valueOf(100));
        BigDecimal prev = compare.marginRatio().multiply(BigDecimal.valueOf(100));
        return signedPercentPoints(cur.subtract(prev));
    }

    static String percentChange(BigDecimal current, BigDecimal previous) {
        if (previous == null || previous.compareTo(BigDecimal.ZERO) == 0) {
            if (current == null || current.compareTo(BigDecimal.ZERO) == 0) {
                return "0.00%";
            }
            return "+100.00%";
        }
        BigDecimal delta =
                current.subtract(previous)
                        .multiply(BigDecimal.valueOf(100))
                        .divide(previous, 2, RoundingMode.HALF_UP);
        return signedPercentPoints(delta);
    }

    static Map<String, Object> changePayload(PeriodRollup current, PeriodRollup compare) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("grossMarginRateChange", marginChangePoints(current, compare));
        m.put("salesAmountChange", percentChange(current.sales(), compare.sales()));
        m.put("soldPortionsChange", percentChange(current.portions(), compare.portions()));
        m.put("profitAmountChange", percentChange(current.profit(), compare.profit()));
        return m;
    }

    static List<Map<String, Object>> filterRowsByFoodIds(
            List<Map<String, Object>> rows, Set<Integer> foodIds) {
        if (rows == null || rows.isEmpty() || foodIds == null || foodIds.isEmpty()) {
            return List.of();
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            Integer fid = parseFoodId(row);
            if (fid != null && foodIds.contains(fid)) {
                out.add(row);
            }
        }
        return out;
    }

    static Integer parseFoodId(Map<String, Object> row) {
        if (row == null) {
            return null;
        }
        Object fid = row.get("foodId");
        if (fid == null) {
            fid = row.get("dishId");
        }
        if (fid instanceof Integer) {
            return (Integer) fid;
        }
        if (fid instanceof Number) {
            return ((Number) fid).intValue();
        }
        if (fid != null) {
            try {
                return Integer.parseInt(String.valueOf(fid).trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    static Set<Integer> foodIdsUnderCategory(List<GbDistributerFoodEntity> dishes, Integer categoryId) {
        Set<Integer> ids = new LinkedHashSet<>();
        if (dishes == null || categoryId == null) {
            return ids;
        }
        for (GbDistributerFoodEntity dish : dishes) {
            if (dish == null || dish.getGbDistributerFoodId() == null) {
                continue;
            }
            if (categoryId.equals(dish.getGbDfFoodFatherId())) {
                ids.add(dish.getGbDistributerFoodId());
            }
        }
        return ids;
    }

    private static PortfolioCounts classifyPortfolio(List<Map<String, Object>> rows) {
        PortfolioCounts counts = new PortfolioCounts();
        if (rows == null || rows.isEmpty()) {
            return counts;
        }
        List<BigDecimal> salesValues = new ArrayList<>();
        List<BigDecimal> profitValues = new ArrayList<>();
        List<DishSlice> slices = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            BigDecimal qty = coerce(row.get("soldPortionsTotal"));
            BigDecimal rev = coerce(row.get("actualRevenue"));
            BigDecimal cost123 = coerce(row.get("actualCostTotalAmount123"));
            if (cost123.compareTo(BigDecimal.ZERO) == 0) {
                cost123 = coerce(row.get("actualCostTotalAmount"));
            }
            BigDecimal profit = rev.subtract(cost123);
            salesValues.add(qty);
            profitValues.add(profit);
            slices.add(new DishSlice(qty, profit));
        }
        counts.total = slices.size();
        BigDecimal salesThreshold = median(salesValues);
        BigDecimal profitThreshold = median(profitValues);
        for (DishSlice s : slices) {
            Quadrant q = resolveQuadrant(s, salesThreshold, profitThreshold);
            switch (q) {
                case STAR -> counts.star++;
                case TRAFFIC -> counts.traffic++;
                case POTENTIAL -> counts.potential++;
                case WATCH -> counts.watch++;
                default -> {
                }
            }
        }
        return counts;
    }

    private static Quadrant resolveQuadrant(DishSlice s, BigDecimal salesThreshold, BigDecimal profitThreshold) {
        boolean highSales = s.soldPortions.compareTo(salesThreshold) >= 0;
        boolean highProfit = s.profit.compareTo(profitThreshold) >= 0;
        if (highSales) {
            if (highProfit && s.profit.compareTo(BigDecimal.ZERO) >= 0) {
                return Quadrant.STAR;
            }
            return Quadrant.TRAFFIC;
        }
        return highProfit ? Quadrant.POTENTIAL : Quadrant.WATCH;
    }

    private static BigDecimal median(List<BigDecimal> values) {
        if (values == null || values.isEmpty()) {
            return BigDecimal.ZERO;
        }
        List<BigDecimal> sorted = new ArrayList<>(values);
        sorted.sort(Comparator.naturalOrder());
        int n = sorted.size();
        if (n % 2 == 1) {
            return sorted.get(n / 2);
        }
        return sorted.get(n / 2 - 1)
                .add(sorted.get(n / 2))
                .divide(BigDecimal.valueOf(2), 4, RoundingMode.HALF_UP);
    }

    private static BigDecimal coerce(Object raw) {
        return GbDepartmentGoodsStockReduceSupport.coerceDecimal(raw);
    }

    private static String moneyPlain(BigDecimal v) {
        return v.setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
    }

    private static String moneyDisplay(BigDecimal v) {
        return v.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private static String blendedMarginPercent(BigDecimal revenue, BigDecimal cost123) {
        if (revenue == null || revenue.compareTo(BigDecimal.ZERO) <= 0) {
            return "0.00";
        }
        BigDecimal profit = revenue.subtract(cost123);
        return GbDepartmentGoodsStockReduceSupport.formatRatioAsPercentTwoDecimals(
                profit.divide(revenue, 8, RoundingMode.HALF_UP));
    }

    static String percentDisplayFromRatio(BigDecimal ratio0to1) {
        if (ratio0to1 == null) {
            return "0.00%";
        }
        return percentDisplayFromNumber(ratio0to1.multiply(BigDecimal.valueOf(100)));
    }

    private static String percentDisplayFromNumber(BigDecimal percentNumber) {
        return percentNumber.setScale(2, RoundingMode.HALF_UP).toPlainString() + "%";
    }

    private static String signedPercentPoints(BigDecimal deltaPoints) {
        BigDecimal v = deltaPoints.setScale(2, RoundingMode.HALF_UP);
        if (v.compareTo(BigDecimal.ZERO) > 0) {
            return "+" + v.toPlainString() + "%";
        }
        return v.toPlainString() + "%";
    }

    private enum Quadrant {
        STAR,
        TRAFFIC,
        POTENTIAL,
        WATCH
    }

    private record DishSlice(BigDecimal soldPortions, BigDecimal profit) {
    }

    static final class PeriodRollup {
        private final BigDecimal sales;
        private final BigDecimal portions;
        private final BigDecimal cost;
        private final BigDecimal profit;
        private final BigDecimal marginRatio;

        PeriodRollup(BigDecimal sales, BigDecimal portions, BigDecimal cost, BigDecimal profit, BigDecimal marginRatio) {
            this.sales = sales;
            this.portions = portions;
            this.cost = cost;
            this.profit = profit;
            this.marginRatio = marginRatio;
        }

        BigDecimal sales() {
            return sales;
        }

        BigDecimal portions() {
            return portions;
        }

        BigDecimal cost() {
            return cost;
        }

        BigDecimal profit() {
            return profit;
        }

        BigDecimal marginRatio() {
            return marginRatio;
        }
    }

    static final class PortfolioCounts {
        int star;
        int traffic;
        int potential;
        int watch;
        int total;
    }

    static DishRoleInfo defaultDishRole() {
        return DishRoleInfo.fromQuadrant(Quadrant.WATCH);
    }

    static String moneyDisplayPublic(BigDecimal v) {
        return moneyDisplay(v);
    }

    static String signedPercentPointsPublic(BigDecimal deltaPoints) {
        return signedPercentPoints(deltaPoints);
    }

    static Map<String, Object> foodListPeriodBlock(
            LocalDate currentStart,
            LocalDate currentEnd,
            LocalDate compareStart,
            LocalDate compareEnd,
            int days) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("days", days);
        m.put("currentStartDate", currentStart.toString());
        m.put("currentEndDate", currentEnd.toString());
        m.put("compareStartDate", compareStart.toString());
        m.put("compareEndDate", compareEnd.toString());
        m.put("compareLabel", "较上周期");
        return m;
    }

    static Map<Integer, DishRoleInfo> assignDishRoles(List<Map<String, Object>> rows) {
        Map<Integer, DishRoleInfo> out = new LinkedHashMap<>();
        if (rows == null || rows.isEmpty()) {
            return out;
        }
        List<BigDecimal> salesValues = new ArrayList<>();
        List<BigDecimal> profitValues = new ArrayList<>();
        List<DishSliceWithId> slices = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            Integer foodId = parseFoodId(row);
            if (foodId == null) {
                continue;
            }
            BigDecimal qty = coerce(row.get("soldPortionsTotal"));
            BigDecimal rev = coerce(row.get("actualRevenue"));
            BigDecimal cost123 = dishActualCost(row);
            BigDecimal profit = rev.subtract(cost123);
            salesValues.add(qty);
            profitValues.add(profit);
            slices.add(new DishSliceWithId(foodId, qty, profit));
        }
        BigDecimal salesThreshold = median(salesValues);
        BigDecimal profitThreshold = median(profitValues);
        for (DishSliceWithId s : slices) {
            Quadrant q = resolveQuadrant(new DishSlice(s.soldPortions(), s.profit()), salesThreshold, profitThreshold);
            out.put(s.foodId(), DishRoleInfo.fromQuadrant(q));
        }
        return out;
    }

    static BigDecimal dishActualCost(Map<String, Object> row) {
        BigDecimal cost123 = coerce(row.get("actualCostTotalAmount123"));
        if (cost123.compareTo(BigDecimal.ZERO) == 0) {
            cost123 = coerce(row.get("actualCostTotalAmount"));
        }
        return cost123;
    }

    static BigDecimal dishTheoryCost(Map<String, Object> row) {
        return coerce(row.get("theoreticalCostTotalAmount"));
    }

    static BigDecimal dishProfit(Map<String, Object> row) {
        return coerce(row.get("actualRevenue")).subtract(dishActualCost(row));
    }

    static BigDecimal marginRatioFromRow(Map<String, Object> row) {
        BigDecimal rev = coerce(row.get("actualRevenue"));
        if (rev.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        return dishProfit(row).divide(rev, 8, RoundingMode.HALF_UP);
    }

    static BigDecimal theoryMarginRatioFromRow(Map<String, Object> row) {
        BigDecimal rev = coerce(row.get("actualRevenue"));
        if (rev.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        return rev.subtract(dishTheoryCost(row)).divide(rev, 8, RoundingMode.HALF_UP);
    }

    /** 标价收入：listPricePerPortion × soldPortionsTotal（与 dep food 单菜 grossMarginRateTheoryOnListPrice 同口径分母） */
    static BigDecimal listPriceRevenueFromRow(Map<String, Object> row) {
        BigDecimal listPp = coerce(row.get("listPricePerPortion"));
        BigDecimal qty = coerce(row.get("soldPortionsTotal"));
        if (listPp.compareTo(BigDecimal.ZERO) <= 0 || qty.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        return listPp.multiply(qty).setScale(2, RoundingMode.HALF_UP);
    }

    /** 理论毛利率（标价口径）：(标价收入 − 理论成本) ÷ 标价收入，与 dep food grossMarginRateTheoryOnListPrice 公式一致 */
    static BigDecimal theoryMarginRatioOnListPrice(Map<String, Object> row) {
        BigDecimal listRev = listPriceRevenueFromRow(row);
        if (listRev.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        return listRev.subtract(dishTheoryCost(row)).divide(listRev, 8, RoundingMode.HALF_UP);
    }

    static String grossMarginGapRateText(Map<String, Object> row) {
        BigDecimal actual = marginRatioFromRow(row).multiply(BigDecimal.valueOf(100));
        BigDecimal theory = theoryMarginRatioFromRow(row).multiply(BigDecimal.valueOf(100));
        return signedPercentPoints(actual.subtract(theory));
    }

    static BigDecimal costGapAmount(Map<String, Object> row) {
        return dishActualCost(row).subtract(dishTheoryCost(row));
    }

    static Map<String, Object> buildFoodPeriodMetrics(Map<String, Object> row, boolean includeRanks) {
        Map<String, Object> m = new LinkedHashMap<>();
        BigDecimal qty = coerce(row.get("soldPortionsTotal"));
        BigDecimal rev = coerce(row.get("actualRevenue"));
        BigDecimal cost = dishActualCost(row);
        BigDecimal theory = dishTheoryCost(row);
        BigDecimal profit = rev.subtract(cost);
        m.put("salesCount", moneyDisplay(qty));
        m.put("salesAmount", moneyDisplay(rev));
        m.put("actualCostAmount", moneyDisplay(cost));
        m.put("theoreticalCostAmount", moneyDisplay(theory));
        m.put("actualProfitAmount", moneyDisplay(profit));
        if (qty.compareTo(BigDecimal.ZERO) > 0) {
            m.put("actualCostPerPortion", moneyDisplay(cost.divide(qty, 8, RoundingMode.HALF_UP)));
            m.put("theoreticalCostPerPortion", moneyDisplay(theory.divide(qty, 8, RoundingMode.HALF_UP)));
        } else {
            m.put("actualCostPerPortion", moneyDisplay(BigDecimal.ZERO));
            m.put("theoreticalCostPerPortion", moneyDisplay(BigDecimal.ZERO));
        }
        m.put("actualGrossMarginRate", percentDisplayFromRatio(marginRatioFromRow(row)));
        m.put("theoreticalGrossMarginRate", percentDisplayFromRatio(theoryMarginRatioOnListPrice(row)));
        m.put("grossMarginGapRate", grossMarginGapRateText(row));
        if (includeRanks) {
            m.put("salesRank", row.get("salesRank"));
            m.put("profitRank", row.get("profitRank"));
            m.put("marginRank", row.get("marginRank"));
        }
        return m;
    }

    static Map<String, Object> buildFoodCompareMetrics(Map<String, Object> row) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("salesCount", moneyDisplay(coerce(row.get("soldPortionsTotal"))));
        m.put("salesAmount", moneyDisplay(coerce(row.get("actualRevenue"))));
        m.put("actualProfitAmount", moneyDisplay(dishProfit(row)));
        m.put("actualGrossMarginRate", percentDisplayFromRatio(marginRatioFromRow(row)));
        return m;
    }

    static Map<String, Object> buildFoodChangeMetrics(Map<String, Object> current, Map<String, Object> compare) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put(
                "salesCountChangeRate",
                percentChange(
                        coerce(current.get("soldPortionsTotal")),
                        coerce(compare.get("soldPortionsTotal"))));
        m.put(
                "salesAmountChangeRate",
                percentChange(
                        coerce(current.get("actualRevenue")),
                        coerce(compare.get("actualRevenue"))));
        m.put(
                "actualProfitChangeRate",
                percentChange(dishProfit(current), dishProfit(compare)));
        BigDecimal curMargin = marginRatioFromRow(current).multiply(BigDecimal.valueOf(100));
        BigDecimal prevMargin = marginRatioFromRow(compare).multiply(BigDecimal.valueOf(100));
        BigDecimal delta = curMargin.subtract(prevMargin).setScale(2, RoundingMode.HALF_UP);
        if (delta.compareTo(BigDecimal.ZERO) > 0) {
            m.put("grossMarginRateChangePoint", "+" + delta.toPlainString());
        } else {
            m.put("grossMarginRateChangePoint", delta.toPlainString());
        }
        return m;
    }

    static Map<String, Object> buildBusinessDiagnosis(
            Map<String, Object> currentRow, DishRoleInfo role, GbDistributerFoodEntity dish) {
        Map<String, Object> m = new LinkedHashMap<>();
        BigDecimal gapPoints =
                marginRatioFromRow(currentRow)
                        .subtract(theoryMarginRatioFromRow(currentRow))
                        .multiply(BigDecimal.valueOf(100));
        if (gapPoints.compareTo(BigDecimal.valueOf(3)) >= 0) {
            m.put("level", "GOOD");
            m.put("title", "实际毛利高于理论毛利");
            m.put(
                    "message",
                    "实际毛利高于理论 "
                            + gapPoints.setScale(1, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()
                            + "%，成本控制优秀。");
            m.put("suggestedAction", role != null ? role.suggestedAction() : "继续主推");
            return m;
        }
        if (gapPoints.compareTo(BigDecimal.valueOf(-3)) <= 0) {
            m.put("level", "WARN");
            m.put("title", "实际毛利低于理论毛利");
            m.put(
                    "message",
                    "实际毛利低于理论 "
                            + gapPoints.abs().setScale(1, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()
                            + "%，建议复核成本与配方。");
            m.put("suggestedAction", "优先复核成本结构与出品标准");
            return m;
        }
        if (dish != null
                && dish.getGbDfTargetGrossMarginRate() != null
                && marginRatioFromRow(currentRow)
                                .multiply(BigDecimal.valueOf(100))
                                .compareTo(dish.getGbDfTargetGrossMarginRate())
                        < 0) {
            m.put("level", "WARN");
            m.put("title", "实际毛利率低于菜品目标");
            m.put("message", "实际毛利率低于该菜品目标，建议复核定价与成本。");
            m.put("suggestedAction", "复核定价、套餐搭配与主推策略");
            return m;
        }
        if (role != null && role.code().equals("WATCH")) {
            m.put("level", "OBSERVE");
            m.put("title", "观察档菜品");
            m.put("message", role.reason());
            m.put("suggestedAction", role.suggestedAction());
            return m;
        }
        m.put("level", "NORMAL");
        m.put("title", "经营表现平稳");
        m.put("message", role != null ? role.reason() : "销量与毛利处于本分类正常区间。");
        m.put("suggestedAction", role != null ? role.suggestedAction() : "保持供应并持续跟踪");
        return m;
    }

    static boolean isRiskFood(Map<String, Object> diagnosis) {
        if (diagnosis == null) {
            return false;
        }
        Object level = diagnosis.get("level");
        return "WARN".equals(level) || "BAD".equals(level);
    }

    static Map<Integer, Map<String, Object>> indexRowsByFoodId(List<Map<String, Object>> rows) {
        Map<Integer, Map<String, Object>> out = new LinkedHashMap<>();
        if (rows == null) {
            return out;
        }
        for (Map<String, Object> row : rows) {
            Integer foodId = parseFoodId(row);
            if (foodId != null) {
                out.put(foodId, row);
            }
        }
        return out;
    }

    static void assignRanks(List<Map<String, Object>> rows) {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        assignRankField(rows, "salesRank", r -> coerce(r.get("soldPortionsTotal")));
        assignRankField(rows, "profitRank", MenuCategoryBusinessOverviewSupport::dishProfit);
        assignRankField(rows, "marginRank", MenuCategoryBusinessOverviewSupport::marginRatioFromRow);
    }

    private static void assignRankField(
            List<Map<String, Object>> rows, String field, java.util.function.Function<Map<String, Object>, BigDecimal> key) {
        List<Map<String, Object>> sorted = new ArrayList<>(rows);
        sorted.sort(
                (a, b) -> {
                    int cmp = key.apply(b).compareTo(key.apply(a));
                    if (cmp != 0) {
                        return cmp;
                    }
                    Integer fa = parseFoodId(a);
                    Integer fb = parseFoodId(b);
                    return fa != null && fb != null ? fa.compareTo(fb) : 0;
                });
        for (int i = 0; i < sorted.size(); i++) {
            sorted.get(i).put(field, i + 1);
        }
    }

    static List<Map<String, Object>> sortFoodRows(
            List<Map<String, Object>> rows, String sortBy, String sortOrder) {
        if (rows == null || rows.size() <= 1) {
            return rows == null ? List.of() : rows;
        }
        String key = sortBy == null ? "salesCount" : sortBy.trim();
        boolean asc = sortOrder != null && "ASC".equalsIgnoreCase(sortOrder.trim());
        Comparator<Map<String, Object>> cmp =
                switch (key) {
                    case "actualGrossMarginRate" ->
                            Comparator.comparing(MenuCategoryBusinessOverviewSupport::marginRatioFromRow);
                    case "actualProfitAmount" ->
                            Comparator.comparing(MenuCategoryBusinessOverviewSupport::dishProfit);
                    case "costGapAmount" ->
                            Comparator.comparing(MenuCategoryBusinessOverviewSupport::costGapAmount);
                    case "actualCostPerPortion" ->
                            Comparator.comparing(
                                    r -> {
                                        BigDecimal qty = coerce(r.get("soldPortionsTotal"));
                                        if (qty.compareTo(BigDecimal.ZERO) <= 0) {
                                            return BigDecimal.ZERO;
                                        }
                                        return dishActualCost(r).divide(qty, 8, RoundingMode.HALF_UP);
                                    });
                    default -> Comparator.comparing(r -> coerce(r.get("soldPortionsTotal")));
                };
        if (!asc) {
            cmp = cmp.reversed();
        }
        final Comparator<Map<String, Object>> sortCmp = cmp;
        List<Map<String, Object>> sorted = new ArrayList<>(rows);
        sorted.sort(
                (a, b) -> {
                    int c = sortCmp.compare(a, b);
                    if (c != 0) {
                        return c;
                    }
                    Integer fa = parseFoodId(a);
                    Integer fb = parseFoodId(b);
                    return fa != null && fb != null ? fa.compareTo(fb) : 0;
                });
        return sorted;
    }

    static PeriodRollupWithTheory rollupWithTheory(List<Map<String, Object>> rows) {
        BigDecimal sales = BigDecimal.ZERO;
        BigDecimal listRevenue = BigDecimal.ZERO;
        BigDecimal portions = BigDecimal.ZERO;
        BigDecimal cost = BigDecimal.ZERO;
        BigDecimal theory = BigDecimal.ZERO;
        if (rows != null) {
            for (Map<String, Object> row : rows) {
                if (row == null) {
                    continue;
                }
                sales = sales.add(coerce(row.get("actualRevenue")));
                listRevenue = listRevenue.add(listPriceRevenueFromRow(row));
                portions = portions.add(coerce(row.get("soldPortionsTotal")));
                cost = cost.add(dishActualCost(row));
                theory = theory.add(dishTheoryCost(row));
            }
        }
        BigDecimal profit = sales.subtract(cost);
        BigDecimal marginRatio = BigDecimal.ZERO;
        BigDecimal theoryMarginRatio = BigDecimal.ZERO;
        if (listRevenue.compareTo(BigDecimal.ZERO) > 0) {
            marginRatio = profit.divide(sales, 8, RoundingMode.HALF_UP);
            theoryMarginRatio = listRevenue.subtract(theory).divide(listRevenue, 8, RoundingMode.HALF_UP);
        }
        return new PeriodRollupWithTheory(sales, portions, cost, theory, profit, marginRatio, theoryMarginRatio);
    }

    static record PeriodRollupWithTheory(
            BigDecimal sales,
            BigDecimal portions,
            BigDecimal cost,
            BigDecimal theoryCost,
            BigDecimal profit,
            BigDecimal marginRatio,
            BigDecimal theoryMarginRatio) {
    }

    static final class DishRoleInfo {
        private final String code;
        private final String name;
        private final String reason;
        private final String suggestedAction;

        private DishRoleInfo(String code, String name, String reason, String suggestedAction) {
            this.code = code;
            this.name = name;
            this.reason = reason;
            this.suggestedAction = suggestedAction;
        }

        static DishRoleInfo fromQuadrant(Quadrant q) {
            return switch (q) {
                case STAR ->
                        new DishRoleInfo(
                                "STAR",
                                "明星菜",
                                "销量高，实际利润也高",
                                "稳定供应、继续主推");
                case TRAFFIC ->
                        new DishRoleInfo(
                                "TRAFFIC",
                                "引流菜",
                                "销量高，但实际利润偏低",
                                "保留流量价值，优先降本或适度提价");
                case POTENTIAL ->
                        new DishRoleInfo(
                                "POTENTIAL",
                                "潜力菜",
                                "销量一般，实际利润较好",
                                "增加曝光、放到推荐位、尝试套餐");
                case WATCH ->
                        new DishRoleInfo(
                                "WATCH",
                                "观察菜",
                                "销量和利润都相对偏低",
                                "先观察并复核曝光与备货");
            };
        }

        String code() {
            return code;
        }

        String name() {
            return name;
        }

        String reason() {
            return reason;
        }

        String suggestedAction() {
            return suggestedAction;
        }
    }

    private record DishSliceWithId(Integer foodId, BigDecimal soldPortions, BigDecimal profit) {
    }
}
