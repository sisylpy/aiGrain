package com.nongxinle.ai.graph.business;

import com.nongxinle.ai.context.AiResolvedOrgScope;
import com.nongxinle.ai.context.AiResolvedQueryContext;
import com.nongxinle.ai.context.AiStoreScopeDTO;
import com.nongxinle.ai.context.AiUserContext;
import com.nongxinle.ai.conversation.AiQuerySemanticLexicon;
import com.nongxinle.ai.core.AiRunState;
import com.nongxinle.ai.graph.business.execution.PurchaseSemanticExecutionArgs;
import com.nongxinle.ai.graph.business.scope.BusinessScopeResolutionSupport;
import com.nongxinle.ai.mapping.AiRoleMapper;
import com.nongxinle.ai.scope.AiQueryScope;
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
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 抽取 {@link AiBusinessToolIds#PURCHASE_OVERVIEW} 参数组装与执行，供 {@link BusinessToolExecutionNode} 与
 * {@link com.nongxinle.ai.agent.business.PurchaseAgent} 共用；参数形状与历史 BTEN 分支一致。
 */
@Component
@RequiredArgsConstructor
public class PurchaseOverviewToolExecutor {

    private final ToolRegistry toolRegistry;
    private final AiPermissionGuard permissionGuard;
    private final AiSseEventPublisher publisher;

    /**
     * 与 {@link BusinessToolExecutionNode#toolArgs} 中 PURCHASE_OVERVIEW 分支一致。
     */
    public static Map<String, Object> buildPurchaseOverviewToolArgs(
            Long dept, Long dis, String start, String stop, AiRunState state) {
        Map<String, Object> m = new HashMap<>(16);
        if (dis != null) {
            m.put(AiBusinessToolIds.ARG_DIS_ID, dis);
        }
        if (state != null && state.isGroupPurchaseOverview()) {
            m.put(AiBusinessToolIds.ARG_GROUP_PURCHASE_AGGREGATION, Boolean.TRUE);
            List<Integer> visibleStoreRoots =
                    BusinessScopeResolutionSupport.extractVisibleStoreRootDepartmentIds(state.getResolvedQueryContext());
            if (!visibleStoreRoots.isEmpty()) {
                m.put(AiBusinessToolIds.ARG_RESOLVED_DEPARTMENT_IDS, new ArrayList<>(visibleStoreRoots));
                m.put(AiBusinessToolIds.ARG_PARENT_STORE_COUNT, visibleStoreRoots.size());
            } else {
                AiQueryScope sc = state.getScope();
                if (sc != null && sc.getResolvedDepartmentIds() != null && !sc.getResolvedDepartmentIds().isEmpty()) {
                    m.put(AiBusinessToolIds.ARG_RESOLVED_DEPARTMENT_IDS, new ArrayList<>(sc.getResolvedDepartmentIds()));
                }
                if (sc != null) {
                    m.put(AiBusinessToolIds.ARG_PARENT_STORE_COUNT, sc.getParentStoreCount());
                }
            }
        } else {
            if (dept != null) {
                m.put(AiBusinessToolIds.ARG_DEPARTMENT_FATHER_ID, dept);
                m.put(AiBusinessToolIds.ARG_PURCHASE_DEPARTMENT_ID, dept);
            }
        }
        putPurchaseResolvedScopeArgs(m, state);
        if (state != null) {
            var ctxPur = state.getAiUserContext();
            if (ctxPur != null) {
                String roleTag = ctxPur.getRoleCode();
                if ((roleTag == null || roleTag.isBlank()) && ctxPur.getSourceAdminRole() != null) {
                    roleTag = AiRoleMapper.resolveAdmin(ctxPur.getSourceAdminRole())
                            .map(AiRoleMapper.AiRoleDefinition::roleCode)
                            .orElse(null);
                }
                if (roleTag != null && !roleTag.isBlank()) {
                    m.put(AiBusinessToolIds.ARG_AI_ROLE_CODE, roleTag);
                }
            }
        }
        if (start != null) {
            m.put(AiBusinessToolIds.ARG_START_DATE, start);
        }
        if (stop != null) {
            m.put(AiBusinessToolIds.ARG_STOP_DATE, stop);
        }
        AiResolvedQueryContext purCtx = state != null ? state.getResolvedQueryContext() : null;
        if (purCtx != null && purCtx.getQueryIntent() != null) {
            var qi = purCtx.getQueryIntent();
            String pst = qi.getPurchaseSourceType();
            if (pst != null && !pst.isBlank()) {
                m.put(AiBusinessToolIds.ARG_PURCHASE_SOURCE_FOCUS, pst);
            }
            String nar = qi.getStructuredIntentDetail();
            if (state != null && state.isBusinessDiagnosisPath()) {
                nar = AiQuerySemanticLexicon.STRUCTURED_PURCHASE_OVERVIEW_SUMMARY;
            }
            if (nar != null && !nar.isBlank()) {
                m.put(AiBusinessToolIds.ARG_PURCHASE_NARRATIVE_MODE, nar);
            }
        }
        PurchaseSemanticExecutionArgs.applyToToolArgs(m, purCtx);
        return m;
    }

    /**
     * 执行 PURCHASE_OVERVIEW：权限校验、SSE、写入 {@link AiRunState#getToolResults()}（key 与 legacy 一致）。
     *
     * @return {@code null} 表示权限拒绝；否则为 Tool 结果
     */
    public ToolResult executePurchaseOverview(
            long rid, AiRunState state, Long deptForScopedTools, Long dis, String start, String stop,
            Map<String, Object> toolEnvelopes) {
        String toolId = AiBusinessToolIds.PURCHASE_OVERVIEW;
        ToolRequest req = ToolRequest.builder()
                .runId(rid)
                .userId(state.getUserId())
                .toolName(toolId)
                .args(buildPurchaseOverviewToolArgs(deptForScopedTools, dis, start, stop, state))
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

    /** 采购概览：抬头 + visibleStores（与 {@link com.nongxinle.ai.tool.business.PurchaseOverviewTool} 对齐）。 */
    static void putPurchaseResolvedScopeArgs(Map<String, Object> m, AiRunState state) {
        if (state == null || m == null) {
            return;
        }
        String banner = buildPurchaseOverviewScopeBanner(state);
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

    static String buildPurchaseOverviewScopeBanner(AiRunState state) {
        AiResolvedQueryContext rq = state != null ? state.getResolvedQueryContext() : null;
        if (rq == null || rq.getOrgScope() == null) {
            return null;
        }
        AiResolvedOrgScope os = rq.getOrgScope();
        String scope = os.getScopeType() == null ? "" : os.getScopeType();
        AiUserContext uctx = state != null ? state.getAiUserContext() : null;
        String roleCode = uctx != null && uctx.getRoleCode() != null ? uctx.getRoleCode().trim() : "";
        if (state != null && state.isGroupPurchaseOverview() && AiResolvedOrgScope.SCOPE_GROUP.equals(scope)) {
            List<AiStoreScopeDTO> stores = os.getVisibleStores();
            if (stores == null || stores.isEmpty()) {
                String fallback = os.getQueryScopeBanner();
                return (fallback != null && !fallback.isBlank())
                        ? fallback.trim() + " 下面按集团范围汇总采购入库。"
                        : "你当前可查看集团范围。下面按集团范围汇总采购入库。";
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
                    "你当前可查看集团范围，本次识别到 %d 家门店：%s。下面按集团范围汇总采购入库。",
                    stores.size(), names.isEmpty() ? "—" : names);
        }
        if (AiResolvedOrgScope.SCOPE_WAREHOUSE.equals(scope)) {
            return "下面按你当前可查看的库房/所属门店入库数据分析。";
        }
        if (AiResolvedOrgScope.SCOPE_STORE.equals(scope) || AiResolvedOrgScope.SCOPE_PURCHASER.equals(scope)) {
            if (AiRoleCodes.STORE_MANAGER.equals(roleCode)) {
                return "下面按本门店采购入库数据分析。";
            }
            if (isPurchasingStaffRole(roleCode)) {
                return "你当前账号为门店采购角色，下面按你可查看的门店采购入库数据分析。";
            }
            String name = os.getVisibleStores() != null && !os.getVisibleStores().isEmpty()
                    ? os.getVisibleStores().get(0).getStoreName()
                    : null;
            String label = name != null && !name.isBlank() ? name.trim() : "本门店";
            return String.format(Locale.CHINA,
                    "你当前可查看本门店（%s）采购。下面按门店范围汇总采购入库。", label);
        }
        String note = os.getQueryScopeBanner();
        return note != null && !note.isBlank() ? note.trim() : null;
    }

    static boolean isPurchasingStaffRole(String roleCode) {
        if (roleCode == null || roleCode.isBlank()) {
            return false;
        }
        return AiRoleCodes.STORE_PURCHASER.equals(roleCode)
                || AiRoleCodes.GROUP_PURCHASER.equals(roleCode)
                || AiRoleCodes.WAREHOUSE_PURCHASER.equals(roleCode)
                || AiRoleCodes.CENTRAL_KITCHEN_PURCHASER.equals(roleCode)
                || AiRoleCodes.REGION_PURCHASER.equals(roleCode);
    }
}
