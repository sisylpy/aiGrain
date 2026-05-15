package com.nongxinle.ai.graph.business;

import com.nongxinle.ai.context.AiResolvedQueryContext;
import com.nongxinle.ai.context.AiResolvedQueryIntent;
import com.nongxinle.ai.conversation.AiQuerySemanticLexicon;
import com.nongxinle.ai.core.AiRunState;
import com.nongxinle.ai.dto.business.AiDishProfitOverviewResult;
import com.nongxinle.ai.dto.business.DishSalesAnswerPlan;
import com.nongxinle.ai.semantic.AiQuerySemanticParseResult;
import com.nongxinle.ai.tool.business.AiBusinessToolIds;
import com.nongxinle.ai.util.AiTimeWindowTextFormatter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 挂载 {@link DishSalesAnswerPlan}：仅 {@link AiResolvedQueryIntent#DISH_SALES_QUERY} /
 * {@link AiResolvedQueryIntent#PATH_DISH_SALES_QUERY}；读 {@link AiBusinessToolIds#DISH_PROFIT_ANALYSIS} 快照中的
 * {@code dishRows}（Phase 1 复用毛利 Tool）。
 */
public final class DishSalesAnswerPlanBuilder {

    private static final Logger log = LoggerFactory.getLogger(DishSalesAnswerPlanBuilder.class);

    private static final String SOURCE_TOOL = "dish_profit_analysis";

    private DishSalesAnswerPlanBuilder() {
    }

    /** 在 {@link AiDishProfitOverviewResult} 已算出但尚未写回 {@link AiRunState} 时，传入本轮 snapshot。 */
    public static void attachIfApplicable(AiRunState state, AiDishProfitOverviewResult overview) {
        if (state == null) {
            return;
        }
        state.setDishSalesAnswerPlan(null);
        AiResolvedQueryContext rq = state.getResolvedQueryContext();
        if (rq == null) {
            return;
        }
        String effIntent = blankToNull(rq.getEffectiveIntentCode());
        String effPath = blankToNull(rq.getEffectivePathCode());
        boolean dishSales =
                AiResolvedQueryIntent.DISH_SALES_QUERY.equals(effIntent)
                        || AiResolvedQueryIntent.PATH_DISH_SALES_QUERY.equals(effPath);
        if (!dishSales) {
            return;
        }

        List<Map<String, Object>> dishRows = extractDishRows(state);
        int dishRowsCount = dishRows.size();
        LinkedHashMap<String, Object> debug = new LinkedHashMap<>();
        debug.put("sourceTool", SOURCE_TOOL);
        boolean toolOk = toolEnvelopeSuccess(state);
        debug.put("toolSuccess", toolOk);
        debug.put("dishRowsCount", dishRowsCount);
        debug.put("rankedRowCount", 0);

        if (!toolOk) {
            debug.put("earlyReturnReason", "tool_envelope_missing_or_unsuccessful");
            log.warn(
                    "[DishSalesAnswerPlan] early exit: dish_profit_analysis envelope success=false or missing. runId={}",
                    state.getRunId());
            attachEarlyExitPlan(state, overview, debug, List.of("菜品毛利工具未成功返回，无法生成销量/销售额排行计划。"));
            return;
        }

        var qi = rq.getQueryIntent();
        DishSalesWireResolution wres = resolveDishSalesWire(rq, qi);
        debug.put("rawStructuredIntentDetail", wres.rawStructuredIntentDetail());
        debug.put("fallbackMetricRankingType", wres.fallbackMetricRankingType());
        debug.put("resolvedDishSalesWire", wres.resolvedCanonicalWire());

        String wire = wres.resolvedCanonicalWire();
        if (!StringUtils.hasText(wire)) {
            debug.put("earlyReturnReason", "no_wire_from_structured_or_metric_ranking_type");
            log.warn(
                    "[DishSalesAnswerPlan] early exit: no wire after structuredIntentDetail + metric fallback. runId={}",
                    state.getRunId());
            attachEarlyExitPlan(
                    state,
                    overview,
                    debug,
                    List.of("未从 structuredIntentDetail 或语义 metric.rankingType 解析到有效的菜品销量排行口径。"));
            return;
        }

        String planType;
        String metricType;
        String sortKey;
        if (AiQuerySemanticLexicon.STRUCTURED_DISH_SALES_COUNT_RANKING_HIGH.equals(wire)) {
            planType = DishSalesAnswerPlan.TYPE_DISH_SALES_COUNT_RANKING_HIGH;
            metricType = DishSalesAnswerPlan.METRIC_COUNT_HIGH;
            sortKey = "soldPortionsTotal";
        } else if (AiQuerySemanticLexicon.STRUCTURED_DISH_SALES_AMOUNT_RANKING_HIGH.equals(wire)) {
            planType = DishSalesAnswerPlan.TYPE_DISH_SALES_AMOUNT_RANKING_HIGH;
            metricType = DishSalesAnswerPlan.METRIC_AMOUNT_HIGH;
            sortKey = "listPriceRevenue";
        } else if (AiQuerySemanticLexicon.STRUCTURED_DISH_SALES_COUNT_RANKING_LOW.equals(wire)) {
            planType = DishSalesAnswerPlan.TYPE_DISH_SALES_COUNT_RANKING_LOW;
            metricType = DishSalesAnswerPlan.METRIC_COUNT_LOW;
            sortKey = "soldPortionsTotal";
        } else {
            debug.put("earlyReturnReason", "wire_not_accepted_dish_sales_ranking");
            log.warn(
                    "[DishSalesAnswerPlan] early exit: canonical wire is not a dish_sales ranking wire: {} runId={}",
                    wire,
                    state.getRunId());
            attachEarlyExitPlan(
                    state,
                    overview,
                    debug,
                    List.of("当前子意图不是菜品销量/销售额排行（count_high / amount_high / count_low）。"));
            return;
        }

        String scopeLabel = scopeLabel(overview);
        String timeLabel = timeLabel(state, overview);

        debug.put("rowCount", dishRowsCount);
        debug.put("structuredIntentDetailWire", wire);
        debug.put("sortKey", sortKey);

        List<String> limitations = new ArrayList<>();
        if (dishRows.isEmpty()) {
            limitations.add("本轮菜品行为空，无法在现有数据上生成销量/销售额排行。");
            debug.put("rankedRowCount", 0);
            debug.put("earlyReturnReason", null);
            LinkedHashMap<String, Object> cov = new LinkedHashMap<>();
            cov.put("rowCount", 0);
            cov.put("rankedRowCount", 0);
            DishSalesAnswerPlan empty =
                    DishSalesAnswerPlan.builder()
                            .planType(planType)
                            .metricType(metricType)
                            .scopeLabel(scopeLabel)
                            .timeLabel(timeLabel)
                            .rankingRows(List.of())
                            .dataCoverage(cov)
                            .limitations(limitations)
                            .summary(null)
                            .debug(debug)
                            .build();
            state.setDishSalesAnswerPlan(empty);
            return;
        }

        List<ScoredRow> scored = new ArrayList<>(dishRows.size());
        for (Map<String, Object> row : dishRows) {
            if (row == null) {
                continue;
            }
            BigDecimal qty = coerceDecimalNullable(row.get("soldPortionsTotal"));
            BigDecimal amt = coerceDecimalNullable(row.get("listPriceRevenue"));
            boolean qtyValid = qty != null;
            scored.add(new ScoredRow(row, qty, amt, qtyValid));
        }

        Comparator<ScoredRow> cmp;
        if (DishSalesAnswerPlan.METRIC_COUNT_HIGH.equals(metricType)) {
            cmp =
                    Comparator.comparing(ScoredRow::qtyValid)
                            .reversed()
                            .thenComparing(ScoredRow::qty, Comparator.nullsLast(Comparator.reverseOrder()));
        } else if (DishSalesAnswerPlan.METRIC_AMOUNT_HIGH.equals(metricType)) {
            cmp =
                    Comparator.comparing(ScoredRow::qtyValidForAmountSort)
                            .reversed()
                            .thenComparing(ScoredRow::amt, Comparator.nullsLast(Comparator.reverseOrder()));
        } else {
            cmp =
                    Comparator.comparing(ScoredRow::qtyValid)
                            .reversed()
                            .thenComparing(ScoredRow::qty, Comparator.nullsLast(Comparator.naturalOrder()));
        }
        scored.sort(cmp);

        List<Map<String, Object>> rankingRows = new ArrayList<>();
        int rank = 1;
        for (ScoredRow sr : scored) {
            LinkedHashMap<String, Object> m = new LinkedHashMap<>();
            m.put("rank", rank++);
            m.put("dishName", stringify(sr.row().get("dishName")));
            m.put("soldPortionsTotal", stringify(sr.row().get("soldPortionsTotal")));
            m.put("listPriceRevenue", stringify(sr.row().get("listPriceRevenue")));
            m.put("grossMarginRate", formatGrossMarginRate(sr.row()));
            m.put("actualCostAmount", stringify(sr.row().get("actualCostAmount")));
            m.put("theoryCostAmount", stringify(sr.row().get("theoryCostAmount")));
            Object fid = sr.row().get("foodId");
            if (fid != null && StringUtils.hasText(fid.toString())) {
                m.put("foodId", fid.toString().trim());
            }
            if (!sr.qtyValid() && DishSalesAnswerPlan.METRIC_COUNT_LOW.equals(metricType)) {
                m.put("note", "销量口径缺失或非数字，不参与「最低销量」解读");
            } else if (!sr.qtyValid()
                    && (DishSalesAnswerPlan.METRIC_COUNT_HIGH.equals(metricType))) {
                m.put("note", "销量口径缺失或非数字，排在末位");
            } else if (!sr.qtyValidForAmountSort()
                    && DishSalesAnswerPlan.METRIC_AMOUNT_HIGH.equals(metricType)) {
                m.put("note", "销售额口径缺失或非数字，排在末位");
            }
            rankingRows.add(m);
        }

        debug.put("rankedRowCount", rankingRows.size());
        debug.put("earlyReturnReason", null);

        LinkedHashMap<String, Object> cov = new LinkedHashMap<>();
        cov.put("rowCount", dishRows.size());
        cov.put("rankedRowCount", rankingRows.size());

        String summary = buildSummary(metricType, rankingRows);

        DishSalesAnswerPlan plan =
                DishSalesAnswerPlan.builder()
                        .planType(planType)
                        .metricType(metricType)
                        .scopeLabel(scopeLabel)
                        .timeLabel(timeLabel)
                        .rankingRows(rankingRows)
                        .dataCoverage(cov)
                        .limitations(limitations)
                        .summary(summary)
                        .debug(debug)
                        .build();
        state.setDishSalesAnswerPlan(plan);
    }

    public static void attachIfApplicable(AiRunState state) {
        attachIfApplicable(state, state != null ? state.getDishProfitOverviewResult() : null);
    }

    private record ScoredRow(Map<String, Object> row, BigDecimal qty, BigDecimal amt, boolean qtyValid) {
        boolean qtyValidForAmountSort() {
            return amt != null;
        }
    }

    private static String buildSummary(String metricType, List<Map<String, Object>> rankingRows) {
        if (rankingRows == null || rankingRows.isEmpty()) {
            return null;
        }
        Map<String, Object> top = rankingRows.get(0);
        if (top == null) {
            return null;
        }
        String name = nzString(top.get("dishName"));
        if (!StringUtils.hasText(name)) {
            name = "（未命名菜品）";
        }
        String qty = nzString(top.get("soldPortionsTotal"));
        String rev = nzString(top.get("listPriceRevenue"));
        if (DishSalesAnswerPlan.METRIC_AMOUNT_HIGH.equals(metricType)) {
            return String.format(Locale.CHINA, "当前范围内销售额最高的菜品是 %s，销售额 %s，销量 %s 份。", name, nzOrDash(rev), nzOrDash(qty));
        }
        if (DishSalesAnswerPlan.METRIC_COUNT_LOW.equals(metricType)) {
            return String.format(Locale.CHINA, "当前范围内销量最低的菜品是 %s，销量 %s 份，销售额 %s。", name, nzOrDash(qty), nzOrDash(rev));
        }
        return String.format(Locale.CHINA, "当前范围内销量最高的菜品是 %s，销量 %s 份，销售额 %s。", name, nzOrDash(qty), nzOrDash(rev));
    }

    private static String nzOrDash(String s) {
        return StringUtils.hasText(s) ? s : "—";
    }

    private static String nzString(Object v) {
        if (v == null) {
            return "";
        }
        return v.toString().trim();
    }

    private static String formatGrossMarginRate(Map<String, Object> row) {
        if (row == null) {
            return "";
        }
        Object b = row.get("blendedGrossMarginRateOnListPrice");
        String s = stringify(b);
        if (!StringUtils.hasText(s)) {
            s = stringify(row.get("grossMarginRateOnListPrice"));
        }
        return s;
    }

    private static String stringify(Object v) {
        return v == null ? "" : v.toString().trim();
    }

    /**
     * 与 {@link com.nongxinle.utils.GbDepartmentGoodsStockReduceSupport#coerceDecimal} 区分：空串/null/非数字为 null，
     * 不把缺失销量当成 0（避免「最低销量」误判）。
     */
    private static BigDecimal coerceDecimalNullable(Object v) {
        if (v == null) {
            return null;
        }
        if (v instanceof BigDecimal bd) {
            return bd;
        }
        if (v instanceof Number n) {
            try {
                return new BigDecimal(n.toString());
            } catch (Exception e) {
                return null;
            }
        }
        String s = v.toString();
        if (s == null || (s = s.trim()).isEmpty()) {
            return null;
        }
        s = s.replace('\uFF0C', '.').replace('，', '.');
        try {
            return new BigDecimal(s);
        } catch (Exception e) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> extractDishRows(AiRunState state) {
        Object env = state.getToolResults() == null ? null : state.getToolResults().get(AiBusinessToolIds.DISH_PROFIT_ANALYSIS);
        if (!(env instanceof Map<?, ?> tm)) {
            return List.of();
        }
        Object data = tm.get("data");
        if (!(data instanceof Map<?, ?> dm)) {
            return List.of();
        }
        Object raw = dm.get("dishRows");
        if (!(raw instanceof List<?> list)) {
            return List.of();
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Map) {
                out.add((Map<String, Object>) item);
            }
        }
        return out;
    }

    private static boolean toolEnvelopeSuccess(AiRunState state) {
        Object env = state.getToolResults() == null ? null : state.getToolResults().get(AiBusinessToolIds.DISH_PROFIT_ANALYSIS);
        if (!(env instanceof Map<?, ?> m)) {
            return false;
        }
        return Boolean.TRUE.equals(m.get("success"));
    }

    private static String scopeLabel(AiDishProfitOverviewResult out) {
        if (out == null) {
            return "当前查询范围";
        }
        String n = nz(out.getScopeName());
        if (StringUtils.hasText(n)) {
            return n;
        }
        String b = nz(out.getQueryScopeBanner());
        return StringUtils.hasText(b) ? b : "当前查询范围";
    }

    private static String timeLabel(AiRunState state, AiDishProfitOverviewResult out) {
        AiTimeWindowTextFormatter.UserPhrases tw = AiTimeWindowTextFormatter.forAnswer(state);
        if (tw != null && StringUtils.hasText(tw.getDisplayTimeRange())) {
            return tw.getDisplayTimeRange().trim();
        }
        if (out == null) {
            return "";
        }
        String a = nz(out.getStatStartDate());
        String b = nz(out.getStatEndDate());
        if (StringUtils.hasText(a) && StringUtils.hasText(b)) {
            return a + " 至 " + b;
        }
        return StringUtils.hasText(a) ? a : b;
    }

    private static String nz(String s) {
        return s == null ? "" : s.trim();
    }

    private static String blankToNull(String s) {
        if (!StringUtils.hasText(s)) {
            return null;
        }
        return s.trim();
    }

    /**
     * rawStructuredIntentDetail：queryIntent.structuredIntentDetail 原文；fallbackMetricRankingType：语义 metric.rankingType；
     * resolvedCanonicalWire：经 {@link AiQuerySemanticLexicon#canonicalStructuredIntentDetailWire} 且仅限三种菜品销量/额排行 wire。
     */
    private record DishSalesWireResolution(
            String rawStructuredIntentDetail, String fallbackMetricRankingType, String resolvedCanonicalWire) {}

    private static DishSalesWireResolution resolveDishSalesWire(AiResolvedQueryContext rq, AiResolvedQueryIntent qi) {
        String raw = null;
        if (qi != null && StringUtils.hasText(qi.getStructuredIntentDetail())) {
            raw = qi.getStructuredIntentDetail().trim();
        }
        String fallbackRanking = null;
        if (rq != null && rq.getQuerySemanticParse() != null && rq.getQuerySemanticParse().getMetric() != null) {
            String rt = rq.getQuerySemanticParse().getMetric().getRankingType();
            if (StringUtils.hasText(rt)) {
                fallbackRanking = rt.trim();
            }
        }
        String wire = null;
        if (StringUtils.hasText(raw)) {
            wire = AiQuerySemanticLexicon.canonicalStructuredIntentDetailWire(raw);
        } else if (StringUtils.hasText(fallbackRanking)) {
            wire = AiQuerySemanticLexicon.canonicalStructuredIntentDetailWire(fallbackRanking);
        }
        if (!acceptedDishSalesRankingWire(wire)) {
            wire = null;
        }
        return new DishSalesWireResolution(raw, fallbackRanking, wire);
    }

    private static boolean acceptedDishSalesRankingWire(String wire) {
        if (!StringUtils.hasText(wire)) {
            return false;
        }
        return AiQuerySemanticLexicon.STRUCTURED_DISH_SALES_COUNT_RANKING_HIGH.equals(wire)
                || AiQuerySemanticLexicon.STRUCTURED_DISH_SALES_AMOUNT_RANKING_HIGH.equals(wire)
                || AiQuerySemanticLexicon.STRUCTURED_DISH_SALES_COUNT_RANKING_LOW.equals(wire);
    }

    private static void attachEarlyExitPlan(
            AiRunState state,
            AiDishProfitOverviewResult overview,
            LinkedHashMap<String, Object> debug,
            List<String> limitations) {
        Object drc = debug.get("dishRowsCount");
        int rowCount = drc instanceof Number n ? n.intValue() : 0;
        LinkedHashMap<String, Object> cov = new LinkedHashMap<>();
        cov.put("rowCount", rowCount);
        cov.put("rankedRowCount", 0);
        DishSalesAnswerPlan plan =
                DishSalesAnswerPlan.builder()
                        .planType(null)
                        .metricType(null)
                        .scopeLabel(scopeLabel(overview))
                        .timeLabel(timeLabel(state, overview))
                        .rankingRows(List.of())
                        .dataCoverage(cov)
                        .limitations(new ArrayList<>(limitations))
                        .summary(null)
                        .debug(debug)
                        .build();
        state.setDishSalesAnswerPlan(plan);
    }
}
