package com.nongxinle.ai.semantic;

import com.nongxinle.ai.context.AiResolvedQueryIntent;
import com.nongxinle.ai.conversation.AiQuerySemanticLexicon;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * v2 菜品毛利 metric 一致性校验与低风险归一化（仅依赖 LLM 结构化字段，不读用户原文）。
 */
public final class AiQuerySemanticV2DishProfitGate {

    private static final Set<String> ALLOWED_DISH_RANKING_TYPES = Set.of(
            "dish_gross_profit_rate_ranking_low",
            "dish_gross_profit_rate_ranking_high",
            "dish_actual_cost_ranking_high",
            "dish_actual_cost_ranking_low",
            "dish_theoretical_cost_ranking_high",
            "dish_theoretical_cost_ranking_low",
            "dish_gap_ranking_max");

    /** D-8：菜品销量/销售额排行；与 {@link AiResolvedQueryIntent#DISH_SALES_QUERY} 对齐，勿并入毛利 allowed。 */
    private static final Set<String> ALLOWED_DISH_SALES_RANKING_TYPES = Set.of(
            AiQuerySemanticLexicon.STRUCTURED_DISH_SALES_COUNT_RANKING_HIGH,
            AiQuerySemanticLexicon.STRUCTURED_DISH_SALES_AMOUNT_RANKING_HIGH,
            AiQuerySemanticLexicon.STRUCTURED_DISH_SALES_COUNT_RANKING_LOW);

    private AiQuerySemanticV2DishProfitGate() {
    }

    public record SanitizeResult(
            AiQuerySemanticParseResult semantic,
            boolean adoptable,
            List<String> semanticAdoptionRejectedFields,
            String semanticAdoptionRejectedReason,
            String normalizedMetricFrom,
            String normalizedMetricTo) {

        static SanitizeResult ok(AiQuerySemanticParseResult s) {
            return new SanitizeResult(s, true, null, null, null, null);
        }

        static SanitizeResult ok(AiQuerySemanticParseResult s, String from, String to) {
            return new SanitizeResult(s, true, null, null, from, to);
        }

        static SanitizeResult reject(String reason, List<String> fields) {
            return new SanitizeResult(null, false, fields, reason, null, null);
        }
    }

    public static SanitizeResult sanitize(AiQuerySemanticParseResult in) {
        if (in == null) {
            return SanitizeResult.reject("semantic_null", null);
        }
        AiQuerySemanticParseResult cur = normalizeExplicitTimeVersusInherit(in);

        String intentU =
                cur.getIntent() != null
                        ? cur.getIntent().trim().toUpperCase(Locale.ROOT).replace('-', '_')
                        : "";
        if ("DISH_SALES_QUERY".equals(intentU)) {
            return sanitizeDishSalesRanking(cur);
        }

        if (metricHasStructuredDishSalesRanking(cur)
                && !"DISH_SALES_QUERY".equals(intentU)) {
            return SanitizeResult.reject(
                    "dish_sales_ranking_requires_dish_sales_query_intent",
                    List.of("intent", "metric.rankingType"));
        }

        if (!touchesDishProfitMetric(cur)) {
            return SanitizeResult.ok(cur);
        }

        AiQuerySemanticParseResult.MetricPart m0 = cur.getMetric();
        if (m0 == null) {
            return SanitizeResult.ok(cur);
        }

        boolean hasMention = StringUtils.hasText(cur.getMentionedDishName());
        String pmRaw = m0.getPrimaryMetric();
        boolean pmProfit = isProfitMarginPrimary(pmRaw);
        String rt0 = m0.getRankingType();
        String rtSnake = StringUtils.hasText(rt0) ? rt0.trim().toLowerCase(Locale.ROOT).replace('-', '_') : "";

        if (hasMention && (pmProfit || !StringUtils.hasText(pmRaw))) {
            if (rtSnake.startsWith("dish_actual_cost_ranking")
                    || rtSnake.startsWith("dish_theoretical_cost_ranking")) {
                String from = rtSnake;
                cur = cur.toBuilder().metric(cloneMetricClearRanking(m0)).build();
                return SanitizeResult.ok(cur, from, "cleared_single_dish_profit_margin");
            }
            // 点名单菜问毛利时不得继续携带「综合毛利率排行」rankingType（含模型误标 INHERIT_PREVIOUS）。
            if (rtSnake.startsWith("dish_gross_profit_rate_ranking")) {
                String from = rtSnake;
                cur = cur.toBuilder()
                        .metric(cloneMetricClearRanking(m0))
                        .metricAction("OVERRIDE")
                        .build();
                return SanitizeResult.ok(cur, from, "cleared_named_dish_gross_margin_vs_ranking");
            }
        }

        if (!StringUtils.hasText(rtSnake)) {
            return SanitizeResult.ok(cur);
        }

        if (!ALLOWED_DISH_RANKING_TYPES.contains(rtSnake)) {
            return SanitizeResult.reject(
                    "dish_profit_ranking_type_not_allowed:" + rtSnake,
                    List.of("metric.rankingType"));
        }

        if (pmProfit) {
            // D-7 Phase 2：已输出实际成本排行时，优先保留 rankingType；勿因 primaryMetric 误标为
            // profit_margin 而改写成毛利率排行（结构化字段-only，不读用户原文）。
            if ("dish_actual_cost_ranking_high".equals(rtSnake)
                    || "dish_actual_cost_ranking_low".equals(rtSnake)) {
                return SanitizeResult.ok(cur);
            }
            if (rtSnake.startsWith("dish_theoretical_cost_ranking")) {
                return SanitizeResult.reject(
                        "profit_margin_primary_with_theoretical_cost_ranking",
                        List.of("metric.primaryMetric", "metric.rankingType"));
            }
        }

        return SanitizeResult.ok(cur);
    }

    private static boolean metricHasStructuredDishSalesRanking(AiQuerySemanticParseResult cur) {
        if (cur == null || cur.getMetric() == null) {
            return false;
        }
        String rt = cur.getMetric().getRankingType();
        return StringUtils.hasText(rt) && AiQuerySemanticLexicon.isStructuredDishSalesDetail(rt.trim());
    }

    /**
     * {@link AiResolvedQueryIntent#DISH_SALES_QUERY} 专用：只校验销量/销售额排行 wire，不归入
     * {@link #ALLOWED_DISH_RANKING_TYPES}。
     */
    private static SanitizeResult sanitizeDishSalesRanking(AiQuerySemanticParseResult cur) {
        AiQuerySemanticParseResult.MetricPart m0 = cur.getMetric();
        if (m0 == null || !StringUtils.hasText(m0.getRankingType())) {
            return SanitizeResult.ok(cur);
        }
        String rt0 = m0.getRankingType();
        String rtSnake = rt0.trim().toLowerCase(Locale.ROOT).replace('-', '_');
        String canon = AiQuerySemanticLexicon.canonicalStructuredIntentDetailWire(rtSnake);
        String key = StringUtils.hasText(canon) ? canon : rtSnake;
        if (!ALLOWED_DISH_SALES_RANKING_TYPES.contains(key)) {
            return SanitizeResult.reject(
                    "dish_sales_ranking_type_not_allowed:" + rtSnake,
                    List.of("metric.rankingType"));
        }
        if (StringUtils.hasText(canon) && !canon.equals(rtSnake)) {
            cur = cur.toBuilder().metric(cloneMetricWithRanking(m0, canon)).build();
            return SanitizeResult.ok(cur, rtSnake, canon);
        }
        return SanitizeResult.ok(cur);
    }

    private static AiQuerySemanticParseResult normalizeExplicitTimeVersusInherit(AiQuerySemanticParseResult in) {
        AiQuerySemanticParseResult.TimePart t = in.getTime();
        if (t == null || !StringUtils.hasText(t.getTimeType())) {
            return in;
        }
        String tt = t.getTimeType().trim().toUpperCase(Locale.ROOT).replace('-', '_');
        if ("CUSTOM".equals(tt)) {
            return in;
        }
        String ta = in.getTimeAction();
        if (!StringUtils.hasText(ta)) {
            return in.toBuilder().timeAction("NEW").build();
        }
        if ("INHERIT_PREVIOUS".equals(ta.trim().toUpperCase(Locale.ROOT))) {
            return in.toBuilder().timeAction("NEW").build();
        }
        return in;
    }

    private static boolean touchesDishProfitMetric(AiQuerySemanticParseResult sem) {
        String intent = sem.getIntent();
        String iu =
                StringUtils.hasText(intent)
                        ? intent.trim().toUpperCase(Locale.ROOT).replace('-', '_')
                        : "";
        if ("DISH_SALES_QUERY".equals(iu)) {
            return false;
        }
        if (sem.getMetric() != null && StringUtils.hasText(sem.getMetric().getRankingType())) {
            String rt = sem.getMetric().getRankingType().trim();
            if (AiQuerySemanticLexicon.isStructuredDishSalesDetail(rt)) {
                return "DISH_PROFIT".equals(iu) || "DISH_MARGIN".equals(iu);
            }
            String rtl = rt.toLowerCase(Locale.ROOT);
            if (rtl.startsWith("dish_")) {
                return true;
            }
        }
        if (!StringUtils.hasText(intent)) {
            return false;
        }
        return "DISH_PROFIT".equals(iu) || "DISH_MARGIN".equals(iu);
    }

    private static boolean isProfitMarginPrimary(String pm) {
        if (!StringUtils.hasText(pm)) {
            return false;
        }
        String u = pm.trim().toUpperCase(Locale.ROOT).replace('-', '_');
        return "PROFIT_MARGIN".equals(u) || "MARGIN".equals(u) || "GROSS_MARGIN".equals(u);
    }

    private static AiQuerySemanticParseResult.MetricPart cloneMetricClearRanking(AiQuerySemanticParseResult.MetricPart m) {
        return AiQuerySemanticParseResult.MetricPart.builder()
                .primaryMetric(m.getPrimaryMetric())
                .rankingType(null)
                .purchaseSourceType(m.getPurchaseSourceType())
                .stockReduceType(m.getStockReduceType())
                .build();
    }

    private static AiQuerySemanticParseResult.MetricPart cloneMetricWithRanking(
            AiQuerySemanticParseResult.MetricPart m, String rankingType) {
        return AiQuerySemanticParseResult.MetricPart.builder()
                .primaryMetric(m.getPrimaryMetric())
                .rankingType(rankingType)
                .purchaseSourceType(m.getPurchaseSourceType())
                .stockReduceType(m.getStockReduceType())
                .build();
    }

    /** Merge 后仍为菜品专线且 metric 无 ranking 时补 wire（单菜毛利）。 */
    public static void ensureDishGrossMarginQueryWireWhenSingleDishProfit(
            AiResolvedQueryIntent qi, AiQuerySemanticParseResult sem) {
        if (qi == null || sem == null) {
            return;
        }
        if (!AiResolvedQueryIntent.PATH_DISH_PROFIT.equals(qi.getPathCode())) {
            return;
        }
        if (!StringUtils.hasText(sem.getMentionedDishName())) {
            return;
        }
        AiQuerySemanticParseResult.MetricPart m = sem.getMetric();
        if (m != null && StringUtils.hasText(m.getRankingType())) {
            return;
        }
        if (StringUtils.hasText(qi.getStructuredIntentDetail())) {
            String w = AiQuerySemanticLexicon.canonicalStructuredIntentDetailWire(qi.getStructuredIntentDetail());
            // 上一轮排行 wire + 本句点菜名且无 rankingType：升格为单菜毛利率口径，禁止继承排行榜子意图。
            if (StringUtils.hasText(w) && AiQuerySemanticLexicon.isDishProfitRankingStructuredDetail(w)) {
                qi.setStructuredIntentDetail(AiQuerySemanticLexicon.STRUCTURED_DISH_GROSS_MARGIN_QUERY);
                return;
            }
        }
        qi.setStructuredIntentDetail(AiQuerySemanticLexicon.STRUCTURED_DISH_GROSS_MARGIN_QUERY);
    }
}
