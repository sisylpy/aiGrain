package com.nongxinle.ai.capability.dish;

import com.nongxinle.entity.GbDistributerFoodEntity;
import com.nongxinle.service.GbDishCostAnalysisService;
import com.nongxinle.service.GbDistributerFoodService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * AI 菜品成本分析 Capability：复用 {@link GbDishCostAnalysisService#buildIngredientAnalysisReport}，
 * 不新增 Mapper / SQL，不重算成本口径。
 */
@Component
@RequiredArgsConstructor
public class DishCostAnalysisCapabilityAdapter {

    private static final String REASON_DISH_NOT_FOUND = "dish_not_found";
    private static final String REASON_NO_DATA = "no_data";
    private static final String REASON_MISSING_DISH_SELECTOR = "missing_dish_selector";
    private static final String REASON_NO_RECIPE = "no_recipe_for_dish";

    private final GbDishCostAnalysisService gbDishCostAnalysisService;
    private final GbDistributerFoodService gbDistributerFoodService;

    @SuppressWarnings("unchecked")
    public DishCostAnalysisCapabilityResult analyze(DishCostAnalysisCapabilityRequest request) {
        if (request == null) {
            return error("request_null", "request is null");
        }
        String startDate = trimToNull(request.getStartDate());
        String stopDate = resolveStopDate(request);
        Integer disId = request.getDisId();
        Integer depFatherId = request.getDepFatherId();
        if (startDate == null || stopDate == null || disId == null) {
            return error("invalid_args", "startDate、stopDate/endDate、disId 不能为空");
        }

        String searchDepId = resolveSearchDepId(request);
        String sortBy = StringUtils.hasText(request.getSortBy()) ? request.getSortBy().trim() : "sales";
        String sortOrder = StringUtils.hasText(request.getSortOrder()) ? request.getSortOrder().trim() : "desc";

        Map<String, Object> report;
        try {
            report = gbDishCostAnalysisService.buildIngredientAnalysisReport(
                    startDate, stopDate, disId, searchDepId, depFatherId, sortBy, sortOrder);
        } catch (IllegalArgumentException e) {
            return error("service_rejected", e.getMessage());
        } catch (RuntimeException e) {
            return error("service_error", e.getMessage() == null ? "buildIngredientAnalysisReport failed" : e.getMessage());
        }

        List<Map<String, Object>> salesDishRows = report.get("salesDishRows") instanceof List
                ? (List<Map<String, Object>>) report.get("salesDishRows")
                : List.of();
        Map<String, Object> scopeSalesSubtotals = report.get("scopeSalesSubtotals") instanceof Map
                ? (Map<String, Object>) report.get("scopeSalesSubtotals")
                : Map.of();
        Map<String, Object> rawSummary = buildRawReportSummary(report, scopeSalesSubtotals, salesDishRows.size());

        Integer foodId = request.getFoodId();
        String dishName = trimToNull(request.getDishName());

        if (foodId != null) {
            DishCostAnalysisCapabilityResult fromSales = resolveByFoodId(foodId, salesDishRows, rawSummary);
            if (fromSales.getStatus() != DishCostAnalysisCapabilityStatus.NO_DATA) {
                return fromSales;
            }
            return loadDishRowByFoodId(request, foodId, rawSummary);
        }
        if (StringUtils.hasText(dishName)) {
            DishCostAnalysisCapabilityResult fromSales = resolveByDishName(dishName, salesDishRows, rawSummary);
            if (fromSales.getStatus() != DishCostAnalysisCapabilityStatus.NO_DATA) {
                return fromSales;
            }
            Integer resolvedFoodId = lookupSingleFoodIdByName(disId, dishName);
            if (resolvedFoodId != null) {
                return loadDishRowByFoodId(request, resolvedFoodId, rawSummary);
            }
            return fromSales;
        }

        if (salesDishRows.isEmpty()) {
            return DishCostAnalysisCapabilityResult.builder()
                    .status(DishCostAnalysisCapabilityStatus.NO_DATA)
                    .reasonCode(REASON_NO_DATA)
                    .message("区间内无菜品销售行")
                    .rawReportSummary(rawSummary)
                    .ingredientRows(List.of())
                    .candidates(List.of())
                    .build();
        }

        return DishCostAnalysisCapabilityResult.builder()
                .status(DishCostAnalysisCapabilityStatus.NO_DATA)
                .reasonCode(REASON_MISSING_DISH_SELECTOR)
                .message("缺少 dishName 或 foodId，无法定位单菜")
                .rawReportSummary(rawSummary)
                .ingredientRows(List.of())
                .candidates(List.of())
                .build();
    }

    private DishCostAnalysisCapabilityResult loadDishRowByFoodId(
            DishCostAnalysisCapabilityRequest request,
            Integer foodId,
            Map<String, Object> rawSummary) {
        if (request.getDepFatherId() == null) {
            return DishCostAnalysisCapabilityResult.builder()
                    .status(DishCostAnalysisCapabilityStatus.NO_DATA)
                    .reasonCode(REASON_DISH_NOT_FOUND)
                    .message("缺少 depFatherId，无法加载单菜配料行")
                    .rawReportSummary(rawSummary)
                    .ingredientRows(List.of())
                    .candidates(List.of())
                    .build();
        }
        Map<String, Object> dishRow;
        try {
            dishRow = gbDishCostAnalysisService.buildIngredientAnalysisDishRowForFoodId(
                    request.getStartDate(),
                    resolveStopDate(request),
                    request.getDisId(),
                    request.getDepFatherId(),
                    resolveSearchDepId(request),
                    foodId,
                    null);
        } catch (IllegalArgumentException e) {
            return DishCostAnalysisCapabilityResult.builder()
                    .status(DishCostAnalysisCapabilityStatus.NO_DATA)
                    .reasonCode(REASON_DISH_NOT_FOUND)
                    .message(e.getMessage())
                    .rawReportSummary(rawSummary)
                    .ingredientRows(List.of())
                    .candidates(List.of())
                    .build();
        } catch (RuntimeException e) {
            return error("service_error", e.getMessage() == null ? "buildIngredientAnalysisDishRowForFoodId failed" : e.getMessage());
        }
        if (dishRow == null || dishRow.isEmpty()) {
            return DishCostAnalysisCapabilityResult.builder()
                    .status(DishCostAnalysisCapabilityStatus.NO_DATA)
                    .reasonCode(REASON_DISH_NOT_FOUND)
                    .message("未找到指定菜品")
                    .rawReportSummary(rawSummary)
                    .ingredientRows(List.of())
                    .candidates(List.of())
                    .build();
        }
        return successFromRow(dishRow, rawSummary);
    }

    private Integer lookupSingleFoodIdByName(Integer disId, String dishName) {
        if (disId == null || !StringUtils.hasText(dishName)) {
            return null;
        }
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("disId", disId);
        map.put("foodName", dishName.trim());
        List<GbDistributerFoodEntity> foods = gbDistributerFoodService.queryFoodByParams(map);
        if (foods == null || foods.isEmpty()) {
            return null;
        }
        LinkedHashSet<Integer> ids = new LinkedHashSet<>();
        for (GbDistributerFoodEntity food : foods) {
            if (food != null && food.getGbDistributerFoodId() != null) {
                ids.add(food.getGbDistributerFoodId());
            }
        }
        return ids.size() == 1 ? ids.iterator().next() : null;
    }

    private static DishCostAnalysisCapabilityResult resolveByFoodId(
            Integer foodId, List<Map<String, Object>> salesDishRows, Map<String, Object> rawSummary) {
        List<Map<String, Object>> matched = new ArrayList<>();
        for (Map<String, Object> row : salesDishRows) {
            if (Objects.equals(foodId, row.get("dishId"))) {
                matched.add(row);
            }
        }
        if (matched.isEmpty()) {
            return DishCostAnalysisCapabilityResult.builder()
                    .status(DishCostAnalysisCapabilityStatus.NO_DATA)
                    .reasonCode(REASON_DISH_NOT_FOUND)
                    .message("未找到指定 foodId 的菜品分析行")
                    .rawReportSummary(rawSummary)
                    .ingredientRows(List.of())
                    .candidates(List.of())
                    .build();
        }
        return successFromRow(matched.get(0), rawSummary);
    }

    private static DishCostAnalysisCapabilityResult resolveByDishName(
            String dishName, List<Map<String, Object>> salesDishRows, Map<String, Object> rawSummary) {
        String needle = dishName.trim();
        List<Map<String, Object>> exact = new ArrayList<>();
        for (Map<String, Object> row : salesDishRows) {
            String name = str(row.get("dishName"));
            if (needle.equals(name)) {
                exact.add(row);
            }
        }
        if (exact.size() == 1) {
            return successFromRow(exact.get(0), rawSummary);
        }
        if (exact.size() > 1) {
            return clarificationFromRows(exact, rawSummary, "同名菜品需澄清");
        }

        List<Map<String, Object>> contains = new ArrayList<>();
        for (Map<String, Object> row : salesDishRows) {
            String name = str(row.get("dishName"));
            if (name.contains(needle)) {
                contains.add(row);
            }
        }
        if (contains.isEmpty()) {
            return DishCostAnalysisCapabilityResult.builder()
                    .status(DishCostAnalysisCapabilityStatus.NO_DATA)
                    .reasonCode(REASON_DISH_NOT_FOUND)
                    .message("未找到匹配菜名")
                    .rawReportSummary(rawSummary)
                    .ingredientRows(List.of())
                    .candidates(List.of())
                    .build();
        }
        if (contains.size() == 1) {
            return successFromRow(contains.get(0), rawSummary);
        }
        return clarificationFromRows(contains, rawSummary, "菜名匹配多条，需澄清");
    }

    private static DishCostAnalysisCapabilityResult clarificationFromRows(
            List<Map<String, Object>> rows, Map<String, Object> rawSummary, String message) {
        List<Map<String, Object>> candidates = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            candidates.add(candidateBrief(row));
        }
        return DishCostAnalysisCapabilityResult.builder()
                .status(DishCostAnalysisCapabilityStatus.NEED_CLARIFICATION)
                .reasonCode("need_clarification")
                .message(message)
                .candidates(candidates)
                .rawReportSummary(rawSummary)
                .ingredientRows(List.of())
                .build();
    }

    @SuppressWarnings("unchecked")
    private static DishCostAnalysisCapabilityResult successFromRow(
            Map<String, Object> row, Map<String, Object> rawSummary) {
        Integer dishId = row.get("dishId") instanceof Number n ? n.intValue() : null;
        if (dishId == null && row.get("foodId") instanceof Number fn) {
            dishId = fn.intValue();
        }
        String dishName = str(row.get("dishName"));
        String salesPortions = str(row.get("salesPortions"));
        String salesAmount = str(row.get("salesAmount"));
        String salesUnitPrice = str(row.get("salesUnitPrice"));
        String theoryCostPerPortion = str(row.get("theoryCostPerPortion"));
        String actualCostPerPortion = str(row.get("actualCostPerPortion"));
        String actualCostAmount = str(row.get("actualCostAmount"));
        String diffCostPerPortion = str(row.get("diffCostPerPortion"));
        List<Map<String, Object>> ingredientRows = row.get("ingredientRows") instanceof List
                ? (List<Map<String, Object>>) row.get("ingredientRows")
                : List.of();
        Map<String, Object> bottle =
                row.get("bottle") instanceof Map ? (Map<String, Object>) row.get("bottle") : null;

        String reasonCode = "ok";
        String message = "ok";
        if (ingredientRows.isEmpty()) {
            reasonCode = REASON_NO_RECIPE;
            message = "菜品无配方或未维护配料行";
        }

        Map<String, Object> cardPayload = DishCostAnalysisCapabilityResult.buildCardPayload(
                dishName,
                salesPortions,
                salesAmount,
                salesUnitPrice,
                theoryCostPerPortion,
                actualCostPerPortion,
                diffCostPerPortion,
                ingredientRows);

        return DishCostAnalysisCapabilityResult.builder()
                .status(DishCostAnalysisCapabilityStatus.SUCCESS)
                .reasonCode(reasonCode)
                .message(message)
                .dishId(dishId)
                .dishName(dishName)
                .salesPortions(salesPortions)
                .salesAmount(salesAmount)
                .salesUnitPrice(salesUnitPrice)
                .theoryCostPerPortion(theoryCostPerPortion)
                .actualCostPerPortion(actualCostPerPortion)
                .actualCostAmount(actualCostAmount)
                .diffCostPerPortion(diffCostPerPortion)
                .ingredientRows(ingredientRows)
                .bottle(bottle)
                .candidates(List.of())
                .rawReportSummary(rawSummary)
                .cardPayload(cardPayload)
                .build();
    }

    private static Map<String, Object> candidateBrief(Map<String, Object> row) {
        Map<String, Object> c = new LinkedHashMap<>();
        c.put("dishId", row.get("dishId"));
        c.put("dishName", row.get("dishName"));
        c.put("salesPortions", row.get("salesPortions"));
        c.put("salesAmount", row.get("salesAmount"));
        c.put("actualCostPerPortion", row.get("actualCostPerPortion"));
        c.put("diffCostPerPortion", row.get("diffCostPerPortion"));
        return c;
    }

    private static Map<String, Object> buildRawReportSummary(
            Map<String, Object> report, Map<String, Object> scopeSalesSubtotals, int rowCount) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("startDate", report.get("startDate"));
        summary.put("stopDate", report.get("stopDate"));
        summary.put("disId", report.get("disId"));
        summary.put("depFatherId", report.get("depFatherId"));
        summary.put("sortBy", report.get("sortBy"));
        summary.put("sortOrder", report.get("sortOrder"));
        summary.put("salesDishRowCount", rowCount);
        summary.put("scopeSalesSubtotals", scopeSalesSubtotals);
        return summary;
    }

    private static DishCostAnalysisCapabilityResult error(String reasonCode, String message) {
        return DishCostAnalysisCapabilityResult.builder()
                .status(DishCostAnalysisCapabilityStatus.ERROR)
                .reasonCode(reasonCode)
                .message(message)
                .ingredientRows(List.of())
                .candidates(List.of())
                .build();
    }

    private static String resolveStopDate(DishCostAnalysisCapabilityRequest request) {
        String stop = trimToNull(request.getStopDate());
        if (stop != null) {
            return stop;
        }
        return trimToNull(request.getEndDate());
    }

    private static String resolveSearchDepId(DishCostAnalysisCapabilityRequest request) {
        String search = trimToNull(request.getSearchDepId());
        if (search != null) {
            return search;
        }
        if (request.getSubDepId() != null) {
            return String.valueOf(request.getSubDepId());
        }
        return null;
    }

    private static String trimToNull(String s) {
        if (!StringUtils.hasText(s)) {
            return null;
        }
        return s.trim();
    }

    private static String str(Object o) {
        if (o == null) {
            return "";
        }
        return o.toString().trim();
    }
}
