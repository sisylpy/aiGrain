package com.nongxinle.service.impl;

import com.nongxinle.constants.AiInsightDishProfitScope;
import com.nongxinle.entity.GbDistributerFoodEntity;
import com.nongxinle.service.GbDishCostAnalysisService;
import com.nongxinle.service.GbDistributerFoodService;
import com.nongxinle.service.GbMenuCategoryBusinessOverviewService;
import com.nongxinle.utils.GbDateTimeUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class GbMenuCategoryBusinessOverviewServiceImpl implements GbMenuCategoryBusinessOverviewService {

    private static final String SCOPE_GROUP = "GROUP";
    private static final String SCOPE_STORE = "STORE";

    private final GbDishCostAnalysisService gbDishCostAnalysisService;
    private final GbDistributerFoodService gbDistributerFoodService;

    @Override
    public Map<String, Object> buildOverview(Integer distributerId, String scopeMode, Integer departmentId, int days) {
        int effectiveDays = days > 0 ? days : 30;
        LocalDate today = GbDateTimeUtils.todayChina();
        Map<String, LocalDate> currentRange = MenuCategoryBusinessOverviewSupport.currentPeriodRange(today, effectiveDays);
        Map<String, LocalDate> compareRange = MenuCategoryBusinessOverviewSupport.comparePeriodRange(today, effectiveDays);

        String currentStart = currentRange.get("start").toString();
        String currentEnd = currentRange.get("end").toString();
        String compareStart = compareRange.get("start").toString();
        String compareEnd = compareRange.get("end").toString();

        int depFatherId = resolveDepFatherId(scopeMode, departmentId);

        List<Map<String, Object>> currentRows =
                gbDishCostAnalysisService.buildCategoryOverviewDishRows(
                        currentStart, currentEnd, distributerId, depFatherId, null);
        List<Map<String, Object>> compareRows =
                gbDishCostAnalysisService.buildCategoryOverviewDishRows(
                        compareStart, compareEnd, distributerId, depFatherId, null);

        currentRows = MenuCategoryBusinessOverviewSupport.aggregateDishRowsByFoodId(currentRows);
        compareRows = MenuCategoryBusinessOverviewSupport.aggregateDishRowsByFoodId(compareRows);

        Catalog catalog = loadCatalog(distributerId);

        MenuCategoryBusinessOverviewSupport.PeriodRollup overallCurrent =
                MenuCategoryBusinessOverviewSupport.rollupRows(currentRows);
        MenuCategoryBusinessOverviewSupport.PeriodRollup overallCompare =
                MenuCategoryBusinessOverviewSupport.rollupRows(compareRows);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("scopeMode", scopeMode.toUpperCase());
        result.put("distributerId", distributerId);
        result.put("departmentId", SCOPE_STORE.equalsIgnoreCase(scopeMode) ? departmentId : null);
        result.put(
                "currentPeriod",
                MenuCategoryBusinessOverviewSupport.periodMeta(
                        currentRange.get("start"), currentRange.get("end"), effectiveDays));
        result.put(
                "comparePeriod",
                MenuCategoryBusinessOverviewSupport.periodMeta(
                        compareRange.get("start"), compareRange.get("end"), effectiveDays));

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("categoryCount", catalog.categories.size());
        summary.put("dishCount", catalog.dishCount);
        summary.put(
                "averageGrossMarginRate",
                MenuCategoryBusinessOverviewSupport.percentDisplayFromRatio(overallCurrent.marginRatio()));
        summary.put(
                "grossMarginRateChangeVsPreviousPeriod",
                MenuCategoryBusinessOverviewSupport.marginChangePoints(overallCurrent, overallCompare));
        result.put("summary", summary);

        List<Map<String, Object>> categoriesOut = new ArrayList<>();
        for (GbDistributerFoodEntity category : catalog.categories) {
            Integer categoryId = category.getGbDistributerFoodId();
            Set<Integer> foodIds =
                    MenuCategoryBusinessOverviewSupport.foodIdsUnderCategory(catalog.dishes, categoryId);

            List<Map<String, Object>> catCurrentRows =
                    MenuCategoryBusinessOverviewSupport.filterRowsByFoodIds(currentRows, foodIds);
            List<Map<String, Object>> catCompareRows =
                    MenuCategoryBusinessOverviewSupport.filterRowsByFoodIds(compareRows, foodIds);

            MenuCategoryBusinessOverviewSupport.PeriodRollup catCurrent =
                    MenuCategoryBusinessOverviewSupport.rollupRows(catCurrentRows);
            MenuCategoryBusinessOverviewSupport.PeriodRollup catCompare =
                    MenuCategoryBusinessOverviewSupport.rollupRows(catCompareRows);
            MenuCategoryBusinessOverviewSupport.PortfolioCounts portfolioCounts =
                    classifyForHint(catCurrentRows);

            List<GbDistributerFoodEntity> categoryDishes =
                    MenuCategoryBusinessOverviewSupport.dishesUnderCategory(catalog.dishes, categoryId);
            BigDecimal averageDishTargetGrossMargin =
                    MenuCategoryBusinessOverviewSupport.averageTargetGrossMarginRatePercent(categoryDishes);

            Map<String, Object> cat = new LinkedHashMap<>();
            cat.put("categoryId", categoryId);
            cat.put("categoryName", category.getGbDfFoodName());
            cat.put("dishCount", foodIds.size());
            cat.put(
                    "targetGrossMarginRate",
                    MenuCategoryBusinessOverviewSupport.targetGrossMarginRateTextFromDishes(categoryDishes));
            cat.put(
                    "actualAverageGrossMarginRate",
                    MenuCategoryBusinessOverviewSupport.percentDisplayFromRatio(catCurrent.marginRatio()));
            cat.put("currentPeriod", MenuCategoryBusinessOverviewSupport.periodPayload(catCurrent));
            cat.put("comparePeriod", MenuCategoryBusinessOverviewSupport.periodPayload(catCompare));
            cat.put(
                    "change",
                    MenuCategoryBusinessOverviewSupport.changePayload(catCurrent, catCompare));
            cat.put("portfolio", MenuCategoryBusinessOverviewSupport.portfolioCounts(catCurrentRows));
            cat.put(
                    "businessHint",
                    MenuCategoryBusinessOverviewSupport.buildBusinessHint(
                            portfolioCounts, catCurrent, averageDishTargetGrossMargin));
            categoriesOut.add(cat);
        }
        result.put("categories", categoriesOut);
        return result;
    }

    private static int resolveDepFatherId(String scopeMode, Integer departmentId) {
        if (SCOPE_STORE.equalsIgnoreCase(scopeMode)) {
            if (departmentId == null) {
                throw new IllegalArgumentException("scopeMode=STORE 时 departmentId 必填");
            }
            return departmentId;
        }
        if (!SCOPE_GROUP.equalsIgnoreCase(scopeMode)) {
            throw new IllegalArgumentException("scopeMode 仅支持 GROUP 或 STORE");
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
        categories.sort(
                (a, b) -> {
                    Integer sa = a.getGbDfGoodsSort() != null ? a.getGbDfGoodsSort() : 0;
                    Integer sb = b.getGbDfGoodsSort() != null ? b.getGbDfGoodsSort() : 0;
                    int cmp = sa.compareTo(sb);
                    if (cmp != 0) {
                        return cmp;
                    }
                    String na = a.getGbDfFoodName() != null ? a.getGbDfFoodName() : "";
                    String nb = b.getGbDfFoodName() != null ? b.getGbDfFoodName() : "";
                    return na.compareTo(nb);
                });
        return new Catalog(categories, dishes, dishes.size());
    }

    private static MenuCategoryBusinessOverviewSupport.PortfolioCounts classifyForHint(
            List<Map<String, Object>> rows) {
        Map<String, Object> portfolio = MenuCategoryBusinessOverviewSupport.portfolioCounts(rows);
        MenuCategoryBusinessOverviewSupport.PortfolioCounts c =
                new MenuCategoryBusinessOverviewSupport.PortfolioCounts();
        c.star = ((Number) portfolio.get("starCount")).intValue();
        c.traffic = ((Number) portfolio.get("trafficCount")).intValue();
        c.potential = ((Number) portfolio.get("potentialCount")).intValue();
        c.watch = ((Number) portfolio.get("watchCount")).intValue();
        c.total = ((Number) portfolio.get("totalDishCount")).intValue();
        return c;
    }

    private static final class Catalog {
        private final List<GbDistributerFoodEntity> categories;
        private final List<GbDistributerFoodEntity> dishes;
        private final int dishCount;

        private Catalog(
                List<GbDistributerFoodEntity> categories,
                List<GbDistributerFoodEntity> dishes,
                int dishCount) {
            this.categories = categories;
            this.dishes = dishes;
            this.dishCount = dishCount;
        }
    }
}
