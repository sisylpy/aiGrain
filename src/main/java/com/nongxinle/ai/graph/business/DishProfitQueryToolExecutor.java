package com.nongxinle.ai.graph.business;

import com.nongxinle.ai.context.AiResolvedDataScope;
import com.nongxinle.ai.context.AiResolvedQueryContext;
import com.nongxinle.ai.context.AiUserContext;
import com.nongxinle.ai.core.AiRunState;
import com.nongxinle.ai.mapping.AiRoleMapper;
import com.nongxinle.ai.scope.AiQueryScope;
import com.nongxinle.ai.security.AiPermissionDenied;
import com.nongxinle.ai.security.AiPermissionGuard;
import com.nongxinle.ai.tool.ToolRegistry;
import com.nongxinle.ai.tool.ToolRequest;
import com.nongxinle.ai.tool.ToolResult;
import com.nongxinle.ai.tool.business.AiBusinessToolIds;
import com.nongxinle.ai.trace.AiSseEventPublisher;
import com.nongxinle.constants.AiInsightDishProfitScope;
import com.nongxinle.service.GbAiDailyRevenueService;
import com.alibaba.fastjson2.JSON;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 抽取 {@link AiBusinessToolIds#DISH_PROFIT_ANALYSIS} 参数组装与执行，供
 * {@link BusinessToolExecutionNode} 与 {@link com.nongxinle.ai.agent.business.DishProfitAgent} 共用。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DishProfitQueryToolExecutor {

    private final ToolRegistry toolRegistry;
    private final AiPermissionGuard permissionGuard;
    private final AiSseEventPublisher publisher;
    private final GbAiDailyRevenueService gbAiDailyRevenueService;

    /**
     * 与 {@link BusinessToolExecutionNode#toolArgs} 中 {@link AiBusinessToolIds#DISH_PROFIT_ANALYSIS} 分支一致。
     */
    public Map<String, Object> buildDishProfitAnalysisToolArgs(
            Long deptForScopedTools,
            Long deptForBuildInsight,
            Long dis,
            String start,
            String stop,
            AiRunState state) {
        String toolId = AiBusinessToolIds.DISH_PROFIT_ANALYSIS;
        Map<String, Object> m = new LinkedHashMap<>(16);
        Long dept =
                BusinessToolExecutionNode.departmentIdArgumentForToolPublic(
                        toolId, deptForScopedTools, deptForBuildInsight);

        if (dis != null) {
            m.put(AiBusinessToolIds.ARG_DIS_ID, dis);
        }
        if (BusinessToolExecutionNode.shouldRouteGroupWideDishInsight(state)) {
            m.put(
                    AiBusinessToolIds.ARG_DEPARTMENT_FATHER_ID,
                    (long) AiInsightDishProfitScope.DEP_FATHER_ID_GROUP_WIDE_Mendian_AGGREGATE_UNDER_DIS_ID);
        } else if (dept != null) {
            m.put(AiBusinessToolIds.ARG_DEPARTMENT_FATHER_ID, dept);
        }
        if (start != null) {
            m.put(AiBusinessToolIds.ARG_START_DATE, start);
        }
        if (stop != null) {
            m.put(AiBusinessToolIds.ARG_STOP_DATE, stop);
        }

        AiQueryScope scopeSnapshot = state != null ? state.getScope() : null;

        boolean dishProfitGroup =
                BusinessToolExecutionNode.shouldRouteGroupWideDishInsight(state);
        List<Integer> visibleStoreRoots =
                dishProfitGroup
                        ? BusinessToolExecutionNode.extractVisibleStoreDepartmentIds(state.getResolvedQueryContext())
                        : List.of();
        if (dishProfitGroup && !visibleStoreRoots.isEmpty()) {
            List<Integer> expandedForSql =
                    gbAiDailyRevenueService.expandStoreRootsToDailyRevenueScopeIds(visibleStoreRoots);
            m.put(AiBusinessToolIds.ARG_RESOLVED_DEPARTMENT_IDS, new ArrayList<>(expandedForSql));
            m.put(AiBusinessToolIds.ARG_PARENT_STORE_COUNT, visibleStoreRoots.size());
        } else {
            List<Integer> fromResolvedCtx =
                    BusinessToolExecutionNode.extractSqlQueryDepartmentIdsForTools(state.getResolvedQueryContext());
            if (!fromResolvedCtx.isEmpty()) {
                m.put(AiBusinessToolIds.ARG_RESOLVED_DEPARTMENT_IDS, new ArrayList<>(fromResolvedCtx));
            } else if (scopeSnapshot != null
                    && scopeSnapshot.getResolvedDepartmentIds() != null
                    && !scopeSnapshot.getResolvedDepartmentIds().isEmpty()) {
                log.warn(
                        "[DishProfitQueryToolExecutor] AiResolvedDataScope 无有效 SQL 部门 IN，回退 AiQueryScope.resolvedDepartmentIds（可能滞后）。runId={}",
                        state != null ? state.getRunId() : null);
                m.put(AiBusinessToolIds.ARG_RESOLVED_DEPARTMENT_IDS,
                        new ArrayList<>(scopeSnapshot.getResolvedDepartmentIds()));
            }
        }

        String raw = state != null ? state.getNormalizedUserInput() : null;
        if (raw != null && !raw.isBlank()) {
            m.put(AiBusinessToolIds.ARG_USER_QUESTION_HINT, raw.trim());
        }
        AiResolvedQueryContext rqCtx = state != null ? state.getResolvedQueryContext() : null;
        if (rqCtx != null) {
            var qix = rqCtx.getQueryIntent();
            if (qix != null && qix.getStructuredIntentDetail() != null && !qix.getStructuredIntentDetail().isBlank()) {
                m.put(AiBusinessToolIds.ARG_DISH_PROFIT_STRUCTURED_DETAIL, qix.getStructuredIntentDetail().trim());
            }
            if (rqCtx.getMentionedDishName() != null && !rqCtx.getMentionedDishName().isBlank()) {
                m.put(AiBusinessToolIds.ARG_DISH_NAME_FOCUS_HINT, rqCtx.getMentionedDishName().trim());
            }
        }
        AiUserContext ctxSnap = state != null ? state.getAiUserContext() : null;
        if (ctxSnap != null) {
            String roleTag = ctxSnap.getRoleCode();
            if ((roleTag == null || roleTag.isBlank()) && ctxSnap.getSourceAdminRole() != null) {
                roleTag = AiRoleMapper.resolveAdmin(ctxSnap.getSourceAdminRole())
                        .map(AiRoleMapper.AiRoleDefinition::roleCode)
                        .orElse(null);
            }
            if (roleTag != null && !roleTag.isBlank()) {
                m.put(AiBusinessToolIds.ARG_AI_ROLE_CODE, roleTag);
            }
        }
        if (!dishProfitGroup || visibleStoreRoots.isEmpty()) {
            Integer derivedPc = deriveParentStoreCountForDishTool(rqCtx);
            if (derivedPc != null && derivedPc > 0) {
                m.put(AiBusinessToolIds.ARG_PARENT_STORE_COUNT, derivedPc);
            } else if (scopeSnapshot != null && scopeSnapshot.getParentStoreCount() > 0) {
                m.put(AiBusinessToolIds.ARG_PARENT_STORE_COUNT, scopeSnapshot.getParentStoreCount());
            }
        }

        if (rqCtx != null && rqCtx.getDataScope() != null) {
            AiResolvedDataScope ds = rqCtx.getDataScope();
            if (ds.getQueryScopeKind() != null) {
                m.put(AiBusinessToolIds.ARG_QUERY_SCOPE_KIND, ds.getQueryScopeKind());
            }
            m.put(AiBusinessToolIds.ARG_QUERY_STORE_IDS, new ArrayList<>(ds.getQueryStoreIds()));
            m.put(AiBusinessToolIds.ARG_QUERY_REAL_DEPARTMENT_IDS, new ArrayList<>(ds.getQueryRealDepartmentIds()));
            m.put(AiBusinessToolIds.ARG_QUERY_DISTRIBUTER_ID, ds.getQueryDistributerId());
            m.put(AiBusinessToolIds.ARG_STORE_TO_DEPARTMENT_IDS, new LinkedHashMap<>(ds.getStoreToDepartmentIds()));
        }

        if (log.isInfoEnabled() && state != null) {
            log.info(
                    "[DISH_PROFIT_SCOPE] DishProfitQueryToolExecutor toolArgsBuilt runId={} departmentFatherId={} "
                            + "disId={} dateRange={}..{} resolvedDepartmentIds={} parentStoreCount={} dishProfitGroupWide={} "
                            + "queryScopeKindInArgs={} queryStoreIdsInArgs={}",
                    state.getRunId(),
                    m.get(AiBusinessToolIds.ARG_DEPARTMENT_FATHER_ID),
                    m.get(AiBusinessToolIds.ARG_DIS_ID),
                    m.get(AiBusinessToolIds.ARG_START_DATE),
                    m.get(AiBusinessToolIds.ARG_STOP_DATE),
                    m.get(AiBusinessToolIds.ARG_RESOLVED_DEPARTMENT_IDS),
                    m.get(AiBusinessToolIds.ARG_PARENT_STORE_COUNT),
                    dishProfitGroup,
                    m.get(AiBusinessToolIds.ARG_QUERY_SCOPE_KIND),
                    m.get(AiBusinessToolIds.ARG_QUERY_STORE_IDS));
        }

        return m;
    }

    /**
     * 执行 DISH_PROFIT_ANALYSIS：权限校验、SSE、写入 {@link AiRunState#getToolResults()}。
     *
     * @return {@code null} 表示权限拒绝；否则为 Tool 结果
     */
    public ToolResult executeDishProfitAnalysis(
            long rid,
            AiRunState state,
            Long deptForScopedTools,
            Long deptForBuildInsight,
            Long dis,
            String start,
            String stop,
            Map<String, Object> toolEnvelopes) {
        String toolId = AiBusinessToolIds.DISH_PROFIT_ANALYSIS;
        ToolRequest req =
                ToolRequest.builder()
                        .runId(rid)
                        .userId(state.getUserId())
                        .toolName(toolId)
                        .args(buildDishProfitAnalysisToolArgs(deptForScopedTools, deptForBuildInsight, dis, start, stop, state))
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
                    ex,
                    denial);
            publisher.publish(
                    rid,
                    "tool_finished",
                    Map.of(
                            "tool", toolId,
                            "skipped", true,
                            "permissionDenied", denial != null ? denial.asDataMap() : Map.of(),
                            "displayText", "无权执行该工具：" + toolId,
                            "success", false));
            return null;
        }

        publisher.publish(rid, "tool_started", Map.of("tool", toolId, "displayText", "调用工具：" + toolId));

        ToolResult executed =
                toolRegistry
                        .find(toolId)
                        .map(t -> t.execute(req))
                        .orElseGet(
                                () ->
                                        ToolResult.builder()
                                                .success(false)
                                                .message("unknown_tool")
                                                .data(Map.of())
                                                .build());

        Map<String, Object> payload = unwrapToolPayload(executed.getData());
        state.getToolResults().put(toolId, payload != null ? payload : Map.of());
        toolEnvelopes.put(toolId, state.getToolResults().get(toolId));

        publisher.publish(
                rid,
                "tool_finished",
                Map.of(
                        "tool", toolId,
                        "displayText", executed.isSuccess() ? "工具已完成：" + toolId : "工具失败：" + toolId,
                        "success", executed.isSuccess()));
        return executed;
    }

    private static Integer deriveParentStoreCountForDishTool(AiResolvedQueryContext rq) {
        if (rq == null || rq.getDataScope() == null) {
            return null;
        }
        AiResolvedDataScope ds = rq.getDataScope();
        if (ds.getQueryStoreIds() != null && !ds.getQueryStoreIds().isEmpty()) {
            return ds.getQueryStoreIds().size();
        }
        List<Long> roots = ds.getVisibleStoreRootIds();
        if (roots != null && !roots.isEmpty()) {
            return roots.size();
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> unwrapToolPayload(Object data) {
        if (data instanceof Map<?, ?> mp) {
            return (Map<String, Object>) mp;
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
