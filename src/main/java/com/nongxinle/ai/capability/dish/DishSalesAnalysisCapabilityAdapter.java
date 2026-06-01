package com.nongxinle.ai.capability.dish;

import com.nongxinle.service.GbDepFoodBusinessInsightService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * AI 单菜销售 Capability：复用 {@link GbDepFoodBusinessInsightService#buildInsight}（与
 * {@code POST /api/gbdepfood/depGeFoodBusiness} 同源），不新增 Mapper / SQL。
 */
@Component
@RequiredArgsConstructor
public class DishSalesAnalysisCapabilityAdapter {

    private static final String REASON_DISH_NOT_FOUND = "dish_not_found";
    private static final String REASON_NO_DATA = "no_data";
    private static final String REASON_MISSING_DISH_SELECTOR = "missing_dish_selector";

    private final GbDepFoodBusinessInsightService gbDepFoodBusinessInsightService;

    @SuppressWarnings("unchecked")
    public DishSalesAnalysisCapabilityResult analyze(DishSalesAnalysisCapabilityRequest request) {
        if (request == null) {
            return error("request_null", "request is null");
        }
        String startDate = trimToNull(request.getStartDate());
        String stopDate = resolveStopDate(request);
        Integer disId = request.getDisId();
        Integer depFatherId = request.getDepFatherId();
        if (startDate == null || stopDate == null || disId == null || depFatherId == null) {
            return error("invalid_args", "startDate、stopDate/endDate、disId、depFatherId 不能为空");
        }

        Integer subDepId = resolveSubDepId(request);
        Map<String, Object> insight;
        try {
            insight = gbDepFoodBusinessInsightService.buildInsight(
                    disId, depFatherId, startDate, stopDate, subDepId);
        } catch (IllegalArgumentException e) {
            return error("service_rejected", e.getMessage());
        } catch (RuntimeException e) {
            return error("service_error", e.getMessage() == null ? "buildInsight failed" : e.getMessage());
        }

        List<Map<String, Object>> dishRows = insight.get("dishes") instanceof List
                ? (List<Map<String, Object>>) insight.get("dishes")
                : List.of();
        Map<String, Object> rawSummary = buildRawReportSummary(insight, dishRows.size());

        if (dishRows.isEmpty()) {
            return DishSalesAnalysisCapabilityResult.builder()
                    .status(DishCostAnalysisCapabilityStatus.NO_DATA)
                    .reasonCode(REASON_NO_DATA)
                    .message("区间内无菜品销售行")
                    .rawReportSummary(rawSummary)
                    .rawSalesRows(List.of())
                    .candidates(List.of())
                    .build();
        }

        List<Map<String, Object>> rankedRows =
                rankBySoldPortionsDesc(collapseRowsByFoodIdentity(dishRows));

        Integer foodId = request.getFoodId();
        String dishName = trimToNull(request.getDishName());
        if (foodId != null) {
            return resolveByFoodId(foodId, rankedRows, rawSummary);
        }
        if (StringUtils.hasText(dishName)) {
            return resolveByDishName(dishName, rankedRows, rawSummary);
        }

        return overviewFromRows(rankedRows, rawSummary);
    }

    /** 排行/概览：无单菜锚点时返回全量 rawSalesRows，供 AnswerPlan 排序举证。 */
    private static DishSalesAnalysisCapabilityResult overviewFromRows(
            List<Map<String, Object>> rankedRows, Map<String, Object> rawSummary) {
        return DishSalesAnalysisCapabilityResult.builder()
                .status(DishCostAnalysisCapabilityStatus.SUCCESS)
                .message("菜品销售数据已返回")
                .rawReportSummary(rawSummary)
                .rawSalesRows(rankedRows)
                .candidates(List.of())
                .build();
    }

    private static DishSalesAnalysisCapabilityResult resolveByFoodId(
            Integer foodId, List<Map<String, Object>> rankedRows, Map<String, Object> rawSummary) {
        List<Map<String, Object>> matched = new ArrayList<>();
        for (Map<String, Object> row : rankedRows) {
            if (Objects.equals(foodId, row.get("foodId"))) {
                matched.add(row);
            }
        }
        if (matched.isEmpty()) {
            return DishSalesAnalysisCapabilityResult.builder()
                    .status(DishCostAnalysisCapabilityStatus.NO_DATA)
                    .reasonCode(REASON_DISH_NOT_FOUND)
                    .message("未找到指定 foodId 的菜品销售行")
                    .rawReportSummary(rawSummary)
                    .rawSalesRows(rankedRows)
                    .candidates(List.of())
                    .build();
        }
        List<Map<String, Object>> collapsed = collapseRowsByFoodIdentity(matched);
        return successFromRow(collapsed.get(0), rankingOf(rankedRows, collapsed.get(0)), rankedRows, rawSummary);
    }

    private static DishSalesAnalysisCapabilityResult resolveByDishName(
            String dishName, List<Map<String, Object>> rankedRows, Map<String, Object> rawSummary) {
        String needle = dishName.trim();
        List<Map<String, Object>> exact = new ArrayList<>();
        for (Map<String, Object> row : rankedRows) {
            String name = dishDisplayName(row);
            if (needle.equals(name)) {
                exact.add(row);
            }
        }
        if (exact.isEmpty()) {
            return resolveByPartialDishName(needle, rankedRows, rawSummary);
        }
        List<Map<String, Object>> collapsed = collapseRowsByFoodIdentity(exact);
        if (collapsed.size() == 1) {
            return successFromRow(
                    collapsed.get(0), rankingOf(rankedRows, collapsed.get(0)), rankedRows, rawSummary);
        }
        return clarificationFromRows(collapsed, rankedRows, rawSummary, needle, "exact_name");
    }

    private static DishSalesAnalysisCapabilityResult resolveByPartialDishName(
            String needle, List<Map<String, Object>> rankedRows, Map<String, Object> rawSummary) {
        List<Map<String, Object>> contains = new ArrayList<>();
        for (Map<String, Object> row : rankedRows) {
            String name = dishDisplayName(row);
            if (StringUtils.hasText(name) && name.contains(needle)) {
                contains.add(row);
            }
        }
        if (contains.isEmpty()) {
            return DishSalesAnalysisCapabilityResult.builder()
                    .status(DishCostAnalysisCapabilityStatus.NO_DATA)
                    .reasonCode(REASON_DISH_NOT_FOUND)
                    .message("未找到匹配菜名")
                    .rawReportSummary(rawSummary)
                    .rawSalesRows(rankedRows)
                    .candidates(List.of())
                    .build();
        }
        List<Map<String, Object>> collapsed = collapseRowsByFoodIdentity(contains);
        if (collapsed.size() == 1) {
            return successFromRow(
                    collapsed.get(0), rankingOf(rankedRows, collapsed.get(0)), rankedRows, rawSummary);
        }
        return clarificationFromRows(collapsed, rankedRows, rawSummary, needle, "partial_name");
    }

    /** 单店多行同一 foodId：聚合销量/销售额后视为同一菜品实体。 */
    private static List<Map<String, Object>> collapseRowsByFoodIdentity(List<Map<String, Object>> rows) {
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        Map<String, Map<String, Object>> byFoodId = new LinkedHashMap<>();
        List<Map<String, Object>> withoutFoodId = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            if (row == null) {
                continue;
            }
            Object foodId = row.get("foodId");
            if (foodId == null) {
                withoutFoodId.add(row);
                continue;
            }
            String key = foodId.toString().trim();
            Map<String, Object> existing = byFoodId.get(key);
            if (existing == null) {
                byFoodId.put(key, new LinkedHashMap<>(row));
            } else {
                mergeSalesMetricsInto(existing, row);
            }
        }
        List<Map<String, Object>> collapsed = new ArrayList<>(byFoodId.values());
        collapsed.addAll(withoutFoodId);
        return collapsed;
    }

    private static void mergeSalesMetricsInto(Map<String, Object> target, Map<String, Object> addition) {
        target.put(
                "soldPortionsTotal",
                sumMetricStrings(str(target.get("soldPortionsTotal")), str(addition.get("soldPortionsTotal"))));
        target.put(
                "listPriceRevenue",
                sumMetricStrings(str(target.get("listPriceRevenue")), str(addition.get("listPriceRevenue"))));
        if (!StringUtils.hasText(str(target.get("foodName"))) && StringUtils.hasText(str(addition.get("foodName")))) {
            target.put("foodName", addition.get("foodName"));
        }
        if (!StringUtils.hasText(str(target.get("dishName"))) && StringUtils.hasText(str(addition.get("dishName")))) {
            target.put("dishName", addition.get("dishName"));
        }
    }

    private static String sumMetricStrings(String a, String b) {
        double av = parseDoubleSafe(a);
        double bv = parseDoubleSafe(b);
        if (av == 0d && !StringUtils.hasText(a)) {
            return StringUtils.hasText(b) ? b.trim() : "";
        }
        if (bv == 0d && !StringUtils.hasText(b)) {
            return StringUtils.hasText(a) ? a.trim() : "";
        }
        double sum = av + bv;
        if (Math.rint(sum) == sum) {
            return String.valueOf((long) sum);
        }
        return String.valueOf(sum);
    }

    private static DishSalesAnalysisCapabilityResult clarificationFromRows(
            List<Map<String, Object>> rows,
            List<Map<String, Object>> rawSalesRows,
            Map<String, Object> rawSummary,
            String dishNameLabel,
            String matchKind) {
        List<Map<String, Object>> deduped = collapseRowsByFoodIdentity(rows);
        List<Map<String, Object>> candidates = new ArrayList<>();
        for (Map<String, Object> row : deduped) {
            candidates.add(candidateBrief(row));
        }
        String message = formatEntityDisambiguationMessage(dishNameLabel, candidates, matchKind);
        return DishSalesAnalysisCapabilityResult.builder()
                .status(DishCostAnalysisCapabilityStatus.NEED_CLARIFICATION)
                .reasonCode("entity_disambiguation")
                .message(message)
                .candidates(candidates)
                .rawSalesRows(rawSalesRows)
                .rawReportSummary(rawSummary)
                .build();
    }

    private static String formatEntityDisambiguationMessage(
            String dishNameLabel, List<Map<String, Object>> candidates, String matchKind) {
        int n = candidates == null ? 0 : candidates.size();
        String label = StringUtils.hasText(dishNameLabel) ? dishNameLabel.trim() : "该菜名";
        StringBuilder sb = new StringBuilder();
        if ("partial_name".equals(matchKind)) {
            sb.append("「").append(label).append("」在当前查询范围内匹配到 ").append(n).append(" 道菜品");
        } else {
            sb.append("「").append(label).append("」在当前查询范围内有 ").append(n).append(" 道同名菜品");
        }
        sb.append("，需要您指定具体是哪一个。");
        sb.append("这是菜品实体消歧（查询口径与 Tool 正常执行），不是因为 wire、卡片或查询失败；");
        sb.append("选定唯一菜品后即可展示销售卡片。");
        if (n > 0) {
            sb.append(" 候选：");
            int limit = Math.min(n, 5);
            for (int i = 0; i < limit; i++) {
                if (i > 0) {
                    sb.append("；");
                }
                sb.append(formatCandidateLine(candidates.get(i), i + 1));
            }
            if (n > limit) {
                sb.append("；等共 ").append(n).append(" 道");
            }
        }
        return sb.toString();
    }

    private static String formatCandidateLine(Map<String, Object> candidate, int index) {
        if (candidate == null) {
            return index + ".（未知）";
        }
        String name = firstNonBlank(str(candidate.get("dishName")), str(candidate.get("foodName")));
        if (!StringUtils.hasText(name)) {
            name = "（未命名）";
        }
        StringBuilder line = new StringBuilder();
        line.append(index).append('.').append(name);
        Object foodId = candidate.get("foodId");
        if (foodId != null && StringUtils.hasText(foodId.toString())) {
            line.append("（foodId=").append(foodId.toString().trim()).append(')');
        }
        String sold = str(candidate.get("soldPortionsTotal"));
        if (StringUtils.hasText(sold)) {
            line.append("，销量 ").append(sold).append('份');
        }
        return line.toString();
    }

    private static DishSalesAnalysisCapabilityResult successFromRow(
            Map<String, Object> row,
            Integer ranking,
            List<Map<String, Object>> rawSalesRows,
            Map<String, Object> rawSummary) {
        Integer foodId = row.get("foodId") instanceof Number n ? n.intValue() : null;
        String dishName = dishDisplayName(row);
        String salesPortions = str(row.get("soldPortionsTotal"));
        String salesAmount = str(row.get("listPriceRevenue"));
        String salesUnitPrice = str(row.get("listPrice"));

        Map<String, Object> cardPayload = DishSalesAnalysisCapabilityResult.buildCardPayload(
                dishName, foodId, salesPortions, salesAmount, salesUnitPrice, ranking);

        return DishSalesAnalysisCapabilityResult.builder()
                .status(DishCostAnalysisCapabilityStatus.SUCCESS)
                .dishId(foodId)
                .dishName(dishName)
                .salesPortions(salesPortions)
                .salesAmount(salesAmount)
                .salesUnitPrice(salesUnitPrice)
                .ranking(ranking)
                .rawSalesRows(rawSalesRows)
                .rawReportSummary(rawSummary)
                .cardPayload(cardPayload)
                .build();
    }

    private static List<Map<String, Object>> rankBySoldPortionsDesc(List<Map<String, Object>> dishRows) {
        List<Map<String, Object>> copy = new ArrayList<>(dishRows);
        copy.sort((a, b) -> compareSoldDesc(a, b));
        return copy;
    }

    private static int compareSoldDesc(Map<String, Object> a, Map<String, Object> b) {
        double av = parseDoubleSafe(str(a.get("soldPortionsTotal")));
        double bv = parseDoubleSafe(str(b.get("soldPortionsTotal")));
        return Double.compare(bv, av);
    }

    private static Integer rankingOf(List<Map<String, Object>> rankedRows, Map<String, Object> target) {
        for (int i = 0; i < rankedRows.size(); i++) {
            if (rankedRows.get(i) == target) {
                return i + 1;
            }
            Object fid = rankedRows.get(i).get("foodId");
            Object tfid = target.get("foodId");
            if (fid != null && fid.equals(tfid)) {
                return i + 1;
            }
        }
        return null;
    }

    private static Map<String, Object> candidateBrief(Map<String, Object> row) {
        Map<String, Object> c = new LinkedHashMap<>();
        c.put("foodId", row.get("foodId"));
        c.put("dishName", dishDisplayName(row));
        c.put("soldPortionsTotal", row.get("soldPortionsTotal"));
        c.put("listPriceRevenue", row.get("listPriceRevenue"));
        return c;
    }

    private static Map<String, Object> buildRawReportSummary(Map<String, Object> insight, int dishCount) {
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("startDate", insight.get("startDate"));
        s.put("stopDate", insight.get("stopDate"));
        s.put("disId", insight.get("disId"));
        s.put("depFatherId", insight.get("depFatherId"));
        s.put("dishCount", dishCount);
        s.put("source", "GbDepFoodBusinessInsightService.buildInsight");
        return s;
    }

    private static DishSalesAnalysisCapabilityResult error(String reasonCode, String message) {
        return DishSalesAnalysisCapabilityResult.builder()
                .status(DishCostAnalysisCapabilityStatus.ERROR)
                .reasonCode(reasonCode)
                .message(message)
                .candidates(List.of())
                .build();
    }

    private static Integer resolveSubDepId(DishSalesAnalysisCapabilityRequest request) {
        if (request.getSubDepId() != null) {
            return request.getSubDepId();
        }
        String searchDepId = trimToNull(request.getSearchDepId());
        if (searchDepId == null || "-1".equals(searchDepId)) {
            return null;
        }
        try {
            return Integer.parseInt(searchDepId);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String resolveStopDate(DishSalesAnalysisCapabilityRequest request) {
        String stop = trimToNull(request.getStopDate());
        if (stop != null) {
            return stop;
        }
        return trimToNull(request.getEndDate());
    }

    private static String dishDisplayName(Map<String, Object> row) {
        return firstNonBlank(str(row.get("foodName")), str(row.get("dishName")));
    }

    private static String str(Object o) {
        return o == null ? "" : o.toString().trim();
    }

    private static String trimToNull(String s) {
        if (!StringUtils.hasText(s)) {
            return null;
        }
        return s.trim();
    }

    private static String firstNonBlank(String a, String b) {
        if (StringUtils.hasText(a)) {
            return a.trim();
        }
        if (StringUtils.hasText(b)) {
            return b.trim();
        }
        return "";
    }

    private static double parseDoubleSafe(String s) {
        if (!StringUtils.hasText(s)) {
            return 0d;
        }
        try {
            return Double.parseDouble(s.replace('\uFF0C', '.').replace('，', '.'));
        } catch (NumberFormatException e) {
            return 0d;
        }
    }
}
