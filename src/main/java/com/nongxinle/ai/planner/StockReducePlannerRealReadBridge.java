package com.nongxinle.ai.planner;

import com.nongxinle.ai.context.AiResolvedQueryContext;
import com.nongxinle.ai.core.AiRunState;
import com.nongxinle.ai.dto.business.StockReduceAnswerPlan;
import com.nongxinle.ai.graph.business.StockReduceAnswerPlanBuilder;
import com.nongxinle.ai.graph.business.StockReduceQueryToolExecutor;
import com.nongxinle.ai.graph.business.toolrequest.BusinessToolExecutionRequestResolver;
import com.nongxinle.ai.graph.business.toolrequest.StockReduceToolRequestContext;
import com.nongxinle.ai.tool.ToolResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 出库/核销 Planner 与生产 {@code stock_reduce_query} 的真实桥接：与 {@link com.nongxinle.ai.agent.business.StockReduceAgent}
 * 对齐 — {@link BusinessToolExecutionRequestResolver#buildStockReduceRequestContext} →
 * {@link StockReduceQueryToolExecutor#executeStockReduceQuery} → {@link com.nongxinle.ai.tool.business.StockReduceQueryTool}，
 * 成功则 {@link StockReduceAnswerPlanBuilder#attachIfApplicable}。
 *
 * <p>真实执行入口：{@link #readWithExecutionContext}；{@link StockReducePlannerReadBridge#readStockReduce} 仅降级。<strong>禁止</strong>解析用户原文、Bridge 内 SQL。</p>
 *
 * <p>C-22：无 Spring 依赖时（Harness 内 {@code new StockReducePlannerRealReadBridge()}）在上下文齐全后仍返回
 * {@link #ERROR_SKELETON_NO_TOOL}，不调用 Executor。</p>
 *
 * @see StockReducePlannerExecutionContext
 */
@Component
public class StockReducePlannerRealReadBridge implements StockReducePlannerReadBridge {

    public static final String ERROR_NO_PLANNER_EXECUTION_CONTEXT_ON_REQUEST =
            "STOCK_REDUCE_REAL_BRIDGE_NO_PLANNER_EXECUTION_CONTEXT_ON_READ";

    public static final String ERROR_NO_RUN_STATE = "ADAPTER_NO_RUN_STATE";
    public static final String ERROR_NO_RESOLVED_CONTEXT = "ADAPTER_NO_RESOLVED_CONTEXT";

    public static final String ERROR_RUN_ID_MISSING = "STOCK_REDUCE_RUN_ID_MISSING";
    public static final String ERROR_RUN_ID_UNPARSABLE = "STOCK_REDUCE_RUN_ID_UNPARSABLE";

    public static final String ERROR_TOOL_PERMISSION_DEGRADED = "STOCK_REDUCE_TOOL_PERMISSION_DEGRADED";
    public static final String ERROR_TOOL_EXECUTION_DEGRADED = "STOCK_REDUCE_TOOL_EXECUTION_DEGRADED";
    public static final String ERROR_ANSWER_PLAN_MISSING_AFTER_TOOL = "STOCK_REDUCE_ANSWER_PLAN_MISSING_AFTER_TOOL";
    public static final String ERROR_ANSWER_PLAN_ATTACH_DEGRADED = "STOCK_REDUCE_ANSWER_PLAN_ATTACH_DEGRADED";
    public static final String ERROR_PAYLOAD_EMPTY = "STOCK_REDUCE_TOOL_OK_BUT_EMPTY_OVERVIEW_PAYLOAD";

    /**
     * 无 {@link StockReduceQueryToolExecutor} / {@link BusinessToolExecutionRequestResolver} 时（C-22 {@code new} 桥），
     * 即使上下文已物化也不调 Tool。
     */
    public static final String ERROR_SKELETON_NO_TOOL = "STOCK_REDUCE_REAL_READ_BRIDGE_SKELETON";

    private final StockReduceQueryToolExecutor stockReduceQueryToolExecutor;
    private final BusinessToolExecutionRequestResolver toolExecutionRequestResolver;

    /** C-22：供 Harness 内 {@code new}；依赖为空时 {@link #readWithExecutionContext} 走骨架降级。 */
    public StockReducePlannerRealReadBridge() {
        this(null, null);
    }

    @Autowired
    public StockReducePlannerRealReadBridge(
            StockReduceQueryToolExecutor stockReduceQueryToolExecutor,
            BusinessToolExecutionRequestResolver toolExecutionRequestResolver) {
        this.stockReduceQueryToolExecutor = stockReduceQueryToolExecutor;
        this.toolExecutionRequestResolver = toolExecutionRequestResolver;
    }

    @Override
    public StockReducePlannerReadResponse readStockReduce(StockReducePlannerReadRequest request) {
        return degraded(
                ERROR_NO_PLANNER_EXECUTION_CONTEXT_ON_REQUEST,
                "StockReducePlannerRealReadBridge: use readWithExecutionContext via "
                        + "PlannerAgentAdapterRequest.stockReduceExecutionContext / "
                        + "PlannerExecutionPlan.stockReduceExecutionContext");
    }

    /**
     * 基于显式 {@link StockReducePlannerExecutionContext} 的出库/核销只读入口。
     */
    public StockReducePlannerReadResponse readWithExecutionContext(StockReducePlannerExecutionContext ctx) {
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
            String suffix =
                    blankOrNull(ctx.getResolvedQueryContextRef())
                            ? "resolved_query_context_missing"
                            : "resolved_query_context_ref_not_hydrated";
            return degraded(ERROR_NO_RESOLVED_CONTEXT, suffix);
        }

        if (stockReduceQueryToolExecutor == null || toolExecutionRequestResolver == null) {
            return degraded(
                    ERROR_SKELETON_NO_TOOL,
                    "c22_skeleton:stock_reduce_query_tool_executor_not_wired");
        }

        hydrateRunStateFromContext(runState, rq, ctx);

        long rid = resolveRunId(runState, ctx);
        if (rid == -1L) {
            return degraded(ERROR_RUN_ID_MISSING, "neither state.runId nor ctx.runId usable");
        }
        if (rid == -2L) {
            return degraded(ERROR_RUN_ID_UNPARSABLE, "ctx.runId not a valid long");
        }

        StockReduceToolRequestContext srCtx = toolExecutionRequestResolver.buildStockReduceRequestContext(runState, rq);
        runState.setStatStartDate(srCtx.getStartDateIso());
        runState.setStatEndDate(srCtx.getEndDateIso());

        Long dis = runState.getDistributerId() != null ? runState.getDistributerId() : ctx.getDistributerId();

        ToolResult executed =
                stockReduceQueryToolExecutor.executeStockReduceQuery(
                        rid,
                        runState,
                        srCtx.getDepartmentFatherIdForScopedTools(),
                        dis,
                        srCtx.getStartDateIso(),
                        srCtx.getEndDateIso(),
                        new LinkedHashMap<>());

        if (executed == null) {
            return degraded(ERROR_TOOL_PERMISSION_DEGRADED, "executeStockReduceQuery returned null (permission denied)");
        }
        if (!executed.isSuccess()) {
            String msg =
                    executed.getMessage() == null ? "stock_reduce_query_failed" : executed.getMessage();
            return degraded(ERROR_TOOL_EXECUTION_DEGRADED, msg);
        }

        StockReduceAnswerPlanBuilder.attachIfApplicable(runState);
        StockReduceAnswerPlan plan = runState.getStockReduceAnswerPlan();
        if (plan == null) {
            return degraded(
                    ERROR_ANSWER_PLAN_MISSING_AFTER_TOOL,
                    "attachIfApplicable left stockReduceAnswerPlan null (empty or invalid tool payload)");
        }
        if (plan.getDebug() != null && plan.getDebug().get("failureReason") != null) {
            Object detail = plan.getDebug().get("failureDetail");
            return degraded(
                    ERROR_ANSWER_PLAN_ATTACH_DEGRADED,
                    detail == null ? "attach_failure" : detail.toString());
        }

        return mapPlanToResponse(plan, ctx.getPlannerReadRequest());
    }

    private static void hydrateRunStateFromContext(AiRunState runState, AiResolvedQueryContext rq,
            StockReducePlannerExecutionContext ctx) {
        if (runState.getResolvedQueryContext() == null) {
            runState.setResolvedQueryContext(rq);
        }
        if (runState.getToolResults() == null) {
            runState.setToolResults(new LinkedHashMap<>());
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

    private static long resolveRunId(AiRunState state, StockReducePlannerExecutionContext ctx) {
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

    private static StockReducePlannerReadResponse mapPlanToResponse(StockReduceAnswerPlan plan,
            StockReducePlannerReadRequest slice) {
        Map<String, Object> summary = plan.getSummary() != null ? plan.getSummary() : Map.of();
        BigDecimal grand = toBigDecimal(summary.get("grandTotalFourTypes"));
        BigDecimal produce = toBigDecimal(summary.get("produceTotal"));
        BigDecimal waste = toBigDecimal(summary.get("wasteTotal"));
        BigDecimal loss = toBigDecimal(summary.get("lossTotal"));
        BigDecimal ret = toBigDecimal(summary.get("returnTotal"));
        String totalsBasis = summary.get("totalsBasis") != null ? summary.get("totalsBasis").toString() : null;

        List<Map<String, Object>> focus = plan.getFocusRows() != null ? plan.getFocusRows() : List.of();

        if (grand == null && produce == null && waste == null && loss == null && ret == null) {
            boolean anyFocus =
                    focus.stream()
                            .filter(Objects::nonNull)
                            .anyMatch(
                                    r -> {
                                        Object a = r.get("amount");
                                        return toBigDecimal(a) != null;
                                    });
            if (!anyFocus) {
                return degraded(ERROR_PAYLOAD_EMPTY, "no overview totals in plan summary or focus rows");
            }
        }

        String timeLabel = slice != null ? slice.getTimeLabel() : null;
        Map<String, Object> summaryOut = summary.isEmpty() ? new LinkedHashMap<>() : new LinkedHashMap<>(summary);
        if (timeLabel != null && !timeLabel.isBlank()) {
            summaryOut.putIfAbsent("timeLabel", timeLabel);
        }

        return StockReducePlannerReadResponse.builder()
                .status(StockReducePlannerReadStatus.OK)
                .grandTotalAmount(grand)
                .produceTotal(produce)
                .wasteTotal(waste)
                .lossTotal(loss)
                .returnTotal(ret)
                .totalsBasis(totalsBasis)
                .summary(summaryOut)
                .focusRows(focus.isEmpty() ? new ArrayList<>() : new ArrayList<>(focus))
                .secondaryRows(
                        plan.getSecondaryRows() == null ? new ArrayList<>() : new ArrayList<>(plan.getSecondaryRows()))
                .build();
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

    private static boolean blankOrNull(String s) {
        return s == null || s.isBlank();
    }

    private static StockReducePlannerReadResponse degraded(String code, String message) {
        return StockReducePlannerReadResponse.builder()
                .status(StockReducePlannerReadStatus.DEGRADED)
                .errorCode(code)
                .errorMessage(message)
                .build();
    }
}
