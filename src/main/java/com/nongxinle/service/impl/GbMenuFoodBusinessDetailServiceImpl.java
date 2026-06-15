package com.nongxinle.service.impl;

import com.nongxinle.constants.AiInsightDishProfitScope;
import com.nongxinle.entity.GbDistributerFoodEntity;
import com.nongxinle.service.GbDepFoodBusinessInsightService;
import com.nongxinle.service.GbDishCostAnalysisService;
import com.nongxinle.service.GbDistributerFoodService;
import com.nongxinle.service.GbMenuFoodBusinessDetailService;
import com.nongxinle.utils.GbDateTimeUtils;
import com.nongxinle.utils.GbDepartmentGoodsStockReduceSupport;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class GbMenuFoodBusinessDetailServiceImpl implements GbMenuFoodBusinessDetailService {

    private static final String SCOPE_GROUP = "GROUP";
    private static final String SCOPE_STORE = "STORE";

    private final GbDishCostAnalysisService gbDishCostAnalysisService;
    private final GbDistributerFoodService gbDistributerFoodService;
    private final GbDepFoodBusinessInsightService gbDepFoodBusinessInsightService;

    @Override
    public Map<String, Object> buildDetail(
            Integer distributerId,
            String scopeMode,
            Integer departmentId,
            Integer foodId,
            int days,
            String startDate,
            String stopDate,
            Integer categoryId) {
        if (distributerId == null) {
            throw new IllegalArgumentException("distributerId 必填");
        }
        if (foodId == null) {
            throw new IllegalArgumentException("foodId 必填");
        }
        if (scopeMode == null || scopeMode.isBlank()) {
            throw new IllegalArgumentException("scopeMode 必填，取值 GROUP 或 STORE");
        }
        String mode = scopeMode.trim().toUpperCase();
        if (!SCOPE_GROUP.equals(mode) && !SCOPE_STORE.equals(mode)) {
            throw new IllegalArgumentException("scopeMode 仅支持 GROUP 或 STORE");
        }

        GbDistributerFoodEntity foodEntity = gbDistributerFoodService.queryObject(foodId);
        if (foodEntity == null || !distributerId.equals(foodEntity.getGbDfDistributerId())) {
            throw new IllegalArgumentException("foodId 无效或不属于当前 distributer");
        }

        if (categoryId != null) {
            validateFoodInCategory(distributerId, categoryId, foodId);
        }

        int effectiveDays = days > 0 ? days : 30;
        PeriodWindow window = resolvePeriodWindow(effectiveDays, startDate, stopDate);
        int depFatherId = resolveDepFatherId(mode, departmentId);

        Map<String, Object> rawRow =
                gbDishCostAnalysisService.buildCategoryOverviewDishRowForFoodId(
                        window.start, window.end, distributerId, depFatherId, foodId);

        Map<String, Object> dish = buildDishPayload(foodEntity, rawRow);
        List<Map<String, Object>> ingredientCostRows = loadIngredientCostRows(
                window.start, window.end, distributerId, depFatherId, foodId);
        Map<String, Object> weekdayBlock =
                gbDepFoodBusinessInsightService.buildWeekdaySalesDistributionForFood(
                        distributerId,
                        depFatherId,
                        foodId,
                        window.start,
                        window.end,
                        weekdayDishCostContext(foodEntity, rawRow));
        attachWeekdayPeakToDish(dish, weekdayBlock);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("distributerId", distributerId);
        result.put("scopeMode", mode);
        result.put("departmentId", SCOPE_STORE.equals(mode) ? departmentId : null);
        result.put("foodId", foodId);
        result.put(
                "period",
                MenuCategoryBusinessOverviewSupport.periodMeta(
                        window.periodStart, window.periodEnd, effectiveDays));
        result.put("dish", dish);
        result.put("ingredientCostRows", ingredientCostRows);
        result.put("weekdaySalesDistribution", weekdayBlock.get("weekdaySalesDistribution"));
        Map<String, Object> weekdayPeak = new LinkedHashMap<>();
        putIfPresent(weekdayPeak, "peakWeekdayName", weekdayBlock.get("peakWeekdayName"));
        putIfPresent(weekdayPeak, "peakWeekdayCode", weekdayBlock.get("peakWeekdayCode"));
        putIfPresent(weekdayPeak, "peakWeekdaySalesCount", weekdayBlock.get("peakWeekdaySalesCount"));
        putIfPresent(weekdayPeak, "peakWeekdaySalesAmount", weekdayBlock.get("peakWeekdaySalesAmount"));
        putIfPresent(weekdayPeak, "unassignedSalesCount", weekdayBlock.get("unassignedSalesCount"));
        putIfPresent(weekdayPeak, "unassignedSalesAmount", weekdayBlock.get("unassignedSalesAmount"));
        putIfPresent(weekdayPeak, "unassignedOrderCount", weekdayBlock.get("unassignedOrderCount"));
        result.put("weekdayPeak", weekdayPeak);
        return result;
    }

    private static Map<String, Object> weekdayDishCostContext(
            GbDistributerFoodEntity foodEntity, Map<String, Object> rawRow) {
        Map<String, Object> ctx = new LinkedHashMap<>();
        ctx.put("actualCostPerPortion", rawRow.get("actualCostPerPortion"));
        ctx.put("listPrice", resolveListPrice(foodEntity, rawRow));
        return ctx;
    }

    private static void attachWeekdayPeakToDish(Map<String, Object> dish, Map<String, Object> weekdayBlock) {
        if (dish == null || weekdayBlock == null) {
            return;
        }
        putIfPresent(dish, "peakWeekdayName", weekdayBlock.get("peakWeekdayName"));
        putIfPresent(dish, "peakWeekdaySalesCount", weekdayBlock.get("peakWeekdaySalesCount"));
        putIfPresent(dish, "peakWeekdaySalesAmount", weekdayBlock.get("peakWeekdaySalesAmount"));
    }

    private static void putIfPresent(Map<String, Object> target, String key, Object value) {
        if (value != null) {
            target.put(key, value);
        }
    }

    private static Map<String, Object> buildDishPayload(GbDistributerFoodEntity foodEntity, Map<String, Object> rawRow) {
        Map<String, Object> periodMetrics =
                MenuCategoryBusinessOverviewSupport.buildFoodPeriodMetrics(rawRow, false);

        Map<String, Object> dish = new LinkedHashMap<>();
        dish.put("foodId", rawRow.get("foodId"));
        dish.put("dishName", rawRow.get("foodName"));
        dish.put("listPrice", resolveListPrice(foodEntity, rawRow));
        dish.put("soldPortionsTotal", periodMetrics.get("salesCount"));
        dish.put("salesAmount", periodMetrics.get("salesAmount"));
        dish.put("actualRevenue", MenuCategoryBusinessOverviewSupport.moneyDisplayPublic(
                GbDepartmentGoodsStockReduceSupport.coerceDecimal(rawRow.get("actualRevenue"))));
        dish.put("actualCostPerPortion", periodMetrics.get("actualCostPerPortion"));
        dish.put("theoreticalCostPerPortion", periodMetrics.get("theoreticalCostPerPortion"));
        dish.put("actualCostTotalAmount123", periodMetrics.get("actualCostAmount"));
        dish.put("theoreticalCostAmount", periodMetrics.get("theoreticalCostAmount"));
        dish.put("actualProfitAmount", periodMetrics.get("actualProfitAmount"));
        dish.put("actualGrossMarginRate", periodMetrics.get("actualGrossMarginRate"));
        dish.put("theoreticalGrossMarginRate", periodMetrics.get("theoreticalGrossMarginRate"));
        dish.put("grossMarginGapRate", periodMetrics.get("grossMarginGapRate"));
        dish.put(
                "costGapAmount",
                MenuCategoryBusinessOverviewSupport.moneyDisplayPublic(
                        MenuCategoryBusinessOverviewSupport.costGapAmount(rawRow)));
        dish.put("costGapPerPortion", formatCostGapPerPortion(rawRow));
        return dish;
    }

    private static String formatCostGapPerPortion(Map<String, Object> rawRow) {
        Object diff = rawRow.get("diffCostPerPortion");
        if (diff != null && !String.valueOf(diff).isBlank()) {
            return String.valueOf(diff);
        }
        BigDecimal qty = GbDepartmentGoodsStockReduceSupport.coerceDecimal(rawRow.get("soldPortionsTotal"));
        if (qty.compareTo(BigDecimal.ZERO) <= 0) {
            return MenuCategoryBusinessOverviewSupport.moneyDisplayPublic(BigDecimal.ZERO);
        }
        BigDecimal gap =
                MenuCategoryBusinessOverviewSupport.costGapAmount(rawRow)
                        .divide(qty, 8, RoundingMode.HALF_UP);
        return MenuCategoryBusinessOverviewSupport.moneyDisplayPublic(gap);
    }

    private static String resolveListPrice(GbDistributerFoodEntity foodEntity, Map<String, Object> rawRow) {
        if (foodEntity != null
                && foodEntity.getGbDfFoodPrice() != null
                && !foodEntity.getGbDfFoodPrice().isBlank()) {
            return foodEntity.getGbDfFoodPrice().trim();
        }
        Object unit = rawRow.get("salesUnitPrice");
        return unit != null ? String.valueOf(unit) : "0.00";
    }

    private List<Map<String, Object>> loadIngredientCostRows(
            String start, String end, Integer distributerId, int depFatherId, Integer foodId) {
        Set<Integer> ids = Set.of(foodId);
        Map<Integer, List<Map<String, Object>>> byFood =
                gbDishCostAnalysisService.buildIngredientRowsForFoodIds(
                        start, end, distributerId, depFatherId, ids);
        List<Map<String, Object>> raw = byFood.getOrDefault(foodId, List.of());
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> row : raw) {
            if (row != null) {
                out.add(mapIngredientCostRow(row));
            }
        }
        return out;
    }

    private static Map<String, Object> mapIngredientCostRow(Map<String, Object> row) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("disGoodsId", row.get("disGoodsId"));
        m.put("ingredientName", firstNonBlank(row.get("gbDgGoodsName"), row.get("goodsName")));
        m.put("recipeUnitPerDish", row.get("recipeUnitPerDish"));
        m.put("theoryUsage", row.get("theoryUsage"));
        m.put("salesUsageFromOrders", row.get("salesUsageFromOrders"));
        m.put("actualUsage", row.get("actualUsage"));
        m.put("unitPrice", row.get("unitPrice"));
        m.put("theoryCostPerPortion", row.get("theoryCostPerPortion"));
        m.put("actualCostPerPortion", row.get("actualCostPerPortion"));
        m.put("produceCostPerPortion", row.get("produceCostPerPortion"));
        m.put("wasteCostPerPortion", row.get("wasteCostPerPortion"));
        m.put("lossCostPerPortion", row.get("lossCostPerPortion"));
        m.put("lossAndWasteCostPerPortion", row.get("lossAndWasteCostPerPortion"));
        m.put("actualProduceUsage", row.get("actualProduceUsage"));
        m.put("actualWasteUsage", row.get("actualWasteUsage"));
        m.put("actualLossUsage", row.get("actualLossUsage"));
        m.put("actualLossAndWasteUsage", row.get("actualLossAndWasteUsage"));
        m.put("devianceRate", row.get("devianceRate"));
        m.put("deviance", row.get("deviance"));
        m.put("costGapPerPortion", ingredientCostGapPerPortion(row));
        return m;
    }

    private static String ingredientCostGapPerPortion(Map<String, Object> row) {
        BigDecimal actual = GbDepartmentGoodsStockReduceSupport.coerceDecimal(row.get("actualCostPerPortion"));
        BigDecimal theory = GbDepartmentGoodsStockReduceSupport.coerceDecimal(row.get("theoryCostPerPortion"));
        return MenuCategoryBusinessOverviewSupport.moneyDisplayPublic(actual.subtract(theory));
    }

    private static String firstNonBlank(Object a, Object b) {
        if (a != null && !String.valueOf(a).isBlank()) {
            return String.valueOf(a);
        }
        if (b != null && !String.valueOf(b).isBlank()) {
            return String.valueOf(b);
        }
        return null;
    }

    private void validateFoodInCategory(Integer distributerId, Integer categoryId, Integer foodId) {
        Catalog catalog = loadCatalog(distributerId);
        if (findCategory(catalog.categories, categoryId) == null) {
            throw new IllegalArgumentException("categoryId 无效或不属于当前 distributer");
        }
        Set<Integer> categoryFoodIds =
                MenuCategoryBusinessOverviewSupport.foodIdsUnderCategory(catalog.dishes, categoryId);
        if (!categoryFoodIds.contains(foodId)) {
            throw new IllegalArgumentException("foodId 不属于 categoryId 对应分类");
        }
    }

    private static PeriodWindow resolvePeriodWindow(int days, String startDate, String stopDate) {
        String sd = startDate != null ? startDate.trim() : "";
        String ed = stopDate != null ? stopDate.trim() : "";
        if (!sd.isEmpty() && !ed.isEmpty()) {
            LocalDate start = LocalDate.parse(sd);
            LocalDate end = LocalDate.parse(ed);
            if (end.isBefore(start)) {
                throw new IllegalArgumentException("stopDate 不能早于 startDate");
            }
            return new PeriodWindow(sd, ed, start, end, days);
        }
        if (!sd.isEmpty() || !ed.isEmpty()) {
            throw new IllegalArgumentException("startDate 与 stopDate 需同时传入");
        }
        LocalDate today = GbDateTimeUtils.todayChina();
        Map<String, LocalDate> range = MenuCategoryBusinessOverviewSupport.currentPeriodRange(today, days);
        LocalDate start = range.get("start");
        LocalDate end = range.get("end");
        return new PeriodWindow(start.toString(), end.toString(), start, end, days);
    }

    private static int resolveDepFatherId(String scopeMode, Integer departmentId) {
        if (SCOPE_STORE.equalsIgnoreCase(scopeMode)) {
            if (departmentId == null) {
                throw new IllegalArgumentException("scopeMode=STORE 时 departmentId 必填");
            }
            return departmentId;
        }
        return AiInsightDishProfitScope.DEP_FATHER_ID_GROUP_WIDE_Mendian_AGGREGATE_UNDER_DIS_ID;
    }

    private Catalog loadCatalog(Integer distributerId) {
        Map<String, Object> q = new HashMap<>();
        q.put("disId", distributerId);
        List<GbDistributerFoodEntity> all = gbDistributerFoodService.queryDisAllFood(q);
        List<GbDistributerFoodEntity> categories = new ArrayList<>();
        List<GbDistributerFoodEntity> dishes = new ArrayList<>();
        if (all != null) {
            for (GbDistributerFoodEntity row : all) {
                if (row == null) {
                    continue;
                }
                Integer fatherId = row.getGbDfFoodFatherId();
                if (fatherId != null && fatherId == 0) {
                    categories.add(row);
                } else {
                    dishes.add(row);
                }
            }
        }
        return new Catalog(categories, dishes);
    }

    private static GbDistributerFoodEntity findCategory(List<GbDistributerFoodEntity> categories, Integer categoryId) {
        if (categories == null) {
            return null;
        }
        for (GbDistributerFoodEntity c : categories) {
            if (c != null && categoryId.equals(c.getGbDistributerFoodId())) {
                return c;
            }
        }
        return null;
    }

    private static final class PeriodWindow {
        private final String start;
        private final String end;
        private final LocalDate periodStart;
        private final LocalDate periodEnd;
        private final int days;

        private PeriodWindow(String start, String end, LocalDate periodStart, LocalDate periodEnd, int days) {
            this.start = start;
            this.end = end;
            this.periodStart = periodStart;
            this.periodEnd = periodEnd;
            this.days = days;
        }
    }

    private static final class Catalog {
        private final List<GbDistributerFoodEntity> categories;
        private final List<GbDistributerFoodEntity> dishes;

        private Catalog(List<GbDistributerFoodEntity> categories, List<GbDistributerFoodEntity> dishes) {
            this.categories = categories;
            this.dishes = dishes;
        }
    }
}
