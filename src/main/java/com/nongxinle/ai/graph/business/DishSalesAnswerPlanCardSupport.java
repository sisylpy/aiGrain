package com.nongxinle.ai.graph.business;

import com.nongxinle.ai.core.AiRunState;
import com.nongxinle.ai.dto.business.DishSalesAnswerPlan;
import com.nongxinle.ai.util.AiTimeWindowTextFormatter;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 从已定稿 {@link DishSalesAnswerPlan} 生成展示卡；不查库、不重算排行。
 * 单菜 → {@link DishSalesAnswerPlan#CARD_TYPE_DISH_SALES}；
 * 排行 → {@link DishSalesAnswerPlan#CARD_TYPE_DISH_SALES_RANKING}。
 */
public final class DishSalesAnswerPlanCardSupport {

    private static final String SOURCE = "dishSalesAnswerPlan";
    private static final String DATA_REF_RANKING_ROWS = "rankingRows";
    private static final String CHART_TYPE_TABLE = "TABLE";
    private static final String PAYLOAD_STATUS_EMPTY = "EMPTY";
    private static final String PAYLOAD_STATUS_OK = "OK";

    private DishSalesAnswerPlanCardSupport() {}

    public static Map<String, Object> buildCardPayload(DishSalesAnswerPlan plan) {
        return buildCardPayload(plan, null);
    }

    public static Map<String, Object> buildCardPayload(DishSalesAnswerPlan plan, AiRunState state) {
        if (plan == null) {
            return null;
        }
        String planType = blankToNull(plan.getPlanType());
        if (!StringUtils.hasText(planType)) {
            return null;
        }
        if (DishSalesAnswerPlan.TYPE_DISH_SALES_SINGLE_DISH.equals(planType)) {
            return buildSingleDishCardPayload(plan);
        }
        if (DishSalesRankingSalesEvidenceSupport.isNoDataRankingPlan(plan)) {
            return buildEmptyRankingCardPayload(plan, state);
        }
        if (isRankingPlanType(planType)) {
            return buildRankingCardPayload(plan, state);
        }
        return null;
    }

    public static boolean isRankingPlanType(String planType) {
        if (!StringUtils.hasText(planType)) {
            return false;
        }
        String pt = planType.trim();
        return DishSalesAnswerPlan.TYPE_DISH_SALES_COUNT_RANKING_HIGH.equals(pt)
                || DishSalesAnswerPlan.TYPE_DISH_SALES_AMOUNT_RANKING_HIGH.equals(pt)
                || DishSalesAnswerPlan.TYPE_DISH_SALES_COUNT_RANKING_LOW.equals(pt);
    }

    /**
     * 单菜销售合同且已命中菜品行时返回卡片；否则 null。
     */
    public static Map<String, Object> buildSingleDishCardPayload(DishSalesAnswerPlan plan) {
        if (plan == null
                || !DishSalesAnswerPlan.TYPE_DISH_SALES_SINGLE_DISH.equals(plan.getPlanType())) {
            return null;
        }
        List<Map<String, Object>> rows = plan.getRankingRows();
        if (rows == null || rows.isEmpty()) {
            return null;
        }
        Map<String, Object> row = rows.get(0);
        if (row == null || row.isEmpty()) {
            return null;
        }
        String dishName = blankToNull(row.get("dishName"));
        if (!StringUtils.hasText(dishName)) {
            return null;
        }

        String soldPortions = firstNonBlank(row.get("soldPortionsTotal"), row.get("salesPortions"));
        String salesAmount = firstNonBlank(row.get("actualRevenue"), row.get("salesAmount"));
        String salesUnitPrice =
                firstNonBlank(
                        row.get("salesUnitPrice"),
                        row.get("listPrice"),
                        deriveUnitPrice(salesAmount, soldPortions));

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("dishName", dishName);
        putOptional(data, "foodId", row.get("foodId"));
        putOptional(data, "dishId", row.get("foodId"));
        putOptional(data, "soldPortionsTotal", soldPortions);
        putOptional(data, "salesPortions", firstNonBlank(row.get("salesPortions"), soldPortions));
        putOptional(data, "actualRevenue", salesAmount);
        putOptional(data, "salesAmount", salesAmount);
        putOptional(data, "salesUnitPrice", salesUnitPrice);
        putOptional(data, "listPrice", row.get("listPrice"));
        putOptional(data, "grossMarginRate", row.get("grossMarginRate"));
        putOptional(data, "ranking", row.get("ranking"));
        if (!data.containsKey("ranking")) {
            putOptional(data, "ranking", row.get("rank"));
        }
        putOptional(data, "timeLabel", plan.getTimeLabel());
        putOptional(data, "scopeLabel", plan.getScopeLabel());
        data.put("source", SOURCE);

        Map<String, Object> card = new LinkedHashMap<>();
        card.put("cardType", DishSalesAnswerPlan.CARD_TYPE_DISH_SALES);
        card.put("data", data);
        return card;
    }

    /** 销量/销售额排行 AnswerPlan → {@link DishSalesAnswerPlan#CARD_TYPE_DISH_SALES_RANKING}。 */
    public static Map<String, Object> buildRankingCardPayload(DishSalesAnswerPlan plan, AiRunState state) {
        if (plan == null || !isRankingPlanType(plan.getPlanType())) {
            return null;
        }
        if (DishSalesRankingSalesEvidenceSupport.isNoDataRankingPlan(plan)) {
            return buildEmptyRankingCardPayload(plan, state);
        }
        List<Map<String, Object>> rows = plan.getRankingRows();
        if (rows == null || rows.isEmpty()) {
            return null;
        }
        if (!DishSalesRankingSalesEvidenceSupport.hasRankingEvidenceForMetric(
                plan.getMetricType(), rows)) {
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
        payload.put("rows", copyRankingRows(rows));
        putOptional(payload, "summary", plan.getSummary());
        payload.put("status", PAYLOAD_STATUS_OK);
        payload.put("source", SOURCE);

        Map<String, Object> card = new LinkedHashMap<>();
        card.put("cardType", DishSalesAnswerPlan.CARD_TYPE_DISH_SALES_RANKING);
        card.put("title", composeRankingTitle(plan, timeDisplayText));
        card.put("subtitle", meta.subtitle());
        card.put("chartType", CHART_TYPE_TABLE);
        card.put("payload", payload);
        Map<String, Object> source = new LinkedHashMap<>();
        source.put("answerPlan", SOURCE);
        source.put("dataRef", DATA_REF_RANKING_ROWS);
        card.put("source", source);
        return card;
    }

    /** 无销量/销售额证据时仍投影 EMPTY 态排行卡，供前端展示明确空态。 */
    public static Map<String, Object> buildEmptyRankingCardPayload(
            DishSalesAnswerPlan plan, AiRunState state) {
        if (plan == null) {
            return null;
        }

        String startDate = state != null ? blankToNull(state.getStatStartDate()) : null;
        String endDate = state != null ? blankToNull(state.getStatEndDate()) : null;
        String timeDisplayText = resolveTimeDisplayText(plan, state, startDate, endDate);
        DishSalesAnswerPlan metaSource = planForRankingMeta(plan);
        RankingMeta meta = resolveRankingMeta(metaSource);

        Map<String, Object> payload = new LinkedHashMap<>();
        putOptional(payload, "startDate", startDate);
        putOptional(payload, "endDate", endDate);
        putOptional(payload, "timeLabel", plan.getTimeLabel());
        putOptional(payload, "timeDisplayText", timeDisplayText);
        putOptional(payload, "scopeLabel", plan.getScopeLabel());
        payload.put("rankingType", meta.rankingType());
        payload.put("metricLabel", meta.metricLabel());
        payload.put("rows", List.of());
        payload.put("status", PAYLOAD_STATUS_EMPTY);
        payload.put(
                "message", DishSalesRankingSalesEvidenceSupport.EMPTY_RANKING_MESSAGE);
        payload.put("source", SOURCE);

        Map<String, Object> card = new LinkedHashMap<>();
        card.put("cardType", DishSalesAnswerPlan.CARD_TYPE_DISH_SALES_RANKING);
        card.put("title", composeRankingTitle(plan, timeDisplayText));
        card.put("subtitle", meta.subtitle());
        card.put("chartType", CHART_TYPE_TABLE);
        card.put("payload", payload);
        Map<String, Object> source = new LinkedHashMap<>();
        source.put("answerPlan", SOURCE);
        source.put("dataRef", DATA_REF_RANKING_ROWS);
        card.put("source", source);
        return card;
    }

    private static DishSalesAnswerPlan planForRankingMeta(DishSalesAnswerPlan plan) {
        String requested =
                DishSalesRankingSalesEvidenceSupport.resolveRequestedRankingPlanType(plan);
        if (!StringUtils.hasText(requested)) {
            return plan;
        }
        return DishSalesAnswerPlan.builder()
                .planType(requested)
                .metricType(plan.getMetricType())
                .timeLabel(plan.getTimeLabel())
                .scopeLabel(plan.getScopeLabel())
                .build();
    }

    /** @deprecated 请使用 {@link #buildRankingCardPayload(DishSalesAnswerPlan, AiRunState)}。 */
    @Deprecated
    public static Map<String, Object> buildRankingCardPayload(DishSalesAnswerPlan plan) {
        return buildRankingCardPayload(plan, null);
    }

    private static String resolveTimeDisplayText(
            DishSalesAnswerPlan plan, AiRunState state, String startDate, String endDate) {
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

    private static String composeRankingTitle(DishSalesAnswerPlan plan, String timeDisplayText) {
        String time = firstNonBlank(plan.getTimeLabel(), timeDisplayText);
        if (StringUtils.hasText(time)) {
            return time.trim() + "·菜品销量排行";
        }
        return "菜品销量排行";
    }

    private record RankingMeta(String rankingType, String metricLabel, String subtitle) {}

    private static RankingMeta resolveRankingMeta(DishSalesAnswerPlan plan) {
        String planType = plan.getPlanType() == null ? "" : plan.getPlanType().trim();
        if (DishSalesAnswerPlan.TYPE_DISH_SALES_AMOUNT_RANKING_HIGH.equals(planType)
                || DishSalesAnswerPlan.METRIC_AMOUNT_HIGH.equals(blankToNull(plan.getMetricType()))) {
            return new RankingMeta(
                    DishSalesAnswerPlan.RANKING_TYPE_AMOUNT_HIGH, "销售额", "按销售额排序");
        }
        if (DishSalesAnswerPlan.TYPE_DISH_SALES_COUNT_RANKING_LOW.equals(planType)
                || DishSalesAnswerPlan.METRIC_COUNT_LOW.equals(blankToNull(plan.getMetricType()))) {
            return new RankingMeta(
                    DishSalesAnswerPlan.RANKING_TYPE_COUNT_LOW, "销量", "按销量从低到高");
        }
        return new RankingMeta(
                DishSalesAnswerPlan.RANKING_TYPE_COUNT_HIGH, "销量", "按销量排序");
    }

    private static List<Map<String, Object>> copyRankingRows(List<Map<String, Object>> rows) {
        List<Map<String, Object>> out = new ArrayList<>(rows.size());
        for (Map<String, Object> row : rows) {
            if (row == null || row.isEmpty()) {
                continue;
            }
            Map<String, Object> item = new LinkedHashMap<>();
            putOptional(item, "rank", firstNonBlank(row.get("rank"), row.get("ranking")));
            putOptional(item, "dishName", row.get("dishName"));
            putOptional(item, "soldPortionsTotal", row.get("soldPortionsTotal"));
            putOptional(
                    item,
                    "salesAmount",
                    firstNonBlank(row.get("salesAmount"), row.get("actualRevenue")));
            putOptional(item, "foodId", row.get("foodId"));
            if (!item.isEmpty()) {
                out.add(item);
            }
        }
        return out.isEmpty() ? List.of() : out;
    }

    private static String deriveUnitPrice(String salesAmount, String soldPortions) {
        BigDecimal amount = parseDecimal(salesAmount);
        BigDecimal portions = parseDecimal(soldPortions);
        if (amount == null || portions == null || portions.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }
        return amount.divide(portions, 2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
    }

    private static BigDecimal parseDecimal(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        try {
            return new BigDecimal(raw.trim().replace('\uFF0C', '.').replace('，', '.'));
        } catch (NumberFormatException e) {
            return null;
        }
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
