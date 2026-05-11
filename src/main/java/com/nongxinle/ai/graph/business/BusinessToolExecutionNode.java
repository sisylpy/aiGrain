package com.nongxinle.ai.graph.business;

import com.nongxinle.ai.context.*;
import com.nongxinle.constants.AiInsightDishProfitScope;
import com.nongxinle.ai.core.AgentNode;
import com.nongxinle.ai.core.AiRunState;
import com.nongxinle.ai.scope.AiConversationScopeMode;
import com.nongxinle.ai.mapping.AiRoleMapper;
import com.nongxinle.ai.security.AiRoleCodes;
import com.nongxinle.ai.scope.AiQueryScope;
import com.nongxinle.ai.scope.AiScopeResolver;
import com.nongxinle.ai.security.AiPermissionDenied;
import com.nongxinle.ai.security.AiPermissionGuard;
import com.nongxinle.utils.GbConstants;
import com.nongxinle.service.GbAiDailyRevenueService;
import com.nongxinle.ai.tool.ToolRegistry;
import com.nongxinle.ai.tool.ToolRequest;
import com.nongxinle.ai.tool.ToolResult;
import com.nongxinle.ai.tool.business.AiBusinessToolIds;
import com.nongxinle.ai.trace.AiSseEventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Slf4j
public class BusinessToolExecutionNode implements AgentNode {

    private final ToolRegistry toolRegistry;
    private final AiPermissionGuard permissionGuard;
    private final AiScopeResolver scopeResolver;
    private final AiSseEventPublisher publisher;
    private final GbAiDailyRevenueService gbAiDailyRevenueService;

    @Override
    public String name() {
        return "ToolExecution";
    }

    @Override
    public boolean shouldRun(AiRunState state) {
        boolean planned = state.isCostInsightPath()
                || state.isBusinessOverviewPath()
                || state.isPurchaseCostInsightPath()
                || state.isPurchaseOverviewPath()
                || state.isWarehouseStockOverviewPath()
                || state.isDishProfitPath()
                || state.isStockReduceQueryPath();
        return planned && state.getDataPlanTools() != null && !state.getDataPlanTools().isEmpty();
    }

    @Override
    public AiRunState run(AiRunState state) {
        long rid = state.getRunId();
        String hint;
        if (state.isPurchaseCostInsightPath()) {
            hint = "采购与核销/出库…";
        } else if (state.isStockReduceQueryPath()) {
            hint = "出库/核销汇总…";
        } else if (state.isWarehouseStockOverviewPath()) {
            hint = "库房库存概览汇总…";
        } else if (state.isDishProfitPath()) {
            hint = "菜品毛利透视…";
        } else if (state.isCostInsightPath()) {
            hint = "营业额、采购、核销与菜品…";
        } else {
            hint = "经营看板、菜品、采购与毛利…";
        }
        publisher.publish(rid, "agent_started", Map.of(
                "agent", "ToolExecutionNode",
                "displayText", "正在拉取" + hint
        ));

        Long dept = state.getDepartmentId();
        Long dis = state.getDistributerId();
        Long deptForScopedTools = resolveToolDepartmentFatherId(state, dept);
        Long deptForBuildInsightTools = resolveBuildInsightDepartmentFatherId(state, deptForScopedTools);
        String start = state.getStatStartDate();
        String stop = state.getStatEndDate();
        if (state.isBusinessOverviewPath() || state.isDishProfitPath() || state.isWarehouseStockOverviewPath()
                || state.isPurchaseCostInsightPath()
                || state.isStockReduceQueryPath()) {
            AiResolvedQueryContext rq = state.getResolvedQueryContext();
            if (rq != null && rq.getTimeWindow() != null) {
                if (rq.getTimeWindow().getStartDate() != null) {
                    start = rq.getTimeWindow().getStartDate().toString();
                }
                if (rq.getTimeWindow().getEndDate() != null) {
                    stop = rq.getTimeWindow().getEndDate().toString();
                }
                state.setStatStartDate(start);
                state.setStatEndDate(stop);
            }
        }

        Map<String, Object> toolEnvelopes = new LinkedHashMap<>();

        List<String> plan = state.getDataPlanTools();
        for (String toolId : plan) {
            if (state.isCancelled()) {
                publisher.publish(rid, "tool_finished", Map.of(
                        "tool", toolId,
                        "skipped", true,
                        "displayText", "运行已取消，跳过后续工具",
                        "success", false
                ));
                break;
            }

            ToolRequest req = ToolRequest.builder()
                    .runId(rid)
                    .userId(state.getUserId())
                    .toolName(toolId)
                    .args(toolArgs(toolId, departmentIdArgumentForTool(toolId, deptForScopedTools,
                            deptForBuildInsightTools), dis, start, stop, toolEnvelopes, state))
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
                continue;
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

            toolEnvelopes.put(toolId, state.getToolResults().get(toolId));

            publisher.publish(rid, "tool_finished", Map.of(
                    "tool", toolId,
                    "displayText", executed.isSuccess() ? "工具已完成：" + toolId : "工具失败：" + toolId,
                    "success", executed.isSuccess()
            ));
        }

        publisher.publish(rid, "agent_finished", Map.of(
                "agent", "ToolExecutionNode",
                "displayText", "经营数据抓取完成",
                "toolCount", state.getToolResults().size()
        ));
        return state;
    }

    /**
     * 门店采购端：子部门入参归一到门店根（{@code gb_department_father_id = 0}），再传给采购/核销工具。
     */
    private Long resolveToolDepartmentFatherId(AiRunState state, Long dept) {
        if (dept == null || state.getAiUserContext() == null) {
            return dept;
        }
        if (!AiRoleCodes.STORE_PURCHASER.equals(state.getAiUserContext().getRoleCode())
                && !AiRoleCodes.WAREHOUSE_MANAGER.equals(state.getAiUserContext().getRoleCode())
                && !AiRoleCodes.REGION_WAREHOUSE.equals(state.getAiUserContext().getRoleCode())
                && !AiRoleCodes.STORE_MANAGER.equals(state.getAiUserContext().getRoleCode())) {
            return dept;
        }
        int normalized = scopeResolver.resolveDomainStoreDepartmentId(dept.intValue());
        return (long) normalized;
    }

    /**
     * 菜品 insight / buildInsight 的 depFatherId 必须与 SQL 子部门展开属于同一门店根。
     * 多轮追问切店后 {@link AiRunState#getDepartmentId()} 仍为创建会话时的 request 锚点，会与
     * {@link AiQueryScope#getResolvedDepartmentIds()} 错配，导致 {@code resolveScopeDepIds} 与 allow 列表求交为空、无菜品行。
     */
    private static Long resolveBuildInsightDepartmentFatherId(AiRunState state, Long deptFallback) {
        if (state == null) {
            return deptFallback;
        }
        AiResolvedQueryContext rq = state.getResolvedQueryContext();
        if (rq != null && rq.getOrgScope() != null) {
            AiResolvedOrgScope org = rq.getOrgScope();
            if (AiResolvedOrgScope.SCOPE_STORE.equals(org.getScopeType())) {
                if (org.getCurrentStoreDepartmentId() != null) {
                    return org.getCurrentStoreDepartmentId();
                }
                if (org.getVisibleStores() != null && !org.getVisibleStores().isEmpty()) {
                    AiStoreScopeDTO s0 = org.getVisibleStores().get(0);
                    if (s0 != null && s0.getStoreDepartmentId() != null) {
                        return s0.getStoreDepartmentId();
                    }
                }
            }
            // 集团角色在多轮中收窄到单店：scopeType 仍为 GROUP，但 visibleStores 仅 1 家 — buildInsight 必须用该店 root。
            if (org.getVisibleStores() != null && org.getVisibleStores().size() == 1) {
                AiStoreScopeDTO s0 = org.getVisibleStores().get(0);
                if (s0 != null && s0.getStoreDepartmentId() != null) {
                    return s0.getStoreDepartmentId();
                }
            }
        }
        AiQueryScope sc = state.getScope();
        if (sc != null && sc.getMode() == AiConversationScopeMode.STORE && sc.getDepartmentFatherId() != null) {
            return sc.getDepartmentFatherId();
        }
        return deptFallback;
    }

    private static Long departmentIdArgumentForTool(String toolId, Long defaultDept, Long buildInsightDept) {
        if (AiBusinessToolIds.DISH_PROFIT_ANALYSIS.equals(toolId) || AiBusinessToolIds.DISH_SALES_QUERY.equals(toolId)) {
            return buildInsightDept;
        }
        return defaultDept;
    }

    /**
     * {@link GbDepFoodBusinessInsightServiceImpl#buildInsight} 集团聚合入口：仅当解析后的组织范围确为集团/多店时启用，
     * 避免「集团角色 + 单店解析范围」误传 sentinel，或与单店 depFather 错位。
     */
    static boolean shouldRouteGroupWideDishInsight(AiRunState state) {
        return shouldRouteGroupWideBusinessOverview(state) && resolvedOrgIndicatesGroupOrMultiStoreDishAggregate(state);
    }

    static boolean resolvedOrgIndicatesGroupOrMultiStoreDishAggregate(AiRunState state) {
        if (state == null) {
            return false;
        }
        AiResolvedQueryContext rq = state.getResolvedQueryContext();
        if (rq == null || rq.getOrgScope() == null) {
            return true;
        }
        AiResolvedOrgScope org = rq.getOrgScope();
        if (AiResolvedOrgScope.SCOPE_GROUP.equals(org.getScopeType())) {
            return true;
        }
        List<AiStoreScopeDTO> vs = org.getVisibleStores();
        return vs != null && vs.size() > 1;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> unwrapData(Object data) {
        if (!(data instanceof Map)) {
            return Map.of();
        }
        return (Map<String, Object>) data;
    }

    private Map<String, Object> toolArgs(String toolId, Long dept, Long dis, String start, String stop,
            Map<String, Object> envelopes, AiRunState state) {
        if (AiBusinessToolIds.STOCK_REDUCE_QUERY.equals(toolId) && state != null && state.isStockReduceQueryPath()) {
            return buildStockReduceHarnessToolArgs(dept, dis, start, stop, state);
        }
        Map<String, Object> m = new HashMap<>(8);
        if (AiBusinessToolIds.REVENUE_QUERY.equals(toolId) || AiBusinessToolIds.BUSINESS_OVERVIEW_QUERY.equals(toolId)
                || AiBusinessToolIds.STOCK_REDUCE_QUERY.equals(toolId) || AiBusinessToolIds.STOCK_QUERY.equals(toolId)
                || AiBusinessToolIds.WAREHOUSE_STOCK_OVERVIEW.equals(toolId)) {
            boolean warehouseOverviewTool = AiBusinessToolIds.WAREHOUSE_STOCK_OVERVIEW.equals(toolId);
            if (warehouseOverviewTool && state.isGroupWarehouseStockOverview()) {
                m.put(AiBusinessToolIds.ARG_GROUP_WAREHOUSE_STOCK_AGGREGATION, Boolean.TRUE);
                if (dis != null) {
                    m.put(AiBusinessToolIds.ARG_DIS_ID, dis);
                }
                AiQueryScope sc = state.getScope();
                List<Integer> visibleStoreRoots = extractVisibleStoreDepartmentIds(state.getResolvedQueryContext());
                if (!visibleStoreRoots.isEmpty()) {
                    m.put(AiBusinessToolIds.ARG_RESOLVED_DEPARTMENT_IDS, new ArrayList<>(visibleStoreRoots));
                    m.put(AiBusinessToolIds.ARG_PARENT_STORE_COUNT, visibleStoreRoots.size());
                } else if (sc != null) {
                    if (sc.getResolvedDepartmentIds() != null && !sc.getResolvedDepartmentIds().isEmpty()) {
                        m.put(AiBusinessToolIds.ARG_RESOLVED_DEPARTMENT_IDS,
                                new java.util.ArrayList<>(sc.getResolvedDepartmentIds()));
                    }
                    m.put(AiBusinessToolIds.ARG_PARENT_STORE_COUNT, sc.getParentStoreCount());
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
                if (dis != null && (AiBusinessToolIds.STOCK_QUERY.equals(toolId)
                        || AiBusinessToolIds.WAREHOUSE_STOCK_OVERVIEW.equals(toolId))) {
                    m.put(AiBusinessToolIds.ARG_DIS_ID, dis);
                }
                if (warehouseOverviewTool) {
                    putWarehouseResolvedScopeArgs(m, state);
                }
            }
            if (AiBusinessToolIds.BUSINESS_OVERVIEW_QUERY.equals(toolId)
                    && shouldRouteGroupWideBusinessOverview(state)) {
                m.put(AiBusinessToolIds.ARG_GROUP_WIDE_OVERVIEW_HINT, Boolean.TRUE);
                AiQueryScope scope = state.getScope();
                var ctx = state.getAiUserContext();

                List<Integer> resolvedFromPublicCtx = extractVisibleStoreDepartmentIds(state.getResolvedQueryContext());
                if (!resolvedFromPublicCtx.isEmpty()) {
                    m.put(AiBusinessToolIds.ARG_RESOLVED_DEPARTMENT_IDS, new ArrayList<>(resolvedFromPublicCtx));
                    m.put(AiBusinessToolIds.ARG_PARENT_STORE_COUNT, resolvedFromPublicCtx.size());
                } else if (scope != null) {
                    if (scope.getResolvedDepartmentIds() != null && !scope.getResolvedDepartmentIds().isEmpty()) {
                        m.put(AiBusinessToolIds.ARG_RESOLVED_DEPARTMENT_IDS,
                                new ArrayList<>(scope.getResolvedDepartmentIds()));
                    }
                    m.put(AiBusinessToolIds.ARG_PARENT_STORE_COUNT, scope.getParentStoreCount());
                }
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
            }
            if (start != null) {
                m.put(AiBusinessToolIds.ARG_START_DATE, start);
            }
            if (stop != null) {
                m.put(AiBusinessToolIds.ARG_STOP_DATE, stop);
            }
        } else if (AiBusinessToolIds.PURCHASE_OVERVIEW.equals(toolId)) {
            if (dis != null) {
                m.put(AiBusinessToolIds.ARG_DIS_ID, dis);
            }
            if (state.isGroupPurchaseOverview()) {
                m.put(AiBusinessToolIds.ARG_GROUP_PURCHASE_AGGREGATION, Boolean.TRUE);
                List<Integer> visibleStoreRoots = extractVisibleStoreDepartmentIds(state.getResolvedQueryContext());
                if (!visibleStoreRoots.isEmpty()) {
                    m.put(AiBusinessToolIds.ARG_RESOLVED_DEPARTMENT_IDS, new ArrayList<>(visibleStoreRoots));
                    m.put(AiBusinessToolIds.ARG_PARENT_STORE_COUNT, visibleStoreRoots.size());
                } else {
                    AiQueryScope sc = state.getScope();
                    if (sc != null && sc.getResolvedDepartmentIds() != null && !sc.getResolvedDepartmentIds().isEmpty()) {
                        m.put(AiBusinessToolIds.ARG_RESOLVED_DEPARTMENT_IDS,
                                new ArrayList<>(sc.getResolvedDepartmentIds()));
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
            if (start != null) {
                m.put(AiBusinessToolIds.ARG_START_DATE, start);
            }
            if (stop != null) {
                m.put(AiBusinessToolIds.ARG_STOP_DATE, stop);
            }
            var purCtx = state.getResolvedQueryContext();
            if (purCtx != null && purCtx.getQueryIntent() != null) {
                var qi = purCtx.getQueryIntent();
                String pst = qi.getPurchaseSourceType();
                if (pst != null && !pst.isBlank()) {
                    m.put(AiBusinessToolIds.ARG_PURCHASE_SOURCE_FOCUS, pst);
                }
                String nar = qi.getStructuredIntentDetail();
                if (nar != null && !nar.isBlank()) {
                    m.put(AiBusinessToolIds.ARG_PURCHASE_NARRATIVE_MODE, nar);
                }
            }
            logPurchaseOverviewToolArgs(state, m);
        } else if (AiBusinessToolIds.PURCHASE_QUERY.equals(toolId)) {
            if (dis != null) {
                m.put(AiBusinessToolIds.ARG_DIS_ID, dis);
            }
            if (dept != null) {
                m.put(AiBusinessToolIds.ARG_DEPARTMENT_FATHER_ID, dept);
                m.put(AiBusinessToolIds.ARG_PURCHASE_DEPARTMENT_ID, dept);
            }
            if (start != null) {
                m.put(AiBusinessToolIds.ARG_START_DATE, start);
            }
            if (stop != null) {
                m.put(AiBusinessToolIds.ARG_STOP_DATE, stop);
            }
        } else if (AiBusinessToolIds.DISH_SALES_QUERY.equals(toolId)
                || AiBusinessToolIds.DISH_PROFIT_ANALYSIS.equals(toolId)) {
            logDishToolScopeBeforeArgs(toolId, state);
            if (dis != null) {
                m.put(AiBusinessToolIds.ARG_DIS_ID, dis);
            }
            if (AiBusinessToolIds.DISH_PROFIT_ANALYSIS.equals(toolId)
                    && shouldRouteGroupWideDishInsight(state)) {
                m.put(AiBusinessToolIds.ARG_DEPARTMENT_FATHER_ID,
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
            AiQueryScope scopeSnapshot = state.getScope();

            boolean dishProfitGroup = AiBusinessToolIds.DISH_PROFIT_ANALYSIS.equals(toolId)
                    && shouldRouteGroupWideDishInsight(state);
            List<Integer> visibleStoreRoots = dishProfitGroup
                    ? extractVisibleStoreDepartmentIds(state.getResolvedQueryContext())
                    : List.of();
            if (dishProfitGroup && !visibleStoreRoots.isEmpty()) {
                List<Integer> expandedForSql =
                        gbAiDailyRevenueService.expandStoreRootsToDailyRevenueScopeIds(visibleStoreRoots);
                m.put(AiBusinessToolIds.ARG_RESOLVED_DEPARTMENT_IDS, new ArrayList<>(expandedForSql));
                m.put(AiBusinessToolIds.ARG_PARENT_STORE_COUNT, visibleStoreRoots.size());
            } else {
                // 必须与 Harness 的 expandedSqlDepartmentIds / AiResolvedDataScope 同源；勿用会话 AiQueryScope 的展开列表（多轮切店后常仍是旧范围）
                List<Integer> fromResolvedCtx = extractSqlQueryDepartmentIdsForTools(state.getResolvedQueryContext());
                if (!fromResolvedCtx.isEmpty()) {
                    m.put(AiBusinessToolIds.ARG_RESOLVED_DEPARTMENT_IDS, new ArrayList<>(fromResolvedCtx));
                } else if (scopeSnapshot != null && scopeSnapshot.getResolvedDepartmentIds() != null
                        && !scopeSnapshot.getResolvedDepartmentIds().isEmpty()) {
                    log.warn(
                            "[BusinessToolExecutionNode] toolArgs {}: AiResolvedDataScope 无有效 SQL 部门 IN，回退 AiQueryScope.resolvedDepartmentIds（可能滞后为上一轮范围）。runId={}",
                            toolId, state.getRunId());
                    m.put(AiBusinessToolIds.ARG_RESOLVED_DEPARTMENT_IDS,
                            new java.util.ArrayList<>(scopeSnapshot.getResolvedDepartmentIds()));
                }
            }
            if (AiBusinessToolIds.DISH_PROFIT_ANALYSIS.equals(toolId)) {
                String raw = state.getNormalizedUserInput();
                if (raw != null && !raw.isBlank()) {
                    m.put(AiBusinessToolIds.ARG_USER_QUESTION_HINT, raw.trim());
                }
                var rqCtx = state.getResolvedQueryContext();
                if (rqCtx != null) {
                    var qix = rqCtx.getQueryIntent();
                    if (qix != null && qix.getStructuredIntentDetail() != null && !qix.getStructuredIntentDetail().isBlank()) {
                        m.put(AiBusinessToolIds.ARG_DISH_PROFIT_STRUCTURED_DETAIL, qix.getStructuredIntentDetail().trim());
                    }
                    if (rqCtx.getMentionedDishName() != null && !rqCtx.getMentionedDishName().isBlank()) {
                        m.put(AiBusinessToolIds.ARG_DISH_NAME_FOCUS_HINT, rqCtx.getMentionedDishName().trim());
                    }
                }
                var ctxSnap = state.getAiUserContext();
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
                    // 与本轮 AiResolvedDataScope 对齐；勿用 AiQueryScope.parentStoreCount（多轮从集团切单店时常仍为 2）
                    Integer derivedPc = deriveParentStoreCountForDishTool(rqCtx);
                    if (derivedPc != null && derivedPc > 0) {
                        m.put(AiBusinessToolIds.ARG_PARENT_STORE_COUNT, derivedPc);
                    } else if (scopeSnapshot != null && scopeSnapshot.getParentStoreCount() > 0) {
                        m.put(AiBusinessToolIds.ARG_PARENT_STORE_COUNT, scopeSnapshot.getParentStoreCount());
                    }
                }
            }
            AiResolvedQueryContext rqScope = state.getResolvedQueryContext();
            if (rqScope != null && rqScope.getDataScope() != null) {
                AiResolvedDataScope ds = rqScope.getDataScope();
                if (ds.getQueryScopeKind() != null) {
                    m.put(AiBusinessToolIds.ARG_QUERY_SCOPE_KIND, ds.getQueryScopeKind());
                }
                m.put(AiBusinessToolIds.ARG_QUERY_STORE_IDS, new ArrayList<>(ds.getQueryStoreIds()));
                m.put(AiBusinessToolIds.ARG_QUERY_REAL_DEPARTMENT_IDS, new ArrayList<>(ds.getQueryRealDepartmentIds()));
                m.put(AiBusinessToolIds.ARG_QUERY_DISTRIBUTER_ID, ds.getQueryDistributerId());
                m.put(AiBusinessToolIds.ARG_STORE_TO_DEPARTMENT_IDS, new LinkedHashMap<>(ds.getStoreToDepartmentIds()));
            }
            if (log.isInfoEnabled()) {
                log.info(
                        "[DISH_PROFIT_SCOPE] toolArgsBuilt toolId={} runId={} conversationId={} departmentFatherId={} disId={} "
                                + "dateRange={}..{} resolvedDepartmentIds={} parentStoreCount={} dishProfitGroupWide={} "
                                + "queryScopeKindInArgs={} queryStoreIdsInArgs={} stateDepartmentId={}",
                        toolId,
                        state.getRunId(),
                        state.getConversationId(),
                        m.get(AiBusinessToolIds.ARG_DEPARTMENT_FATHER_ID),
                        m.get(AiBusinessToolIds.ARG_DIS_ID),
                        m.get(AiBusinessToolIds.ARG_START_DATE),
                        m.get(AiBusinessToolIds.ARG_STOP_DATE),
                        m.get(AiBusinessToolIds.ARG_RESOLVED_DEPARTMENT_IDS),
                        m.get(AiBusinessToolIds.ARG_PARENT_STORE_COUNT),
                        dishProfitGroup,
                        m.get(AiBusinessToolIds.ARG_QUERY_SCOPE_KIND),
                        m.get(AiBusinessToolIds.ARG_QUERY_STORE_IDS),
                        state.getDepartmentId());
            }
        } else if (AiBusinessToolIds.GROSS_MARGIN_CALCULATOR.equals(toolId)) {
            m.put(AiBusinessToolIds.ARG_INPUT_SNAPSHOT, new LinkedHashMap<>(envelopes));
        }
        return m;
    }

    /**
     * 经营看板广角：不因 roleCode 偶发空缺而误走「单锚点单行」路径；集团与 {@link AiOrgScope} 对齐。
     * <p>包内可见便于回归单测（集团/门店话术不可串线）。
     */
    static boolean shouldRouteGroupWideBusinessOverview(AiRunState state) {
        if (state == null || state.getAiUserContext() == null) {
            return false;
        }
        var ctx = state.getAiUserContext();
        String rc = ctx.getRoleCode();
        if (rc != null && !rc.isBlank() && AiRoleMapper.isGroupWideOrgScope(rc.trim())) {
            return true;
        }
        Integer admin = ctx.getSourceAdminRole();
        if (admin != null && admin.equals(GbConstants.DepartmentUserRole.GROUP_MANAGER_APP)) {
            return true;
        }
        AiOrgScope os = state.getAiOrgScope();
        return os != null && AiOrgScopeResolver.SCOPE_GROUP.equals(os.getScopeType());
    }

    /**
     * 库房库存：把 {@link AiResolvedQueryContext} 中的范围与抬头透传给 {@link com.nongxinle.ai.tool.business.WarehouseStockOverviewTool}。
     */
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

    /**
     * 采购概览：抬头 + visibleStores（与 {@link com.nongxinle.ai.tool.business.PurchaseOverviewTool} 对齐）。
     */
    private static void putPurchaseResolvedScopeArgs(Map<String, Object> m, AiRunState state) {
        if (state == null || m == null) {
            return;
        }
        String banner = buildPurchaseQueryScopeBanner(state);
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

    private static String buildPurchaseQueryScopeBanner(AiRunState state) {
        AiResolvedQueryContext rq = state.getResolvedQueryContext();
        if (rq == null || rq.getOrgScope() == null) {
            return null;
        }
        AiResolvedOrgScope os = rq.getOrgScope();
        String scope = os.getScopeType() == null ? "" : os.getScopeType();
        AiUserContext uctx = state.getAiUserContext();
        String roleCode = uctx != null && uctx.getRoleCode() != null ? uctx.getRoleCode().trim() : "";
        if (state.isGroupPurchaseOverview() && AiResolvedOrgScope.SCOPE_GROUP.equals(scope)) {
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

    private static boolean isPurchasingStaffRole(String roleCode) {
        if (roleCode == null || roleCode.isBlank()) {
            return false;
        }
        return AiRoleCodes.STORE_PURCHASER.equals(roleCode)
                || AiRoleCodes.GROUP_PURCHASER.equals(roleCode)
                || AiRoleCodes.WAREHOUSE_PURCHASER.equals(roleCode)
                || AiRoleCodes.CENTRAL_KITCHEN_PURCHASER.equals(roleCode)
                || AiRoleCodes.REGION_PURCHASER.equals(roleCode);
    }

    /**
     * 与菜品毛利 {@code queryScopeBanner} 风格对齐：集团枚举门店名，禁止依赖「反问哪家门店」。
     */
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

    private void logPurchaseOverviewToolArgs(AiRunState state, Map<String, Object> m) {
        if (!log.isInfoEnabled()) {
            return;
        }
        long rid = state.getRunId();
        String raw = state.getRawUserInput();
        AiResolvedQueryContext rq = state.getResolvedQueryContext();
        var qi = rq != null ? rq.getQueryIntent() : null;
        var fur = rq != null ? rq.getFollowUpResolution() : null;
        AiResolvedOrgScope org = rq != null ? rq.getOrgScope() : null;
        var ds = rq != null ? rq.getDataScope() : null;
        boolean groupAgg = Boolean.TRUE.equals(m.get(AiBusinessToolIds.ARG_GROUP_PURCHASE_AGGREGATION));
        List<Integer> resolved = intListFromToolArg(m.get(AiBusinessToolIds.ARG_RESOLVED_DEPARTMENT_IDS));
        String mentioned = fur != null ? fur.getStoreScopeFollowUpMentionedName() : null;
        Long matchedStoreDept = fur != null ? fur.getStoreScopeFollowUpMatchedStoreRootId() : null;
        if ((mentioned == null || mentioned.isBlank()) && org != null && org.getVisibleStores() != null
                && org.getVisibleStores().size() == 1) {
            AiStoreScopeDTO s0 = org.getVisibleStores().get(0);
            if (s0 != null) {
                mentioned = s0.getStoreName();
                if (matchedStoreDept == null) {
                    matchedStoreDept = s0.getStoreDepartmentId();
                }
            }
        }
        List<Integer> rootsForExpand = new ArrayList<>(resolved);
        if (rootsForExpand.isEmpty()) {
            rootsForExpand.addAll(storeRootsFromVisibleStoresToolArg(m.get(AiBusinessToolIds.ARG_VISIBLE_STORES)));
        }
        List<Integer> actualExpanded = List.of();
        if (!rootsForExpand.isEmpty()) {
            try {
                List<Integer> exp = gbAiDailyRevenueService.expandStoreRootsToDailyRevenueScopeIds(rootsForExpand);
                actualExpanded = exp != null ? new ArrayList<>(exp) : List.of();
            } catch (Exception ex) {
                log.debug("[BusinessToolExecutionNode] expandStoreRootsForPurchaseLog failed runId={}", rid, ex);
            }
        }
        log.info(
                "[BusinessToolExecutionNode] PURCHASE Overview toolArgs runId={} rawMessage={} currentIntentCode={} "
                        + "currentPathCode={} currentMentionedStoreName={} matchedStoreDepartmentId={} "
                        + "effectiveIntentSource={} effectiveScopeSource={} scopeType={} visibleStores={} "
                        + "effectiveSqlDepartmentIds={} groupPurchaseAggregation={} resolvedDepartmentIds={} "
                        + "actualQueryDepartmentIds={}",
                rid,
                raw,
                qi != null ? qi.getIntentCode() : null,
                qi != null ? qi.getPathCode() : null,
                mentioned,
                matchedStoreDept,
                rq != null ? rq.getEffectiveIntentSource() : null,
                rq != null ? rq.getEffectiveScopeSource() : null,
                org != null ? org.getScopeType() : null,
                formatVisibleStoresIdsNames(org),
                ds != null ? ds.getEffectiveSqlDepartmentIds() : null,
                groupAgg,
                resolved,
                actualExpanded);
    }

    @SuppressWarnings("unchecked")
    private static List<Integer> intListFromToolArg(Object raw) {
        if (!(raw instanceof List<?> list)) {
            return List.of();
        }
        List<Integer> out = new ArrayList<>();
        for (Object o : list) {
            if (o instanceof Number n) {
                out.add(n.intValue());
            }
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private static List<Integer> storeRootsFromVisibleStoresToolArg(Object raw) {
        if (!(raw instanceof List<?> list)) {
            return List.of();
        }
        List<Integer> out = new ArrayList<>();
        for (Object row : list) {
            if (!(row instanceof Map<?, ?> map)) {
                continue;
            }
            Object id = map.get("storeDepartmentId");
            if (id instanceof Number n) {
                out.add(n.intValue());
            }
        }
        return out;
    }

    private static String formatVisibleStoresIdsNames(AiResolvedOrgScope org) {
        if (org == null || org.getVisibleStores() == null || org.getVisibleStores().isEmpty()) {
            return "[]";
        }
        StringBuilder sb = new StringBuilder("[");
        List<AiStoreScopeDTO> vs = org.getVisibleStores();
        for (int i = 0; i < vs.size(); i++) {
            AiStoreScopeDTO s = vs.get(i);
            if (i > 0) {
                sb.append(',');
            }
            if (s == null) {
                sb.append("null");
                continue;
            }
            sb.append("{id=").append(s.getStoreDepartmentId());
            sb.append(",name=").append(s.getStoreName() == null ? "" : s.getStoreName().trim());
            sb.append('}');
        }
        sb.append(']');
        return sb.toString();
    }

    private static Map<String, Object> buildStockReduceHarnessToolArgs(Long dept, Long dis, String start, String stop,
            AiRunState state) {
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
        if (rq != null && rq.getQueryIntent() != null) {
            String nar = rq.getQueryIntent().getStructuredIntentDetail();
            if (nar != null && !nar.isBlank()) {
                m.put(AiBusinessToolIds.ARG_STOCK_REDUCE_NARRATIVE_MODE, nar.trim());
            }
        }
        putStockReduceResolvedScopeArgs(m, state);
        if (state.isGroupStockReduceQuery()) {
            m.put(AiBusinessToolIds.ARG_GROUP_STOCK_REDUCE_AGGREGATION, Boolean.TRUE);
            List<Integer> roots = extractVisibleStoreDepartmentIds(state.getResolvedQueryContext());
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

    /**
     * 菜品毛利/销量：在组装 Tool 参数前打印 org + dataScope 快照（与 Run Debug 字段对齐，便于后台对照）。
     */
    /**
     * 菜品工具 parentStoreCount：以本轮解析范围为准（queryStoreIds / visibleStoreRootIds），避免会话 scope 快照滞后。
     */
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

    private static void logDishToolScopeBeforeArgs(String toolId, AiRunState state) {
        if (!log.isInfoEnabled() || state == null) {
            return;
        }
        AiResolvedQueryContext rq = state.getResolvedQueryContext();
        AiResolvedOrgScope org = rq != null ? rq.getOrgScope() : null;
        AiResolvedDataScope ds = rq != null ? rq.getDataScope() : null;
        String visibleStoresStr = formatVisibleStoreNamesForDishLog(org);
        String mentionedStore = resolveMentionedStoreForDishLog(rq, org);
        String intent = rq != null ? rq.getEffectiveIntentCode() : null;
        String path = rq != null ? rq.getEffectivePathCode() : null;
        String scopeType = org != null ? org.getScopeType() : null;
        log.info(
                "[DISH_PROFIT_SCOPE] toolId={}, conversationId={}, runId={}, intent={}, path={}, scopeType={}, mentionedStore={}, visibleStores={}",
                toolId,
                state.getConversationId(),
                state.getRunId(),
                intent,
                path,
                scopeType,
                mentionedStore,
                visibleStoresStr);
        if (ds != null) {
            log.info(
                    "[DISH_PROFIT_SCOPE] queryScopeKind={}, queryStoreIds={}, queryRealDepartmentIds={}, queryDistributerId={}, storeToDepartmentIds={}",
                    ds.getQueryScopeKind(),
                    ds.getQueryStoreIds(),
                    ds.getQueryRealDepartmentIds(),
                    ds.getQueryDistributerId(),
                    ds.getStoreToDepartmentIds());
            log.info(
                    "[DISH_PROFIT_SCOPE] resolved visibleStoreRootIds={}, expandedSqlDepartmentIds={}, orgScopeType={}",
                    ds.getVisibleStoreRootIds(),
                    ds.getExpandedSqlDepartmentIds(),
                    org != null ? org.getScopeType() : null);
        } else {
            log.info(
                    "[DISH_PROFIT_SCOPE] dataScope=null resolvedQueryContextPresent={}",
                    rq != null);
        }
    }

    private static String resolveMentionedStoreForDishLog(AiResolvedQueryContext rq, AiResolvedOrgScope org) {
        if (rq != null && rq.getFollowUpResolution() != null) {
            String n = rq.getFollowUpResolution().getStoreScopeFollowUpMentionedName();
            if (n != null && !n.isBlank()) {
                return n.trim();
            }
        }
        if (org != null && org.getVisibleStores() != null && org.getVisibleStores().size() == 1) {
            AiStoreScopeDTO s0 = org.getVisibleStores().get(0);
            if (s0 != null && s0.getStoreName() != null && !s0.getStoreName().isBlank()) {
                return s0.getStoreName().trim();
            }
        }
        return null;
    }

    private static String formatVisibleStoreNamesForDishLog(AiResolvedOrgScope org) {
        if (org == null || org.getVisibleStores() == null || org.getVisibleStores().isEmpty()) {
            return "—";
        }
        return org.getVisibleStores().stream()
                .filter(Objects::nonNull)
                .map(s -> {
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
    }

    /** 集团经营概览：与 {@link AiResolvedQueryContext#getOrgScope()}{@code #visibleStores} 对齐，避免仅按 ScopeIntersect 单列 request 锚点。 */
    static List<Integer> extractVisibleStoreDepartmentIds(AiResolvedQueryContext ctx) {
        if (ctx == null || ctx.getOrgScope() == null) {
            return List.of();
        }
        List<AiStoreScopeDTO> stores = ctx.getOrgScope().getVisibleStores();
        if (stores == null || stores.isEmpty()) {
            return List.of();
        }
        List<Integer> out = new ArrayList<>(stores.size());
        for (AiStoreScopeDTO s : stores) {
            if (s == null || s.getStoreDepartmentId() == null) {
                continue;
            }
            long id = s.getStoreDepartmentId();
            if (id > Integer.MAX_VALUE || id <= 0) {
                continue;
            }
            out.add((int) id);
        }
        return out;
    }

    /**
     * 菜品毛利/销量等工具：与 Harness {@code effectiveSqlDepartmentIds} 同源（{@link com.nongxinle.ai.context.AiResolvedDataScope#getEffectiveSqlDepartmentIds()}）；
     * 优先于会话 {@link AiQueryScope#getResolvedDepartmentIds()}，避免追问收窄门店后 allow 列表仍携带旧集团/别店 ID，
     * 与本轮 {@code depFatherId} 求交为空。
     */
    static List<Integer> extractSqlQueryDepartmentIdsForTools(AiResolvedQueryContext ctx) {
        if (ctx == null || ctx.getDataScope() == null) {
            return List.of();
        }
        List<Long> raw = ctx.getDataScope().getEffectiveSqlDepartmentIds();
        if (raw == null || raw.isEmpty()) {
            return List.of();
        }
        List<Integer> out = new ArrayList<>(raw.size());
        for (Long id : raw) {
            if (id == null || id <= 0 || id > Integer.MAX_VALUE) {
                continue;
            }
            out.add(id.intValue());
        }
        return out;
    }
}
