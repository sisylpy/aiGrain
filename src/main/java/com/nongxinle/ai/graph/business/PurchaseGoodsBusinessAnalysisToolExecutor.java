package com.nongxinle.ai.graph.business;

import com.nongxinle.ai.context.AiResolvedQueryContext;
import com.nongxinle.ai.core.AiRunState;
import com.nongxinle.ai.graph.business.execution.PurchaseSemanticExecutionArgs;
import com.nongxinle.ai.identity.BusinessEntityIdentityScopeSupport;
import com.nongxinle.ai.security.AiPermissionDenied;
import com.nongxinle.ai.security.AiPermissionGuard;
import com.nongxinle.ai.tool.ToolRequest;
import com.nongxinle.ai.tool.ToolResult;
import com.nongxinle.ai.tool.business.AiBusinessToolIds;
import com.nongxinle.ai.trace.AiSseEventPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * {@link AiBusinessToolIds#PURCHASE_GOODS_BUSINESS_ANALYSIS} 执行入口；与 {@link PurchaseOverviewToolExecutor} 隔离，
 * 不写入 {@code purchase_overview} toolResults。
 */
@Component
@RequiredArgsConstructor
public class PurchaseGoodsBusinessAnalysisToolExecutor {

    private final PurchaseGoodsBusinessAnalysisSupport support;
    private final AiPermissionGuard permissionGuard;
    private final AiSseEventPublisher publisher;

    public ToolResult execute(
            long rid,
            AiRunState state,
            Long deptForScopedTools,
            Long dis,
            String start,
            String stop,
            Map<String, Object> toolEnvelopes) {
        String toolId = AiBusinessToolIds.PURCHASE_GOODS_BUSINESS_ANALYSIS;
        Map<String, Object> args = buildToolArgs(deptForScopedTools, dis, start, stop, state);
        ToolRequest req =
                ToolRequest.builder()
                        .runId(rid)
                        .userId(state.getUserId())
                        .toolName(toolId)
                        .args(args)
                        .resolvedQueryContext(state.getResolvedQueryContext())
                        .build();

        var perm = permissionGuard.evaluateToolInvocation(state, req);
        if (!perm.isAllowed()) {
            AiPermissionDenied denial = perm.getDenial();
            if (denial != null) {
                state.getPermissionDenials().add(denial);
            }
            LinkedHashMap<String, Object> ex = new LinkedHashMap<>();
            ex.put("tool", toolId);
            publisher.publishError(
                    rid,
                    denial != null ? denial.getReason() : "无权调用工具 " + toolId,
                    "tool permission denied",
                    "TOOL_PERMISSION_DENIED",
                    "BusinessError",
                    ex);
            return null;
        }

        LinkedHashMap<String, Object> debug = new LinkedHashMap<>();
        AiResolvedQueryContext rq = state.getResolvedQueryContext();
        Integer disIdHint = BusinessEntityIdentityScopeSupport.disIdFromToolArgs(args);
        Map<String, Object> core = support.buildPayload(state, rq, args, debug, disIdHint);

        LinkedHashMap<String, Object> data = new LinkedHashMap<>();
        data.put(PurchaseGoodsBusinessAnalysisSupport.PAYLOAD_KEY, core);
        data.put("debug", debug);

        boolean ok = "OK".equalsIgnoreCase(str(core.get("status")));
        ToolResult result =
                ToolResult.builder()
                        .success(ok)
                        .message(ok ? "ok" : str(core.get("failureReason")))
                        .data(
                                com.nongxinle.ai.tool.business.AiBusinessToolResponses.envelope(
                                        toolId,
                                        ok,
                                        false,
                                        start,
                                        stop,
                                        deptForScopedTools,
                                        dis,
                                        data,
                                        ok ? null : str(core.get("failureReason"))))
                        .build();

        if (toolEnvelopes != null) {
            toolEnvelopes.put(toolId, result.getData());
        }
        if (state.getToolResults() == null) {
            state.setToolResults(new HashMap<>());
        }
        state.getToolResults().put(toolId, result.getData());
        return result;
    }

    public static Map<String, Object> buildToolArgs(
            Long dept, Long dis, String start, String stop, AiRunState state) {
        Map<String, Object> m =
                new HashMap<>(PurchaseOverviewToolExecutor.buildPurchaseOverviewToolArgs(dept, dis, start, stop, state));
        AiResolvedQueryContext rq = state == null ? null : state.getResolvedQueryContext();
        PurchaseSemanticExecutionArgs.applyToToolArgs(m, rq);
        return m;
    }

    private static String str(Object o) {
        return o == null ? "" : o.toString().trim();
    }
}
