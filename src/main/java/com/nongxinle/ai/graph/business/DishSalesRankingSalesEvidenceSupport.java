package com.nongxinle.ai.graph.business;

import com.nongxinle.ai.dto.business.DishSalesAnswerPlan;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * DISH_SALES 排行类 AnswerPlan 的销量/销售额证据门禁：无真实证据时不生成排行结果、不写 Top1 话术、不沉淀 resultAnchor。
 */
public final class DishSalesRankingSalesEvidenceSupport {

    public static final String NO_DATA_REASON_NO_DISH_SALES_FOR_PERIOD = "NO_DISH_SALES_FOR_PERIOD";

    public static final String DEBUG_NO_DATA_REASON = "dishSalesRankingNoDataReason";
    public static final String DEBUG_REQUESTED_RANKING_PLAN_TYPE = "requestedRankingPlanType";
    public static final String DEBUG_REQUESTED_METRIC_TYPE = "requestedMetricType";

    public static final String EMPTY_RANKING_MESSAGE =
            "该时间范围内没有查询到菜品销售数据，暂不能生成菜品销量排行。";

    private DishSalesRankingSalesEvidenceSupport() {}

    public static boolean isRankingMetricType(String metricType) {
        if (!StringUtils.hasText(metricType)) {
            return false;
        }
        String mt = metricType.trim();
        return DishSalesAnswerPlan.METRIC_COUNT_HIGH.equals(mt)
                || DishSalesAnswerPlan.METRIC_COUNT_LOW.equals(mt)
                || DishSalesAnswerPlan.METRIC_AMOUNT_HIGH.equals(mt);
    }

    public static boolean hasCountRankingEvidence(List<Map<String, Object>> rows) {
        if (rows == null || rows.isEmpty()) {
            return false;
        }
        for (Map<String, Object> row : rows) {
            if (row == null) {
                continue;
            }
            BigDecimal qty = parseDecimal(row.get("soldPortionsTotal"));
            if (qty != null && qty.compareTo(BigDecimal.ZERO) > 0) {
                return true;
            }
        }
        return false;
    }

    public static boolean hasAmountRankingEvidence(List<Map<String, Object>> rows) {
        if (rows == null || rows.isEmpty()) {
            return false;
        }
        for (Map<String, Object> row : rows) {
            if (row == null) {
                continue;
            }
            BigDecimal amt =
                    firstPositiveDecimal(row.get("listPriceRevenue"), row.get("salesAmount"));
            if (amt != null) {
                return true;
            }
        }
        return false;
    }

    public static boolean hasRankingEvidenceForMetric(
            String metricType, List<Map<String, Object>> aggregatedRows) {
        if (DishSalesAnswerPlan.METRIC_AMOUNT_HIGH.equals(metricType)) {
            return hasAmountRankingEvidence(aggregatedRows);
        }
        if (DishSalesAnswerPlan.METRIC_COUNT_HIGH.equals(metricType)
                || DishSalesAnswerPlan.METRIC_COUNT_LOW.equals(metricType)) {
            return hasCountRankingEvidence(aggregatedRows);
        }
        return false;
    }

    public static boolean isNoDataRankingPlan(DishSalesAnswerPlan plan) {
        if (plan == null) {
            return false;
        }
        if (DishSalesAnswerPlan.TYPE_DISH_SALES_RANKING_NO_DATA.equals(plan.getPlanType())) {
            return true;
        }
        Map<String, Object> debug = plan.getDebug();
        return debug != null
                && NO_DATA_REASON_NO_DISH_SALES_FOR_PERIOD.equals(
                        stringOrNull(debug.get(DEBUG_NO_DATA_REASON)));
    }

    public static String resolveRequestedRankingPlanType(DishSalesAnswerPlan plan) {
        if (plan == null) {
            return null;
        }
        Map<String, Object> debug = plan.getDebug();
        if (debug != null) {
            String fromDebug = stringOrNull(debug.get(DEBUG_REQUESTED_RANKING_PLAN_TYPE));
            if (StringUtils.hasText(fromDebug)) {
                return fromDebug;
            }
        }
        String planType = stringOrNull(plan.getPlanType());
        if (StringUtils.hasText(planType)
                && !DishSalesAnswerPlan.TYPE_DISH_SALES_RANKING_NO_DATA.equals(planType)) {
            return planType;
        }
        return null;
    }

    public static DishSalesAnswerPlan buildNoDataRankingPlan(
            String requestedRankingPlanType,
            String metricType,
            String scopeLabel,
            String timeLabel,
            Map<String, Object> baseDebug,
            List<String> limitations) {
        LinkedHashMap<String, Object> debug = new LinkedHashMap<>();
        if (baseDebug != null) {
            debug.putAll(baseDebug);
        }
        debug.put(DEBUG_NO_DATA_REASON, NO_DATA_REASON_NO_DISH_SALES_FOR_PERIOD);
        if (StringUtils.hasText(requestedRankingPlanType)) {
            debug.put(DEBUG_REQUESTED_RANKING_PLAN_TYPE, requestedRankingPlanType.trim());
        }
        if (StringUtils.hasText(metricType)) {
            debug.put(DEBUG_REQUESTED_METRIC_TYPE, metricType.trim());
        }
        LinkedHashMap<String, Object> cov = new LinkedHashMap<>();
        cov.put("rowCount", debug.getOrDefault("rowCount", 0));
        cov.put("rankedRowCount", 0);
        List<String> lim = limitations == null ? new ArrayList<>() : new ArrayList<>(limitations);
        return DishSalesAnswerPlan.builder()
                .planType(DishSalesAnswerPlan.TYPE_DISH_SALES_RANKING_NO_DATA)
                .metricType(metricType)
                .scopeLabel(scopeLabel)
                .timeLabel(timeLabel)
                .rankingRows(List.of())
                .dataCoverage(cov)
                .limitations(lim)
                .summary(null)
                .resultAnchors(List.of())
                .debug(debug)
                .build();
    }

    private static BigDecimal firstPositiveDecimal(Object... candidates) {
        if (candidates == null) {
            return null;
        }
        for (Object candidate : candidates) {
            BigDecimal parsed = parseDecimal(candidate);
            if (parsed != null && parsed.compareTo(BigDecimal.ZERO) > 0) {
                return parsed;
            }
        }
        return null;
    }

    private static BigDecimal parseDecimal(Object v) {
        if (v == null) {
            return null;
        }
        if (v instanceof BigDecimal bd) {
            return bd;
        }
        if (v instanceof Number n) {
            try {
                return new BigDecimal(n.toString());
            } catch (NumberFormatException ex) {
                return null;
            }
        }
        String s = v.toString().trim();
        if (!StringUtils.hasText(s)) {
            return null;
        }
        try {
            return new BigDecimal(s.replace('\uFF0C', '.').replace('，', '.'));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static String stringOrNull(Object v) {
        if (v == null || !StringUtils.hasText(v.toString())) {
            return null;
        }
        return v.toString().trim();
    }
}
