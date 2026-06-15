package com.nongxinle.ai.composer.menu;

import com.nongxinle.ai.core.AiRunState;
import com.nongxinle.ai.dto.business.MenuOperationAnswerPlan;
import com.nongxinle.ai.tool.business.AiBusinessToolIds;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 菜单专家 LLM 纯统计事实包：只含汇总指标与 dishRows，不含 Java 侧优化分组/动作结论。
 */
public final class MenuExpertNarrativeFactPackBuilder {

    private static final String[] DISH_ACTION_KEYS = {
        "suggestedAction",
        "suggestedActionLabel",
        "recommendedAction",
        "quadrantCode",
        "quadrantName",
        "categoryCode",
        "categoryName",
        "groupCode",
        "groupName",
        "bucket",
        "reason",
        "shouldPromote",
        "shouldReviewCost",
        "nextStep"
    };

    private MenuExpertNarrativeFactPackBuilder() {}

    public static Map<String, Object> build(AiRunState state, MenuOperationAnswerPlan plan) {
        Map<String, Object> toolData = toolEnvelopeData(state);
        List<Map<String, Object>> rawRows = extractDishRows(toolData);
        Map<String, Object> insight = businessInsightSummary(toolData);

        List<RowMetrics> metrics = toMetrics(rawRows);
        Aggregates aggregates = aggregate(metrics, insight);

        List<Map<String, Object>> dishRows = buildDishRows(metrics, aggregates);

        Map<String, Object> pack = new LinkedHashMap<>();
        pack.put("totalDishCount", dishRows.size());
        pack.put("totalSalesAmount", formatAmount(aggregates.totalSales));
        pack.put("totalSoldPortions", formatAmount(aggregates.totalSoldPortions));
        pack.put("totalActualCost", formatAmount(aggregates.totalActualCost));
        pack.put("totalActualProfit", formatAmount(aggregates.totalActualProfit));
        pack.put("blendedGrossMarginRate", formatRate(aggregates.blendedGrossMarginRate));
        pack.put("dishRows", dishRows);
        if (plan != null) {
            if (StringUtils.hasText(plan.getScopeLabel())) {
                pack.put("scopeLabel", plan.getScopeLabel().trim());
            }
            if (StringUtils.hasText(plan.getTimeLabel())) {
                pack.put("timeLabel", plan.getTimeLabel().trim());
            }
        }
        return pack;
    }

    public static Map<String, MenuExpertFactDishRow> indexDishRows(AiRunState state, MenuOperationAnswerPlan plan) {
        Map<String, Object> pack = build(state, plan);
        Object rowsObj = pack.get("dishRows");
        LinkedHashMap<String, MenuExpertFactDishRow> index = new LinkedHashMap<>();
        if (!(rowsObj instanceof List<?> rows)) {
            return index;
        }
        for (Object item : rows) {
            if (!(item instanceof Map<?, ?> row)) {
                continue;
            }
            String name = stringValue(row.get("dishName"));
            if (!StringUtils.hasText(name)) {
                continue;
            }
            index.putIfAbsent(
                    name.trim(),
                    new MenuExpertFactDishRow(
                            name.trim(),
                            stringValue(row.get("blendedGrossMarginRateOnListPrice")),
                            stringValue(row.get("actualProfitAmount"))));
        }
        return index;
    }

    private static List<Map<String, Object>> buildDishRows(List<RowMetrics> metrics, Aggregates aggregates) {
        if (metrics.isEmpty()) {
            return List.of();
        }
        int n = metrics.size();
        List<RowMetrics> bySales = sortedCopy(metrics, Comparator.comparing(RowMetrics::soldPortions).reversed());
        List<RowMetrics> byProfit = sortedCopy(metrics, Comparator.comparing(RowMetrics::actualProfit).reversed());
        List<RowMetrics> byMargin = sortedCopy(metrics, Comparator.comparing(RowMetrics::blendedMargin).reversed());
        List<RowMetrics> byCostGap =
                sortedCopy(metrics, Comparator.comparing(RowMetrics::costGap).reversed());

        Map<String, Integer> salesRank = rankMap(bySales);
        Map<String, Integer> profitRank = rankMap(byProfit);
        Map<String, Integer> marginRank = rankMap(byMargin);
        Map<String, Integer> costGapRank = rankMap(byCostGap);

        List<Map<String, Object>> out = new ArrayList<>();
        for (RowMetrics m : metrics) {
            LinkedHashMap<String, Object> row = new LinkedHashMap<>();
            row.put("dishName", m.dishName());
            putIfPresent(row, "soldPortionsTotal", formatAmount(m.soldPortions()));
            putIfPresent(row, "actualRevenue", formatAmount(m.listPriceRevenue()));
            putIfPresent(row, "listPrice", m.listPrice());
            putIfPresent(row, "currentPrice", firstNonBlank(m.currentPrice(), m.listPrice()));
            putIfPresent(row, "actualCostTotalAmount123", formatAmount(m.actualCost123()));
            putIfPresent(row, "theoreticalCostAmount", m.theoreticalCostAmount());
            putIfPresent(row, "actualProfitAmount", formatAmount(m.actualProfit()));
            putIfPresent(row, "blendedGrossMarginRateOnListPrice", formatRate(m.blendedMargin()));
            putIfPresent(row, "theoreticalGrossMarginRate", m.theoreticalGrossMarginRate());
            putIfPresent(row, "costGapAmount", m.costGapAmount());
            putIfPresent(row, "lossRate", m.lossRate());
            putIfPresent(row, "wasteAmount", m.wasteAmount());
            putIfPresent(row, "lossAmount", m.lossAmount());

            String key = m.dishName();
            putIfPresent(row, "salesRank", salesRank.get(key));
            putIfPresent(row, "profitRank", profitRank.get(key));
            putIfPresent(row, "marginRank", marginRank.get(key));
            putIfPresent(row, "costGapRank", costGapRank.get(key));
            if (aggregates.totalSales.compareTo(BigDecimal.ZERO) > 0) {
                row.put(
                        "salesContributionRate",
                        formatRate(
                                m.listPriceRevenue()
                                        .multiply(BigDecimal.valueOf(100))
                                        .divide(aggregates.totalSales, 2, RoundingMode.HALF_UP)));
            }
            if (aggregates.totalActualProfit.compareTo(BigDecimal.ZERO) > 0) {
                row.put(
                        "profitContributionRate",
                        formatRate(
                                m.actualProfit()
                                        .multiply(BigDecimal.valueOf(100))
                                        .divide(aggregates.totalActualProfit, 2, RoundingMode.HALF_UP)));
            }
            if (n > 0 && salesRank.get(key) != null) {
                row.put(
                        "salesPercentile",
                        formatRate(
                                BigDecimal.valueOf(n - salesRank.get(key) + 1L)
                                        .multiply(BigDecimal.valueOf(100))
                                        .divide(BigDecimal.valueOf(n), 2, RoundingMode.HALF_UP)));
            }
            out.add(row);
        }
        return out;
    }

    private static Map<String, Integer> rankMap(List<RowMetrics> sorted) {
        LinkedHashMap<String, Integer> ranks = new LinkedHashMap<>();
        int rank = 1;
        for (RowMetrics m : sorted) {
            ranks.putIfAbsent(m.dishName(), rank++);
        }
        return ranks;
    }

    private static List<RowMetrics> sortedCopy(List<RowMetrics> source, Comparator<RowMetrics> cmp) {
        List<RowMetrics> copy = new ArrayList<>(source);
        copy.sort(cmp);
        return copy;
    }

    private static Aggregates aggregate(List<RowMetrics> metrics, Map<String, Object> insight) {
        BigDecimal totalSales = BigDecimal.ZERO;
        BigDecimal totalSold = BigDecimal.ZERO;
        BigDecimal totalCost = BigDecimal.ZERO;
        for (RowMetrics m : metrics) {
            totalSales = totalSales.add(m.listPriceRevenue());
            totalSold = totalSold.add(m.soldPortions());
            totalCost = totalCost.add(m.actualCost123());
        }
        BigDecimal totalProfit = totalSales.subtract(totalCost);
        BigDecimal margin = parseDecimal(insight.get("comprehensiveGrossMarginRateOnListPrice"));
        if (margin.compareTo(BigDecimal.ZERO) == 0 && totalSales.compareTo(BigDecimal.ZERO) > 0) {
            margin =
                    totalProfit
                            .multiply(BigDecimal.valueOf(100))
                            .divide(totalSales, 2, RoundingMode.HALF_UP);
        }
        return new Aggregates(totalSales, totalSold, totalCost, totalProfit, margin);
    }

    private record Aggregates(
            BigDecimal totalSales,
            BigDecimal totalSoldPortions,
            BigDecimal totalActualCost,
            BigDecimal totalActualProfit,
            BigDecimal blendedGrossMarginRate) {}

    private record RowMetrics(
            String dishName,
            BigDecimal soldPortions,
            BigDecimal listPriceRevenue,
            String listPrice,
            String currentPrice,
            BigDecimal actualCost123,
            String theoreticalCostAmount,
            BigDecimal actualProfit,
            BigDecimal blendedMargin,
            String theoreticalGrossMarginRate,
            BigDecimal costGap,
            String costGapAmount,
            String lossRate,
            String wasteAmount,
            String lossAmount) {

        static RowMetrics fromRow(Map<String, Object> row) {
            String name = firstNonBlank(stringValue(row.get("dishName")), stringValue(row.get("foodName")));
            if (!StringUtils.hasText(name)) {
                name = "（未命名菜品）";
            }
            BigDecimal sold = parseDecimal(row.get("soldPortionsTotal"));
            BigDecimal rev = parseDecimal(row.get("actualRevenue"));
            BigDecimal cost123 = parseDecimal(row.get("actualCostTotalAmount123"));
            if (cost123.compareTo(BigDecimal.ZERO) == 0) {
                cost123 = parseDecimal(row.get("actualCostTotalAmount"));
            }
            BigDecimal profit = rev.subtract(cost123);
            BigDecimal margin = parseDecimal(row.get("blendedGrossMarginRateOnListPrice"));
            BigDecimal costGap = parseDecimal(row.get("diffCostAmount"));
            return new RowMetrics(
                    name.trim(),
                    sold,
                    rev,
                    stringValue(row.get("listPrice")),
                    stringValue(row.get("currentPrice")),
                    cost123,
                    stringValue(row.get("theoryCostAmount")),
                    profit,
                    margin,
                    stringValue(row.get("grossMarginRateTheoryOnListPrice")),
                    costGap,
                    stringValue(row.get("diffCostAmount")),
                    stringValue(row.get("lossRate")),
                    stringValue(row.get("wasteAmount")),
                    stringValue(row.get("lossAmount")));
        }
    }

    private static List<RowMetrics> toMetrics(List<Map<String, Object>> dishRows) {
        List<RowMetrics> out = new ArrayList<>();
        if (dishRows == null) {
            return out;
        }
        for (Map<String, Object> row : dishRows) {
            if (row == null || row.isEmpty()) {
                continue;
            }
            for (String key : DISH_ACTION_KEYS) {
                row.remove(key);
            }
            out.add(RowMetrics.fromRow(row));
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> toolEnvelopeData(AiRunState state) {
        if (state == null || state.getToolResults() == null) {
            return Map.of();
        }
        Object env = state.getToolResults().get(AiBusinessToolIds.DISH_PROFIT_ANALYSIS);
        if (!(env instanceof Map<?, ?> tm)) {
            return Map.of();
        }
        Object data = tm.get("data");
        if (!(data instanceof Map<?, ?> dm)) {
            return Map.of();
        }
        return (Map<String, Object>) dm;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> extractDishRows(Map<String, Object> toolData) {
        if (toolData == null) {
            return List.of();
        }
        Object raw = toolData.get("dishRows");
        if (!(raw instanceof List<?> list)) {
            return List.of();
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Map<?, ?> map) {
                out.add(new LinkedHashMap<>((Map<String, Object>) map));
            }
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> businessInsightSummary(Map<String, Object> toolData) {
        if (toolData == null) {
            return Map.of();
        }
        Object raw = toolData.get("businessInsightSummary");
        if (raw instanceof Map<?, ?> m) {
            return (Map<String, Object>) m;
        }
        return Map.of();
    }

    private static BigDecimal parseDecimal(Object raw) {
        if (raw == null) {
            return BigDecimal.ZERO;
        }
        try {
            return new BigDecimal(raw.toString().trim().replace(",", "").replace("%", ""));
        } catch (Exception ignore) {
            return BigDecimal.ZERO;
        }
    }

    private static String formatAmount(BigDecimal value) {
        if (value == null) {
            return "";
        }
        return value.setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
    }

    private static String formatRate(BigDecimal value) {
        if (value == null) {
            return "";
        }
        return value.setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString() + "%";
    }

    private static String stringValue(Object raw) {
        return raw == null ? "" : raw.toString().trim();
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return "";
        }
        for (String v : values) {
            if (StringUtils.hasText(v)) {
                return v.trim();
            }
        }
        return "";
    }

    private static void putIfPresent(Map<String, Object> target, String key, Object value) {
        if (value == null) {
            return;
        }
        if (value instanceof String s && !StringUtils.hasText(s)) {
            return;
        }
        target.put(key, value);
    }
}
