package com.nongxinle.ai.graph.business;

import com.nongxinle.ai.agent.business.MasterBusinessAgent;
import com.nongxinle.ai.agent.business.MasterBusinessAgentResult;
import com.nongxinle.ai.context.*;
import com.nongxinle.ai.graph.business.scope.BusinessScopeResolutionSupport;
import com.nongxinle.ai.graph.business.toolrequest.BusinessToolExecutionRequestResolver;
import com.nongxinle.ai.graph.business.toolrequest.PurchaseToolRequestContext;
import com.nongxinle.ai.harness.HarnessPlannedToolArgsCapture;
import com.nongxinle.ai.core.AgentNode;
import com.nongxinle.ai.core.AiRunState;
import com.nongxinle.ai.mapping.AiRoleMapper;
import com.nongxinle.ai.security.AiRoleCodes;
import com.nongxinle.ai.security.AiPermissionDenied;
import com.nongxinle.ai.security.AiPermissionGuard;
import com.nongxinle.utils.GbConstants;
import com.nongxinle.service.GbAiDailyRevenueService;
import com.nongxinle.ai.tool.ToolRegistry;
import com.nongxinle.ai.tool.ToolRequest;
import com.nongxinle.ai.tool.ToolResult;
import com.nongxinle.ai.tool.business.AiBusinessToolIds;
import com.nongxinle.ai.trace.AiSseEventPublisher;
import com.alibaba.fastjson2.JSON;
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
    private final AiSseEventPublisher publisher;
    private final GbAiDailyRevenueService gbAiDailyRevenueService;
    private final ToolDepartmentResolutionSupport toolDepartmentResolutionSupport;
    private final RevenueQueryToolExecutor revenueQueryToolExecutor;
    private final StockReduceQueryToolExecutor stockReduceQueryToolExecutor;
    private final DishProfitQueryToolExecutor dishProfitQueryToolExecutor;
    private final MasterBusinessAgent masterBusinessAgent;
    private final BusinessToolExecutionRequestResolver toolExecutionRequestResolver;

    @Override
    public String name() {
        return "ToolExecution";
    }

    @Override
    public boolean shouldRun(AiRunState state) {
        boolean purchaseOverviewToolPlanned = state.getDataPlanTools() != null
                && state.getDataPlanTools().contains(AiBusinessToolIds.PURCHASE_OVERVIEW);
        boolean revenueToolPlanned = state.getDataPlanTools() != null
                && state.getDataPlanTools().contains(AiBusinessToolIds.REVENUE_QUERY);
        boolean planned = state.isCostInsightPath()
                || state.isBusinessOverviewPath()
                || state.isPurchaseCostInsightPath()
                || state.isPurchaseOverviewPath()
                || state.isWarehouseStockOverviewPath()
                || state.isDishProfitPath()
                || state.isStockReduceQueryPath()
                || state.isBusinessDiagnosisPath()
                || state.isRevenueOverviewPath()
                // 编排层已列入 purchase_overview 时，即使 path 布尔位异常也必须执行 Tool 链并 attach PurchaseAnswerPlan
                || purchaseOverviewToolPlanned
                || revenueToolPlanned;
        return planned && state.getDataPlanTools() != null && !state.getDataPlanTools().isEmpty();
    }

    @Override
    public AiRunState run(AiRunState state) {
        if (state.isHarnessToolRequestOnly()) {
            return runHarnessToolRequestCaptureOnly(state);
        }
        long rid = state.getRunId();
        String hint;
        if (state.isPurchaseCostInsightPath()) {
            hint = "采购与核销/出库…";
        } else if (state.isStockReduceQueryPath()) {
            hint = "出库/核销汇总…";
        } else if (state.isRevenueOverviewPath()) {
            hint = "日营业额/营收汇总…";
        } else if (state.isWarehouseStockOverviewPath()) {
            hint = "库房库存概览汇总…";
        } else if (state.isBusinessDiagnosisPath()) {
            hint = "经营诊断（采购·出库·菜品毛利）…";
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
        Long deptForScopedTools = toolDepartmentResolutionSupport.resolveToolDepartmentFatherId(state, dept);
        Long deptForBuildInsightTools = toolDepartmentResolutionSupport.resolveBuildInsightDepartmentFatherId(state,
                deptForScopedTools);
        String start = state.getStatStartDate();
        String stop = state.getStatEndDate();
        if (state.isBusinessOverviewPath() || state.isDishProfitPath() || state.isWarehouseStockOverviewPath()
                || state.isPurchaseCostInsightPath()
                || state.isStockReduceQueryPath()
                || state.isRevenueOverviewPath()
                || state.isBusinessDiagnosisPath()) {
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

        MasterBusinessAgentResult businessOverviewMultiMaster =
                masterBusinessAgent.tryOrchestrateBusinessOverviewMultiAgent(state);
        MasterBusinessAgentResult revenueMaster = masterBusinessAgent.tryOrchestrateRevenueOverview(state);
        MasterBusinessAgentResult purchaseMaster = masterBusinessAgent.tryOrchestratePurchaseOverview(state);
        MasterBusinessAgentResult stockReduceMaster = masterBusinessAgent.tryOrchestrateStockReduceQuery(state);
        MasterBusinessAgentResult dishProfitMaster = masterBusinessAgent.tryOrchestrateDishProfitAnalysis(state);
        MasterBusinessAgentResult warehouseMaster = masterBusinessAgent.tryOrchestrateWarehouseStockOverview(state);
        mergeMasterBusinessAgentDebug(
                state, revenueMaster, purchaseMaster, stockReduceMaster, dishProfitMaster,
                businessOverviewMultiMaster, warehouseMaster);
        final boolean businessOverviewMultiBatch =
                businessOverviewMultiMaster != null
                        && businessOverviewMultiMaster.isBusinessOverviewMultiAgentBatchAttempted();
        final boolean diagnosisOrOverviewMultiHarness = businessOverviewMultiBatch
                && (state.isBusinessOverviewPath() || state.isBusinessDiagnosisPath());
        if (diagnosisOrOverviewMultiHarness
                && !businessOverviewMultiMaster.isBusinessOverviewMultiAgentAnyDomainSuccess()) {
            state.setNeedClarification(true);
            if (state.getClarificationQuestion() == null || state.getClarificationQuestion().isBlank()) {
                state.setClarificationQuestion("当前查询范围内未能汇总经营数据，请确认时间或门店范围后再试。");
            }
        }
        final boolean revenueHandledByMaster = businessOverviewMultiBatch
                || (revenueMaster != null && revenueMaster.isMasterAgentEnabled());
        final boolean purchaseHandledByMaster = businessOverviewMultiBatch
                || (purchaseMaster != null && purchaseMaster.isMasterAgentEnabled());
        final boolean stockReduceHandledByMaster = businessOverviewMultiBatch
                || (stockReduceMaster != null && stockReduceMaster.isMasterAgentEnabled());
        final boolean dishProfitHandledByMaster = businessOverviewMultiBatch
                || (dishProfitMaster != null && dishProfitMaster.isMasterAgentEnabled());
        final boolean warehouseHandledByMaster =
                warehouseMaster != null && warehouseMaster.isMasterAgentEnabled();

        List<String> plan = state.getDataPlanTools();
        try {
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

                if (revenueHandledByMaster && AiBusinessToolIds.REVENUE_QUERY.equals(toolId)) {
                    continue;
                }

                if (purchaseHandledByMaster && AiBusinessToolIds.PURCHASE_OVERVIEW.equals(toolId)) {
                    continue;
                }

                if (stockReduceHandledByMaster && AiBusinessToolIds.STOCK_REDUCE_QUERY.equals(toolId)) {
                    continue;
                }

                if (dishProfitHandledByMaster && AiBusinessToolIds.DISH_PROFIT_ANALYSIS.equals(toolId)) {
                    continue;
                }

                if (warehouseHandledByMaster && AiBusinessToolIds.WAREHOUSE_STOCK_OVERVIEW.equals(toolId)) {
                    continue;
                }

                if (AiBusinessToolIds.REVENUE_QUERY.equals(toolId)) {
                    ToolResult executed = revenueQueryToolExecutor.executeRevenueQuery(rid, state, deptForScopedTools,
                            deptForBuildInsightTools, dis, start, stop, toolEnvelopes);
                    if (executed == null) {
                        continue;
                    }
                    continue;
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
        } finally {
            // 与 usedBuildInsight 无关：只要本轮 toolResults 中有采购信封即尝试 attach（含后续工具抛错但采购已成功写入的情形）
            PurchaseAnswerPlanBuilder.attachIfApplicable(state);
            StockReduceAnswerPlanBuilder.attachIfApplicable(state);
            DailyRevenueAnswerPlanBuilder.attachIfApplicable(state);
            WarehouseAnswerPlanBuilder.attachIfApplicable(state);
        }
        return state;
    }

    /**
     * Harness {@link com.nongxinle.ai.harness.replay.AiHarnessReplayDryRunStage#TOOL_REQUEST_ONLY}：
     * DataPlanner 已写入 {@code dataPlanTools} 后，仅 {@link BusinessToolExecutionRequestResolver} +
     * {@link PurchaseOverviewToolExecutor#buildPurchaseOverviewToolArgs} 快照，不跑 Master Agent / Tool.execute / AnswerPlan。
     */
    private AiRunState runHarnessToolRequestCaptureOnly(AiRunState state) {
        long rid = state.getRunId();
        publisher.publish(rid, "agent_started", Map.of(
                "agent", "ToolExecutionNode",
                "displayText", "Harness：规划 Tool 入参（不执行 SQL）…"
        ));

        Long dept = state.getDepartmentId();
        Long dis = state.getDistributerId();
        Long deptForScopedTools = toolDepartmentResolutionSupport.resolveToolDepartmentFatherId(state, dept);
        Long deptForBuildInsight = toolDepartmentResolutionSupport.resolveBuildInsightDepartmentFatherId(state,
                deptForScopedTools);
        String start = state.getStatStartDate();
        String stop = state.getStatEndDate();
        AiResolvedQueryContext rqTw = state.getResolvedQueryContext();
        if (rqTw != null && rqTw.getTimeWindow() != null) {
            if (rqTw.getTimeWindow().getStartDate() != null) {
                start = rqTw.getTimeWindow().getStartDate().toString();
            }
            if (rqTw.getTimeWindow().getEndDate() != null) {
                stop = rqTw.getTimeWindow().getEndDate().toString();
            }
            state.setStatStartDate(start);
            state.setStatEndDate(stop);
        }

        LinkedHashMap<String, Map<String, Object>> planned = new LinkedHashMap<>();
        List<String> plan = state.getDataPlanTools();
        if (plan != null) {
            for (String toolId : plan) {
                if (state.isCancelled()) {
                    break;
                }
                AiResolvedQueryContext rq = state.getResolvedQueryContext();
                if (AiBusinessToolIds.PURCHASE_OVERVIEW.equals(toolId)) {
                    PurchaseToolRequestContext ctx =
                            toolExecutionRequestResolver.buildPurchaseRequestContext(state, rq);
                    Map<String, Object> args = PurchaseOverviewToolExecutor.buildPurchaseOverviewToolArgs(
                            deptForScopedTools, dis, start, stop, state);
                    planned.put(
                            toolId,
                            HarnessPlannedToolArgsCapture.snapshotPurchase(state, rq, toolId, args, ctx));
                    continue;
                }
                Map<String, Object> args = toolArgs(
                        toolId,
                        departmentIdArgumentForTool(toolId, deptForScopedTools, deptForBuildInsight),
                        dis,
                        start,
                        stop,
                        new LinkedHashMap<>(),
                        state);
                planned.put(toolId, HarnessPlannedToolArgsCapture.snapshotToolRequest(state, rq, toolId, args));
            }
        }

        state.setPlannedToolArgsByToolId(planned);
        state.setToolRequestCaptured(!planned.isEmpty());
        state.setToolExecuteSkipped(true);

        publisher.publish(rid, "agent_finished", Map.of(
                "agent", "ToolExecutionNode",
                "displayText", "Harness：Tool 入参快照完成（未执行 SQL）",
                "toolRequestCaptured", state.isToolRequestCaptured(),
                "plannedToolCount", planned.size()
        ));
        return state;
    }

    private static final List<String> BUSINESS_OVERVIEW_MULTI_ORCH_PROMOTE_KEYS = List.of(
            "businessOverviewAgentResults",
            "businessOverviewSelectedAgents",
            "businessOverviewExpectedDomainOrder",
            "businessOverviewAgentResultStatus",
            "businessOverviewDomainsAttempted",
            "businessOverviewSuccessfulDomains",
            "businessOverviewFailedDomains",
            "businessOverviewAllExpectedDomainsAttempted",
            "businessOverviewEnvelopeSummary",
            "businessOverviewMultiAgentBatchAttempted",
            "businessOverviewMultiAgentBatchLoopFinished",
            "businessOverviewMultiAgentAllExpectedDomainsExecuted",
            "businessOverviewMultiAgentAllDomainsSkipped",
            "businessOverviewMultiAgentBatchUsableForDiagnosis",
            "businessOverviewMultiAgentBatchCompleted",
            "businessOverviewMultiAgentAnyDomainSuccess",
            "businessOverviewOrchestrationWarnings",
            "harnessOrchestratedSurfacePath",
            "harnessOrchestratedSurfaceIntent",
            "harnessOrchestratedPurposeIntent",
            "harnessOriginalEffectivePath",
            "harnessOriginalEffectiveIntent",
            "businessOverviewDispatchPlan",
            "businessOverviewMultiMasterEnabled");

    private static void mergeMasterBusinessAgentDebug(
            AiRunState state,
            MasterBusinessAgentResult revenueMr,
            MasterBusinessAgentResult purchaseMr,
            MasterBusinessAgentResult stockReduceMr,
            MasterBusinessAgentResult dishProfitMr,
            MasterBusinessAgentResult businessOverviewMultiMr,
            MasterBusinessAgentResult warehouseMr) {
        if (state == null) {
            return;
        }
        LinkedHashMap<String, Object> dbg = new LinkedHashMap<>();
        LinkedHashMap<String, Object> multiNested = null;
        Map<String, Object> multiFlatSource = null;
        if (businessOverviewMultiMr != null
                && businessOverviewMultiMr.getDebug() != null
                && !businessOverviewMultiMr.getDebug().isEmpty()) {
            multiFlatSource = businessOverviewMultiMr.getDebug();
            multiNested = new LinkedHashMap<>(multiFlatSource);
            dbg.put("businessOverviewMultiMaster", multiNested);
        }
        if (revenueMr != null && revenueMr.getDebug() != null && !revenueMr.getDebug().isEmpty()) {
            dbg.putAll(revenueMr.getDebug());
        }
        if (purchaseMr != null && purchaseMr.getDebug() != null && !purchaseMr.getDebug().isEmpty()) {
            dbg.putAll(purchaseMr.getDebug());
        }
        if (stockReduceMr != null && stockReduceMr.getDebug() != null && !stockReduceMr.getDebug().isEmpty()) {
            dbg.putAll(stockReduceMr.getDebug());
        }
        if (dishProfitMr != null && dishProfitMr.getDebug() != null && !dishProfitMr.getDebug().isEmpty()) {
            dbg.putAll(dishProfitMr.getDebug());
        }
        if (warehouseMr != null && warehouseMr.getDebug() != null && !warehouseMr.getDebug().isEmpty()) {
            dbg.putAll(warehouseMr.getDebug());
        }
        if (multiFlatSource != null) {
            for (String k : BUSINESS_OVERVIEW_MULTI_ORCH_PROMOTE_KEYS) {
                if (multiFlatSource.containsKey(k)) {
                    dbg.put(k, multiFlatSource.get(k));
                }
            }
        }
        BusinessOverviewExecutionDebugContract.apply(dbg, state, businessOverviewMultiMr);
        state.setMasterBusinessAgentDebug(dbg.isEmpty() ? null : dbg);
    }

    private static Long departmentIdArgumentForTool(String toolId, Long defaultDept, Long buildInsightDept) {
        if (AiBusinessToolIds.DISH_PROFIT_ANALYSIS.equals(toolId)
                || AiBusinessToolIds.DISH_INGREDIENT_COST_BREAKDOWN.equals(toolId)) {
            return buildInsightDept;
        }
        return defaultDept;
    }

    /** {@link RevenueQueryToolExecutor} 等共用，避免复制菜品毛利 dep 规则。 */
    public static Long departmentIdArgumentForToolPublic(String toolId, Long defaultDept, Long buildInsightDept) {
        return departmentIdArgumentForTool(toolId, defaultDept, buildInsightDept);
    }

    /**
     * 避免「集团角色 + 单店解析范围」误传 sentinel，或与单店 depFather 错位。
     */
    static boolean shouldRouteGroupWideDishInsight(AiRunState state) {
        return shouldRouteGroupWideBusinessOverview(state) && resolvedOrgIndicatesGroupOrMultiStoreDishAggregate(state);
    }

    /**
     * 出库 Tool 走 harness 入参（含 narrative / group 聚合）：专线、经营诊断，以及四域内嵌链（概览 MULTI / 成本诊断）。
     */
    static boolean usesStockReduceHarnessToolArgs(AiRunState state) {
        if (state == null) {
            return false;
        }
        if (state.isStockReduceQueryPath() || state.isBusinessDiagnosisPath()) {
            return true;
        }
        List<String> plan = state.getDataPlanTools();
        if (plan == null || !plan.contains(AiBusinessToolIds.STOCK_REDUCE_QUERY)) {
            return false;
        }
        return state.isCostInsightPath() || state.isBusinessOverviewPath();
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

    private Map<String, Object> toolArgs(String toolId, Long dept, Long dis, String start, String stop,
            Map<String, Object> envelopes, AiRunState state) {
        if (AiBusinessToolIds.STOCK_REDUCE_QUERY.equals(toolId) && state != null
                && usesStockReduceHarnessToolArgs(state)) {
            return stockReduceQueryToolExecutor.buildHarnessToolArgs(dept, dis, start, stop, state);
        }
        if ((AiBusinessToolIds.DISH_PROFIT_ANALYSIS.equals(toolId)
                        || AiBusinessToolIds.DISH_INGREDIENT_COST_BREAKDOWN.equals(toolId))
                && state != null) {
            Long deptScoped = toolDepartmentResolutionSupport.resolveToolDepartmentFatherId(state, state.getDepartmentId());
            Long deptBuild =
                    toolDepartmentResolutionSupport.resolveBuildInsightDepartmentFatherId(state, deptScoped);
            return dishProfitQueryToolExecutor.buildDishProfitAnalysisToolArgs(
                    deptScoped, deptBuild, dis, start, stop, state);
        }
        Map<String, Object> m = new HashMap<>(8);
        if (AiBusinessToolIds.REVENUE_QUERY.equals(toolId)
                || AiBusinessToolIds.STOCK_REDUCE_QUERY.equals(toolId)
                || AiBusinessToolIds.WAREHOUSE_STOCK_OVERVIEW.equals(toolId)) {
            boolean warehouseOverviewTool = AiBusinessToolIds.WAREHOUSE_STOCK_OVERVIEW.equals(toolId);
            if (warehouseOverviewTool && state.isGroupWarehouseStockOverview()) {
                m.put(AiBusinessToolIds.ARG_GROUP_WAREHOUSE_STOCK_AGGREGATION, Boolean.TRUE);
                if (dis != null) {
                    m.put(AiBusinessToolIds.ARG_DIS_ID, dis);
                }
                applyGroupWideToolScopeArgs(m, state, "WAREHOUSE_STOCK_OVERVIEW");
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
                if (dis != null && AiBusinessToolIds.WAREHOUSE_STOCK_OVERVIEW.equals(toolId)) {
                    m.put(AiBusinessToolIds.ARG_DIS_ID, dis);
                }
                if (warehouseOverviewTool) {
                    putWarehouseResolvedScopeArgs(m, state);
                }
            }
            if (AiBusinessToolIds.REVENUE_QUERY.equals(toolId) && state != null
                    && (state.isRevenueOverviewPath() || state.isBusinessOverviewPath()
                    || state.isBusinessDiagnosisPath() || state.isCostInsightPath())) {
                boolean multiVisible = extractVisibleStoreDepartmentIds(state.getResolvedQueryContext()).size() > 1;
                if (shouldRouteGroupWideBusinessOverview(state) || multiVisible) {
                    m.put(AiBusinessToolIds.ARG_GROUP_WIDE_OVERVIEW_HINT, Boolean.TRUE);
                    var revenueCtx = state.getAiUserContext();
                    applyGroupWideToolScopeArgs(m, state, toolId);
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
        } else if (AiBusinessToolIds.PURCHASE_OVERVIEW.equals(toolId)) {
            m.putAll(PurchaseOverviewToolExecutor.buildPurchaseOverviewToolArgs(dept, dis, start, stop, state));
            logPurchaseOverviewToolArgs(state, m);
        }
        return m;
    }

    /**
     * 经营看板广角：不因 roleCode 偶发空缺而误走「单锚点单行」路径；集团与 {@link AiResolvedOrgScope} 对齐。
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
        AiResolvedQueryContext rq = state.getResolvedQueryContext();
        if (rq != null && rq.getOrgScope() != null
                && AiResolvedOrgScope.SCOPE_GROUP.equals(rq.getOrgScope().getScopeType())) {
            return true;
        }
        return false;
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

    private static void applyGroupWideToolScopeArgs(Map<String, Object> m, AiRunState state, String toolId) {
        if (m == null || state == null) {
            return;
        }
        BusinessScopeResolutionSupport.GroupWideToolScope scope =
                BusinessScopeResolutionSupport.resolveGroupWideToolScope(state.getResolvedQueryContext());
        if (!scope.resolvedDepartmentIds().isEmpty()) {
            m.put(AiBusinessToolIds.ARG_RESOLVED_DEPARTMENT_IDS, new ArrayList<>(scope.resolvedDepartmentIds()));
            m.put(AiBusinessToolIds.ARG_PARENT_STORE_COUNT, scope.parentStoreCount());
            return;
        }
        log.warn(
                "[BusinessToolExecutionNode] GROUP tool scope missing from ResolvedQueryContext; "
                        + "legacy AiQueryScope not applied. runId={} toolId={} scopeSource={}",
                state.getRunId(),
                toolId,
                scope.scopeSource());
    }

    /**
     * 集团经营概览：与 {@link AiResolvedQueryContext#getOrgScope()}{@code #visibleStores} 对齐。
     * 实现见 {@link BusinessScopeResolutionSupport#extractVisibleStoreRootDepartmentIds}。
     */
    public static List<Integer> extractVisibleStoreDepartmentIds(AiResolvedQueryContext ctx) {
        return BusinessScopeResolutionSupport.extractVisibleStoreRootDepartmentIds(ctx);
    }

    /**
     * 菜品毛利/销量等工具：与 Harness {@code effectiveSqlDepartmentIds} 同源（{@link com.nongxinle.ai.context.AiResolvedDataScope#getEffectiveSqlDepartmentIds()}）。
     */
    static List<Integer> extractSqlQueryDepartmentIdsForTools(AiResolvedQueryContext ctx) {
        return BusinessScopeResolutionSupport.extractEffectiveSqlDepartmentIdsForTools(ctx);
    }
}
