package com.nongxinle.ai.graph.business;

import com.nongxinle.ai.context.AiResolvedQueryContext;
import com.nongxinle.ai.context.AiResolvedQueryIntent;
import com.nongxinle.ai.conversation.AiConversationTurnMemory;
import com.nongxinle.ai.conversation.AiQuerySemanticLexicon;
import com.nongxinle.ai.core.AiRunState;
import com.nongxinle.ai.dto.business.DailyRevenueAnswerPlan;
import com.nongxinle.ai.harness.followup.RevenueDrilldownMatrix;
import com.nongxinle.ai.harness.followup.RevenueDrilldownMatrixRow;
import com.nongxinle.ai.semantic.AiQuerySemanticParseResult;
import com.nongxinle.ai.tool.business.AiBusinessToolIds;
import org.springframework.util.StringUtils;
import com.alibaba.fastjson2.JSON;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 在 {@link BusinessToolExecutionNode} 完成 {@link AiBusinessToolIds#REVENUE_QUERY} 且路径为营收专线、
 * {@link AiRunState#isBusinessDiagnosisPath() 经营诊断} 或 {@link AiRunState#isBusinessOverviewPath() 经营概览}
 * 时，基于 Tool 信封生成 {@link DailyRevenueAnswerPlan}（不重查 SQL）。
 * <p>
 * 成本主链也会调用 {@code revenue_query}；非上述 surface 时不挂载，避免串 Run。
 */
@Slf4j
public final class DailyRevenueAnswerPlanBuilder {

    private DailyRevenueAnswerPlanBuilder() {
    }

    public static void attachIfApplicable(AiRunState state) {
        if (state == null) {
            return;
        }
        if (!state.isRevenueOverviewPath() && !state.isBusinessDiagnosisPath() && !state.isBusinessOverviewPath()) {
            return;
        }

        LinkedHashMap<String, Object> baseDiag = new LinkedHashMap<>();
        baseDiag.put("attachAttempted", true);
        baseDiag.put("expectedToolKey", AiBusinessToolIds.REVENUE_QUERY);
        baseDiag.put("source", "RevenueQueryTool");
        baseDiag.put("sourceToolKey", AiBusinessToolIds.REVENUE_QUERY);
        baseDiag.put("toolResultKeys",
                state.getToolResults() == null ? null : state.getToolResults().keySet());

        Object env = state.getToolResults() == null ? null
                : state.getToolResults().get(AiBusinessToolIds.REVENUE_QUERY);
        baseDiag.put("hasRevenueToolResult", env != null);
        baseDiag.put("dataClass", env == null ? null : env.getClass().getName());

        if (env == null) {
            attachFailure(state, baseDiag, "missing_tool_result",
                    "toolResults[" + AiBusinessToolIds.REVENUE_QUERY + "] is null");
            return;
        }
        if (!(env instanceof Map<?, ?> envMapRaw)) {
            attachFailure(state, baseDiag, "missing_or_invalid_tool_envelope",
                    "toolResults[" + AiBusinessToolIds.REVENUE_QUERY + "] is not a Map");
            return;
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> envMap = (Map<String, Object>) envMapRaw;
        boolean envSuccess = Boolean.TRUE.equals(envMap.get("success"));
        if (!envSuccess) {
            baseDiag.put("foundDataPath", "envelope.success=false");
            attachFailure(state, baseDiag, "tool_envelope_unsuccessful",
                    envMap.get("message") == null ? "success=false" : envMap.get("message").toString());
            return;
        }

        Object dataObj = unwrapDataMaybeJsonString(envMap.get("data"));
        if (!(dataObj instanceof Map<?, ?>)) {
            attachFailure(state, baseDiag, "empty_inner_data", "envelope.data is missing or not a Map");
            return;
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> inner = (Map<String, Object>) dataObj;
        baseDiag.put("foundDataPath", "envelope.data");

        Object rawStatsObj = inner.get("rawStats");
        Map<String, Object> rawStats = null;
        if (rawStatsObj instanceof Map<?, ?> rm) {
            @SuppressWarnings("unchecked")
            Map<String, Object> cast = (Map<String, Object>) rm;
            rawStats = cast;
        }
        boolean statsEmpty = rawStats == null || rawStats.isEmpty();
        baseDiag.put("foundRevenueOverview", !statsEmpty || inner.get("totalRevenue") != null);

        AiResolvedQueryContext rq = state.getResolvedQueryContext();
        String wire = resolveWire(rq);
        String prevWire = prevStructuredWire(rq);
        String inheritedPlanType = prevInheritedPlanType(rq);
        baseDiag.put("previousPlanType", inheritedPlanType);
        baseDiag.put("structuredIntentDetailWire", wire.isEmpty() ? null : wire);

        String norm = rq != null ? rq.getNormalizedQuestion() : null;
        String planType =
                resolvePlanType(
                        wire, inheritedPlanType, rq != null ? rq.getQuerySemanticParse() : null, norm);
        baseDiag.put("resolvedPlanType", planType);
        baseDiag.put("inheritedPlanType",
                inheritedPlanType != null && inheritedPlanType.equals(planType) ? planType : null);

        try {
            DailyRevenueAnswerPlan plan = buildPlan(state, rq, inner, rawStats, planType, prevWire,
                    inheritedPlanType, baseDiag);
            state.setRevenueAnswerPlan(plan);
            Map<String, Object> dbg = plan.getDebug();
            log.info("[DailyRevenueAnswerPlan] attached runId={} type={} focusSize={} secondarySize={}",
                    state.getRunId(),
                    plan.getPlanType(),
                    plan.getFocusRows() == null ? 0 : plan.getFocusRows().size(),
                    plan.getSecondaryRows() == null ? 0 : plan.getSecondaryRows().size());
            if (dbg != null) {
                dbg.put("focusRowsSize", plan.getFocusRows() == null ? 0 : plan.getFocusRows().size());
                dbg.put("secondaryRowsSize", plan.getSecondaryRows() == null ? 0 : plan.getSecondaryRows().size());
            }
        } catch (Exception ex) {
            log.warn("[DailyRevenueAnswerPlan] attach failed runId={}", state.getRunId(), ex);
            baseDiag.put("failureReason", "build_exception");
            baseDiag.put("exception", ex.getClass().getName());
            attachFailure(state, baseDiag, "build_exception", ex.getMessage());
        }
    }

    private static void attachFailure(AiRunState state, Map<String, Object> diag, String reasonCode, String detail) {
        diag.put("failureReason", reasonCode);
        diag.put("failureDetail", detail);
        AiResolvedQueryContext rq = state.getResolvedQueryContext();
        String wire = resolveWire(rq);
        String prevWire = prevStructuredWire(rq);
        String inherited = prevInheritedPlanType(rq);
        String planType =
                resolvePlanType(
                        wire,
                        inherited,
                        rq != null ? rq.getQuerySemanticParse() : null,
                        rq != null ? rq.getNormalizedQuestion() : null);
        diag.put("resolvedPlanType", planType);
        diag.put("focusRowsSize", 0);
        diag.put("secondaryRowsSize", 0);
        LinkedHashMap<String, Object> failDbg = new LinkedHashMap<>(diag);
        enrichRevenueMatrixDebug(failDbg, rq, planType, wire);
        DailyRevenueAnswerPlan plan = DailyRevenueAnswerPlan.builder()
                .planType(planType)
                .revenueChannel(DailyRevenueAnswerPlan.CHANNEL_ALL)
                .scopeLabel(resolveScopeLabel(rq))
                .timeLabel(resolveTimeLabel(state, rq))
                .summary(new LinkedHashMap<>())
                .focusRows(new ArrayList<>())
                .secondaryRows(new ArrayList<>())
                .debug(failDbg)
                .build();
        state.setRevenueAnswerPlan(plan);
        log.info("[DailyRevenueAnswerPlan] attachFailure runId={} reason={}", state.getRunId(), reasonCode);
    }

    static DailyRevenueAnswerPlan buildPlan(AiRunState state, AiResolvedQueryContext rq,
            Map<String, Object> inner, Map<String, Object> rawStats, String planType,
            String prevWire, String inheritedPlanType,
            LinkedHashMap<String, Object> debug) {

        LinkedHashMap<String, Object> dbg = new LinkedHashMap<>(debug);
        dbg.put("sourceToolKey", AiBusinessToolIds.REVENUE_QUERY);
        dbg.put("resolvedPlanType", planType);
        String wire = resolveWire(rq);
        dbg.put("structuredIntentDetailWire", wire.isEmpty() ? null : wire);

        BigDecimal totalRev = nz(inner.get("totalRevenue"));
        int days = inner.get("days") instanceof Number ? Math.max(((Number) inner.get("days")).intValue(), 0) : 0;
        BigDecimal avgDailyTool = nz(inner.get("avgDailyRevenue"));

        String scopeLabel = resolveScopeLabel(rq);
        String timeLabel = resolveTimeLabel(state, rq);

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("totalRevenue", totalRev.doubleValue());
        summary.put("days", days);
        summary.put("avgDailyRevenue", avgDailyTool.doubleValue());

        List<Map<String, Object>> focusRows = new ArrayList<>();
        List<Map<String, Object>> secondaryRows = new ArrayList<>();

        String revenueChannel = DailyRevenueAnswerPlan.CHANNEL_ALL;
        dbg.put("rankingMetric", null);
        dbg.put("sortKey", null);
        dbg.put("sortDirection", null);

        switch (planType) {
            case DailyRevenueAnswerPlan.TYPE_REVENUE_OVERVIEW -> {
                revenueChannel = DailyRevenueAnswerPlan.CHANNEL_ALL;
                dbg.put("revenueChannel", revenueChannel);
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("role", "overview");
                row.put("totalRevenue", totalRev.doubleValue());
                row.put("days", days);
                row.put("avgDailyRevenue", avgDailyTool.doubleValue());
                focusRows.add(row);
                mergeRawStatTotalsIntoSummary(summary, rawStats);
                appendChannelSecondaryFromRawStats(rawStats, secondaryRows);
            }
            case DailyRevenueAnswerPlan.TYPE_REVENUE_DINE_IN_OVERVIEW -> {
                revenueChannel = DailyRevenueAnswerPlan.CHANNEL_DINE_IN;
                dbg.put("revenueChannel", revenueChannel);
                if (rawStats == null || rawStats.get("total_dine_in_revenue") == null) {
                    failPlan(planType, dbg, "missing_revenue_overview", "rawStats.total_dine_in_revenue absent");
                    break;
                }
                BigDecimal dine = nz(rawStats.get("total_dine_in_revenue"));
                summary.put("totalDineInRevenue", dine.doubleValue());
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("role", "dine_in_total");
                row.put("revenueAmount", dine.doubleValue());
                focusRows.add(row);
            }
            case DailyRevenueAnswerPlan.TYPE_REVENUE_TAKEOUT_OVERVIEW -> {
                revenueChannel = DailyRevenueAnswerPlan.CHANNEL_TAKEOUT;
                dbg.put("revenueChannel", revenueChannel);
                if (rawStats == null || rawStats.get("total_takeout_revenue") == null) {
                    failPlan(planType, dbg, "missing_revenue_overview", "rawStats.total_takeout_revenue absent");
                    break;
                }
                BigDecimal take = nz(rawStats.get("total_takeout_revenue"));
                summary.put("totalTakeoutRevenue", take.doubleValue());
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("role", "takeout_total");
                row.put("revenueAmount", take.doubleValue());
                focusRows.add(row);
            }
            case DailyRevenueAnswerPlan.TYPE_REVENUE_PLATFORM_RANKING -> {
                // 预留枚举：日营业额表无美团/饿了么等分列，不按真实平台排行产出；降级为外卖渠道合计。
                revenueChannel = DailyRevenueAnswerPlan.CHANNEL_TAKEOUT;
                dbg.put("revenueChannel", revenueChannel);
                dbg.put("explainTakeoutChannelAggregateOnly", Boolean.TRUE);
                dbg.put("degradedFromPlanType", DailyRevenueAnswerPlan.TYPE_REVENUE_PLATFORM_RANKING);
                if (rawStats == null || rawStats.get("total_takeout_revenue") == null) {
                    failPlan(planType, dbg, "missing_revenue_overview", "rawStats.total_takeout_revenue absent");
                    break;
                }
                BigDecimal takePlat = nz(rawStats.get("total_takeout_revenue"));
                summary.put("totalTakeoutRevenue", takePlat.doubleValue());
                Map<String, Object> platRow = new LinkedHashMap<>();
                platRow.put("role", "takeout_total");
                platRow.put("revenueAmount", takePlat.doubleValue());
                focusRows.add(platRow);
            }
            case DailyRevenueAnswerPlan.TYPE_REVENUE_ORDER_COUNT_OVERVIEW -> {
                dbg.put("revenueChannel", revenueChannel);
                if (rawStats == null || rawStats.get("total_orders") == null) {
                    failPlan(planType, dbg, "missing_revenue_overview", "rawStats.total_orders absent");
                    break;
                }
                BigDecimal ord = nz(rawStats.get("total_orders"));
                summary.put("totalOrders", ord.doubleValue());
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("role", "order_count_total");
                row.put("orderCount", ord.doubleValue());
                focusRows.add(row);
            }
            case DailyRevenueAnswerPlan.TYPE_REVENUE_CUSTOMER_COUNT_OVERVIEW -> {
                dbg.put("revenueChannel", revenueChannel);
                failPlan(planType, dbg, "missing_revenue_overview",
                        "total customer count not exposed on RevenueQueryTool rawStats aggregate map");
            }
            case DailyRevenueAnswerPlan.TYPE_REVENUE_AVERAGE_ORDER_VALUE -> {
                dbg.put("revenueChannel", revenueChannel);
                if (rawStats == null || rawStats.get("total_orders") == null) {
                    failPlan(planType, dbg, "missing_average_order_value", "cannot derive AOV without total_orders");
                    break;
                }
                BigDecimal orders = nz(rawStats.get("total_orders"));
                if (orders.compareTo(BigDecimal.ZERO) <= 0 || totalRev.compareTo(BigDecimal.ZERO) <= 0) {
                    failPlan(planType, dbg, "missing_average_order_value", "total_orders or total revenue is zero");
                    break;
                }
                BigDecimal aov = totalRev.divide(orders, 2, RoundingMode.HALF_UP);
                summary.put("averageOrderValue", aov.doubleValue());
                summary.put("totalOrders", orders.doubleValue());
                summary.put("totalRevenue", totalRev.doubleValue());
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("role", "average_order_value");
                row.put("averageOrderValue", aov.doubleValue());
                row.put("basis", "totalRevenue/total_orders from rawStats");
                focusRows.add(row);
            }
            case DailyRevenueAnswerPlan.TYPE_REVENUE_DAILY_AMOUNT_RANKING -> {
                dbg.put("revenueChannel", revenueChannel);
                dbg.put("rankingMetric", "revenueAmount");
                String dir = resolveDailyRankingSortDirection();
                dbg.put("sortKey", "revenueAmount");
                dbg.put("sortDirection", dir);
                if (rawStats == null || rawStats.get("max_daily_revenue") == null || rawStats.get("min_daily_revenue") == null) {
                    failPlan(planType, dbg, "missing_daily_ranking",
                            "max_daily_revenue/min_daily_revenue absent on aggregate stats");
                    break;
                }
                BigDecimal mx = nz(rawStats.get("max_daily_revenue"));
                BigDecimal mn = nz(rawStats.get("min_daily_revenue"));
                Map<String, Object> pick = new LinkedHashMap<>();
                pick.put("role", "daily_rank_pick");
                pick.put("rankMetric", "revenueAmount");
                pick.put("sortDirection", dir);
                if ("ASC".equals(dir)) {
                    pick.put("revenueAmount", mn.doubleValue());
                    pick.put("semantic", "lowest_daily_gross_in_period_aggregate");
                } else {
                    pick.put("revenueAmount", mx.doubleValue());
                    pick.put("semantic", "highest_daily_gross_in_period_aggregate");
                }
                pick.put("businessDateKnown", false);
                pick.put("note", "Tool payload omits argmax/argmin calendar date; values are SQL period max/min only.");
                focusRows.add(pick);
                Map<String, Object> alt = new LinkedHashMap<>();
                alt.put("role", "daily_rank_other_bound");
                alt.put("revenueAmount", ("ASC".equals(dir) ? mx : mn).doubleValue());
                secondaryRows.add(alt);
            }
            case DailyRevenueAnswerPlan.TYPE_REVENUE_STORE_AMOUNT_RANKING -> {
                dbg.put("revenueChannel", revenueChannel);
                dbg.put("rankingMetric", "revenueAmount");
                dbg.put("sortKey", "revenueAmount");
                String dirStore = "DESC";
                dbg.put("sortDirection", dirStore);
                List<Map<String, Object>> rankingRaw = coerceStoreRankingRows(inner.get("storeRevenueRanking"));
                if (rankingRaw.isEmpty()) {
                    failPlan(planType, dbg, "missing_store_ranking",
                            "RevenueQueryTool.storeRevenueRanking empty or absent");
                    break;
                }
                List<Map<String, Object>> ranking = new ArrayList<>(rankingRaw);
                sortStoreRankingRows(ranking, dirStore);
                Map<String, Object> top = new LinkedHashMap<>(ranking.get(0));
                top.put("role", "store_rank_top");
                focusRows.add(top);
                for (int i = 1; i < ranking.size(); i++) {
                    Map<String, Object> rest = new LinkedHashMap<>(ranking.get(i));
                    rest.put("role", "store_rank_rest");
                    secondaryRows.add(rest);
                }
            }
            case DailyRevenueAnswerPlan.TYPE_REVENUE_CHANNEL_BREAKDOWN -> {
                revenueChannel = DailyRevenueAnswerPlan.CHANNEL_MIXED_BREAKDOWN;
                dbg.put("revenueChannel", revenueChannel);
                if (rawStats == null) {
                    failPlan(planType, dbg, "missing_revenue_overview", "rawStats absent");
                    break;
                }
                BigDecimal dine = nz(rawStats.get("total_dine_in_revenue"));
                BigDecimal take = nz(rawStats.get("total_takeout_revenue"));
                BigDecimal plat = nz(rawStats.get("total_platform_fee"));
                summary.put("totalDineInRevenue", dine.doubleValue());
                summary.put("totalTakeoutRevenue", take.doubleValue());
                summary.put("totalPlatformFee", plat.doubleValue());
                BigDecimal grossChannels = dine.add(take);
                Map<String, Object> overviewRow = new LinkedHashMap<>();
                overviewRow.put("role", "channel_breakdown_total");
                overviewRow.put("totalRevenue", totalRev.doubleValue());
                overviewRow.put("days", days);
                focusRows.add(overviewRow);
                Map<String, Object> dineRow = new LinkedHashMap<>();
                dineRow.put("channel", DailyRevenueAnswerPlan.CHANNEL_DINE_IN);
                dineRow.put("label", "堂食");
                dineRow.put("revenueAmount", dine.doubleValue());
                dineRow.put("shareOfGrossChannels",
                        grossChannels.compareTo(BigDecimal.ZERO) <= 0 ? null
                                : dine.divide(grossChannels, 4, RoundingMode.HALF_UP).doubleValue());
                Map<String, Object> takeRow = new LinkedHashMap<>();
                takeRow.put("channel", DailyRevenueAnswerPlan.CHANNEL_TAKEOUT);
                takeRow.put("label", "外卖");
                takeRow.put("revenueAmount", take.doubleValue());
                takeRow.put("shareOfGrossChannels",
                        grossChannels.compareTo(BigDecimal.ZERO) <= 0 ? null
                                : take.divide(grossChannels, 4, RoundingMode.HALF_UP).doubleValue());
                secondaryRows.add(dineRow);
                secondaryRows.add(takeRow);
                if (plat.compareTo(BigDecimal.ZERO) > 0) {
                    Map<String, Object> feeRow = new LinkedHashMap<>();
                    feeRow.put("channel", "PLATFORM_FEE");
                    feeRow.put("feeAmount", plat.doubleValue());
                    secondaryRows.add(feeRow);
                }
            }
            default -> {
                revenueChannel = DailyRevenueAnswerPlan.CHANNEL_ALL;
                dbg.put("revenueChannel", revenueChannel);
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("role", "overview");
                row.put("totalRevenue", totalRev.doubleValue());
                row.put("days", days);
                row.put("avgDailyRevenue", avgDailyTool.doubleValue());
                focusRows.add(row);
                mergeRawStatTotalsIntoSummary(summary, rawStats);
                appendChannelSecondaryFromRawStats(rawStats, secondaryRows);
                dbg.put("note", "unknown planType=" + planType + "; emitted overview-shaped rows");
            }
        }

        dbg.put("focusRowsSize", focusRows.size());
        dbg.put("secondaryRowsSize", secondaryRows.size());

        enrichRevenueMatrixDebug(dbg, rq, planType, wire);

        return DailyRevenueAnswerPlan.builder()
                .planType(planType)
                .scopeLabel(scopeLabel)
                .timeLabel(timeLabel)
                .revenueChannel(revenueChannel)
                .summary(summary)
                .focusRows(focusRows)
                .secondaryRows(secondaryRows)
                .debug(dbg)
                .build();
    }

    private static void failPlan(String planType, Map<String, Object> dbg, String reason, String detail) {
        dbg.put("failureReason", reason);
        dbg.put("failureDetail", detail);
        dbg.put("degradedPlanType", planType);
    }

    private static void mergeRawStatTotalsIntoSummary(Map<String, Object> summary, Map<String, Object> rawStats) {
        if (rawStats == null || rawStats.isEmpty()) {
            return;
        }
        putNum(summary, "total_orders", rawStats.get("total_orders"));
        putNum(summary, "total_dine_in_revenue", rawStats.get("total_dine_in_revenue"));
        putNum(summary, "total_takeout_revenue", rawStats.get("total_takeout_revenue"));
        putNum(summary, "total_platform_fee", rawStats.get("total_platform_fee"));
        putNum(summary, "max_daily_revenue", rawStats.get("max_daily_revenue"));
        putNum(summary, "min_daily_revenue", rawStats.get("min_daily_revenue"));
    }

    private static void putNum(Map<String, Object> summary, String key, Object v) {
        if (v == null) {
            return;
        }
        summary.put(key, nz(v).doubleValue());
    }

    private static void appendChannelSecondaryFromRawStats(Map<String, Object> rawStats,
            List<Map<String, Object>> secondaryRows) {
        if (rawStats == null) {
            return;
        }
        if (rawStats.get("total_dine_in_revenue") != null) {
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("channel", "DINE_IN");
            r.put("revenueAmount", nz(rawStats.get("total_dine_in_revenue")).doubleValue());
            secondaryRows.add(r);
        }
        if (rawStats.get("total_takeout_revenue") != null) {
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("channel", "TAKEOUT");
            r.put("revenueAmount", nz(rawStats.get("total_takeout_revenue")).doubleValue());
            secondaryRows.add(r);
        }
    }

    private static String resolveDailyRankingSortDirection() {
        return "DESC";
    }

    private static void enrichRevenueMatrixDebug(
            LinkedHashMap<String, Object> dbg,
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
        AiQuerySemanticParseResult sem = rq.getQuerySemanticParse();
        String canonWire =
                StringUtils.hasText(wire)
                        ? AiQuerySemanticLexicon.canonicalStructuredIntentDetailWire(wire.trim())
                        : null;
        RevenueDrilldownMatrixRow row =
                RevenueDrilldownMatrix.resolveMatrixRow(path, canonWire, sem);
        if (row != null) {
            dbg.put("revenueMatrixRowId", row.getRowId());
            dbg.put("revenueStructuredIntentDetailWire", row.getStructuredIntentDetailWire());
            String gap = RevenueDrilldownMatrix.knownGapForResolvedRow(row);
            if (gap != null) {
                dbg.put("revenueKnownGap", gap);
            }
        } else if (StringUtils.hasText(canonWire)) {
            dbg.put("revenueStructuredIntentDetailWire", canonWire);
        }
        if (RevenueDrilldownMatrix.detectMatrixWireMissing(sem, path, canonWire)) {
            dbg.put("revenueMatrixWireMissing", RevenueDrilldownMatrix.MATRIX_WIRE_MISSING);
        }
        dbg.put("revenueAnswerPlanType", planType);
        String prevWire = prevStructuredWire(rq);
        if (StringUtils.hasText(prevWire) && StringUtils.hasText(canonWire)) {
            String leak = RevenueDrilldownMatrix.detectPriorCompareOrRankingWireLeak(prevWire, canonWire);
            if (leak != null) {
                dbg.put("revenuePriorWireLeak", leak);
            }
        }
    }

    private static String resolveWire(AiResolvedQueryContext rq) {
        if (rq == null || rq.getQueryIntent() == null) {
            return "";
        }
        String merged = rq.getQueryIntent().getStructuredIntentDetail();
        String norm = rq.getNormalizedQuestion();
        String resolved =
                RevenueDrilldownMatrix.resolveStructuredIntentDetailWire(
                        rq.getQuerySemanticParse(), safePath(rq), merged, norm);
        if (StringUtils.hasText(resolved)) {
            return resolved.trim();
        }
        return merged == null ? "" : merged.trim();
    }

    private static String prevStructuredWire(AiResolvedQueryContext rq) {
        AiConversationTurnMemory prev = rq != null ? rq.getPreviousTurn() : null;
        if (prev == null || prev.getLastStructuredIntentDetail() == null) {
            return "";
        }
        return prev.getLastStructuredIntentDetail().trim();
    }

    private static String prevInheritedPlanType(AiResolvedQueryContext rq) {
        String pw = prevStructuredWire(rq);
        String c = AiQuerySemanticLexicon.canonicalStructuredIntentDetailWire(pw);
        return wireToPlanType(c != null ? c : pw);
    }

    static String resolvePlanType(String wire, String inheritedPlanType) {
        return resolvePlanType(wire, inheritedPlanType, null, null);
    }

    static String resolvePlanType(
            String wire, String inheritedPlanType, AiQuerySemanticParseResult sem) {
        return resolvePlanType(wire, inheritedPlanType, sem, null);
    }

    /**
     * structuredIntentDetail wire + Matrix 行驱动 planType；问句仅用于时间/排行追问形状，不扩门店排行。
     */
    static String resolvePlanType(
            String wire,
            String inheritedPlanType,
            AiQuerySemanticParseResult sem,
            String normalizedUserMessage) {
        String c = AiQuerySemanticLexicon.canonicalStructuredIntentDetailWire(wire);
        String w = c != null ? c : (wire == null ? "" : wire.trim());
        RevenueDrilldownMatrixRow matrixRow =
                RevenueDrilldownMatrix.resolveMatrixRow(
                        AiResolvedQueryIntent.PATH_REVENUE_OVERVIEW, w, sem, normalizedUserMessage);
        if (matrixRow != null && StringUtils.hasText(matrixRow.getTargetRevenuePlanType())) {
            return matrixRow.getTargetRevenuePlanType();
        }
        String fromWire = wireToPlanType(w);
        if (fromWire != null) {
            return fromWire;
        }
        if (inheritedPlanType != null && !inheritedPlanType.isBlank()) {
            if (sem != null && RevenueDrilldownMatrix.isTimeFollowupShape(sem)) {
                return DailyRevenueAnswerPlan.TYPE_REVENUE_OVERVIEW;
            }
            return inheritedPlanType;
        }
        return DailyRevenueAnswerPlan.TYPE_REVENUE_OVERVIEW;
    }

    private static String wireToPlanType(String wire) {
        if (wire == null || wire.isBlank()) {
            return null;
        }
        return switch (wire) {
            case AiQuerySemanticLexicon.STRUCTURED_REVENUE_OVERVIEW_SUMMARY,
                    AiQuerySemanticLexicon.STRUCTURED_REVENUE_OVERVIEW,
                    AiQuerySemanticLexicon.STRUCTURED_REVENUE_SINGLE_STORE_OVERVIEW,
                    AiQuerySemanticLexicon.STRUCTURED_REVENUE_PERIOD_COMPARE,
                    AiQuerySemanticLexicon.STRUCTURED_REVENUE_TREND -> DailyRevenueAnswerPlan.TYPE_REVENUE_OVERVIEW;
            case AiQuerySemanticLexicon.STRUCTURED_REVENUE_STORE_COMPARE -> DailyRevenueAnswerPlan.TYPE_REVENUE_STORE_AMOUNT_RANKING;
            case AiQuerySemanticLexicon.STRUCTURED_REVENUE_DINE_IN_OVERVIEW ->
                    DailyRevenueAnswerPlan.TYPE_REVENUE_DINE_IN_OVERVIEW;
            case AiQuerySemanticLexicon.STRUCTURED_REVENUE_TAKEOUT_OVERVIEW ->
                    DailyRevenueAnswerPlan.TYPE_REVENUE_TAKEOUT_OVERVIEW;
            case AiQuerySemanticLexicon.STRUCTURED_REVENUE_PLATFORM_RANKING ->
                    DailyRevenueAnswerPlan.TYPE_REVENUE_PLATFORM_RANKING;
            case AiQuerySemanticLexicon.STRUCTURED_REVENUE_ORDER_COUNT_OVERVIEW ->
                    DailyRevenueAnswerPlan.TYPE_REVENUE_ORDER_COUNT_OVERVIEW;
            case AiQuerySemanticLexicon.STRUCTURED_REVENUE_CUSTOMER_COUNT_OVERVIEW ->
                    DailyRevenueAnswerPlan.TYPE_REVENUE_CUSTOMER_COUNT_OVERVIEW;
            case AiQuerySemanticLexicon.STRUCTURED_REVENUE_AVERAGE_ORDER_VALUE ->
                    DailyRevenueAnswerPlan.TYPE_REVENUE_AVERAGE_ORDER_VALUE;
            case AiQuerySemanticLexicon.STRUCTURED_REVENUE_DAILY_AMOUNT_RANKING,
                    AiQuerySemanticLexicon.STRUCTURED_REVENUE_DAILY_RANKING ->
                    DailyRevenueAnswerPlan.TYPE_REVENUE_DAILY_AMOUNT_RANKING;
            case AiQuerySemanticLexicon.STRUCTURED_REVENUE_STORE_AMOUNT_RANKING ->
                    DailyRevenueAnswerPlan.TYPE_REVENUE_STORE_AMOUNT_RANKING;
            case AiQuerySemanticLexicon.STRUCTURED_STORE_PRIORITY_RANKING ->
                    DailyRevenueAnswerPlan.TYPE_REVENUE_STORE_AMOUNT_RANKING;
            case AiQuerySemanticLexicon.STRUCTURED_REVENUE_CHANNEL_BREAKDOWN ->
                    DailyRevenueAnswerPlan.TYPE_REVENUE_CHANNEL_BREAKDOWN;
            default -> null;
        };
    }

    private static String resolveScopeLabel(AiResolvedQueryContext rq) {
        if (rq == null || rq.getOrgScope() == null) {
            return null;
        }
        var org = rq.getOrgScope();
        if (org.getVisibleStores() != null && org.getVisibleStores().size() == 1
                && org.getVisibleStores().get(0) != null
                && org.getVisibleStores().get(0).getStoreName() != null) {
            return org.getVisibleStores().get(0).getStoreName().trim();
        }
        if (AiResolvedQueryIntent.PATH_REVENUE_OVERVIEW.equals(safePath(rq))) {
            return "当前解析组织范围";
        }
        return null;
    }

    private static String safePath(AiResolvedQueryContext rq) {
        return rq != null && rq.getQueryIntent() != null ? rq.getQueryIntent().getPathCode() : null;
    }

    private static String resolveTimeLabel(AiRunState state, AiResolvedQueryContext rq) {
        if (rq != null && rq.getTimeWindow() != null && rq.getTimeWindow().getDisplayText() != null) {
            return rq.getTimeWindow().getDisplayText().trim();
        }
        if (rq != null && rq.getTimeWindow() != null && rq.getTimeWindow().getTimeLabel() != null) {
            return rq.getTimeWindow().getTimeLabel().trim();
        }
        String start = state.getStatStartDate();
        String stop = state.getStatEndDate();
        if (start != null && stop != null) {
            return start + " ~ " + stop;
        }
        return null;
    }

    private static BigDecimal nz(Object v) {
        if (v == null) {
            return BigDecimal.ZERO;
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
            return BigDecimal.ZERO;
        }
    }

    private static double rowRevenueAmount(Map<String, Object> row) {
        if (row == null) {
            return 0d;
        }
        Object v = row.get("revenueAmount");
        if (v instanceof Number n) {
            return n.doubleValue();
        }
        try {
            return Double.parseDouble(String.valueOf(v).trim());
        } catch (Exception e) {
            return 0d;
        }
    }

    private static void sortStoreRankingRows(List<Map<String, Object>> rows, String dir) {
        Comparator<Map<String, Object>> cmp = Comparator
                .comparingDouble(DailyRevenueAnswerPlanBuilder::rowRevenueAmount);
        cmp = cmp.thenComparing(m -> {
            Object id = m.get("storeDepartmentId");
            return id instanceof Number ? ((Number) id).longValue() : 0L;
        });
        if (!"ASC".equals(dir)) {
            cmp = cmp.reversed();
        }
        rows.sort(cmp);
    }

    private static List<Map<String, Object>> coerceStoreRankingRows(Object raw) {
        if (raw == null) {
            return List.of();
        }
        if (!(raw instanceof List<?> list)) {
            return List.of();
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (Object o : list) {
            if (!(o instanceof Map<?, ?> m)) {
                continue;
            }
            LinkedHashMap<String, Object> mm = new LinkedHashMap<>();
            for (Map.Entry<?, ?> e : m.entrySet()) {
                if (e.getKey() != null) {
                    mm.put(String.valueOf(e.getKey()), e.getValue());
                }
            }
            out.add(mm);
        }
        return out;
    }

    private static Object unwrapDataMaybeJsonString(Object data) {
        if (data instanceof Map<?, ?> m) {
            return m;
        }
        if (data instanceof String s && !s.isBlank()) {
            try {
                Object parsed = JSON.parseObject(s);
                if (parsed instanceof Map<?, ?> pm) {
                    return pm;
                }
            } catch (Exception ignore) {
                return null;
            }
        }
        return null;
    }
}
