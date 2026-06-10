package com.nongxinle.ai.graph.business;

import com.nongxinle.ai.core.AiRunState;
import com.nongxinle.constants.AiInsightDishProfitScope;
import com.nongxinle.service.GbDishCostAnalysisService;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 库存 / 销货核对卡：销售毛利基础统计 + 配料理论 vs 实际 diff。 */
final class StockIngredientReconcileFactBuilder {

    private static final int DIFF_ITEM_LIMIT = 20;
    private static final BigDecimal DIFF_EPSILON = new BigDecimal("0.01");

    private StockIngredientReconcileFactBuilder() {}

    record FactResult(Map<String, Object> outboundSummary, List<Map<String, Object>> ingredientDiffItems) {}

    static FactResult build(
            AiRunState state,
            BusinessStatusCardBuildRequest req,
            GbDishCostAnalysisService dishCostAnalysisService,
            ToolDepartmentResolutionSupport departmentResolutionSupport) {
        if (state == null || dishCostAnalysisService == null || req == null) {
            return empty();
        }
        Integer disId = resolveDisId(state);
        Integer depFatherId = resolveDepFatherId(state, departmentResolutionSupport);
        String start = req.getStartDate();
        String end = req.getEndDate();
        if (disId == null || depFatherId == null || !StringUtils.hasText(start) || !StringUtils.hasText(end)) {
            return empty();
        }
        Map<String, Object> report;
        try {
            report = dishCostAnalysisService.buildOutboundIngredientAnalysisReport(
                    start.trim(),
                    end.trim(),
                    disId,
                    null,
                    depFatherId,
                    "outbound",
                    "desc",
                    null,
                    null,
                    null,
                    null);
        } catch (RuntimeException e) {
            return empty();
        }
        return new FactResult(extractOutboundSummary(report), buildDiffItemsFromReport(report));
    }

    static List<Map<String, Object>> buildDiffItems(
            AiRunState state,
            BusinessStatusCardBuildRequest req,
            GbDishCostAnalysisService dishCostAnalysisService,
            ToolDepartmentResolutionSupport departmentResolutionSupport) {
        return build(state, req, dishCostAnalysisService, departmentResolutionSupport).ingredientDiffItems();
    }

    private static FactResult empty() {
        return new FactResult(defaultOutboundSummary(), List.of());
    }

    private static Map<String, Object> defaultOutboundSummary() {
        LinkedHashMap<String, Object> summary = new LinkedHashMap<>();
        summary.put("actualOutboundAmount", 0.0);
        summary.put("theoryOutboundAmount", 0.0);
        summary.put("actualGrossMarginRate", null);
        summary.put("theoryGrossMarginRate", null);
        return summary;
    }

    private static Map<String, Object> extractOutboundSummary(Map<String, Object> report) {
        Map<String, Object> summary = defaultOutboundSummary();
        if (report == null) {
            return summary;
        }
        Object raw = report.get("summary");
        if (!(raw instanceof Map<?, ?> src)) {
            return summary;
        }
        putSummaryField(summary, "actualOutboundAmount", src.get("actualOutboundAmount"), src.get("totalOutboundAmount"));
        putSummaryField(summary, "theoryOutboundAmount", src.get("theoryOutboundAmount"), null);
        putSummaryField(summary, "actualGrossMarginRate", src.get("actualGrossMarginRate"), null);
        putSummaryField(summary, "theoryGrossMarginRate", src.get("theoryGrossMarginRate"), null);
        return summary;
    }

    private static void putSummaryField(
            Map<String, Object> target, String key, Object primary, Object fallback) {
        Object v = primary != null ? primary : fallback;
        if (v == null) {
            return;
        }
        if (key.endsWith("Rate")) {
            target.put(key, v.toString());
            return;
        }
        target.put(key, parseDoubleLoose(v));
    }

    private static List<Map<String, Object>> buildDiffItemsFromReport(Map<String, Object> report) {
        if (report == null) {
            return List.of();
        }
        Object raw = report.get("ingredientsAnalysis");
        if (!(raw instanceof List<?> rows) || rows.isEmpty()) {
            return List.of();
        }
        List<Map<String, Object>> candidates = new ArrayList<>();
        for (Object o : rows) {
            if (!(o instanceof Map<?, ?> rowRaw)) {
                continue;
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> row = (Map<String, Object>) rowRaw;
            BigDecimal diff = parseDecimal(row.get("diffUsage"));
            if (diff == null || diff.abs().compareTo(DIFF_EPSILON) <= 0) {
                continue;
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("disGoodsId", row.get("disGoodsId"));
            item.put("ingredientName", firstNonBlank(row.get("gbDgGoodsName"), row.get("goodsName")));
            item.put("standardName", row.get("gbDgGoodsStandardname"));
            item.put("theoryUsage", row.get("theoryUsage"));
            item.put("actualUsage", row.get("actualUsage"));
            item.put("diffUsage", row.get("diffUsage"));
            item.put("diffCostAmount", row.get("diffCostAmount"));
            item.put("_absDiff", diff.abs());
            if (item.get("ingredientName") != null) {
                candidates.add(item);
            }
        }
        candidates.sort(Comparator.comparing(
                (Map<String, Object> m) -> (BigDecimal) m.get("_absDiff"),
                Comparator.nullsLast(Comparator.reverseOrder())));
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> item : candidates) {
            if (out.size() >= DIFF_ITEM_LIMIT) {
                break;
            }
            item.remove("_absDiff");
            out.add(item);
        }
        return out;
    }

    static boolean hasOutboundSummaryData(Map<String, Object> outboundSummary) {
        if (outboundSummary == null || outboundSummary.isEmpty()) {
            return false;
        }
        return parseDoubleLoose(outboundSummary.get("actualOutboundAmount")) > 0
                || parseDoubleLoose(outboundSummary.get("theoryOutboundAmount")) > 0
                || StringUtils.hasText(stringOrNull(outboundSummary.get("actualGrossMarginRate")))
                || StringUtils.hasText(stringOrNull(outboundSummary.get("theoryGrossMarginRate")));
    }

    private static String stringOrNull(Object v) {
        if (v == null) {
            return null;
        }
        String s = v.toString().trim();
        return s.isEmpty() ? null : s;
    }

    private static double parseDoubleLoose(Object v) {
        if (v == null) {
            return 0.0;
        }
        if (v instanceof Number n) {
            return n.doubleValue();
        }
        try {
            return Double.parseDouble(v.toString().trim());
        } catch (Exception e) {
            return 0.0;
        }
    }

    private static Integer resolveDisId(AiRunState state) {
        if (state.getDistributerId() != null && state.getDistributerId() > 0) {
            return state.getDistributerId().intValue();
        }
        return null;
    }

    private static Integer resolveDepFatherId(
            AiRunState state, ToolDepartmentResolutionSupport departmentResolutionSupport) {
        if (BusinessToolExecutionNode.shouldRouteGroupWideDishInsight(state)) {
            return AiInsightDishProfitScope.DEP_FATHER_ID_GROUP_WIDE_Mendian_AGGREGATE_UNDER_DIS_ID;
        }
        Long raw = state.getDepartmentId();
        Long resolved = departmentResolutionSupport != null
                ? departmentResolutionSupport.resolveBuildInsightDepartmentFatherId(state, raw)
                : raw;
        return resolved != null && resolved > 0 ? resolved.intValue() : null;
    }

    private static BigDecimal parseDecimal(Object v) {
        if (v == null) {
            return null;
        }
        if (v instanceof BigDecimal bd) {
            return bd;
        }
        if (v instanceof Number n) {
            return BigDecimal.valueOf(n.doubleValue());
        }
        try {
            return new BigDecimal(v.toString().trim());
        } catch (Exception e) {
            return null;
        }
    }

    private static String firstNonBlank(Object... values) {
        for (Object v : values) {
            if (v == null) {
                continue;
            }
            String s = v.toString().trim();
            if (StringUtils.hasText(s)) {
                return s;
            }
        }
        return null;
    }
}
