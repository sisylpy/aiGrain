package com.nongxinle.ai.graph.business;

import com.alibaba.fastjson2.JSON;
import com.nongxinle.ai.context.AiDepartmentScopeDTO;
import com.nongxinle.ai.context.AiResolvedOrgScope;
import com.nongxinle.ai.context.AiResolvedQueryContext;
import com.nongxinle.ai.context.AiStoreScopeDTO;
import com.nongxinle.ai.context.AiUserContext;
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
 * {@link AiBusinessToolIds#WAREHOUSE_STOCK_OVERVIEW} 权限 + 参数 + 执行，
 * 供 {@link com.nongxinle.ai.agent.business.WarehouseStockAgent} 独占调用（不再由 Graph 节点直连 ToolRegistry）。
 */
@Component
@RequiredArgsConstructor
public class WarehouseStockOverviewToolExecutor {

    private final ToolRegistry toolRegistry;
    private final AiPermissionGuard permissionGuard;
    private final AiSseEventPublisher publisher;

    public Map<String, Object> buildWarehouseStockOverviewToolArgs(
            Long dept, Long dis, String start, String stop, AiRunState state) {
        Map<String, Object> m = new HashMap<>(16);
        boolean warehouseOverviewTool = true;
        if (state != null && state.isGroupWarehouseStockOverview()) {
            m.put(AiBusinessToolIds.ARG_GROUP_WAREHOUSE_STOCK_AGGREGATION, Boolean.TRUE);
            if (dis != null) {
                m.put(AiBusinessToolIds.ARG_DIS_ID, dis);
            }
            BusinessScopeResolutionSupport.GroupWideToolScope scope =
                    BusinessScopeResolutionSupport.resolveGroupWideToolScope(state.getResolvedQueryContext());
            if (!scope.resolvedDepartmentIds().isEmpty()) {
                m.put(AiBusinessToolIds.ARG_RESOLVED_DEPARTMENT_IDS, new ArrayList<>(scope.resolvedDepartmentIds()));
                m.put(AiBusinessToolIds.ARG_PARENT_STORE_COUNT, scope.parentStoreCount());
            }
            var ctx = state.getAiUserContext();
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
            putWarehouseResolvedScopeArgs(m, state);
        } else {
            if (dept != null) {
                m.put(AiBusinessToolIds.ARG_DEPARTMENT_FATHER_ID, dept);
            }
            if (dis != null) {
                m.put(AiBusinessToolIds.ARG_DIS_ID, dis);
            }
            putWarehouseResolvedScopeArgs(m, state);
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
     * @return null 表示权限拒绝
     */
    public ToolResult executeWarehouseStockOverview(
            long rid,
            AiRunState state,
            Long deptForScopedTools,
            Long dis,
            String start,
            String stop,
            Map<String, Object> toolEnvelopes) {

        String toolId = AiBusinessToolIds.WAREHOUSE_STOCK_OVERVIEW;
        ToolRequest req = ToolRequest.builder()
                .runId(rid)
                .userId(state.getUserId())
                .toolName(toolId)
                .args(buildWarehouseStockOverviewToolArgs(deptForScopedTools, dis, start, stop, state))
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

        Map<String, Object> payload = unwrapData(executed.getData());
        state.getToolResults().put(toolId, payload != null ? payload : Map.of());
        if (toolEnvelopes != null) {
            toolEnvelopes.put(toolId, state.getToolResults().get(toolId));
        }

        publisher.publish(rid, "tool_finished", Map.of(
                "tool", toolId,
                "displayText", executed.isSuccess() ? "工具已完成：" + toolId : "工具失败：" + toolId,
                "success", executed.isSuccess()
        ));
        return executed;
    }

    private static void putWarehouseResolvedScopeArgs(Map<String, Object> m, AiRunState state) {
        if (state == null || m == null) {
            return;
        }
        String banner = buildWarehouseStockQueryScopeBanner(state);
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
        if (os.getVisibleWarehouses() != null && !os.getVisibleWarehouses().isEmpty()) {
            List<Map<String, Object>> vw = new ArrayList<>();
            for (AiDepartmentScopeDTO w : os.getVisibleWarehouses()) {
                if (w == null) {
                    continue;
                }
                LinkedHashMap<String, Object> row = new LinkedHashMap<>();
                if (w.getDepartmentId() != null) {
                    row.put("warehouseDepartmentId", w.getDepartmentId());
                }
                row.put("warehouseName", w.getDepartmentName());
                if (w.getFatherId() != null) {
                    row.put("fatherDepartmentId", w.getFatherId());
                }
                vw.add(row);
            }
            if (!vw.isEmpty()) {
                m.put(AiBusinessToolIds.ARG_VISIBLE_WAREHOUSES, vw);
            }
        }
    }

    private static String buildWarehouseStockQueryScopeBanner(AiRunState state) {
        AiResolvedQueryContext rq = state.getResolvedQueryContext();
        if (rq == null || rq.getOrgScope() == null) {
            return null;
        }
        AiResolvedOrgScope os = rq.getOrgScope();
        String scope = os.getScopeType() == null ? "" : os.getScopeType();
        boolean rollupStoresAndWarehouses =
                os.getVisibleWarehouses() != null && !os.getVisibleWarehouses().isEmpty();
        String groupRollupTail = rollupStoresAndWarehouses
                ? "下面按集团下属门店 / 库房库存汇总分析。"
                : "下面按集团下属门店库存汇总分析。";
        if (state.isGroupWarehouseStockOverview() && AiResolvedOrgScope.SCOPE_GROUP.equals(scope)) {
            List<AiStoreScopeDTO> stores = os.getVisibleStores();
            if (stores == null || stores.isEmpty()) {
                String fallback = os.getQueryScopeBanner();
                return (fallback != null && !fallback.isBlank())
                        ? fallback.trim() + " " + groupRollupTail
                        : "你当前可查看集团范围。" + groupRollupTail;
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
                    "你当前可查看集团范围，本次识别到 %d 家门店：%s。%s",
                    stores.size(), names.isEmpty() ? "—" : names, groupRollupTail);
        }
        if (AiResolvedOrgScope.SCOPE_STORE.equals(scope) || AiResolvedOrgScope.SCOPE_PURCHASER.equals(scope)) {
            String name = os.getVisibleStores() != null && !os.getVisibleStores().isEmpty()
                    ? os.getVisibleStores().get(0).getStoreName()
                    : null;
            String label = name != null && !name.isBlank() ? name.trim() : "本门店";
            String storeTail = rollupStoresAndWarehouses
                    ? "下面按门店 / 库房库存汇总分析。"
                    : "下面按门店库存汇总分析。";
            return String.format(Locale.CHINA, "你当前可查看本门店（%s）库存。%s", label, storeTail);
        }
        if (AiResolvedOrgScope.SCOPE_WAREHOUSE.equals(scope)) {
            String wname = os.getVisibleWarehouses() != null && !os.getVisibleWarehouses().isEmpty()
                    ? os.getVisibleWarehouses().get(0).getDepartmentName()
                    : null;
            if (wname != null && !wname.isBlank()) {
                return String.format(Locale.CHINA,
                        "本库房：%s。下面按本人所在库房（及所属门店）库存汇总分析。", wname.trim());
            }
            String b = os.getQueryScopeBanner();
            return (b != null && !b.isBlank())
                    ? b.trim() + " 下面按本人所在库房库存汇总分析。"
                    : "下面按本人所在库房库存汇总分析。";
        }
        String note = os.getQueryScopeBanner();
        return note != null && !note.isBlank() ? note.trim() : null;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> unwrapData(Object data) {
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
