package com.nongxinle.ai.planner;

import com.nongxinle.ai.tool.business.AiBusinessToolIds;

import java.util.ArrayList;
import java.util.List;

/**
 * C-21：出库/核销只读 {@link PlannerAgentAdapter}；{@code targetAgent} / {@code targetTool} 与
 * {@link com.nongxinle.ai.agent.business.BusinessAgentNames#STOCK_REDUCE_QUERY} /
 * {@link AiBusinessToolIds#STOCK_REDUCE_QUERY} 对齐。
 *
 * <p>{@link StockReducePlannerRealReadBridge} 走 {@code readWithExecutionContext}；其它 {@link StockReducePlannerReadBridge}
 * 走 {@code readStockReduce}。无 Bridge 时诚实 {@code DEGRADED}。</p>
 */
public final class StockReducePlannerAgentAdapter implements PlannerAgentAdapter {

    public static final String TARGET_AGENT = com.nongxinle.ai.agent.business.BusinessAgentNames.STOCK_REDUCE_QUERY;
    public static final String TARGET_TOOL = AiBusinessToolIds.STOCK_REDUCE_QUERY;

    public static final String MARKER_NO_REAL_CONTEXT = "ADAPTER_NO_REAL_CONTEXT";
    public static final String MARKER_MISSING_SCOPE = "ADAPTER_MISSING_SCOPE";
    public static final String MARKER_MISSING_TIME = "ADAPTER_MISSING_TIME";

    public static final String HONESTY_MARKER = MARKER_NO_REAL_CONTEXT;

    private final StockReducePlannerReadBridge readBridge;

    public StockReducePlannerAgentAdapter() {
        this(null);
    }

    public StockReducePlannerAgentAdapter(StockReducePlannerReadBridge readBridge) {
        this.readBridge = readBridge;
    }

    @Override
    public boolean supports(String targetAgent, String targetTool) {
        return TARGET_AGENT.equals(targetAgent) || TARGET_TOOL.equals(targetTool);
    }

    @Override
    public PlannerStepExecutionResponse invoke(PlannerAgentAdapterRequest request) {
        if (readBridge == null) {
            return markerDegraded(MARKER_NO_REAL_CONTEXT, "read_bridge_null:stock_reduce_read_not_invoked");
        }
        StockReducePlannerReadRequest readReq = mergeReadRequest(request);
        if (isBlank(readReq.getResolvedQueryContextRef())) {
            return markerDegraded(
                    MARKER_NO_REAL_CONTEXT, "resolved_query_context_ref_missing:stock_reduce_read_not_invoked");
        }
        if (!hasTimeWindow(readReq)) {
            return markerDegraded(MARKER_MISSING_TIME, "time_window_unspecified:need_pair_timeStart_timeEnd_or_timeLabel");
        }
        if (!hasResolvableScope(readReq)) {
            return markerDegraded(
                    MARKER_MISSING_SCOPE,
                    "scope_unspecified:need_scopeType_and_department_or_visibleStores");
        }
        StockReducePlannerReadResponse out;
        if (readBridge instanceof StockReducePlannerRealReadBridge real) {
            StockReducePlannerExecutionContext ctx = request.getStockReduceExecutionContext();
            if (ctx == null) {
                out =
                        StockReducePlannerReadResponse.builder()
                                .status(StockReducePlannerReadStatus.DEGRADED)
                                .errorCode(StockReducePlannerRealReadBridge.ERROR_NO_PLANNER_EXECUTION_CONTEXT_ON_REQUEST)
                                .errorMessage("missing StockReducePlannerExecutionContext on PlannerAgentAdapterRequest")
                                .build();
            } else {
                StockReducePlannerExecutionContext merged =
                        ctx.getPlannerReadRequest() == null
                                ? ctx.toBuilder().plannerReadRequest(readReq).build()
                                : ctx;
                out = real.readWithExecutionContext(merged);
            }
        } else {
            out = readBridge.readStockReduce(readReq);
        }
        return mapReadResponse(out);
    }

    private static StockReducePlannerReadRequest mergeReadRequest(PlannerAgentAdapterRequest request) {
        if (request == null) {
            return StockReducePlannerReadRequest.builder().build();
        }
        StockReducePlannerReadRequest nested = request.getStockReduceReadRequest();
        String ref = coalesce(
                nested != null ? nested.getResolvedQueryContextRef() : null, request.getResolvedQueryContextRef());
        String aRef = coalesce(nested != null ? nested.getAnswerPlanRef() : null, request.getAnswerPlanRef());
        if (nested == null) {
            return StockReducePlannerReadRequest.builder()
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

    private static boolean hasTimeWindow(StockReducePlannerReadRequest r) {
        boolean pair = r.getTimeStart() != null && r.getTimeEnd() != null;
        boolean label = !isBlank(r.getTimeLabel());
        return pair || label;
    }

    private static boolean hasResolvableScope(StockReducePlannerReadRequest r) {
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
            for (StockReducePlannerVisibleStore s : r.getVisibleStores()) {
                if (s != null && s.getDepartmentId() != null) {
                    return true;
                }
            }
        }
        return false;
    }

    private static PlannerStepExecutionResponse mapReadResponse(StockReducePlannerReadResponse resp) {
        if (resp == null) {
            return markerDegraded(MARKER_NO_REAL_CONTEXT, "bridge_returned_null:stock_reduce_read_not_invoked");
        }
        StockReducePlannerReadStatus s =
                resp.getStatus() != null ? resp.getStatus() : StockReducePlannerReadStatus.DEGRADED;
        if (s == StockReducePlannerReadStatus.OK) {
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
        if (s == StockReducePlannerReadStatus.FAILED) {
            return PlannerStepExecutionResponse.builder()
                    .status(PlannerStepStatus.FAILED)
                    .errorMessage(blankToNull(resp.getErrorMessage()))
                    .usedAgents(new ArrayList<>())
                    .usedTools(new ArrayList<>())
                    .build();
        }
        String code = !isBlank(resp.getErrorCode()) ? resp.getErrorCode().trim() : "stock_reduce_read_degraded";
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
