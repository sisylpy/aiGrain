package com.nongxinle.ai.graph.business;

import com.nongxinle.ai.context.AiResolvedOrgScope;
import com.nongxinle.ai.context.AiResolvedQueryContext;
import com.nongxinle.ai.context.AiStoreScopeDTO;
import com.nongxinle.ai.context.AiUserContext;
import com.nongxinle.ai.conversation.AiQuerySemanticLexicon;
import com.nongxinle.ai.core.AiRunState;
import com.nongxinle.ai.graph.business.execution.ToolRequestContractExecutionParamSupport;
import com.nongxinle.ai.graph.business.scope.BusinessScopeResolutionSupport;
import com.nongxinle.ai.mapping.AiRoleMapper;
import com.nongxinle.ai.security.AiPermissionDenied;
import com.nongxinle.ai.security.AiPermissionGuard;
import com.nongxinle.ai.security.AiRoleCodes;
import com.nongxinle.ai.tool.ToolRegistry;
import com.nongxinle.ai.tool.ToolRequest;
import com.nongxinle.ai.tool.ToolResult;
import com.nongxinle.ai.tool.business.AiBusinessToolIds;
import com.nongxinle.ai.trace.AiSseEventPublisher;
import com.alibaba.fastjson2.JSON;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 抽取 {@link AiBusinessToolIds#STOCK_REDUCE_QUERY} harness 参数组装与执行，供
 * {@link BusinessToolExecutionNode} 与 {@link com.nongxinle.ai.agent.business.StockReduceAgent} 共用。
 */
@Component
@RequiredArgsConstructor
public class StockReduceQueryToolExecutor {

    private final ToolRegistry toolRegistry;
    private final AiPermissionGuard permissionGuard;
    private final AiSseEventPublisher publisher;

    /**
     * 与历史 {@link BusinessToolExecutionNode} harness 分支一致（出库专线 / 经营诊断内嵌出库）。
     */
    public Map<String, Object> buildHarnessToolArgs(Long dept, Long dis, String start, String stop, AiRunState state) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put(AiBusinessToolIds.ARG_STOCK_REDUCE_HARNESS_PATH, Boolean.TRUE);
        if (dis != null) {
            m.put(AiBusinessToolIds.ARG_DIS_ID, dis);
        }
        if (start != null) {
            m.put(AiBusinessToolIds.ARG_START_DATE, start);
        }
        if (stop != null) {
            m.put(AiBusinessToolIds.ARG_STOP_DATE, stop);
        }
        AiResolvedQueryContext rq = state != null ? state.getResolvedQueryContext() : null;
        if (state != null && state.isBusinessDiagnosisPath()) {
            m.put(AiBusinessToolIds.ARG_STOCK_REDUCE_NARRATIVE_MODE,
                    AiQuerySemanticLexicon.STRUCTURED_STOCK_REDUCE_OVERVIEW_SUMMARY);
        } else {
            String narrativeWire = ToolRequestContractExecutionParamSupport.resolveContractStructuredIntentDetailWire(rq);
            if (narrativeWire != null && !narrativeWire.isBlank()) {
                String normalized = narrativeWire.trim();
                if (AiQuerySemanticLexicon.STRUCTURED_GOODS_OUTBOUND_COUNT_RANKING.equals(normalized)) {
                    normalized = AiQuerySemanticLexicon.STRUCTURED_GOODS_OUTBOUND_RANKING;
                }
                m.put(AiBusinessToolIds.ARG_STOCK_REDUCE_NARRATIVE_MODE, normalized);
            }
        }
        putStockReduceResolvedScopeArgs(m, state);
        if (state != null && state.isGroupStockReduceQuery()) {
            m.put(AiBusinessToolIds.ARG_GROUP_STOCK_REDUCE_AGGREGATION, Boolean.TRUE);
            List<Integer> roots = BusinessScopeResolutionSupport.extractVisibleStoreRootDepartmentIds(
                    state.getResolvedQueryContext());
            if (!roots.isEmpty()) {
                m.put(AiBusinessToolIds.ARG_RESOLVED_DEPARTMENT_IDS, new ArrayList<>(roots));
                m.put(AiBusinessToolIds.ARG_PARENT_STORE_COUNT, roots.size());
            }
        } else if (dept != null) {
            m.put(AiBusinessToolIds.ARG_DEPARTMENT_FATHER_ID, dept);
        }
        AiUserContext ctx = state != null ? state.getAiUserContext() : null;
        if (ctx != null) {
            String roleTag = ctx.getRoleCode();
            if ((roleTag == null || roleTag.isBlank()) && ctx.getSourceAdminRole() != null) {
                roleTag = AiRoleMapper.resolveAdmin(ctx.getSourceAdminRole())
                        .map(AiRoleMapper.AiRoleDefinition::roleCode)
                        .orElse(null);
            }
            if (roleTag != null && !roleTag.isBlank()) {
                m.put(AiBusinessToolIds.ARG_AI_ROLE_CODE, roleTag);
            }
        }
        return m;
    }

    /**
     * 执行 STOCK_REDUCE_QUERY（harness 专线）：权限校验、SSE、写入 {@link AiRunState#getToolResults()}。
     *
     * @return {@code null} 表示权限拒绝；否则为 Tool 结果
     */
    public ToolResult executeStockReduceQuery(
            long rid,
            AiRunState state,
            Long deptForScopedTools,
            Long dis,
            String start,
            String stop,
            Map<String, Object> toolEnvelopes) {
        String toolId = AiBusinessToolIds.STOCK_REDUCE_QUERY;
        ToolRequest req = ToolRequest.builder()
                .runId(rid)
                .userId(state.getUserId())
                .toolName(toolId)
                .args(buildHarnessToolArgs(deptForScopedTools, dis, start, stop, state))
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

    private static void putStockReduceResolvedScopeArgs(Map<String, Object> m, AiRunState state) {
        if (state == null || m == null) {
            return;
        }
        String banner = buildStockReduceQueryScopeBanner(state);
        if (banner != null && !banner.isBlank()) {
            m.put(AiBusinessToolIds.ARG_QUERY_SCOPE_BANNER, banner.trim());
        }
        AiResolvedQueryContext rq = state.getResolvedQueryContext();
        if (rq == null || rq.getOrgScope() == null) {
            return;
        }
        AiResolvedOrgScope os = rq.getOrgScope();
        if (os.getVisibleStores() != null && !os.getVisibleStores().isEmpty()) {
            List<Map<String, Object>> vs = new ArrayList<>();
            for (AiStoreScopeDTO s : os.getVisibleStores()) {
                if (s == null) {
                    continue;
                }
                LinkedHashMap<String, Object> row = new LinkedHashMap<>();
                if (s.getStoreDepartmentId() != null) {
                    row.put("storeDepartmentId", s.getStoreDepartmentId());
                }
                row.put("storeName", s.getStoreName());
                vs.add(row);
            }
            if (!vs.isEmpty()) {
                m.put(AiBusinessToolIds.ARG_VISIBLE_STORES, vs);
            }
        }
    }

    private static String buildStockReduceQueryScopeBanner(AiRunState state) {
        AiResolvedQueryContext rq = state.getResolvedQueryContext();
        if (rq == null || rq.getOrgScope() == null) {
            return null;
        }
        AiResolvedOrgScope os = rq.getOrgScope();
        String scope = os.getScopeType() == null ? "" : os.getScopeType();
        AiUserContext uctx = state.getAiUserContext();
        String roleCode = uctx != null && uctx.getRoleCode() != null ? uctx.getRoleCode().trim() : "";
        if (state.isGroupStockReduceQuery() && AiResolvedOrgScope.SCOPE_GROUP.equals(scope)) {
            List<AiStoreScopeDTO> stores = os.getVisibleStores();
            if (stores == null || stores.isEmpty()) {
                String fallback = os.getQueryScopeBanner();
                return (fallback != null && !fallback.isBlank())
                        ? fallback.trim() + " 下面按集团范围汇总出库/核销金额。"
                        : "你当前可查看集团范围。下面按集团范围汇总出库/核销金额。";
            }
            String names = stores.stream()
                    .map(s -> {
                        if (s == null) {
                            return null;
                        }
                        if (s.getStoreName() != null && !s.getStoreName().isBlank()) {
                            return s.getStoreName().trim();
                        }
                        if (s.getStoreDepartmentId() != null) {
                            return "门店" + s.getStoreDepartmentId();
                        }
                        return null;
                    })
                    .filter(Objects::nonNull)
                    .collect(Collectors.joining("、"));
            return String.format(Locale.CHINA,
                    "你当前可查看集团范围，本次识别到 %d 家门店：%s。下面按集团范围汇总出库/核销（自然日历日四类金额）。",
                    stores.size(), names.isEmpty() ? "—" : names);
        }
        if (AiResolvedOrgScope.SCOPE_WAREHOUSE.equals(scope)) {
            return "下面按你当前可查看的库房/所属门店汇总出库与核销金额。";
        }
        if (AiResolvedOrgScope.SCOPE_STORE.equals(scope) || AiResolvedOrgScope.SCOPE_PURCHASER.equals(scope)) {
            if (AiRoleCodes.STORE_MANAGER.equals(roleCode)) {
                return "下面按本门店出库/核销金额汇总。";
            }
            String name = os.getVisibleStores() != null && !os.getVisibleStores().isEmpty()
                    ? os.getVisibleStores().get(0).getStoreName()
                    : null;
            String label = name != null && !name.isBlank() ? name.trim() : "本门店";
            return String.format(Locale.CHINA,
                    "你当前可查看本门店（%s）。下面按门店范围汇总出库/核销金额。", label);
        }
        String note = os.getQueryScopeBanner();
        return note != null && !note.isBlank() ? note.trim() : null;
    }
}
