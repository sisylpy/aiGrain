package com.nongxinle.ai.graph.business;

import com.nongxinle.ai.dto.business.DishProfitAnswerPlan;
import com.nongxinle.ai.semantic.matrix.DishProfitSemanticCapabilityMatrix;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * DISH_PROFIT 排行类 AnswerPlan 的销量证据门禁：无真实菜品销售/利润证据时不生成排行结果、不回落聚合 portfolio 话术。
 */
public final class DishProfitRankingSalesEvidenceSupport {

    public static final String NO_DATA_REASON_NO_DISH_SALES_FOR_PERIOD = "NO_DISH_SALES_FOR_PERIOD";

    public static final String DEBUG_NO_DATA_REASON = "dishProfitRankingNoDataReason";
    public static final String DEBUG_REQUESTED_RANKING_PLAN_TYPE = "requestedRankingPlanType";

    public static final String EMPTY_RANKING_MESSAGE =
            "该时间范围内没有查询到菜品销售数据，暂不能做菜品利润排行。";

    private DishProfitRankingSalesEvidenceSupport() {}

    public static boolean isRankingPlanType(String planType) {
        return DishProfitSemanticCapabilityMatrix.isRankingTargetPlanType(planType);
    }

    public static boolean hasRankingSalesEvidence(
            List<Map<String, Object>> dishRows, Map<String, Object> toolData) {
        if (MenuPortfolioSalesEvidenceSupport.countSoldDishesFromRows(dishRows) > 0) {
            return true;
        }
        return parseNonNegativeInt(toolData != null ? toolData.get("salesDishCount") : null) > 0;
    }

    public static String resolveRequestedRankingPlanType(DishProfitAnswerPlan plan) {
        if (plan == null) {
            return null;
        }
        Map<String, Object> debug = plan.getDebug();
        if (debug != null) {
            String requested = stringOrNull(debug.get(DEBUG_REQUESTED_RANKING_PLAN_TYPE));
            if (StringUtils.hasText(requested)) {
                return requested.trim();
            }
        }
        String planType = plan.getPlanType();
        if (isRankingPlanType(planType)) {
            return planType.trim();
        }
        return null;
    }

    public static boolean isNoDataRankingPlan(DishProfitAnswerPlan plan) {
        if (plan == null) {
            return false;
        }
        if (DishProfitAnswerPlan.TYPE_DISH_PROFIT_RANKING_NO_DATA.equals(plan.getPlanType())) {
            return true;
        }
        Map<String, Object> debug = plan.getDebug();
        return debug != null
                && NO_DATA_REASON_NO_DISH_SALES_FOR_PERIOD.equals(
                        stringOrNull(debug.get(DEBUG_NO_DATA_REASON)));
    }

    public static DishProfitAnswerPlan buildNoDataRankingPlan(
            String requestedRankingPlanType,
            String scopeLabel,
            String timeLabel,
            Map<String, Object> baseDebug) {
        LinkedHashMap<String, Object> debug = new LinkedHashMap<>();
        if (baseDebug != null) {
            debug.putAll(baseDebug);
        }
        debug.put(DEBUG_NO_DATA_REASON, NO_DATA_REASON_NO_DISH_SALES_FOR_PERIOD);
        if (StringUtils.hasText(requestedRankingPlanType)) {
            debug.put(DEBUG_REQUESTED_RANKING_PLAN_TYPE, requestedRankingPlanType.trim());
        }
        return DishProfitAnswerPlan.builder()
                .planType(DishProfitAnswerPlan.TYPE_DISH_PROFIT_RANKING_NO_DATA)
                .scopeLabel(scopeLabel)
                .timeLabel(timeLabel)
                .sortKey(null)
                .sortDirection(null)
                .topN(0)
                .focusRows(List.of())
                .secondaryRows(List.of())
                .debug(debug)
                .build();
    }

    private static int parseNonNegativeInt(Object v) {
        if (v == null) {
            return 0;
        }
        if (v instanceof Number n) {
            return Math.max(0, n.intValue());
        }
        try {
            return Math.max(0, Integer.parseInt(v.toString().trim()));
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    private static String stringOrNull(Object v) {
        if (v == null || !StringUtils.hasText(v.toString())) {
            return null;
        }
        return v.toString().trim();
    }
}
