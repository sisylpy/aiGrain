package com.nongxinle.ai.graph.business;

import com.nongxinle.ai.context.AiResolvedQueryContext;
import com.nongxinle.ai.context.AiResolvedQueryIntent;
import com.nongxinle.ai.conversation.AiQuerySemanticLexicon;
import com.nongxinle.ai.core.AiRunState;
import com.nongxinle.ai.dto.business.AiDishProfitOverviewResult;
import com.nongxinle.ai.dto.business.AiResultAnchor;
import com.nongxinle.ai.dto.business.DishSalesAnswerPlan;
import com.nongxinle.ai.semantic.AiQuerySemanticParseResult;
import com.nongxinle.ai.graph.business.execution.ToolRequestContractExecutionParamSupport;
import com.nongxinle.ai.semantic.contract.SemanticContractCompletionEngine;
import com.nongxinle.ai.semantic.matrix.DishSalesSemanticCapabilityMatrix;
import com.nongxinle.ai.semantic.matrix.DishSalesSemanticCapabilityMatrixRow;
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
 * {@code dishRows}（排行）；单菜走 {@link AiBusinessToolIds#DISH_SALES_ANALYSIS_CARD}（depGeFoodBusiness 口径）。
 */
public final class DishSalesAnswerPlanBuilder {

    private static final Logger log = LoggerFactory.getLogger(DishSalesAnswerPlanBuilder.class);

    private static final String SOURCE_TOOL_SALES = "dish_sales_analysis_card";
    private static final String SOURCE_TOOL_RANKING = "dish_profit_analysis";

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
        boolean singleDishSalesTool =
                ToolRequestContractExecutionParamSupport.isDishSalesSingleDishContract(rq);
        boolean salesToolInPlan = usesDishSalesAnalysisTool(state);
        String sourceTool = salesToolInPlan ? SOURCE_TOOL_SALES : SOURCE_TOOL_RANKING;
        LinkedHashMap<String, Object> debug = new LinkedHashMap<>();
        debug.put("sourceTool", sourceTool);
        boolean toolOk =
                salesToolInPlan
                        ? toolEnvelopeSuccess(state, AiBusinessToolIds.DISH_SALES_ANALYSIS_CARD)
                        : toolEnvelopeSuccess(state, AiBusinessToolIds.DISH_PROFIT_ANALYSIS);
        debug.put("toolSuccess", toolOk);
        debug.put("dishRowsCount", dishRowsCount);
        debug.put("rankedRowCount", 0);

        if (!toolOk) {
            debug.put("earlyReturnReason", "tool_envelope_missing_or_unsuccessful");
            log.warn(
                    "[DishSalesAnswerPlan] early exit: {} envelope success=false or missing. runId={}",
                    sourceTool,
                    state.getRunId());
            attachEarlyExitPlan(state, overview, debug, List.of("菜品毛利工具未成功返回，无法生成销量/销售额排行计划。"));
            return;
        }

        DishSalesWireResolution wres = resolveDishSalesWire(rq);
        debug.put("rawStructuredIntentDetail", wres.rawStructuredIntentDetail());
        debug.put("resolvedDishSalesWire", wres.resolvedCanonicalWire());
        if (wres.wireRejectedReason() != null) {
            debug.put("wireRejectedReason", wres.wireRejectedReason());
        }

        String wire = wres.resolvedCanonicalWire();
        if (!StringUtils.hasText(wire)) {
            String er = wres.wireRejectedReason() != null ? wres.wireRejectedReason() : "no_wire_resolved";
            debug.put("earlyReturnReason", er);
            log.warn(
                    "[DishSalesAnswerPlan] early exit: no dish-sales wire (contract locked={} reject={}). runId={}",
                    SemanticContractCompletionEngine.isContractLockedParse(semantic(rq)),
                    wres.wireRejectedReason(),
                    state.getRunId());
            attachEarlyExitPlan(
                    state,
                    overview,
                    debug,
                    List.of("未从 structuredIntentDetail 或 V2 structuredIntentDetailWire 解析到有效的菜品销量口径。"));
            return;
        }

        DishSalesSemanticCapabilityMatrixRow matrixRow =
                DishSalesSemanticCapabilityMatrix.resolveMatrixRow(
                        AiResolvedQueryIntent.PATH_DISH_SALES_QUERY, wire, semantic(rq));
        String knownGap = DishSalesSemanticCapabilityMatrix.knownGapForResolvedRow(matrixRow);
        if (knownGap != null) {
            debug.put("dishSalesKnownGap", knownGap);
        }

        String planType;
        String metricType;
        String sortKey;
        String matrixPlanType = matrixRow == null ? null : matrixRow.getTargetDishSalesPlanType();
        if (StringUtils.hasText(matrixPlanType)) {
            planType = matrixPlanType;
            if (DishSalesAnswerPlan.TYPE_DISH_SALES_SINGLE_DISH.equals(planType)) {
                metricType = DishSalesAnswerPlan.METRIC_SINGLE_DISH;
                sortKey = "soldPortionsTotal";
            } else if (DishSalesAnswerPlan.TYPE_DISH_SALES_AMOUNT_RANKING_HIGH.equals(planType)) {
                metricType = DishSalesAnswerPlan.METRIC_AMOUNT_HIGH;
                sortKey = "actualRevenue";
            } else if (DishSalesAnswerPlan.TYPE_DISH_SALES_COUNT_RANKING_LOW.equals(planType)) {
                metricType = DishSalesAnswerPlan.METRIC_COUNT_LOW;
                sortKey = "soldPortionsTotal";
            } else {
                metricType = DishSalesAnswerPlan.METRIC_COUNT_HIGH;
                sortKey = "soldPortionsTotal";
            }
        } else if (AiQuerySemanticLexicon.STRUCTURED_DISH_SALES_AMOUNT_RANKING_HIGH.equals(wire)) {
            planType = DishSalesAnswerPlan.TYPE_DISH_SALES_AMOUNT_RANKING_HIGH;
            metricType = DishSalesAnswerPlan.METRIC_AMOUNT_HIGH;
            sortKey = "actualRevenue";
        } else if (AiQuerySemanticLexicon.STRUCTURED_DISH_SALES_COUNT_RANKING_LOW.equals(wire)) {
            planType = DishSalesAnswerPlan.TYPE_DISH_SALES_COUNT_RANKING_LOW;
            metricType = DishSalesAnswerPlan.METRIC_COUNT_LOW;
            sortKey = "soldPortionsTotal";
        } else if (AiQuerySemanticLexicon.STRUCTURED_DISH_SALES_COUNT_RANKING_HIGH.equals(wire)) {
            planType = DishSalesAnswerPlan.TYPE_DISH_SALES_COUNT_RANKING_HIGH;
            metricType = DishSalesAnswerPlan.METRIC_COUNT_HIGH;
            sortKey = "soldPortionsTotal";
        } else {
            debug.put("earlyReturnReason", "wire_not_accepted_dish_sales_matrix");
            log.warn(
                    "[DishSalesAnswerPlan] early exit: wire not mapped to dish_sales plan: {} runId={}",
                    wire,
                    state.getRunId());
            attachEarlyExitPlan(
                    state,
                    overview,
                    debug,
                    List.of("当前子意图不在菜品销量 Matrix P1 支持范围内。"));
            return;
        }

        String scopeLabel = scopeLabel(overview);
        String timeLabel = timeLabel(state, overview);

        debug.put("rowCount", dishRowsCount);
        debug.put("structuredIntentDetailWire", wire);
        debug.put("contractStructuredIntentDetailWire", wire);
        if (sortKey != null) {
            debug.put("sortKey", sortKey);
        }
        if (matrixRow != null) {
            putMatrixObservedDebug(debug, matrixRow, wire);
        }

        List<String> limitations = new ArrayList<>();
        appendKnownGapLimitation(limitations, knownGap);

        if (DishSalesAnswerPlan.TYPE_DISH_SALES_SINGLE_DISH.equals(planType)) {
            String dishFocus = resolveMentionedDishName(state, rq);
            if (!StringUtils.hasText(dishFocus)) {
                debug.put("earlyReturnReason", "missing_dish_anchor");
                log.warn(
                        "[DishSalesAnswerPlan] early exit: single-dish contract without DISH anchor. runId={}",
                        state.getRunId());
                state.setDishSalesAnswerPlan(null);
                return;
            }
        }

        if (knownGap != null) {
            LinkedHashMap<String, Object> cov = new LinkedHashMap<>();
            cov.put("rowCount", dishRowsCount);
            cov.put("rankedRowCount", 0);
            DishSalesAnswerPlan gapPlan =
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
            enrichDishSalesMatrixDebug(gapPlan.getDebug(), rq, planType, wire);
            state.setDishSalesAnswerPlan(gapPlan);
            return;
        }

        if (dishRows.isEmpty() && !singleDishSalesTool && !salesToolInPlan) {
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
            enrichDishSalesMatrixDebug(empty.getDebug(), rq, planType, wire);
            state.setDishSalesAnswerPlan(empty);
            return;
        }

        if (DishSalesAnswerPlanCardSupport.isRankingPlanType(planType)) {
            List<Map<String, Object>> aggregatedForEvidence = aggregateDishRowsByFoodId(dishRows);
            if (!DishSalesRankingSalesEvidenceSupport.hasRankingEvidenceForMetric(
                    metricType, aggregatedForEvidence)) {
                debug.put("rankedRowCount", 0);
                debug.put("earlyReturnReason", null);
                DishSalesAnswerPlan noDataPlan =
                        DishSalesRankingSalesEvidenceSupport.buildNoDataRankingPlan(
                                planType,
                                metricType,
                                scopeLabel,
                                timeLabel,
                                debug,
                                limitations);
                enrichDishSalesMatrixDebug(noDataPlan.getDebug(), rq, planType, wire);
                state.setDishSalesAnswerPlan(noDataPlan);
                return;
            }
        }

        List<Map<String, Object>> rankingRows;
        if (DishSalesAnswerPlan.TYPE_DISH_SALES_SINGLE_DISH.equals(planType)) {
            if (singleDishSalesTool) {
                rankingRows = buildSingleDishRowsFromSalesTool(state, debug);
            } else {
                rankingRows = buildSingleDishRows(dishRows, resolveMentionedDishName(state, rq), debug);
            }
        } else {
            rankingRows = buildRankingRows(dishRows, metricType, debug);
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
        enrichDishSalesMatrixDebug(plan.getDebug(), rq, planType, wire);
        populateDishSalesResultAnchors(plan);
        state.setDishSalesAnswerPlan(plan);
    }

    public static void attachIfApplicable(AiRunState state) {
        attachIfApplicable(state, state != null ? state.getDishProfitOverviewResult() : null);
    }

    private static void appendKnownGapLimitation(List<String> limitations, String knownGap) {
        if (!StringUtils.hasText(knownGap)) {
            return;
        }
        if (DishSalesSemanticCapabilityMatrix.KNOWN_GAP_CROSS_DOMAIN_DISH_PROFIT_NOT_IN_P1.equals(knownGap)) {
            limitations.add("菜品销量域暂不承接毛利/毛利率追问，请使用菜品毛利专线。");
        } else if (DishSalesSemanticCapabilityMatrix.KNOWN_GAP_TREND_SERIES_NOT_IMPLEMENTED.equals(knownGap)) {
            limitations.add("当前不支持菜品销量趋势时间序列，仅可提供排行或单菜销量快照。");
        }
    }

    private static List<Map<String, Object>> buildSingleDishRowsFromSalesTool(
            AiRunState state, LinkedHashMap<String, Object> debug) {
        Map<String, Object> data = toolEnvelopeData(state, AiBusinessToolIds.DISH_SALES_ANALYSIS_CARD);
        if (data == null || data.isEmpty()) {
            debug.put("singleDishMatchNote", "dish_sales_analysis_card_data_missing");
            return List.of();
        }
        String status = stringify(data.get("status"));
        if ("NEED_CLARIFICATION".equals(status)) {
            debug.put("singleDishMatchNote", "entity_disambiguation_need_clarification");
            debug.put("singleDishClarificationKind", "entity_disambiguation");
            Object candidates = data.get("candidates");
            if (candidates != null) {
                debug.put("singleDishClarificationCandidates", candidates);
            }
            String msg = stringify(data.get("message"));
            if (StringUtils.hasText(msg)) {
                state.setNeedClarification(true);
                if (!StringUtils.hasText(state.getClarificationQuestion())) {
                    state.setClarificationQuestion(msg.trim());
                }
            }
            return List.of();
        }
        if ("NO_DATA".equals(status)) {
            debug.put("singleDishMatchNote", "no_data");
            String msg = stringify(data.get("message"));
            if (StringUtils.hasText(msg)) {
                debug.put("noDataMessage", msg.trim());
            }
            return List.of();
        }
        if (!"SUCCESS".equals(status)) {
            debug.put("singleDishMatchNote", "dish_sales_analysis_card_not_success");
            return List.of();
        }
        String dishName = stringify(data.get("dishName"));
        if (!StringUtils.hasText(dishName)) {
            debug.put("singleDishMatchNote", "missing_dish_name_in_tool_result");
            return List.of();
        }
        LinkedHashMap<String, Object> m = new LinkedHashMap<>();
        Object rank = data.get("ranking");
        if (rank == null) {
            rank = data.get("rank");
        }
        m.put("rank", rank != null ? rank : 1);
        m.put("ranking", rank != null ? rank : 1);
        m.put("dishName", dishName);
        m.put("soldPortionsTotal", firstNonBlank(stringify(data.get("soldPortionsTotal")), stringify(data.get("salesPortions"))));
        m.put("actualRevenue", firstNonBlank(stringify(data.get("salesAmount")), stringify(data.get("actualRevenue"))));
        m.put("grossMarginRate", stringify(data.get("grossMarginRate")));
        Object fid = data.get("dishId");
        if (fid == null) {
            fid = data.get("foodId");
        }
        if (fid != null && StringUtils.hasText(fid.toString())) {
            m.put("foodId", fid.toString().trim());
        }
        return List.of(m);
    }

    /**
     * TODO：后续单菜匹配应优先精确匹配 dishName == mentionedDishName；
     * 模糊 contains 匹配多条时应标 ambiguous 或澄清；
     * 不要用这个 contains 反推语义。
     */
    private static List<Map<String, Object>> buildSingleDishRows(
            List<Map<String, Object>> dishRows, String dishFocus, LinkedHashMap<String, Object> debug) {
        debug.put("mentionedDishName", dishFocus);
        if (!StringUtils.hasText(dishFocus)) {
            return List.of();
        }
        String needle = dishFocus.trim();
        List<Map<String, Object>> matched = new ArrayList<>();
        for (Map<String, Object> row : dishRows) {
            if (row == null) {
                continue;
            }
            String name = stringify(row.get("dishName"));
            if (StringUtils.hasText(name) && name.contains(needle)) {
                matched.add(row);
            }
        }
        if (matched.isEmpty()) {
            debug.put("singleDishMatchNote", "no_dish_row_matched_mentioned_name");
            return List.of();
        }
        List<Map<String, Object>> out = new ArrayList<>();
        int rank = 1;
        for (Map<String, Object> row : matched) {
            LinkedHashMap<String, Object> m = rowToRankingMap(row, rank++);
            out.add(m);
        }
        return out;
    }

    /** 同一 foodId 多行（多部门/子口径）合并后再排行，避免 Top 列表重复同一菜品。 */
    private static List<Map<String, Object>> aggregateDishRowsByFoodId(List<Map<String, Object>> dishRows) {
        if (dishRows == null || dishRows.isEmpty()) {
            return List.of();
        }
        Map<String, Map<String, Object>> byFoodId = new LinkedHashMap<>();
        List<Map<String, Object>> withoutFoodId = new ArrayList<>();
        for (Map<String, Object> row : dishRows) {
            if (row == null) {
                continue;
            }
            Object foodId = row.get("foodId");
            if (foodId == null || !StringUtils.hasText(foodId.toString())) {
                withoutFoodId.add(row);
                continue;
            }
            String key = foodId.toString().trim();
            Map<String, Object> existing = byFoodId.get(key);
            if (existing == null) {
                byFoodId.put(key, new LinkedHashMap<>(row));
            } else {
                mergeRankingMetrics(existing, row);
            }
        }
        List<Map<String, Object>> out = new ArrayList<>(byFoodId.values());
        out.addAll(withoutFoodId);
        return out;
    }

    private static void mergeRankingMetrics(Map<String, Object> target, Map<String, Object> addition) {
        target.put(
                "soldPortionsTotal",
                sumMetricStrings(stringify(target.get("soldPortionsTotal")), stringify(addition.get("soldPortionsTotal"))));
        target.put(
                "actualRevenue",
                sumMetricStrings(stringify(target.get("actualRevenue")), stringify(addition.get("actualRevenue"))));
        if (!StringUtils.hasText(stringify(target.get("dishName")))
                && StringUtils.hasText(stringify(addition.get("dishName")))) {
            target.put("dishName", addition.get("dishName"));
        }
    }

    private static String sumMetricStrings(String a, String b) {
        boolean aBlank = !StringUtils.hasText(a);
        boolean bBlank = !StringUtils.hasText(b);
        if (aBlank && bBlank) {
            return "";
        }
        if (aBlank) {
            return b.trim();
        }
        if (bBlank) {
            return a.trim();
        }
        BigDecimal av = coerceDecimalNullable(a);
        BigDecimal bv = coerceDecimalNullable(b);
        if (av == null && bv == null) {
            return a.trim();
        }
        if (av == null) {
            return b.trim();
        }
        if (bv == null) {
            return a.trim();
        }
        BigDecimal sum = av.add(bv);
        if (sum.stripTrailingZeros().scale() <= 0) {
            return sum.toBigInteger().toString();
        }
        return sum.stripTrailingZeros().toPlainString();
    }

    private static List<Map<String, Object>> buildRankingRows(
            List<Map<String, Object>> dishRows, String metricType, LinkedHashMap<String, Object> debug) {
        List<Map<String, Object>> aggregated = aggregateDishRowsByFoodId(dishRows);
        List<ScoredRow> scored = new ArrayList<>(aggregated.size());
        for (Map<String, Object> row : aggregated) {
            if (row == null) {
                continue;
            }
            BigDecimal qty = coerceDecimalNullable(row.get("soldPortionsTotal"));
            BigDecimal amt = coerceDecimalNullable(row.get("actualRevenue"));
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
            LinkedHashMap<String, Object> m = rowToRankingMap(sr.row(), rank++);
            if (!sr.qtyValid() && DishSalesAnswerPlan.METRIC_COUNT_LOW.equals(metricType)) {
                m.put("note", "销量口径缺失或非数字，不参与「最低销量」解读");
            } else if (!sr.qtyValid() && DishSalesAnswerPlan.METRIC_COUNT_HIGH.equals(metricType)) {
                m.put("note", "销量口径缺失或非数字，排在末位");
            } else if (!sr.qtyValidForAmountSort() && DishSalesAnswerPlan.METRIC_AMOUNT_HIGH.equals(metricType)) {
                m.put("note", "销售额口径缺失或非数字，排在末位");
            }
            rankingRows.add(m);
        }
        return rankingRows;
    }

    private static LinkedHashMap<String, Object> rowToRankingMap(Map<String, Object> row, int rank) {
        LinkedHashMap<String, Object> m = new LinkedHashMap<>();
        m.put("rank", rank);
        m.put("dishName", stringify(row.get("dishName")));
        m.put("soldPortionsTotal", stringify(row.get("soldPortionsTotal")));
        m.put("actualRevenue", stringify(row.get("actualRevenue")));
        m.put("grossMarginRate", formatGrossMarginRate(row));
        m.put("actualCostAmount", stringify(row.get("actualCostAmount")));
        m.put("theoryCostAmount", stringify(row.get("theoryCostAmount")));
        Object fid = row.get("foodId");
        if (fid != null && StringUtils.hasText(fid.toString())) {
            m.put("foodId", fid.toString().trim());
        }
        return m;
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
        String rev = nzString(top.get("actualRevenue"));
        if (DishSalesAnswerPlan.METRIC_SINGLE_DISH.equals(metricType)) {
            return String.format(Locale.CHINA, "%s 本月销量 %s 份，销售额 %s。", name, nzOrDash(qty), nzOrDash(rev));
        }
        if (DishSalesAnswerPlan.METRIC_AMOUNT_HIGH.equals(metricType)) {
            return String.format(Locale.CHINA, "当前范围内销售额最高的菜品是 %s，销售额 %s，销量 %s 份。", name, nzOrDash(rev), nzOrDash(qty));
        }
        if (DishSalesAnswerPlan.METRIC_COUNT_LOW.equals(metricType)) {
            return String.format(Locale.CHINA, "当前范围内销量最低的菜品是 %s，销量 %s 份，销售额 %s。", name, nzOrDash(qty), nzOrDash(rev));
        }
        return String.format(Locale.CHINA, "当前范围内销量最高的菜品是 %s，销量 %s 份，销售额 %s。", name, nzOrDash(qty), nzOrDash(rev));
    }

    private static String resolveMentionedDishName(AiRunState state, AiResolvedQueryContext rq) {
        Map<String, Object> toolData = new LinkedHashMap<>();
        Object env =
                state.getToolResults() == null
                        ? null
                        : state.getToolResults().get(AiBusinessToolIds.DISH_PROFIT_ANALYSIS);
        if (env instanceof Map<?, ?> tm) {
            Object data = tm.get("data");
            if (data instanceof Map<?, ?> dm) {
                Object hint = dm.get("dishNameFocusHint");
                if (hint != null && StringUtils.hasText(hint.toString())) {
                    toolData.put("dishName", hint.toString().trim());
                }
                Object dishName = dm.get("dishName");
                if (dishName != null && StringUtils.hasText(dishName.toString())) {
                    toolData.put("dishName", dishName.toString().trim());
                }
            }
        }
        return DishEntityDisplayNameSupport.resolveDisplayDishName(rq, toolData);
    }

    private static void enrichDishSalesMatrixDebug(
            Map<String, Object> dbg,
            AiResolvedQueryContext rq,
            String planType,
            String wire) {
        if (dbg == null || rq == null) {
            return;
        }
        String path = rq.getEffectivePathCode();
        if (path == null || path.isBlank()) {
            path = rq.getQueryIntent() != null ? rq.getQueryIntent().getPathCode() : null;
        }
        AiQuerySemanticParseResult sem = semantic(rq);
        String canonWire =
                StringUtils.hasText(wire)
                        ? AiQuerySemanticLexicon.canonicalStructuredIntentDetailWire(wire.trim())
                        : null;
        if (StringUtils.hasText(canonWire)) {
            dbg.put("contractStructuredIntentDetailWire", canonWire);
        }
        DishSalesSemanticCapabilityMatrixRow row =
                DishSalesSemanticCapabilityMatrix.resolveMatrixRow(path, canonWire, sem);
        if (row != null) {
            putMatrixObservedDebug(dbg, row, canonWire);
            String gap = DishSalesSemanticCapabilityMatrix.knownGapForResolvedRow(row);
            if (gap != null) {
                dbg.put("dishSalesKnownGap", gap);
            }
        }
        if (DishSalesSemanticCapabilityMatrix.detectMatrixWireMissing(sem, path, canonWire)) {
            dbg.put("dishSalesMatrixWireMissing", DishSalesSemanticCapabilityMatrix.MATRIX_WIRE_MISSING);
        }
        dbg.put("dishSalesAnswerPlanType", planType);
    }

    /** Matrix row 观测字段：debug-only，不得当作主链 contract wire。 */
    private static void putMatrixObservedDebug(
            Map<String, Object> dbg,
            DishSalesSemanticCapabilityMatrixRow row,
            String contractWire) {
        if (dbg == null || row == null) {
            return;
        }
        dbg.put("matrixObservedDebugOnly", Boolean.TRUE);
        dbg.put("matrixObservedRowId", row.getRowId());
        dbg.put("matrixObservedWire", row.getStructuredIntentDetailWire());
        dbg.put("matrixObservedSalesFacet", row.getSalesFacet());
        if (StringUtils.hasText(contractWire)
                && !contractWire.equals(row.getStructuredIntentDetailWire())) {
            dbg.put("matrixObservedWireDiffersFromContract", Boolean.TRUE);
        }
    }

    private static AiQuerySemanticParseResult semantic(AiResolvedQueryContext rq) {
        return rq == null ? null : rq.getQuerySemanticParse();
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
        List<Map<String, Object>> fromSales = extractDishRowsFromSalesTool(state);
        if (!fromSales.isEmpty()) {
            return fromSales;
        }
        return extractDishRowsFromProfitTool(state);
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> extractDishRowsFromSalesTool(AiRunState state) {
        Map<String, Object> data = toolEnvelopeData(state, AiBusinessToolIds.DISH_SALES_ANALYSIS_CARD);
        if (data == null || data.isEmpty()) {
            return List.of();
        }
        Object raw = data.get("rawSalesRows");
        if (!(raw instanceof List<?> list) || list.isEmpty()) {
            return List.of();
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> rowRaw)) {
                continue;
            }
            Map<String, Object> row = (Map<String, Object>) rowRaw;
            LinkedHashMap<String, Object> normalized = new LinkedHashMap<>();
            String dishName = firstNonBlank(stringify(row.get("dishName")), stringify(row.get("foodName")));
            if (!StringUtils.hasText(dishName)) {
                continue;
            }
            normalized.put("dishName", dishName);
            putIfPresent(normalized, "soldPortionsTotal", row.get("soldPortionsTotal"));
            putIfPresent(normalized, "actualRevenue", row.get("actualRevenue"));
            putIfPresent(normalized, "listPrice", row.get("listPrice"));
            putIfPresent(normalized, "foodId", row.get("foodId"));
            out.add(normalized);
        }
        return out;
    }

    private static void putIfPresent(Map<String, Object> target, String key, Object value) {
        if (value != null && StringUtils.hasText(value.toString())) {
            target.put(key, value);
        }
    }

    private static boolean usesDishSalesAnalysisTool(AiRunState state) {
        if (state == null) {
            return false;
        }
        if (state.getDataPlanTools() != null
                && state.getDataPlanTools().contains(AiBusinessToolIds.DISH_SALES_ANALYSIS_CARD)) {
            return true;
        }
        return state.getToolResults() != null
                && state.getToolResults().containsKey(AiBusinessToolIds.DISH_SALES_ANALYSIS_CARD);
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> extractDishRowsFromProfitTool(AiRunState state) {
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
        return toolEnvelopeSuccess(state, AiBusinessToolIds.DISH_PROFIT_ANALYSIS);
    }

    private static boolean toolEnvelopeSuccess(AiRunState state, String toolId) {
        Object env = state.getToolResults() == null ? null : state.getToolResults().get(toolId);
        if (!(env instanceof Map<?, ?> m)) {
            return false;
        }
        return Boolean.TRUE.equals(m.get("success"));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> toolEnvelopeData(AiRunState state, String toolId) {
        Object env = state.getToolResults() == null ? null : state.getToolResults().get(toolId);
        if (!(env instanceof Map<?, ?> tm)) {
            return Map.of();
        }
        Object data = tm.get("data");
        if (!(data instanceof Map<?, ?> dm)) {
            return Map.of();
        }
        return (Map<String, Object>) dm;
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

    private record DishSalesWireResolution(String rawStructuredIntentDetail, String resolvedCanonicalWire,
                                            String wireRejectedReason) {}

    /**
     * 只接受 contract-locked 后 contract-completed 的 canonical wire。
     * 不再读取 raw LLM structuredIntentDetail / currentTurnStructuredIntentDetailWire / raw semanticSlots wire。
     * 非 contract-locked 或 contract 未产出 wire 一律拒绝（raw=null, wire=null）。
     */
    private static DishSalesWireResolution resolveDishSalesWire(AiResolvedQueryContext rq) {
        AiQuerySemanticParseResult sem = semantic(rq);
        if (!SemanticContractCompletionEngine.isContractLockedParse(sem)) {
            return new DishSalesWireResolution(null, null, "non_contract_locked_parse");
        }
        // 仅从 contract-completed slots 取 wire（applyContractToParse 已写入 canonical wire）
        String raw = sem.getSemanticSlots() != null
                ? blankToNull(sem.getSemanticSlots().getStructuredIntentDetailWire())
                : null;
        if (!StringUtils.hasText(raw)) {
            return new DishSalesWireResolution(null, null, "missing_contract_completed_wire");
        }
        String wire = AiQuerySemanticLexicon.canonicalStructuredIntentDetailWire(raw);
        if (!acceptedDishSalesWire(wire)) {
            return new DishSalesWireResolution(raw, null, "contract_wire_not_accepted_dish_sales_matrix");
        }
        return new DishSalesWireResolution(raw, wire, null);
    }

    private static boolean acceptedDishSalesWire(String wire) {
        if (!StringUtils.hasText(wire)) {
            return false;
        }
        if (AiQuerySemanticLexicon.isStructuredDishSalesDetail(wire)) {
            return true;
        }
        return AiQuerySemanticLexicon.STRUCTURED_DISH_GROSS_MARGIN_QUERY.equals(wire);
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
        String wire = debug.get("resolvedDishSalesWire") == null ? "" : debug.get("resolvedDishSalesWire").toString();
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
        enrichDishSalesMatrixDebug(plan.getDebug(), state.getResolvedQueryContext(), null, wire);
        state.setDishSalesAnswerPlan(plan);
    }

    private static void populateDishSalesResultAnchors(DishSalesAnswerPlan plan) {
        if (plan == null) {
            return;
        }
        if (DishSalesRankingSalesEvidenceSupport.isNoDataRankingPlan(plan)) {
            plan.setResultAnchors(new ArrayList<>());
            return;
        }
        if (!DishSalesSemanticCapabilityMatrix.planTypeEmitsDishSalesResultAnchor(plan.getPlanType())) {
            plan.setResultAnchors(new ArrayList<>());
            return;
        }
        List<Map<String, Object>> rows = plan.getRankingRows();
        if (rows == null || rows.isEmpty()) {
            plan.setResultAnchors(new ArrayList<>());
            return;
        }
        Map<String, Object> row = rows.get(0);
        if (row == null) {
            plan.setResultAnchors(new ArrayList<>());
            return;
        }
        Object dn = row.get("dishName");
        String dishName = dn == null ? null : dn.toString().trim();
        if (!StringUtils.hasText(dishName) || "（未命名菜品）".equals(dishName)) {
            plan.setResultAnchors(new ArrayList<>());
            return;
        }
        Object fid = row.get("foodId");
        String entityId = fid == null ? null : fid.toString().trim();
        Integer rank = 1;
        Object rk = row.get("rank");
        if (rk instanceof Number n) {
            rank = n.intValue();
        } else if (rk != null && StringUtils.hasText(rk.toString())) {
            try {
                rank = Integer.parseInt(rk.toString().trim());
            } catch (NumberFormatException ignored) {
                rank = 1;
            }
        }
        AiResultAnchor anchor =
                AiResultAnchor.builder()
                        .entityType(AiResultAnchor.ENTITY_TYPE_DISH)
                        .entityId(StringUtils.hasText(entityId) ? entityId : null)
                        .entityName(dishName)
                        .rank(rank)
                        .sourcePlanType(plan.getPlanType())
                        .metric(plan.getMetricType())
                        .build();
        plan.setResultAnchors(new ArrayList<>(List.of(anchor)));
    }
}
