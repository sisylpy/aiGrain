package com.nongxinle.ai.planner;

import java.util.ArrayList;
import java.util.List;

/**
 * C-7/C-8：营收只读 {@link PlannerAgentAdapter}；支持 {@code targetAgent = revenue_overview} 或 {@code targetTool = revenue_query}。
 * <p>
 * 职责：校验 {@link RevenuePlannerReadRequest} 是否可由上下文拼出、调用 {@link RevenuePlannerReadBridge}、将
 * {@link RevenuePlannerReadResponse} 转为 {@link PlannerStepExecutionResponse}。<strong>禁止</strong>解析用户聊天原文。
 * </p>
 */
public final class RevenuePlannerAgentAdapter implements PlannerAgentAdapter {

    public static final String TARGET_AGENT = "revenue_overview";
    public static final String TARGET_TOOL = "revenue_query";

    public static final String MARKER_NO_REAL_CONTEXT = "ADAPTER_NO_REAL_CONTEXT";
    public static final String MARKER_MISSING_SCOPE = "ADAPTER_MISSING_SCOPE";
    public static final String MARKER_MISSING_TIME = "ADAPTER_MISSING_TIME";

    /** 与 Harness / 历史摘要字段对齐；语义上同 {@link #MARKER_NO_REAL_CONTEXT}。 */
    public static final String HONESTY_MARKER = MARKER_NO_REAL_CONTEXT;

    private final RevenuePlannerReadBridge readBridge;

    public RevenuePlannerAgentAdapter() {
        this(null);
    }

    public RevenuePlannerAgentAdapter(RevenuePlannerReadBridge readBridge) {
        this.readBridge = readBridge;
    }

    @Override
    public boolean supports(String targetAgent, String targetTool) {
        return TARGET_AGENT.equals(targetAgent) || TARGET_TOOL.equals(targetTool);
    }

    @Override
    public PlannerStepExecutionResponse invoke(PlannerAgentAdapterRequest request) {
        if (readBridge == null) {
            return markerDegraded(MARKER_NO_REAL_CONTEXT, "read_bridge_null:revenue_read_not_invoked");
        }
        RevenuePlannerReadRequest readReq = mergeReadRequest(request);
        if (isBlank(readReq.getResolvedQueryContextRef())) {
            return markerDegraded(MARKER_NO_REAL_CONTEXT, "resolved_query_context_ref_missing:revenue_read_not_invoked");
        }
        if (!hasTimeWindow(readReq)) {
            return markerDegraded(MARKER_MISSING_TIME, "time_window_unspecified:need_pair_timeStart_timeEnd_or_timeLabel");
        }
        if (!hasResolvableScope(readReq)) {
            return markerDegraded(
                    MARKER_MISSING_SCOPE,
                    "scope_unspecified:need_scopeType_and_department_or_visibleStores");
        }
        RevenuePlannerReadResponse out;
        if (readBridge instanceof RevenuePlannerRealReadBridge real) {
            PlannerRevenueExecutionContext ctx = request.getRevenueExecutionContext();
            if (ctx == null) {
                out =
                        RevenuePlannerReadResponse.builder()
                                .status(RevenuePlannerReadStatus.DEGRADED)
                                .errorCode(RevenuePlannerRealReadBridge.ERROR_NO_PLANNER_EXECUTION_CONTEXT_ON_REQUEST)
                                .errorMessage("missing PlannerRevenueExecutionContext on PlannerAgentAdapterRequest")
                                .build();
            } else {
                PlannerRevenueExecutionContext merged =
                        ctx.getPlannerReadRequest() == null
                                ? ctx.toBuilder().plannerReadRequest(readReq).build()
                                : ctx;
                out = real.readWithExecutionContext(merged);
            }
        } else {
            out = readBridge.readRevenue(readReq);
        }
        return mapReadResponse(out);
    }

    private static RevenuePlannerReadRequest mergeReadRequest(PlannerAgentAdapterRequest request) {
        if (request == null) {
            return RevenuePlannerReadRequest.builder().build();
        }
        RevenuePlannerReadRequest nested = request.getRevenueReadRequest();
        String ref = coalesce(
                nested != null ? nested.getResolvedQueryContextRef() : null, request.getResolvedQueryContextRef());
        String aRef = coalesce(nested != null ? nested.getAnswerPlanRef() : null, request.getAnswerPlanRef());
        if (nested == null) {
            return RevenuePlannerReadRequest.builder()
                    .resolvedQueryContextRef(ref)
                    .answerPlanRef(aRef)
                    .build();
        }
        return nested.toBuilder().resolvedQueryContextRef(ref).answerPlanRef(aRef).build();
    }

    private static String coalesce(String a, String b) {
        if (!isBlank(a)) {
            return a.trim();
        }
        if (!isBlank(b)) {
            return b.trim();
        }
        return null;
    }

    private static boolean hasTimeWindow(RevenuePlannerReadRequest r) {
        boolean pair = r.getTimeStart() != null && r.getTimeEnd() != null;
        boolean label = !isBlank(r.getTimeLabel());
        return pair || label;
    }

    private static boolean hasResolvableScope(RevenuePlannerReadRequest r) {
        if (isBlank(r.getScopeType())) {
            return false;
        }
        if (r.getTargetStoreDepartmentId() != null) {
            return true;
        }
        if (r.getQueryDepartmentIds() != null) {
            for (Long id : r.getQueryDepartmentIds()) {
                if (id != null) {
                    return true;
                }
            }
        }
        if (r.getVisibleStores() != null) {
            for (RevenuePlannerVisibleStore s : r.getVisibleStores()) {
                if (s != null && s.getDepartmentId() != null) {
                    return true;
                }
            }
        }
        return false;
    }

    private static PlannerStepExecutionResponse mapReadResponse(RevenuePlannerReadResponse resp) {
        if (resp == null) {
            return markerDegraded(MARKER_NO_REAL_CONTEXT, "bridge_returned_null:revenue_read_not_invoked");
        }
        RevenuePlannerReadStatus s = resp.getStatus() != null ? resp.getStatus() : RevenuePlannerReadStatus.DEGRADED;
        if (s == RevenuePlannerReadStatus.OK) {
            List<String> agents = new ArrayList<>();
            agents.add(TARGET_AGENT);
            List<String> tools = new ArrayList<>();
            tools.add(TARGET_TOOL);
            return PlannerStepExecutionResponse.builder()
                    .status(PlannerStepStatus.SUCCESS)
                    .usedAgents(agents)
                    .usedTools(tools)
                    .build();
        }
        if (s == RevenuePlannerReadStatus.FAILED) {
            return PlannerStepExecutionResponse.builder()
                    .status(PlannerStepStatus.FAILED)
                    .errorMessage(blankToNull(resp.getErrorMessage()))
                    .usedAgents(new ArrayList<>())
                    .usedTools(new ArrayList<>())
                    .build();
        }
        String code = !isBlank(resp.getErrorCode()) ? resp.getErrorCode().trim() : "revenue_read_degraded";
        String msg = blankToNull(resp.getErrorMessage());
        String degraded = msg != null ? code + ":" + msg : code;
        return PlannerStepExecutionResponse.builder()
                .status(PlannerStepStatus.DEGRADED)
                .degradedReason(degraded)
                .usedAgents(new ArrayList<>())
                .usedTools(new ArrayList<>())
                .build();
    }

    private static PlannerStepExecutionResponse markerDegraded(String marker, String detail) {
        return PlannerStepExecutionResponse.builder()
                .status(PlannerStepStatus.DEGRADED)
                .errorMessage(null)
                .degradedReason(marker + ":" + detail)
                .usedAgents(new ArrayList<>())
                .usedTools(new ArrayList<>())
                .build();
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private static String blankToNull(String s) {
        return isBlank(s) ? null : s.trim();
    }
}
