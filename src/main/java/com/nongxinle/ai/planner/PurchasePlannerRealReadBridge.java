package com.nongxinle.ai.planner;

import com.nongxinle.ai.context.AiResolvedQueryContext;
import com.nongxinle.ai.core.AiRunState;
import com.nongxinle.ai.dto.business.PurchaseAnswerPlan;
import com.nongxinle.ai.graph.business.PurchaseAnswerPlanBuilder;
import com.nongxinle.ai.graph.business.PurchaseOverviewToolExecutor;
import com.nongxinle.ai.graph.business.toolrequest.BusinessToolExecutionRequestResolver;
import com.nongxinle.ai.graph.business.toolrequest.PurchaseToolRequestContext;
import com.nongxinle.ai.tool.ToolResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 采购 Planner 与生产 {@code purchase_overview} 的真实桥接：与 {@link com.nongxinle.ai.agent.business.PurchaseAgent}
 * 对齐 — {@link BusinessToolExecutionRequestResolver#buildPurchaseRequestContext} →
 * {@link PurchaseOverviewToolExecutor#executePurchaseOverview} → {@link com.nongxinle.ai.tool.business.PurchaseOverviewTool}，
 * 成功则 {@link PurchaseAnswerPlanBuilder#attachIfApplicable}。
 *
 * <p>真实执行入口：{@link #readWithExecutionContext}；{@link PurchasePlannerReadBridge#readPurchase} 仅降级（引导注入
 * {@link PlannerAgentAdapterRequest#getPurchaseExecutionContext()}）。<strong>禁止</strong>解析用户原文、Bridge 内 SQL。</p>
 *
 * @see PurchasePlannerExecutionContext
 */
@Component
@RequiredArgsConstructor
public class PurchasePlannerRealReadBridge implements PurchasePlannerReadBridge {

    private final PurchaseOverviewToolExecutor purchaseOverviewToolExecutor;
    private final BusinessToolExecutionRequestResolver toolExecutionRequestResolver;

    public static final String ERROR_NO_PLANNER_EXECUTION_CONTEXT_ON_REQUEST =
            "PURCHASE_REAL_BRIDGE_NO_PLANNER_EXECUTION_CONTEXT";

    public static final String ERROR_NO_RUN_STATE = "ADAPTER_NO_RUN_STATE";
    public static final String ERROR_NO_RESOLVED_CONTEXT = "ADAPTER_NO_RESOLVED_CONTEXT";

    public static final String ERROR_RUN_ID_MISSING = "PURCHASE_RUN_ID_MISSING";
    public static final String ERROR_RUN_ID_UNPARSABLE = "PURCHASE_RUN_ID_UNPARSABLE";

    public static final String ERROR_TOOL_PERMISSION_DENIED = "PURCHASE_TOOL_PERMISSION_DENIED";
    public static final String ERROR_TOOL_EXECUTION_FAILED = "PURCHASE_TOOL_EXECUTION_FAILED";
    public static final String ERROR_ANSWER_PLAN_MISSING_AFTER_TOOL = "PURCHASE_ANSWER_PLAN_MISSING_AFTER_TOOL";
    public static final String ERROR_ANSWER_PLAN_DEBUG_DEGRADED = "PURCHASE_ANSWER_PLAN_DEBUG_DEGRADED";
    public static final String ERROR_PURCHASE_PAYLOAD_EMPTY = "PURCHASE_TOOL_OK_BUT_EMPTY_PURCHASE_PAYLOAD";

    /** @deprecated 预留；C-19 起上下文齐全时走真实 Tool，不再返回此码。 */
    @Deprecated
    public static final String ERROR_SKELETON_NO_TOOL = "PURCHASE_REAL_READ_BRIDGE_SKELETON";

    @Override
    public PurchasePlannerReadResponse readPurchase(PurchasePlannerReadRequest request) {
        return degraded(
                ERROR_NO_PLANNER_EXECUTION_CONTEXT_ON_REQUEST,
                "PurchasePlannerRealReadBridge: use readWithExecutionContext via "
                        + "PlannerAgentAdapterRequest.purchaseExecutionContext / "
                        + "PlannerExecutionPlan.purchaseExecutionContext");
    }

    /**
     * 基于显式 {@link PurchasePlannerExecutionContext} 的采购只读入口。
     */
    public PurchasePlannerReadResponse readWithExecutionContext(PurchasePlannerExecutionContext ctx) {
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

        hydrateRunStateFromContext(runState, rq, ctx);

        long rid = resolveRunId(runState, ctx);
        if (rid == -1L) {
            return degraded(ERROR_RUN_ID_MISSING, "neither state.runId nor ctx.runId usable");
        }
        if (rid == -2L) {
            return degraded(ERROR_RUN_ID_UNPARSABLE, "ctx.runId not a valid long");
        }

        PurchaseToolRequestContext purchaseCtx = toolExecutionRequestResolver.buildPurchaseRequestContext(runState, rq);
        runState.setStatStartDate(purchaseCtx.getStartDateIso());
        runState.setStatEndDate(purchaseCtx.getEndDateIso());

        Long dis = runState.getDistributerId() != null ? runState.getDistributerId() : ctx.getDistributerId();

        ToolResult executed =
                purchaseOverviewToolExecutor.executePurchaseOverview(
                        rid,
                        runState,
                        purchaseCtx.getDepartmentFatherIdForScopedTools(),
                        dis,
                        purchaseCtx.getStartDateIso(),
                        purchaseCtx.getEndDateIso(),
                        new LinkedHashMap<>());

        if (executed == null) {
            return failed(ERROR_TOOL_PERMISSION_DENIED, "executePurchaseOverview returned null (permission denied)");
        }
        if (!executed.isSuccess()) {
            String msg = executed.getMessage() == null ? "purchase_overview_failed" : executed.getMessage();
            return failed(ERROR_TOOL_EXECUTION_FAILED, msg);
        }

        PurchaseAnswerPlanBuilder.attachIfApplicable(runState);
        PurchaseAnswerPlan plan = runState.getPurchaseAnswerPlan();
        if (plan == null) {
            return degraded(
                    ERROR_ANSWER_PLAN_MISSING_AFTER_TOOL,
                    "attachIfApplicable left purchaseAnswerPlan null (empty or invalid tool payload)");
        }
        if (plan.getDebug() != null && Boolean.TRUE.equals(plan.getDebug().get("degraded"))) {
            return degraded(
                    ERROR_ANSWER_PLAN_DEBUG_DEGRADED,
                    "purchaseAnswerPlan.debug.degraded=true");
        }

        return mapPlanToResponse(plan, ctx.getPlannerReadRequest());
    }

    private static void hydrateRunStateFromContext(AiRunState runState, AiResolvedQueryContext rq,
            PurchasePlannerExecutionContext ctx) {
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

    private static long resolveRunId(AiRunState state, PurchasePlannerExecutionContext ctx) {
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

    private PurchasePlannerReadResponse mapPlanToResponse(PurchaseAnswerPlan plan, PurchasePlannerReadRequest slice) {
        Map<String, Object> summary = plan.getSummary() != null ? plan.getSummary() : Map.of();
        BigDecimal amt = toBigDecimal(summary.get("totalPurchaseAmount"));
        Long cnt = toLong(summary.get("purchaseOrderCount"));
        List<Map<String, Object>> focus = plan.getFocusRows() != null ? plan.getFocusRows() : List.of();

        if (amt == null && cnt == null) {
            boolean anyFocus =
                    focus.stream()
                            .filter(Objects::nonNull)
                            .anyMatch(
                                    r -> {
                                        Object a = r.get("totalPurchaseAmount");
                                        Object c = r.get("purchaseOrderCount");
                                        return toBigDecimal(a) != null || toLong(c) != null;
                                    });
            if (!anyFocus) {
                return degraded(ERROR_PURCHASE_PAYLOAD_EMPTY, "no totalPurchaseAmount or purchaseOrderCount in plan");
            }
        }

        String timeLabel = slice != null ? slice.getTimeLabel() : null;
        Map<String, Object> summaryOut = summary.isEmpty() ? new LinkedHashMap<>() : new LinkedHashMap<>(summary);
        if (timeLabel != null && !timeLabel.isBlank()) {
            summaryOut.putIfAbsent("timeLabel", timeLabel);
        }

        return PurchasePlannerReadResponse.builder()
                .status(PurchasePlannerReadStatus.OK)
                .purchaseAmount(amt)
                .purchaseCount(cnt)
                .purchaseSourceType(plan.getPurchaseSourceType())
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

    private static PurchasePlannerReadResponse degraded(String code, String message) {
        return PurchasePlannerReadResponse.builder()
                .status(PurchasePlannerReadStatus.DEGRADED)
                .errorCode(code)
                .errorMessage(message)
                .build();
    }

    private static PurchasePlannerReadResponse failed(String code, String message) {
        return PurchasePlannerReadResponse.builder()
                .status(PurchasePlannerReadStatus.FAILED)
                .errorCode(code)
                .errorMessage(message)
                .build();
    }
}
