package com.nongxinle.ai.graph.business;

import com.nongxinle.ai.core.AiRunState;
import com.nongxinle.ai.graph.business.scope.BusinessScopeResolutionSupport;
import com.nongxinle.ai.mapping.AiRoleMapper;
import com.nongxinle.ai.security.AiPermissionDenied;
import com.nongxinle.ai.security.AiPermissionGuard;
import com.nongxinle.ai.tool.ToolRegistry;
import com.nongxinle.ai.tool.ToolRequest;
import com.nongxinle.ai.tool.ToolResult;
import com.nongxinle.ai.tool.business.AiBusinessToolIds;
import com.nongxinle.ai.trace.AiSseEventPublisher;
import com.alibaba.fastjson2.JSON;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 抽取 {@link AiBusinessToolIds#REVENUE_QUERY} 调用逻辑，供 {@link BusinessToolExecutionNode} 与
 * {@link com.nongxinle.ai.agent.business.RevenueAgent} 共用，避免 Spring 循环依赖。
 */
@Component
@RequiredArgsConstructor
public class RevenueQueryToolExecutor {

    private final ToolRegistry toolRegistry;
    private final AiPermissionGuard permissionGuard;
    private final AiSseEventPublisher publisher;

    /**
     * 与 {@link BusinessToolExecutionNode#toolArgs} 中 REVENUE_QUERY 分支一致（不重载 SQL）。
     */
    public Map<String, Object> buildRevenueQueryToolArgs(Long dept, Long dis, String start, String stop, AiRunState state) {
        Map<String, Object> m = new HashMap<>(8);
        if (dept != null) {
            m.put(AiBusinessToolIds.ARG_DEPARTMENT_FATHER_ID, dept);
        }
        if (state != null && (state.isRevenueOverviewPath() || state.isBusinessOverviewPath()
                || state.isBusinessDiagnosisPath() || state.isCostInsightPath())) {
            boolean multiVisible =
                    BusinessScopeResolutionSupport.extractVisibleStoreRootDepartmentIds(state.getResolvedQueryContext())
                                    .size()
                            > 1;
            if (BusinessToolExecutionNode.shouldRouteGroupWideBusinessOverview(state) || multiVisible) {
                m.put(AiBusinessToolIds.ARG_GROUP_WIDE_OVERVIEW_HINT, Boolean.TRUE);
                var revenueCtx = state.getAiUserContext();
                List<Integer> revenueResolved =
                        BusinessScopeResolutionSupport.extractVisibleStoreRootDepartmentIds(
                                state.getResolvedQueryContext());
                if (!revenueResolved.isEmpty()) {
                    m.put(AiBusinessToolIds.ARG_RESOLVED_DEPARTMENT_IDS, new ArrayList<>(revenueResolved));
                    m.put(AiBusinessToolIds.ARG_PARENT_STORE_COUNT, revenueResolved.size());
                }
                if (revenueCtx != null) {
                    String roleTag = revenueCtx.getRoleCode();
                    if ((roleTag == null || roleTag.isBlank()) && revenueCtx.getSourceAdminRole() != null) {
                        roleTag = AiRoleMapper.resolveAdmin(revenueCtx.getSourceAdminRole())
                                .map(AiRoleMapper.AiRoleDefinition::roleCode)
                                .orElse(null);
                    }
                    if (roleTag != null && !roleTag.isBlank()) {
                        m.put(AiBusinessToolIds.ARG_AI_ROLE_CODE, roleTag);
                    }
                }
            }
        }
        if (start != null) {
            m.put(AiBusinessToolIds.ARG_START_DATE, start);
        }
        if (stop != null) {
            m.put(AiBusinessToolIds.ARG_STOP_DATE, stop);
        }
        return m;
    }

    /**
     * 执行 REVENUE_QUERY：权限校验、SSE、写入 {@link AiRunState#getToolResults()}。
     *
     * @return {@code null} 表示权限拒绝（与 BTEN 一致：调用方 continue）；否则为 Tool 结果
     */
    public ToolResult executeRevenueQuery(long rid, AiRunState state,
            Long deptForScopedTools, Long deptForBuildInsight, Long dis, String start, String stop,
            Map<String, Object> toolEnvelopes) {
        String toolId = AiBusinessToolIds.REVENUE_QUERY;
        Long deptArg = BusinessToolExecutionNode.departmentIdArgumentForToolPublic(toolId, deptForScopedTools,
                deptForBuildInsight);

        ToolRequest req = ToolRequest.builder()
                .runId(rid)
                .userId(state.getUserId())
                .toolName(toolId)
                .args(buildRevenueQueryToolArgs(deptArg, dis, start, stop, state))
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
            publisher.publishError(rid,
                    denial != null ? denial.getReason() : "无权调用工具 " + toolId,
                    "tool permission denied",
                    "TOOL_PERMISSION_DENIED",
                    "BusinessError",
                    ex,
                    denial);
            publisher.publish(rid, "tool_finished", Map.of(
                    "tool", toolId,
                    "skipped", true,
                    "permissionDenied", denial != null ? denial.asDataMap() : Map.of(),
                    "displayText", "无权执行该工具：" + toolId,
                    "success", false
            ));
            return null;
        }

        publisher.publish(rid, "tool_started", Map.of(
                "tool", toolId,
                "displayText", "调用工具：" + toolId
        ));

        ToolResult executed = toolRegistry.find(toolId)
                .map(t -> t.execute(req))
                .orElseGet(() -> ToolResult.builder().success(false).message("unknown_tool").data(Map.of()).build());

        Map<String, Object> payload = unwrapToolPayload(executed.getData());
        state.getToolResults().put(toolId, payload != null ? payload : Map.of());
        toolEnvelopes.put(toolId, state.getToolResults().get(toolId));

        publisher.publish(rid, "tool_finished", Map.of(
                "tool", toolId,
                "displayText", executed.isSuccess() ? "工具已完成：" + toolId : "工具失败：" + toolId,
                "success", executed.isSuccess()
        ));
        return executed;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> unwrapToolPayload(Object data) {
        if (data instanceof Map<?, ?> m) {
            return (Map<String, Object>) m;
        }
        if (data instanceof String s && !s.isBlank()) {
            try {
                Object parsed = JSON.parseObject(s);
                if (parsed instanceof Map<?, ?> pm) {
                    return (Map<String, Object>) pm;
                }
            } catch (Exception ignore) {
                return Map.of();
            }
        }
        return Map.of();
    }
}
