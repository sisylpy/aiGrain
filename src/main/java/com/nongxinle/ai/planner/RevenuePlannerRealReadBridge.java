package com.nongxinle.ai.planner;

import com.nongxinle.ai.context.AiResolvedQueryContext;
import com.nongxinle.ai.core.AiRunState;
import com.nongxinle.ai.graph.business.RevenueQueryToolExecutor;
import com.nongxinle.ai.graph.business.toolrequest.BusinessToolExecutionRequestResolver;
import com.nongxinle.ai.graph.business.toolrequest.RevenueToolRequestResolution;
import com.nongxinle.ai.tool.ToolResult;
import com.nongxinle.ai.tool.business.AiBusinessToolIds;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 营收 Planner 与生产 {@link AiBusinessToolIds#REVENUE_QUERY} 的真实桥接（C-12）。
 * <ul>
 *     <li>执行链路与 {@link com.nongxinle.ai.agent.business.RevenueAgent} 对齐：
 *     {@link BusinessToolExecutionRequestResolver#resolveRevenueToolRequest} →
 *     {@link RevenueQueryToolExecutor#executeRevenueQuery}；读路径仅经现有
 *     {@link com.nongxinle.ai.tool.business.RevenueQueryTool}，<strong>不新写 SQL</strong>，
 *     <strong>不直连</strong> {@code GbAiDailyRevenueService}。</li>
 *     <li><strong>禁止</strong>：解析用户 {@code userMessage}、绕过 {@link AiResolvedQueryContext}、在 Bridge 内拼 SQL。</li>
 *     <li>真实执行入口：{@link #readWithExecutionContext}；{@link RevenuePlannerReadBridge#readRevenue} 在本实现中仅返回
 *     降级，引导调用方从 {@link PlannerAgentAdapterRequest#getRevenueExecutionContext()} /
 *     {@link PlannerExecutionPlan#getRevenueExecutionContext()} 注入上下文。</li>
 * </ul>
 *
 * @see PlannerRevenueExecutionContext
 */
@Component
@RequiredArgsConstructor
public class RevenuePlannerRealReadBridge implements RevenuePlannerReadBridge {

    private final RevenueQueryToolExecutor revenueQueryToolExecutor;
    private final BusinessToolExecutionRequestResolver toolExecutionRequestResolver;

    public static final String ERROR_NO_RUN_STATE = "ADAPTER_NO_RUN_STATE";
    public static final String ERROR_NO_RESOLVED_CONTEXT = "ADAPTER_NO_RESOLVED_CONTEXT";
    public static final String ERROR_NO_PLANNER_EXECUTION_CONTEXT_ON_REQUEST = "REVENUE_REAL_BRIDGE_NO_PLANNER_EXECUTION_CONTEXT";

    public static final String ERROR_RUN_ID_MISSING = "REVENUE_RUN_ID_MISSING";
    public static final String ERROR_RUN_ID_UNPARSABLE = "REVENUE_RUN_ID_UNPARSABLE";

    public static final String ERROR_TOOL_PERMISSION_DENIED = "REVENUE_TOOL_PERMISSION_DENIED";
    public static final String ERROR_TOOL_EXECUTION_FAILED = "REVENUE_TOOL_EXECUTION_FAILED";
    public static final String ERROR_TOOL_RESULT_EMPTY = "REVENUE_TOOL_RESULT_EMPTY";
    public static final String ERROR_TOOL_ENVELOPE_NOT_MAP = "REVENUE_TOOL_ENVELOPE_NOT_MAP";
    public static final String ERROR_TOOL_ENVELOPE_UNSUCCESSFUL = "REVENUE_TOOL_ENVELOPE_UNSUCCESSFUL";
    public static final String ERROR_TOOL_INNER_DATA_MISSING = "REVENUE_TOOL_INNER_DATA_MISSING";
    /** {@code success=true} 但缺少 {@code totalRevenue} / {@code rawStats} / {@code storeRevenueRanking} 任一有效片段。 */
    public static final String ERROR_TOOL_OK_BUT_EMPTY_REVENUE_PAYLOAD = "REVENUE_TOOL_OK_BUT_EMPTY_REVENUE_PAYLOAD";

    @Override
    public RevenuePlannerReadResponse readRevenue(RevenuePlannerReadRequest request) {
        return degraded(
                ERROR_NO_PLANNER_EXECUTION_CONTEXT_ON_REQUEST,
                "RevenuePlannerRealReadBridge: use readWithExecutionContext via "
                        + "PlannerAgentAdapterRequest.revenueExecutionContext / PlannerExecutionPlan.revenueExecutionContext");
    }

    /**
     * 基于显式 {@link PlannerRevenueExecutionContext} 的营收只读入口（与 Graph / Harness 编排对齐）。
     */
    public RevenuePlannerReadResponse readWithExecutionContext(PlannerRevenueExecutionContext ctx) {
        if (ctx == null) {
            return degraded(ERROR_NO_RUN_STATE, "execution_context_null");
        }
        AiRunState runState = ctx.getRunState();
        if (runState == null) {
            String suffix = blankOrNull(ctx.getRunStateRef()) ? "run_state_missing" : "run_state_ref_not_hydrated";
            return degraded(ERROR_NO_RUN_STATE, suffix);
        }
        AiResolvedQueryContext rq = ctx.getResolvedQueryContext();
        if (rq == null) {
            String suffix = blankOrNull(ctx.getResolvedQueryContextRef())
                    ? "resolved_query_context_missing"
                    : "resolved_query_context_ref_not_hydrated";
            return degraded(ERROR_NO_RESOLVED_CONTEXT, suffix);
        }

        hydrateRunStateFromContext(runState, rq, ctx);

        long rid = resolveRunId(runState, ctx);
        if (rid == -1L) {
            return degraded(ERROR_RUN_ID_MISSING, "neither state.runId nor ctx.runId usable");
        }
        if (rid == -2L) {
            return degraded(ERROR_RUN_ID_UNPARSABLE, "ctx.runId not a valid long");
        }

        RevenueToolRequestResolution revenueReq =
                toolExecutionRequestResolver.resolveRevenueToolRequest(runState, rq);
        runState.setStatStartDate(revenueReq.getStartDateIso());
        runState.setStatEndDate(revenueReq.getStopDateIso());

        Long dis = runState.getDistributerId() != null ? runState.getDistributerId() : ctx.getDistributerId();

        ToolResult executed =
                revenueQueryToolExecutor.executeRevenueQuery(
                        rid,
                        runState,
                        revenueReq.getDepartmentFatherIdForScopedTools(),
                        revenueReq.getDepartmentFatherIdForBuildInsight(),
                        dis,
                        revenueReq.getStartDateIso(),
                        revenueReq.getStopDateIso(),
                        new LinkedHashMap<>());

        if (executed == null) {
            return failed(ERROR_TOOL_PERMISSION_DENIED, "executeRevenueQuery returned null (permission denied)");
        }
        if (!executed.isSuccess()) {
            String msg = executed.getMessage() == null ? "revenue_tool_failed" : executed.getMessage();
            return failed(ERROR_TOOL_EXECUTION_FAILED, msg);
        }

        Map<String, Object> envelope =
                runState.getToolResults() == null
                        ? null
                        : castMap(runState.getToolResults().get(AiBusinessToolIds.REVENUE_QUERY));

        RevenuePlannerReadRequest slice = ctx.getPlannerReadRequest();
        return mapEnvelopeToResponse(envelope, slice);
    }

    private static void hydrateRunStateFromContext(AiRunState runState, AiResolvedQueryContext rq,
            PlannerRevenueExecutionContext ctx) {
        if (runState.getResolvedQueryContext() == null) {
            runState.setResolvedQueryContext(rq);
        }
        if (ctx.getUserId() != null && runState.getUserId() == null) {
            runState.setUserId(ctx.getUserId());
        }
        if (ctx.getDepartmentId() != null && runState.getDepartmentId() == null) {
            runState.setDepartmentId(ctx.getDepartmentId());
        }
        if (ctx.getDistributerId() != null && runState.getDistributerId() == null) {
            runState.setDistributerId(ctx.getDistributerId());
        }
    }

    /**
     * @return 非负 runId，或 {@code -1} 表示不可用
     */
    private static long resolveRunId(AiRunState state, PlannerRevenueExecutionContext ctx) {
        if (state.getRunId() != null) {
            return state.getRunId();
        }
        if (!blankOrNull(ctx.getRunId())) {
            try {
                long parsed = Long.parseLong(ctx.getRunId().trim());
                state.setRunId(parsed);
                return parsed;
            } catch (NumberFormatException ex) {
                return -2L;
            }
        }
        return -1L;
    }

    private RevenuePlannerReadResponse mapEnvelopeToResponse(Map<String, Object> envelope,
            RevenuePlannerReadRequest slice) {
        if (envelope == null || envelope.isEmpty()) {
            return failed(ERROR_TOOL_RESULT_EMPTY, "toolResults[revenue_query] missing after execute");
        }
        if (!Boolean.TRUE.equals(envelope.get("success"))) {
            Object msg = envelope.get("message");
            return degraded(
                    ERROR_TOOL_ENVELOPE_UNSUCCESSFUL,
                    msg == null ? "envelope success=false" : msg.toString());
        }

        Object dataRaw = envelope.get("data");
        Map<String, Object> inner = castMap(unwrapDataMaybe(dataRaw));
        if (inner == null) {
            return degraded(ERROR_TOOL_INNER_DATA_MISSING, "envelope.data missing or not a Map");
        }

        BigDecimal totalRevenue = toBigDecimal(inner.get("totalRevenue"));
        boolean rawStatsNonEmpty = inner.get("rawStats") instanceof Map<?, ?> rm && !rm.isEmpty();
        List<Map<String, Object>> ranking = castRankingList(inner.get("storeRevenueRanking"));
        boolean rankingNonEmpty = !ranking.isEmpty();

        if (totalRevenue == null && !rawStatsNonEmpty && !rankingNonEmpty) {
            return degraded(ERROR_TOOL_OK_BUT_EMPTY_REVENUE_PAYLOAD, "no totalRevenue, rawStats or storeRevenueRanking");
        }

        List<RevenuePlannerStoreRevenueRow> rows = mapRankingRows(ranking);
        BigDecimal amountOut = totalRevenue;
        if (amountOut == null && !rows.isEmpty()) {
            amountOut =
                    rows.stream()
                            .map(RevenuePlannerStoreRevenueRow::getAmount)
                            .filter(Objects::nonNull)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
        }

        String timeLabel = slice != null ? slice.getTimeLabel() : null;
        String scopeLabel = slice != null ? slice.getScopeType() : null;

        return RevenuePlannerReadResponse.builder()
                .status(RevenuePlannerReadStatus.OK)
                .revenueAmount(amountOut)
                .storeRows(rows)
                .timeLabel(timeLabel)
                .scopeLabel(scopeLabel)
                .build();
    }

    private static List<Map<String, Object>> castRankingList(Object rankingObj) {
        if (!(rankingObj instanceof List<?> list)) {
            return List.of();
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (Object o : list) {
            Map<String, Object> row = castMap(o);
            if (row != null) {
                out.add(row);
            }
        }
        return out;
    }

    private static List<RevenuePlannerStoreRevenueRow> mapRankingRows(List<Map<String, Object>> ranking) {
        List<RevenuePlannerStoreRevenueRow> rows = new ArrayList<>(ranking.size());
        for (Map<String, Object> r : ranking) {
            Long dept = toLong(r.get("storeDepartmentId"));
            String label = r.get("storeName") != null ? r.get("storeName").toString() : null;
            BigDecimal amt = null;
            Object rev = r.get("revenueAmount");
            if (rev instanceof Number n) {
                amt = BigDecimal.valueOf(n.doubleValue());
            } else if (rev != null) {
                try {
                    amt = new BigDecimal(rev.toString());
                } catch (NumberFormatException ignore) {
                    amt = null;
                }
            }
            rows.add(
                    RevenuePlannerStoreRevenueRow.builder()
                            .departmentId(dept)
                            .storeLabel(label)
                            .amount(amt)
                            .build());
        }
        return rows;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castMap(Object o) {
        if (o instanceof Map<?, ?> m) {
            return (Map<String, Object>) m;
        }
        return null;
    }

    private static Object unwrapDataMaybe(Object dataRaw) {
        if (dataRaw instanceof String s && !s.isBlank()) {
            try {
                Object parsed = com.alibaba.fastjson2.JSON.parse(s);
                return parsed != null ? parsed : dataRaw;
            } catch (Exception ignore) {
                return dataRaw;
            }
        }
        return dataRaw;
    }

    private static BigDecimal toBigDecimal(Object v) {
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
            return new BigDecimal(v.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Long toLong(Object v) {
        if (v == null) {
            return null;
        }
        if (v instanceof Number n) {
            return n.longValue();
        }
        try {
            return Long.parseLong(v.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static boolean blankOrNull(String s) {
        return s == null || s.isBlank();
    }

    private static RevenuePlannerReadResponse degraded(String code, String message) {
        return RevenuePlannerReadResponse.builder()
                .status(RevenuePlannerReadStatus.DEGRADED)
                .errorCode(code)
                .errorMessage(message)
                .build();
    }

    private static RevenuePlannerReadResponse failed(String code, String message) {
        return RevenuePlannerReadResponse.builder()
                .status(RevenuePlannerReadStatus.FAILED)
                .errorCode(code)
                .errorMessage(message)
                .build();
    }
}
