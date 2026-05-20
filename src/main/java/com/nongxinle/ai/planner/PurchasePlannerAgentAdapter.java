package com.nongxinle.ai.planner;

import com.nongxinle.ai.tool.business.AiBusinessToolIds;

import java.util.ArrayList;
import java.util.List;

/**
 * C-16/C-17：采购只读 {@link PlannerAgentAdapter}；{@code targetAgent} / {@code targetTool} 与
 * {@link com.nongxinle.ai.agent.business.BusinessAgentNames#PURCHASE_OVERVIEW} /
 * {@link AiBusinessToolIds#PURCHASE_OVERVIEW} 对齐。
 */
public final class PurchasePlannerAgentAdapter implements PlannerAgentAdapter {

    public static final String TARGET_AGENT =
            com.nongxinle.ai.agent.business.BusinessAgentNames.PURCHASE_OVERVIEW;
    public static final String TARGET_TOOL = AiBusinessToolIds.PURCHASE_OVERVIEW;

    public static final String MARKER_NO_REAL_CONTEXT = "ADAPTER_NO_REAL_CONTEXT";
    public static final String MARKER_MISSING_SCOPE = "ADAPTER_MISSING_SCOPE";
    public static final String MARKER_MISSING_TIME = "ADAPTER_MISSING_TIME";

    public static final String HONESTY_MARKER = MARKER_NO_REAL_CONTEXT;

    private final PurchasePlannerReadBridge readBridge;

    public PurchasePlannerAgentAdapter() {
        this(null);
    }

    public PurchasePlannerAgentAdapter(PurchasePlannerReadBridge readBridge) {
        this.readBridge = readBridge;
    }

    @Override
    public boolean supports(String targetAgent, String targetTool) {
        return TARGET_AGENT.equals(targetAgent) || TARGET_TOOL.equals(targetTool);
    }

    @Override
    public PlannerStepExecutionResponse invoke(PlannerAgentAdapterRequest request) {
        if (readBridge == null) {
            return markerDegraded(MARKER_NO_REAL_CONTEXT, "read_bridge_null:purchase_read_not_invoked");
        }
        PurchasePlannerReadRequest readReq = mergeReadRequest(request);
        if (isBlank(readReq.getResolvedQueryContextRef())) {
            return markerDegraded(MARKER_NO_REAL_CONTEXT, "resolved_query_context_ref_missing:purchase_read_not_invoked");
        }
        if (!hasTimeWindow(readReq)) {
            return markerDegraded(MARKER_MISSING_TIME, "time_window_unspecified:timeStart_and_timeEnd_required");
        }
        if (!hasResolvableScope(readReq)) {
            return markerDegraded(
                    MARKER_MISSING_SCOPE,
                    "scope_unspecified:need_scopeType_and_department_or_visibleStores");
        }
        PurchasePlannerReadResponse out;
        if (readBridge instanceof PurchasePlannerRealReadBridge real) {
            PurchasePlannerExecutionContext ctx = request.getPurchaseExecutionContext();
            if (ctx == null) {
                out =
                        PurchasePlannerReadResponse.builder()
                                .status(PurchasePlannerReadStatus.DEGRADED)
                                .errorCode(PurchasePlannerRealReadBridge.ERROR_NO_PLANNER_EXECUTION_CONTEXT_ON_REQUEST)
                                .errorMessage("missing PurchasePlannerExecutionContext on PlannerAgentAdapterRequest")
                                .build();
            } else {
                PurchasePlannerExecutionContext merged =
                        ctx.getPlannerReadRequest() == null
                                ? ctx.toBuilder().plannerReadRequest(readReq).build()
                                : ctx;
                out = real.readWithExecutionContext(merged);
            }
        } else {
            out = readBridge.readPurchase(readReq);
        }
        return mapReadResponse(out);
    }

    private static PurchasePlannerReadRequest mergeReadRequest(PlannerAgentAdapterRequest request) {
        if (request == null) {
            return PurchasePlannerReadRequest.builder().build();
        }
        PurchasePlannerReadRequest nested = request.getPurchaseReadRequest();
        String ref = coalesce(
                nested != null ? nested.getResolvedQueryContextRef() : null, request.getResolvedQueryContextRef());
        String aRef = coalesce(nested != null ? nested.getAnswerPlanRef() : null, request.getAnswerPlanRef());
        if (nested == null) {
            return PurchasePlannerReadRequest.builder()
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

    private static boolean hasTimeWindow(PurchasePlannerReadRequest r) {
        return r.getTimeStart() != null && r.getTimeEnd() != null;
    }

    private static boolean hasResolvableScope(PurchasePlannerReadRequest r) {
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
            for (PurchasePlannerVisibleStore s : r.getVisibleStores()) {
                if (s != null && s.getDepartmentId() != null) {
                    return true;
                }
            }
        }
        return false;
    }

    private static PlannerStepExecutionResponse mapReadResponse(PurchasePlannerReadResponse resp) {
        if (resp == null) {
            return markerDegraded(MARKER_NO_REAL_CONTEXT, "bridge_returned_null:purchase_read_not_invoked");
        }
        PurchasePlannerReadStatus s = resp.getStatus() != null ? resp.getStatus() : PurchasePlannerReadStatus.DEGRADED;
        if (s == PurchasePlannerReadStatus.OK) {
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
        if (s == PurchasePlannerReadStatus.FAILED) {
            return PlannerStepExecutionResponse.builder()
                    .status(PlannerStepStatus.FAILED)
                    .errorMessage(blankToNull(resp.getErrorMessage()))
                    .usedAgents(new ArrayList<>())
                    .usedTools(new ArrayList<>())
                    .build();
        }
        String code = !isBlank(resp.getErrorCode()) ? resp.getErrorCode().trim() : "purchase_read_degraded";
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
