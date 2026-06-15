package com.nongxinle.service.impl;

import com.nongxinle.constants.AiInsightDishProfitScope;
import com.nongxinle.entity.GbDistributerFoodEntity;
import com.nongxinle.service.GbDishCostAnalysisService;
import com.nongxinle.service.GbDistributerFoodService;
import com.nongxinle.service.GbMenuCategoryFoodBusinessListService;
import com.nongxinle.utils.GbDateTimeUtils;
import com.nongxinle.utils.ImagePaths;
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
public class GbMenuCategoryFoodBusinessListServiceImpl implements GbMenuCategoryFoodBusinessListService {

    private static final String SCOPE_GROUP = "GROUP";
    private static final String SCOPE_STORE = "STORE";

    private final GbDishCostAnalysisService gbDishCostAnalysisService;
    private final GbDistributerFoodService gbDistributerFoodService;

    @Override
    public Map<String, Object> buildFoodList(
            Integer distributerId,
            String scopeMode,
            Integer departmentId,
            Integer categoryId,
            int days,
            String keyword,
            String roleFilter,
            String sortBy,
            String sortOrder) {
        if (categoryId == null) {
            throw new IllegalArgumentException("categoryId 必填");
        }
        int effectiveDays = days > 0 ? days : 30;
        LocalDate today = GbDateTimeUtils.todayChina();
        Map<String, LocalDate> currentRange =
                MenuCategoryBusinessOverviewSupport.currentPeriodRange(today, effectiveDays);
        Map<String, LocalDate> compareRange =
                MenuCategoryBusinessOverviewSupport.comparePeriodRange(today, effectiveDays);

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
        GbDistributerFoodEntity category = findCategory(catalog.categories, categoryId);
        if (category == null) {
            throw new IllegalArgumentException("categoryId 无效或不属于当前 distributer");
        }

        Set<Integer> categoryFoodIds =
                MenuCategoryBusinessOverviewSupport.foodIdsUnderCategory(catalog.dishes, categoryId);
        List<GbDistributerFoodEntity> categoryDishes =
                MenuCategoryBusinessOverviewSupport.dishesUnderCategory(catalog.dishes, categoryId);
        String targetGrossMarginRateText =
                MenuCategoryBusinessOverviewSupport.targetGrossMarginRateTextFromDishes(categoryDishes);
        Map<Integer, GbDistributerFoodEntity> dishById = indexDishes(catalog.dishes);

        List<Map<String, Object>> catCurrentRows =
                MenuCategoryBusinessOverviewSupport.filterRowsByFoodIds(currentRows, categoryFoodIds);
        List<Map<String, Object>> catCompareRows =
                MenuCategoryBusinessOverviewSupport.filterRowsByFoodIds(compareRows, categoryFoodIds);

        Map<Integer, Map<String, Object>> compareByFoodId =
                MenuCategoryBusinessOverviewSupport.indexRowsByFoodId(catCompareRows);
        Map<Integer, MenuCategoryBusinessOverviewSupport.DishRoleInfo> roles =
                MenuCategoryBusinessOverviewSupport.assignDishRoles(catCurrentRows);

        MenuCategoryBusinessOverviewSupport.assignRanks(catCurrentRows);

        int riskCount = countCategoryRiskFoods(categoryFoodIds, catCurrentRows, roles, dishById);

        String effectiveRoleFilter = normalizeRoleFilter(roleFilter);
        String kw = keyword != null ? keyword.trim() : "";

        List<Map<String, Object>> foodsOut = new ArrayList<>();
        for (Integer foodId : categoryFoodIds) {
            GbDistributerFoodEntity dishEntity = dishById.get(foodId);
            String foodName = dishEntity != null ? dishEntity.getGbDfFoodName() : null;
            if (!kw.isEmpty() && (foodName == null || !foodName.contains(kw))) {
                continue;
            }

            Map<String, Object> currentRow = findRowForFood(catCurrentRows, foodId);
            Map<String, Object> compareRow = compareByFoodId.getOrDefault(foodId, emptyMetricsRow(foodId, foodName));

            if (currentRow == null) {
                currentRow = emptyMetricsRow(foodId, foodName);
            } else if (foodName != null && !foodName.isBlank()) {
                currentRow.put("foodName", foodName);
            }

            MenuCategoryBusinessOverviewSupport.DishRoleInfo role = roles.get(foodId);
            if (role == null) {
                role = MenuCategoryBusinessOverviewSupport.defaultDishRole();
            }
            if (!"ALL".equals(effectiveRoleFilter) && !effectiveRoleFilter.equals(role.code())) {
                continue;
            }

            Map<String, Object> diagnosis =
                    MenuCategoryBusinessOverviewSupport.buildBusinessDiagnosis(
                            currentRow, role, dishEntity);

            Map<String, Object> food = new LinkedHashMap<>();
            food.put("foodId", foodId);
            food.put("foodName", foodName);
            food.put("categoryId", categoryId);
            food.put("categoryName", category.getGbDfFoodName());
            food.put("imageUrl", resolveFoodImageUrl(dishEntity));
            food.put("salePrice", formatPrice(dishEntity, currentRow));
            food.put("saleUnitName", "份");
            food.put("roleCode", role.code());
            food.put("roleName", role.name());
            food.put("roleReason", role.reason());
            food.put(
                    "currentPeriod",
                    MenuCategoryBusinessOverviewSupport.buildFoodPeriodMetrics(currentRow, true));
            food.put(
                    "comparePeriod",
                    MenuCategoryBusinessOverviewSupport.buildFoodCompareMetrics(compareRow));
            food.put(
                    "change",
                    MenuCategoryBusinessOverviewSupport.buildFoodChangeMetrics(currentRow, compareRow));
            food.put("businessDiagnosis", diagnosis);
            foodsOut.add(food);
        }

        foodsOut =
                sortFoodPayloads(
                        foodsOut,
                        catCurrentRows,
                        sortBy,
                        sortOrder);

        MenuCategoryBusinessOverviewSupport.PeriodRollupWithTheory summaryRollup =
                MenuCategoryBusinessOverviewSupport.rollupWithTheory(catCurrentRows);
        Map<String, Object> portfolio =
                MenuCategoryBusinessOverviewSupport.portfolioCounts(catCurrentRows);

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("foodCount", categoryFoodIds.size());
        summary.put("totalSalesCount", MenuCategoryBusinessOverviewSupport.moneyDisplayPublic(summaryRollup.portions()));
        summary.put("totalSalesAmount", MenuCategoryBusinessOverviewSupport.moneyDisplayPublic(summaryRollup.sales()));
        summary.put("actualCostAmount", MenuCategoryBusinessOverviewSupport.moneyDisplayPublic(summaryRollup.cost()));
        summary.put("actualProfitAmount", MenuCategoryBusinessOverviewSupport.moneyDisplayPublic(summaryRollup.profit()));
        summary.put("targetGrossMarginRate", targetGrossMarginRateText);
        summary.put(
                "actualAverageGrossMarginRate",
                MenuCategoryBusinessOverviewSupport.percentDisplayFromRatio(summaryRollup.marginRatio()));
        summary.put(
                "theoreticalAverageGrossMarginRate",
                MenuCategoryBusinessOverviewSupport.percentDisplayFromRatio(summaryRollup.theoryMarginRatio()));
        BigDecimal gapPoints =
                summaryRollup
                        .marginRatio()
                        .subtract(summaryRollup.theoryMarginRatio())
                        .multiply(BigDecimal.valueOf(100));
        summary.put(
                "grossMarginGapRate",
                MenuCategoryBusinessOverviewSupport.signedPercentPointsPublic(gapPoints));
        summary.put("riskFoodCount", riskCount);
        summary.put("starCount", portfolio.get("starCount"));
        summary.put("trafficCount", portfolio.get("trafficCount"));
        summary.put("potentialCount", portfolio.get("potentialCount"));
        summary.put("watchCount", portfolio.get("watchCount"));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("distributerId", distributerId);
        result.put("scopeMode", scopeMode.toUpperCase());
        result.put("departmentId", SCOPE_STORE.equalsIgnoreCase(scopeMode) ? departmentId : null);
        result.put("categoryId", categoryId);
        result.put("categoryName", category.getGbDfFoodName());
        result.put("targetGrossMarginRate", targetGrossMarginRateText);
        result.put(
                "period",
                MenuCategoryBusinessOverviewSupport.foodListPeriodBlock(
                        currentRange.get("start"),
                        currentRange.get("end"),
                        compareRange.get("start"),
                        compareRange.get("end"),
                        effectiveDays));
        result.put("summary", summary);
        result.put("foods", foodsOut);
        return result;
    }

    private static List<Map<String, Object>> sortFoodPayloads(
            List<Map<String, Object>> foodsOut,
            List<Map<String, Object>> catCurrentRows,
            String sortBy,
            String sortOrder) {
        Map<Integer, Map<String, Object>> rowByFoodId =
                MenuCategoryBusinessOverviewSupport.indexRowsByFoodId(catCurrentRows);
        List<Map<String, Object>> sortable = new ArrayList<>();
        for (Map<String, Object> food : foodsOut) {
            Integer foodId = (Integer) food.get("foodId");
            Map<String, Object> row = rowByFoodId.getOrDefault(foodId, emptyMetricsRow(foodId, null));
            sortable.add(row);
        }
        List<Map<String, Object>> sortedRows =
                MenuCategoryBusinessOverviewSupport.sortFoodRows(sortable, sortBy, sortOrder);
        Map<Integer, Integer> order = new LinkedHashMap<>();
        for (int i = 0; i < sortedRows.size(); i++) {
            Integer fid = MenuCategoryBusinessOverviewSupport.parseFoodId(sortedRows.get(i));
            if (fid != null) {
                order.put(fid, i);
            }
        }
        List<Map<String, Object>> sortedFoods = new ArrayList<>(foodsOut);
        sortedFoods.sort(
                (a, b) -> {
                    Integer fa = (Integer) a.get("foodId");
                    Integer fb = (Integer) b.get("foodId");
                    int oa = order.getOrDefault(fa, Integer.MAX_VALUE);
                    int ob = order.getOrDefault(fb, Integer.MAX_VALUE);
                    return Integer.compare(oa, ob);
                });
        return sortedFoods;
    }

    private static int countCategoryRiskFoods(
            Set<Integer> categoryFoodIds,
            List<Map<String, Object>> catCurrentRows,
            Map<Integer, MenuCategoryBusinessOverviewSupport.DishRoleInfo> roles,
            Map<Integer, GbDistributerFoodEntity> dishById) {
        int riskCount = 0;
        for (Integer foodId : categoryFoodIds) {
            Map<String, Object> currentRow = findRowForFood(catCurrentRows, foodId);
            GbDistributerFoodEntity dishEntity = dishById.get(foodId);
            if (currentRow == null) {
                currentRow =
                        emptyMetricsRow(
                                foodId, dishEntity != null ? dishEntity.getGbDfFoodName() : null);
            }
            MenuCategoryBusinessOverviewSupport.DishRoleInfo role = roles.get(foodId);
            if (role == null) {
                role = MenuCategoryBusinessOverviewSupport.defaultDishRole();
            }
            Map<String, Object> diagnosis =
                    MenuCategoryBusinessOverviewSupport.buildBusinessDiagnosis(
                            currentRow, role, dishEntity);
            if (MenuCategoryBusinessOverviewSupport.isRiskFood(diagnosis)) {
                riskCount++;
            }
        }
        return riskCount;
    }

    private static Map<String, Object> findRowForFood(List<Map<String, Object>> rows, Integer foodId) {
        if (rows == null) {
            return null;
        }
        for (Map<String, Object> row : rows) {
            if (foodId.equals(MenuCategoryBusinessOverviewSupport.parseFoodId(row))) {
                return row;
            }
        }
        return null;
    }

    private static Map<String, Object> emptyMetricsRow(Integer foodId, String foodName) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("foodId", foodId);
        row.put("foodName", foodName);
        row.put("soldPortionsTotal", "0");
        row.put("actualRevenue", "0");
        row.put("actualCostTotalAmount123", "0");
        row.put("theoreticalCostTotalAmount", "0");
        return row;
    }

    private static String normalizeRoleFilter(String roleFilter) {
        if (roleFilter == null || roleFilter.isBlank()) {
            return "ALL";
        }
        return roleFilter.trim().toUpperCase();
    }

    private static String formatPrice(GbDistributerFoodEntity dishEntity, Map<String, Object> currentRow) {
        if (dishEntity != null && dishEntity.getGbDfFoodPrice() != null && !dishEntity.getGbDfFoodPrice().isBlank()) {
            return dishEntity.getGbDfFoodPrice();
        }
        Object unit = currentRow.get("salesUnitPrice");
        return unit != null ? String.valueOf(unit) : "0.00";
    }

    private static String resolveFoodImageUrl(GbDistributerFoodEntity dishEntity) {
        if (dishEntity == null || dishEntity.getGbDfFoodImg() == null || dishEntity.getGbDfFoodImg().isBlank()) {
            return null;
        }
        String img = dishEntity.getGbDfFoodImg().trim();
        if (img.contains("/")) {
            return img;
        }
        return ImagePaths.relative(ImagePaths.FOOD, img);
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

    private static Map<Integer, GbDistributerFoodEntity> indexDishes(List<GbDistributerFoodEntity> dishes) {
        Map<Integer, GbDistributerFoodEntity> out = new LinkedHashMap<>();
        if (dishes != null) {
            for (GbDistributerFoodEntity d : dishes) {
                if (d != null && d.getGbDistributerFoodId() != null) {
                    out.put(d.getGbDistributerFoodId(), d);
                }
            }
        }
        return out;
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
        return new Catalog(categories, dishes);
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
