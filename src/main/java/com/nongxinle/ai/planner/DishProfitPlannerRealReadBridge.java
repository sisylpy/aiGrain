package com.nongxinle.ai.planner;

import com.nongxinle.ai.context.AiResolvedQueryContext;
import com.nongxinle.ai.core.AiRunState;
import com.nongxinle.ai.dto.business.DishProfitAnswerPlan;
import com.nongxinle.ai.graph.business.DishProfitAnswerPlanBuilder;
import com.nongxinle.ai.graph.business.DishProfitQueryToolExecutor;
import com.nongxinle.ai.graph.business.toolrequest.BusinessToolExecutionRequestResolver;
import com.nongxinle.ai.graph.business.toolrequest.DishProfitToolRequestContext;
import com.nongxinle.ai.tool.ToolResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 菜品毛利 Planner 与生产 {@code dish_profit_analysis} 的真实桥接（C-29）：对齐
 * {@link com.nongxinle.ai.agent.business.DishProfitAgent}：
 * {@link BusinessToolExecutionRequestResolver#buildDishProfitRequestContext} →
 * {@link DishProfitQueryToolExecutor#executeDishProfitAnalysis} →
 * {@link com.nongxinle.ai.tool.business.DishProfitAnalysisTool} →
 * {@link DishProfitAnswerPlanBuilder#attachForAgentEnvelope}.
 *
 * <p>真实执行入口：{@link #readWithExecutionContext}；{@link DishProfitPlannerReadBridge#readDishProfit} 仅降级。
 * <strong>禁止</strong>解析用户原文、Bridge 内 SQL。</p>
 *
 * @see DishProfitPlannerExecutionContext
 */
@Component
public class DishProfitPlannerRealReadBridge implements DishProfitPlannerReadBridge {

    public static final String ERROR_NO_PLANNER_EXECUTION_CONTEXT_ON_REQUEST =
            "DISH_PROFIT_REAL_BRIDGE_NO_PLANNER_EXECUTION_CONTEXT_ON_READ";

    public static final String ERROR_NO_RUN_STATE = "ADAPTER_NO_RUN_STATE";
    public static final String ERROR_NO_RESOLVED_CONTEXT = "ADAPTER_NO_RESOLVED_CONTEXT";

    public static final String ERROR_RUN_ID_MISSING = "DISH_PROFIT_RUN_ID_MISSING";
    public static final String ERROR_RUN_ID_UNPARSABLE = "DISH_PROFIT_RUN_ID_UNPARSABLE";

    public static final String ERROR_TOOL_PERMISSION_DEGRADED = "DISH_PROFIT_TOOL_PERMISSION_DEGRADED";
    public static final String ERROR_TOOL_EXECUTION_DEGRADED = "DISH_PROFIT_TOOL_EXECUTION_DEGRADED";
    public static final String ERROR_ANSWER_PLAN_MISSING_AFTER_TOOL = "DISH_PROFIT_ANSWER_PLAN_MISSING_AFTER_TOOL";
    public static final String ERROR_ANSWER_PLAN_ATTACH_DEGRADED = "DISH_PROFIT_ANSWER_PLAN_ATTACH_DEGRADED";
    public static final String ERROR_PAYLOAD_EMPTY = "DISH_PROFIT_TOOL_OK_BUT_EMPTY_OVERVIEW_PAYLOAD";

    /**
     * 无 {@link DishProfitQueryToolExecutor} / {@link BusinessToolExecutionRequestResolver} 时（Harness 内 {@code new} 桥），
     * 即使上下文已物化也不调 Tool。
     */
    public static final String ERROR_SKELETON_NO_TOOL = "DISH_PROFIT_REAL_READ_BRIDGE_SKELETON";

    private final DishProfitQueryToolExecutor dishProfitQueryToolExecutor;
    private final BusinessToolExecutionRequestResolver toolExecutionRequestResolver;

    /** 供 Harness 内 {@code new}；依赖为空时 {@link #readWithExecutionContext} 走骨架降级。 */
    public DishProfitPlannerRealReadBridge() {
        this(null, null);
    }

    @Autowired
    public DishProfitPlannerRealReadBridge(
            DishProfitQueryToolExecutor dishProfitQueryToolExecutor,
            BusinessToolExecutionRequestResolver toolExecutionRequestResolver) {
        this.dishProfitQueryToolExecutor = dishProfitQueryToolExecutor;
        this.toolExecutionRequestResolver = toolExecutionRequestResolver;
    }

    @Override
    public DishProfitPlannerReadResponse readDishProfit(DishProfitPlannerReadRequest request) {
        return degraded(
                ERROR_NO_PLANNER_EXECUTION_CONTEXT_ON_REQUEST,
                "DishProfitPlannerRealReadBridge: use readWithExecutionContext via "
                        + "PlannerAgentAdapterRequest.dishProfitExecutionContext / "
                        + "PlannerExecutionPlan.dishProfitExecutionContext");
    }

    /**
     * 基于显式 {@link DishProfitPlannerExecutionContext} 的菜品毛利只读入口。
     */
    public DishProfitPlannerReadResponse readWithExecutionContext(DishProfitPlannerExecutionContext ctx) {
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

        if (dishProfitQueryToolExecutor == null || toolExecutionRequestResolver == null) {
            return degraded(
                    ERROR_SKELETON_NO_TOOL,
                    "dish_profit_real_bridge:new_or_unwired_dependencies");
        }

        hydrateRunStateFromContext(runState, rq, ctx);

        long rid = resolveRunId(runState, ctx);
        if (rid == -1L) {
            return degraded(ERROR_RUN_ID_MISSING, "neither state.runId nor ctx.runId usable");
        }
        if (rid == -2L) {
            return degraded(ERROR_RUN_ID_UNPARSABLE, "ctx.runId not a valid long");
        }

        DishProfitToolRequestContext dpCtx = toolExecutionRequestResolver.buildDishProfitRequestContext(runState, rq);
        runState.setStatStartDate(dpCtx.getStartDateIso());
        runState.setStatEndDate(dpCtx.getStopDateIso());

        Long dis = runState.getDistributerId() != null ? runState.getDistributerId() : ctx.getDistributerId();
        Long deptScoped = dpCtx.getDepartmentFatherIdForScopedTools();
        Long deptBuild = dpCtx.getDepartmentFatherIdForBuildInsight();

        ToolResult executed =
                dishProfitQueryToolExecutor.executeDishProfitAnalysis(
                        rid,
                        runState,
                        deptScoped,
                        deptBuild,
                        dis,
                        dpCtx.getStartDateIso(),
                        dpCtx.getStopDateIso(),
                        new LinkedHashMap<>());

        if (executed == null) {
            return degraded(
                    ERROR_TOOL_PERMISSION_DEGRADED, "executeDishProfitAnalysis returned null (permission denied)");
        }
        if (!executed.isSuccess()) {
            String msg = executed.getMessage() == null ? "dish_profit_analysis_failed" : executed.getMessage();
            return degraded(ERROR_TOOL_EXECUTION_DEGRADED, msg);
        }

        DishProfitAnswerPlanBuilder.attachForAgentEnvelope(runState, false);
        DishProfitAnswerPlan plan = runState.getDishProfitAnswerPlan();
        if (plan == null) {
            return degraded(
                    ERROR_ANSWER_PLAN_MISSING_AFTER_TOOL,
                    "attachForAgentEnvelope left dishProfitAnswerPlan null (empty or invalid tool payload)");
        }
        if (plan.getDebug() != null && Boolean.TRUE.equals(plan.getDebug().get("degraded"))) {
            Object detail = plan.getDebug().get("degradedDetail");
            return degraded(
                    ERROR_ANSWER_PLAN_ATTACH_DEGRADED, detail == null ? "plan_debug_degraded" : detail.toString());
        }

        return mapPlanToResponse(plan, ctx.getPlannerReadRequest());
    }

    private static void hydrateRunStateFromContext(AiRunState runState, AiResolvedQueryContext rq,
            DishProfitPlannerExecutionContext ctx) {
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

    private static long resolveRunId(AiRunState state, DishProfitPlannerExecutionContext ctx) {
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

    private static DishProfitPlannerReadResponse mapPlanToResponse(
            DishProfitAnswerPlan plan, DishProfitPlannerReadRequest slice) {
        List<Map<String, Object>> focus = plan.getFocusRows() != null ? plan.getFocusRows() : List.of();

        String sales = null;
        String cost = null;
        String rate = null;
        String gpAmt = null;
        if (!focus.isEmpty()) {
            Map<String, Object> r = focus.get(0);
            if (r != null) {
                sales = nzString(r.get("listPriceRevenue"));
                cost = nzString(r.get("actualCostAmount"));
                rate = formatMarginRate(r.get("blendedGrossMarginRateOnListPrice"));
                gpAmt = nzString(r.get("portfolioGrossProfitAmount"));
                if (blankOrNull(gpAmt)) {
                    gpAmt = nzString(r.get("grossProfitAmount"));
                }
            }
        }

        if (focus.isEmpty()) {
            return degraded(ERROR_PAYLOAD_EMPTY, "no focusRows on DishProfitAnswerPlan after attach");
        }

        if (isNoDataToken(sales) && isNoDataToken(cost) && isNoDataToken(gpAmt)) {
            return degraded(ERROR_PAYLOAD_EMPTY, "dish_profit_focus_rows_without_revenue_or_cost_signal");
        }

        String timeLabel = slice != null ? slice.getTimeLabel() : null;
        Map<String, Object> summaryOut = new LinkedHashMap<>();
        summaryOut.put("planType", plan.getPlanType());
        if (timeLabel != null && !timeLabel.isBlank()) {
            summaryOut.put("timeLabel", timeLabel);
        }
        if (slice != null && slice.getStructuredIntentDetail() != null) {
            summaryOut.put("structuredIntentDetail", slice.getStructuredIntentDetail());
        }

        return DishProfitPlannerReadResponse.builder()
                .status(DishProfitPlannerReadStatus.OK)
                .planType(plan.getPlanType())
                .grossProfitAmount(gpAmt)
                .grossProfitRate(rate)
                .salesAmount(sales)
                .costAmount(cost)
                .dishRows(focus.isEmpty() ? new ArrayList<>() : new ArrayList<>(focus))
                .summary(summaryOut)
                .build();
    }

    private static String nzString(Object v) {
        if (v == null) {
            return null;
        }
        String s = v.toString().trim();
        return s.isEmpty() ? null : s;
    }

    private static String formatMarginRate(Object v) {
        if (v == null) {
            return null;
        }
        if (v instanceof Number n) {
            return n.doubleValue() + "%";
        }
        String s = v.toString().trim();
        return s.isEmpty() ? null : s;
    }

    private static boolean isNoDataToken(String s) {
        return blankOrNull(s) || "暂无".equals(s.trim());
    }

    private static DishProfitPlannerReadResponse degraded(String code, String message) {
        return DishProfitPlannerReadResponse.builder()
                .status(DishProfitPlannerReadStatus.DEGRADED)
                .errorCode(code)
                .errorMessage(message)
                .build();
    }

    private static boolean blankOrNull(String s) {
        return s == null || s.isBlank();
    }
}
