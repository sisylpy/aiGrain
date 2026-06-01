package com.nongxinle.ai.graph.business;

import com.nongxinle.ai.core.AiRunState;
import com.nongxinle.ai.dto.business.DishProfitAnswerPlan;
import com.nongxinle.ai.util.AiTimeWindowTextFormatter;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 从已定稿 {@link DishProfitAnswerPlan} 生成展示卡；不查库、不重算排行。
 * <p>
 * 排行展示语义<strong>仅</strong>由 AnswerPlan 的明确 {@link DishProfitAnswerPlan#planType} 决定；
 * 禁止用 {@code sortDirection}、sortKey 或中文关键词推断业务类型。
 */
public final class DishProfitAnswerPlanCardSupport {

    private static final String SOURCE = "dishProfitAnswerPlan";
    private static final String DATA_REF_FOCUS_AND_SECONDARY = "focusRows+secondaryRows";
    private static final String CHART_TYPE_TABLE = "TABLE";
    private static final String PAYLOAD_STATUS_EMPTY = "EMPTY";
    private static final String PAYLOAD_STATUS_OK = "OK";
    private static final String PAYLOAD_RANKING_META_WARNING = "rankingMetaWarning";

    private DishProfitAnswerPlanCardSupport() {}

    public static Map<String, Object> buildCardPayload(DishProfitAnswerPlan plan) {
        return buildCardPayload(plan, null);
    }

    public static Map<String, Object> buildCardPayload(DishProfitAnswerPlan plan, AiRunState state) {
        if (plan == null) {
            return null;
        }
        if (DishProfitRankingSalesEvidenceSupport.isNoDataRankingPlan(plan)) {
            return buildEmptyRankingCardPayload(plan, state);
        }
        String planType = blankToNull(plan.getPlanType());
        if (!StringUtils.hasText(planType)) {
            return null;
        }
        if (isRankingPlanType(planType)) {
            return buildRankingCardPayload(plan, state);
        }
        return null;
    }

    public static boolean isRankingPlanType(String planType) {
        return DishProfitRankingSalesEvidenceSupport.isRankingPlanType(planType);
    }

    public static Map<String, Object> buildRankingCardPayload(DishProfitAnswerPlan plan, AiRunState state) {
        if (plan == null || !isRankingPlanType(plan.getPlanType())) {
            return null;
        }
        if (DishProfitRankingSalesEvidenceSupport.isNoDataRankingPlan(plan)) {
            return buildEmptyRankingCardPayload(plan, state);
        }
        List<Map<String, Object>> rankingRows = mergeRankingRows(plan);
        if (rankingRows.isEmpty()) {
            return buildEmptyRankingCardPayload(plan, state);
        }

        String startDate = state != null ? blankToNull(state.getStatStartDate()) : null;
        String endDate = state != null ? blankToNull(state.getStatEndDate()) : null;
        String timeDisplayText = resolveTimeDisplayText(plan, state, startDate, endDate);
        RankingMeta meta = resolveRankingMeta(plan);

        Map<String, Object> payload = new LinkedHashMap<>();
        putOptional(payload, "startDate", startDate);
        putOptional(payload, "endDate", endDate);
        putOptional(payload, "timeLabel", plan.getTimeLabel());
        putOptional(payload, "timeDisplayText", timeDisplayText);
        putOptional(payload, "scopeLabel", plan.getScopeLabel());
        payload.put("rankingType", meta.rankingType());
        payload.put("metricLabel", meta.metricLabel());
        putOptional(payload, PAYLOAD_RANKING_META_WARNING, meta.warning());
        putOptional(payload, "sortKey", plan.getSortKey());
        putOptional(payload, "sortDirection", plan.getSortDirection());
        payload.put("rows", copyRankingRows(rankingRows));
        payload.put("status", PAYLOAD_STATUS_OK);
        payload.put("source", SOURCE);

        Map<String, Object> card = new LinkedHashMap<>();
        card.put("cardType", DishProfitAnswerPlan.CARD_TYPE_DISH_PROFIT_RANKING);
        card.put("title", composeRankingTitle(plan, timeDisplayText, meta));
        card.put("subtitle", meta.subtitle());
        card.put("chartType", CHART_TYPE_TABLE);
        card.put("payload", payload);
        Map<String, Object> source = new LinkedHashMap<>();
        source.put("answerPlan", SOURCE);
        source.put("dataRef", DATA_REF_FOCUS_AND_SECONDARY);
        card.put("source", source);
        return card;
    }

    /** 无销量/利润证据时仍投影 EMPTY 态排行卡，供前端展示明确空态。 */
    public static Map<String, Object> buildEmptyRankingCardPayload(
            DishProfitAnswerPlan plan, AiRunState state) {
        if (plan == null) {
            return null;
        }

        String startDate = state != null ? blankToNull(state.getStatStartDate()) : null;
        String endDate = state != null ? blankToNull(state.getStatEndDate()) : null;
        String timeDisplayText = resolveTimeDisplayText(plan, state, startDate, endDate);
        DishProfitAnswerPlan metaSource = planForRankingMeta(plan);
        RankingMeta meta = resolveRankingMeta(metaSource);

        Map<String, Object> payload = new LinkedHashMap<>();
        putOptional(payload, "startDate", startDate);
        putOptional(payload, "endDate", endDate);
        putOptional(payload, "timeLabel", plan.getTimeLabel());
        putOptional(payload, "timeDisplayText", timeDisplayText);
        putOptional(payload, "scopeLabel", plan.getScopeLabel());
        payload.put("rankingType", meta.rankingType());
        payload.put("metricLabel", meta.metricLabel());
        putOptional(payload, PAYLOAD_RANKING_META_WARNING, meta.warning());
        payload.put("rows", List.of());
        payload.put("status", PAYLOAD_STATUS_EMPTY);
        payload.put("message", DishProfitRankingSalesEvidenceSupport.EMPTY_RANKING_MESSAGE);
        payload.put("source", SOURCE);

        Map<String, Object> card = new LinkedHashMap<>();
        card.put("cardType", DishProfitAnswerPlan.CARD_TYPE_DISH_PROFIT_RANKING);
        card.put("title", composeRankingTitle(plan, timeDisplayText, meta));
        card.put("subtitle", meta.subtitle());
        card.put("chartType", CHART_TYPE_TABLE);
        card.put("payload", payload);
        Map<String, Object> source = new LinkedHashMap<>();
        source.put("answerPlan", SOURCE);
        source.put("dataRef", DATA_REF_FOCUS_AND_SECONDARY);
        card.put("source", source);
        return card;
    }

    private static DishProfitAnswerPlan planForRankingMeta(DishProfitAnswerPlan plan) {
        String requested = DishProfitRankingSalesEvidenceSupport.resolveRequestedRankingPlanType(plan);
        if (!StringUtils.hasText(requested)) {
            return plan;
        }
        return DishProfitAnswerPlan.builder()
                .planType(requested)
                .timeLabel(plan.getTimeLabel())
                .scopeLabel(plan.getScopeLabel())
                .sortKey(plan.getSortKey())
                .sortDirection(plan.getSortDirection())
                .build();
    }

    public static List<Map<String, Object>> mergeRankingRows(DishProfitAnswerPlan plan) {
        List<Map<String, Object>> out = new ArrayList<>();
        if (plan == null) {
            return out;
        }
        appendRows(out, plan.getFocusRows());
        appendRows(out, plan.getSecondaryRows());
        return out;
    }

    private static void appendRows(List<Map<String, Object>> target, List<Map<String, Object>> rows) {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        for (Map<String, Object> row : rows) {
            if (row != null && !row.isEmpty()) {
                target.add(row);
            }
        }
    }

    private static String resolveTimeDisplayText(
            DishProfitAnswerPlan plan, AiRunState state, String startDate, String endDate) {
        if (state != null) {
            AiTimeWindowTextFormatter.UserPhrases tw = AiTimeWindowTextFormatter.forAnswer(state);
            if (tw != null && StringUtils.hasText(tw.getDisplayTimeRange())) {
                String display = tw.getDisplayTimeRange().trim();
                if (!"该统计区间".equals(display)) {
                    return display;
                }
            }
        }
        String fromPlan = blankToNull(plan.getTimeLabel());
        if (StringUtils.hasText(fromPlan)) {
            return fromPlan;
        }
        if (StringUtils.hasText(startDate) && StringUtils.hasText(endDate)) {
            return startDate + " 至 " + endDate;
        }
        return null;
    }

    private static String composeRankingTitle(
            DishProfitAnswerPlan plan, String timeDisplayText, RankingMeta meta) {
        String time = firstNonBlank(plan.getTimeLabel(), timeDisplayText);
        String suffix = meta.titleSuffix();
        if (StringUtils.hasText(time)) {
            return time.trim() + "·" + suffix;
        }
        return suffix;
    }

    private record RankingMeta(
            String rankingType, String metricLabel, String subtitle, String titleSuffix, String warning) {}

    /**
     * 仅按明确 {@link DishProfitAnswerPlan#planType} 映射展示字段；不读 sortDirection / sortKey。
     */
    private static RankingMeta resolveRankingMeta(DishProfitAnswerPlan plan) {
        if (plan == null) {
            return unknownRankingMeta("(null plan)");
        }
        String planType = blankToNull(plan.getPlanType());
        if (!StringUtils.hasText(planType)) {
            return unknownRankingMeta("(blank planType)");
        }
        return switch (planType) {
            case DishProfitAnswerPlan.TYPE_DISH_HIGHEST_MARGIN ->
                    new RankingMeta(
                            DishProfitAnswerPlan.RANKING_TYPE_MARGIN_HIGH,
                            "毛利率",
                            "按毛利率排序",
                            "菜品毛利率排行",
                            null);
            case DishProfitAnswerPlan.TYPE_DISH_LOWEST_MARGIN ->
                    new RankingMeta(
                            DishProfitAnswerPlan.RANKING_TYPE_MARGIN_LOW,
                            "毛利率",
                            "按毛利率从低到高",
                            "菜品毛利率排行",
                            null);
            case DishProfitAnswerPlan.TYPE_DISH_HIGHEST_PROFIT_AMOUNT ->
                    new RankingMeta(
                            DishProfitAnswerPlan.RANKING_TYPE_PROFIT_AMOUNT_HIGH,
                            "利润额",
                            "按利润额（元）排序",
                            "菜品利润额排行",
                            null);
            case DishProfitAnswerPlan.TYPE_DISH_LOWEST_PROFIT_AMOUNT ->
                    new RankingMeta(
                            DishProfitAnswerPlan.RANKING_TYPE_PROFIT_AMOUNT_LOW,
                            "利润额",
                            "按利润额（元）从低到高",
                            "菜品利润额排行",
                            null);
            case DishProfitAnswerPlan.TYPE_DISH_HIGHEST_ACTUAL_COST ->
                    new RankingMeta(
                            DishProfitAnswerPlan.RANKING_TYPE_ACTUAL_COST_HIGH,
                            "实际成本",
                            "按实际成本排序",
                            "菜品实际成本排行",
                            null);
            case DishProfitAnswerPlan.TYPE_DISH_COST_GAP ->
                    new RankingMeta(
                            DishProfitAnswerPlan.RANKING_TYPE_COST_GAP_HIGH,
                            "成本差额",
                            "按实际与理论成本差额排序",
                            "菜品成本差额排行",
                            null);
            default -> unknownRankingMeta(planType);
        };
    }

    private static RankingMeta unknownRankingMeta(String planTypeForLog) {
        return new RankingMeta(
                DishProfitAnswerPlan.RANKING_TYPE_UNKNOWN,
                "",
                "",
                "菜品排行",
                "unresolved ranking planType="
                        + (StringUtils.hasText(planTypeForLog) ? planTypeForLog.trim() : "(blank)")
                        + "; Card layer does not infer metric from sortDirection");
    }

    private static List<Map<String, Object>> copyRankingRows(List<Map<String, Object>> rows) {
        List<Map<String, Object>> out = new ArrayList<>(rows.size());
        int rank = 1;
        for (Map<String, Object> row : rows) {
            if (row == null || row.isEmpty()) {
                continue;
            }
            Map<String, Object> item = new LinkedHashMap<>();
            String rankText = firstNonBlank(row.get("rank"), row.get("ranking"));
            if (!StringUtils.hasText(rankText)) {
                rankText = Integer.toString(rank);
            }
            putOptional(item, "rank", rankText);
            putOptional(item, "dishName", row.get("dishName"));
            putOptional(item, "dishId", firstNonBlank(row.get("dishId"), row.get("foodId")));
            putOptional(item, "salesQuantity", firstNonBlank(row.get("salesQuantity"), row.get("soldPortionsTotal")));
            putOptional(
                    item,
                    "listPriceRevenue",
                    firstNonBlank(row.get("listPriceRevenue"), row.get("salesAmount")));
            putOptional(
                    item,
                    "theoryCostAmount",
                    firstNonBlank(row.get("theoryCostAmount"), row.get("standardCostAmount")));
            putOptional(
                    item,
                    "actualCostAmount",
                    firstNonBlank(
                            row.get("actualCostAmount"),
                            row.get("totalActualCostAmount123"),
                            row.get("actualCostTotalAmount123")));
            putOptional(
                    item,
                    "blendedGrossMarginRateOnListPrice",
                    firstNonBlank(
                            row.get("blendedGrossMarginRateOnListPrice"),
                            row.get("grossMarginRate")));
            putOptional(item, "grossProfitAmount", row.get("grossProfitAmount"));
            if (!item.isEmpty()) {
                out.add(item);
                rank++;
            }
        }
        return out.isEmpty() ? List.of() : out;
    }

    private static void putOptional(Map<String, Object> target, String key, Object value) {
        if (value == null) {
            return;
        }
        String s = value.toString().trim();
        if (StringUtils.hasText(s)) {
            target.put(key, s);
        }
    }

    private static String firstNonBlank(Object... candidates) {
        if (candidates == null) {
            return null;
        }
        for (Object candidate : candidates) {
            String s = blankToNull(candidate);
            if (StringUtils.hasText(s)) {
                return s;
            }
        }
        return null;
    }

    private static String blankToNull(Object raw) {
        if (raw == null) {
            return null;
        }
        String t = raw.toString().trim();
        return StringUtils.hasText(t) ? t : null;
    }
}
