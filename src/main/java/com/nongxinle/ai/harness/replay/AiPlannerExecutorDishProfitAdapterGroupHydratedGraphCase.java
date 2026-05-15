package com.nongxinle.ai.harness.replay;

import com.nongxinle.ai.context.AiResolvedDataScope;
import com.nongxinle.ai.context.AiResolvedOrgScope;
import com.nongxinle.ai.context.AiResolvedQueryContext;
import com.nongxinle.ai.context.AiResolvedQueryIntent;
import com.nongxinle.ai.context.AiResolvedTimeWindow;
import com.nongxinle.ai.context.AiStoreScopeDTO;
import com.nongxinle.ai.context.AiUserContext;
import com.nongxinle.ai.conversation.AiQuerySemanticLexicon;
import com.nongxinle.ai.core.AiRunState;
import com.nongxinle.ai.dto.business.DishProfitAnswerPlan;
import com.nongxinle.ai.graph.business.scope.BusinessScopeResolutionSupport;
import com.nongxinle.ai.mapping.AiRoleMapper;
import com.nongxinle.ai.planner.DishProfitPlannerAgentAdapter;
import com.nongxinle.ai.planner.DishProfitPlannerExecutionContext;
import com.nongxinle.ai.planner.DishProfitPlannerReadRequest;
import com.nongxinle.ai.planner.DishProfitPlannerVisibleStore;
import com.nongxinle.ai.planner.PlannerExecutionPlan;
import com.nongxinle.ai.planner.PlannerExecutorResult;
import com.nongxinle.ai.planner.PlannerExecutorTrace;
import com.nongxinle.ai.planner.PlannerFailureStrategy;
import com.nongxinle.ai.planner.PlannerStep;
import com.nongxinle.ai.planner.PlannerStepMockExecutionStatus;
import com.nongxinle.ai.planner.PlannerStepResult;
import com.nongxinle.ai.planner.PlannerStepStatus;
import com.nongxinle.ai.planner.RecommendationPlannerMockAgentAdapter;
import com.nongxinle.ai.security.AiPermissions;
import com.nongxinle.ai.security.AiRoleCodes;
import com.nongxinle.ai.semantic.AiQuerySemanticParseResult;
import com.nongxinle.ai.tool.business.AiBusinessToolIds;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * {@link AiHarnessBuiltinCases#PLANNER_EXECUTOR_DISH_PROFIT_ADAPTER_GROUP_HYDRATED_CORE}（C-47）：物化最小
 * {@link AiRunState} + {@link AiResolvedQueryContext}（{@link AiResolvedOrgScope#SCOPE_GROUP}，双可见门店根），使
 * {@link com.nongxinle.ai.planner.DishProfitPlannerRealReadBridge} 走真实 {@code dish_profit_analysis}。仅验证菜品毛利
 * Adapter；不接 Revenue / Purchase / StockReduce / Composite / LLM。
 *
 * <p><b>生产门禁</b>：{@link com.nongxinle.ai.graph.business.BusinessToolExecutionNode#shouldRouteGroupWideDishInsight} 需
 * {@link com.nongxinle.ai.graph.business.BusinessToolExecutionNode#shouldRouteGroupWideBusinessOverview} 为 true。
 * 该方法在 {@code state.getAiUserContext() == null} 时直接 false，故本 Harness 物化 {@link AiUserContext}：
 * {@link AiRoleCodes#GROUP_MANAGER} + {@link AiRoleMapper#permissionsForAiRole}（含 {@link AiPermissions#VIEW_COST} /
 * {@link AiPermissions#VIEW_DISH_SALES}），避免误判为单店 dep 路径；<strong>非</strong>「把 STORE 伪装成 GROUP」——
 * {@code orgScope.scopeType} 仍为 {@code GROUP}，{@code targetStoreDepartmentId=null}，{@code departmentId=null}。
 *
 * <p><b>{@code dishProfitPath}</b>：与 C-29 STORE Hydrated 一致置 {@code true}，对齐「菜品毛利专线」语义；{@link
 * com.nongxinle.ai.graph.business.DishProfitQueryToolExecutor#buildDishProfitAnalysisToolArgs} 不读取该 flag，以 {@code
 * shouldRouteGroupWideDishInsight} + {@code resolvedQueryContext} 为准。
 */
public final class AiPlannerExecutorDishProfitAdapterGroupHydratedGraphCase {

    public static final String CASE_ID =
            AiHarnessBuiltinCases.PLANNER_EXECUTOR_DISH_PROFIT_ADAPTER_GROUP_HYDRATED_CORE;

    public static final String HONESTY_GROUP_TOOL_OK = "REAL_BRIDGE_HYDRATED_DISH_PROFIT_GROUP_TOOL_OK";
    public static final String HONESTY_GROUP_TOOL_DEGRADED = "REAL_BRIDGE_HYDRATED_DISH_PROFIT_GROUP_TOOL_DEGRADED";

    public static final String EXAMPLE_USER_MESSAGE =
            "Harness dish-profit GROUP hydrated real-bridge（scopeType=GROUP；可见 AAA+汀兰；依赖 DB 与权限）";

    public static final String PLAN_ID = "plan-dish-profit-adapter-group-hydrated-v1";
    public static final String PLAN_TYPE = "PLANNER_EXECUTOR_DISH_PROFIT_ADAPTER_GROUP_HYDRATED_CORE_V1";
    public static final String FINAL_ANSWER_PLAN_TYPE =
            "MOCK_RECOMMENDATION_AFTER_DISH_PROFIT_ADAPTER_GROUP_HYDRATED_V1";

    public static final String HARNESS_RESOLVED_CONTEXT_REF =
            "HARNESS_DISH_PROFIT_ADAPTER_GROUP_HYDRATED_RESOLVED_CTX_REF";
    public static final String HARNESS_ANSWER_PLAN_REF_AFTER_DISH_PROFIT =
            "HARNESS_ANSWER_PLAN_REF_AFTER_DISH_PROFIT_GROUP_HYDRATED";

    public static final long HARNESS_STORE_AAA_DEPARTMENT_ID = 1L;
    /** 汀兰餐厅（门店根 gb_department_id=3），与 C-44/C-45/C-46 一致。 */
    public static final long HARNESS_STORE_TINGLAN_DEPARTMENT_ID = 3L;

    public static final long HARNESS_DISH_PROFIT_DISTRIBUTER_ID =
            AiPlannerExecutorDishProfitAdapterRealBridgeHydratedGraphCase.HARNESS_DISH_PROFIT_DISTRIBUTER_ID;

    private static final LocalDate HARNESS_TIME_START = LocalDate.of(2026, 5, 1);
    private static final LocalDate HARNESS_TIME_END = LocalDate.of(2026, 5, 14);

    /** 与 C-46 StockReduce GROUP（9_000_053）错开。 */
    private static final long HARNESS_SYNTHETIC_RUN_ID = 9_000_054L;

    private AiPlannerExecutorDishProfitAdapterGroupHydratedGraphCase() {
    }

    /** 与 C-29 相同 ROLE 权限快照，满足 {@link com.nongxinle.ai.security.AiPermissionGuard} 对菜品毛利 Tool 的校验。 */
    public static AiUserContext buildHarnessGroupManagerUserContext() {
        return AiUserContext.builder()
                .userId(1L)
                .roleCode(AiRoleCodes.GROUP_MANAGER)
                .roleName("Harness GROUP_MANAGER")
                .distributerId(HARNESS_DISH_PROFIT_DISTRIBUTER_ID)
                .permissions(new ArrayList<>(AiRoleMapper.permissionsForAiRole(AiRoleCodes.GROUP_MANAGER)))
                .build();
    }

    public static AiResolvedQueryContext buildHydratedResolvedQueryContext() {
        List<AiStoreScopeDTO> stores =
                List.of(
                        AiStoreScopeDTO.builder()
                                .storeDepartmentId(HARNESS_STORE_AAA_DEPARTMENT_ID)
                                .storeName("AAA")
                                .build(),
                        AiStoreScopeDTO.builder()
                                .storeDepartmentId(HARNESS_STORE_TINGLAN_DEPARTMENT_ID)
                                .storeName("汀兰餐厅")
                                .build());
        AiResolvedOrgScope org =
                AiResolvedOrgScope.builder()
                        .scopeType(AiResolvedOrgScope.SCOPE_GROUP)
                        .currentStoreDepartmentId(null)
                        .requestDepartmentId(null)
                        .visibleStores(stores)
                        .build();
        AiResolvedTimeWindow tw =
                AiResolvedTimeWindow.builder()
                        .startDate(HARNESS_TIME_START)
                        .endDate(HARNESS_TIME_END)
                        .timeLabel("2026-05-01..2026-05-14 (Harness dish-profit GROUP hydrated)")
                        .build();
        AiResolvedQueryIntent qi =
                AiResolvedQueryIntent.builder()
                        .intentCode(AiResolvedQueryIntent.DISH_PROFIT)
                        .pathCode(AiResolvedQueryIntent.PATH_DISH_PROFIT)
                        .structuredIntentDetail(AiQuerySemanticLexicon.STRUCTURED_DISH_PROFIT_OVERVIEW)
                        .build();
        AiResolvedDataScope dataScope = AiResolvedDataScope.fromOrgScope(org);
        AiQuerySemanticParseResult semantic =
                AiQuerySemanticParseResult.builder()
                        .metric(
                                AiQuerySemanticParseResult.MetricPart.builder()
                                        .primaryMetric("DISH_PROFIT_OVERVIEW")
                                        .build())
                        .build();
        return AiResolvedQueryContext.builder()
                .runId(HARNESS_SYNTHETIC_RUN_ID)
                .userId(1L)
                .orgScope(org)
                .timeWindow(tw)
                .dataScope(dataScope)
                .queryIntent(qi)
                .querySemanticParse(semantic)
                .effectiveIntentCode(AiResolvedQueryIntent.DISH_PROFIT)
                .effectivePathCode(AiResolvedQueryIntent.PATH_DISH_PROFIT)
                .mentionedDishName(null)
                .dishProfitMetricType("OVERVIEW")
                .harnessMultiStoreScopeDetected(true)
                .harnessMultiStoreScopeApplied(true)
                .harnessSingleStoreNarrowingBlocked(false)
                .build();
    }

    public static DishProfitPlannerReadRequest buildFullHarnessDishProfitReadRequest() {
        return DishProfitPlannerReadRequest.builder()
                .resolvedQueryContextRef(HARNESS_RESOLVED_CONTEXT_REF)
                .timeStart(HARNESS_TIME_START)
                .timeEnd(HARNESS_TIME_END)
                .timeLabel("2026-05-01..2026-05-14 (Harness dish-profit GROUP hydrated)")
                .scopeType(AiResolvedOrgScope.SCOPE_GROUP)
                .visibleStores(
                        List.of(
                                DishProfitPlannerVisibleStore.builder()
                                        .departmentId(HARNESS_STORE_AAA_DEPARTMENT_ID)
                                        .displayLabel("AAA")
                                        .build(),
                                DishProfitPlannerVisibleStore.builder()
                                        .departmentId(HARNESS_STORE_TINGLAN_DEPARTMENT_ID)
                                        .displayLabel("汀兰餐厅")
                                        .build()))
                .queryDepartmentIds(List.of(HARNESS_STORE_AAA_DEPARTMENT_ID, HARNESS_STORE_TINGLAN_DEPARTMENT_ID))
                .targetStoreDepartmentId(null)
                .structuredIntentDetail(AiQuerySemanticLexicon.STRUCTURED_DISH_PROFIT_OVERVIEW)
                .mentionedDishName(null)
                .dishProfitMetricType("OVERVIEW")
                .answerPlanRef(HARNESS_ANSWER_PLAN_REF_AFTER_DISH_PROFIT)
                .build();
    }

    public static AiRunState buildHydratedRunState(AiResolvedQueryContext rq) {
        return AiRunState.builder()
                .runId(HARNESS_SYNTHETIC_RUN_ID)
                .conversationId(0L)
                .userId(1L)
                .departmentId(null)
                .distributerId(HARNESS_DISH_PROFIT_DISTRIBUTER_ID)
                .resolvedQueryContext(rq)
                .toolResults(new HashMap<>())
                .aiUserContext(buildHarnessGroupManagerUserContext())
                .dishProfitPath(true)
                .groupStockReduceQuery(false)
                .groupPurchaseOverview(false)
                .groupWarehouseStockOverview(false)
                .build();
    }

    public static PlannerExecutionPlan buildPlan() {
        DishProfitPlannerReadRequest slice = buildFullHarnessDishProfitReadRequest();
        AiResolvedQueryContext rq = buildHydratedResolvedQueryContext();
        AiRunState runState = buildHydratedRunState(rq);
        DishProfitPlannerExecutionContext dishExec =
                DishProfitPlannerExecutionContext.builder()
                        .runState(runState)
                        .resolvedQueryContext(rq)
                        .resolvedQueryContextRef(HARNESS_RESOLVED_CONTEXT_REF)
                        .userId(1L)
                        .departmentId(null)
                        .distributerId(HARNESS_DISH_PROFIT_DISTRIBUTER_ID)
                        .conversationId("0")
                        .runId(Long.toString(HARNESS_SYNTHETIC_RUN_ID))
                        .plannerReadRequest(slice)
                        .build();
        List<PlannerStep> steps =
                List.of(
                        PlannerStep.builder()
                                .stepId("step_dish_profit_adapter_hydrated")
                                .stepName("dish_profit_overview_group_hydrated")
                                .order(1)
                                .targetAgent(DishProfitPlannerAgentAdapter.TARGET_AGENT)
                                .targetTool(DishProfitPlannerAgentAdapter.TARGET_TOOL)
                                .inputSummary(
                                        "菜品毛利只读 GROUP（Hydrated AiRunState + AiResolvedQueryContext + GROUP dataScope +"
                                                + " AiUserContext GROUP_MANAGER）")
                                .expectedOutput(
                                        "SUCCESS when dish_profit_analysis returns group/multi-store portfolio; else DEGRADED")
                                .acceptanceCriteria(
                                        "scopeType=GROUP; visibleStores 1+3; targetStoreDepartmentId null; "
                                                + "real dish_profit_analysis; not single-store AAA fallback")
                                .mockExecutionStatus(null)
                                .answerPlanRef(HARNESS_ANSWER_PLAN_REF_AFTER_DISH_PROFIT)
                                .build(),
                        PlannerStep.builder()
                                .stepId("step_recommendation_mock")
                                .stepName("recommendation_three")
                                .order(2)
                                .targetAgent(RecommendationPlannerMockAgentAdapter.TARGET_AGENT)
                                .targetTool(RecommendationPlannerMockAgentAdapter.TARGET_TOOL)
                                .inputSummary("mock 建议步")
                                .expectedOutput("RecommendationPlan 占位")
                                .acceptanceCriteria("mock 成功")
                                .mockExecutionStatus(PlannerStepMockExecutionStatus.SUCCESS)
                                .build());

        return PlannerExecutionPlan.builder()
                .planId(PLAN_ID)
                .planType(PLAN_TYPE)
                .steps(steps)
                .failureStrategy(PlannerFailureStrategy.CONTINUE_WITH_DEGRADED)
                .resolvedContextRef(HARNESS_RESOLVED_CONTEXT_REF)
                .dishProfitReadRequest(slice)
                .dishProfitExecutionContext(dishExec)
                .finalAnswerPlanType(FINAL_ANSWER_PLAN_TYPE)
                .build();
    }

    /**
     * @param executedPlan same instance passed to {@link com.nongxinle.ai.planner.PlannerExecutor#execute}
     */
    public static Map<String, Object> toHarnessSummary(
            PlannerExecutorResult result,
            String replayMessage,
            long runId,
            long conversationId,
            PlannerExecutionPlan executedPlan) {
        Map<String, Object> root =
                new LinkedHashMap<>(
                        AiPlannerExecutorMockGraphCase.toHarnessSummary(
                                result, replayMessage, runId, conversationId, CASE_ID));
        root.put("harnessReplayMode", AiHarnessReplayMode.PLANNER_EXECUTOR_DISH_PROFIT_ADAPTER.name());

        PlannerExecutorTrace tr = result != null ? result.getTrace() : null;
        PlannerStepStatus overall = tr != null ? tr.getOverallStatus() : null;
        PlannerStepResult dishStep = findStepResult(tr, "step_dish_profit_adapter_hydrated");

        boolean dishSuccess = dishStep != null && dishStep.getStatus() == PlannerStepStatus.SUCCESS;
        boolean fullSuccess = overall == PlannerStepStatus.SUCCESS;

        if (dishSuccess && fullSuccess) {
            root.put("plannerDishProfitAdapterHonesty", HONESTY_GROUP_TOOL_OK);
            root.put(
                    "plannerDishProfitAdapterNote",
                    "dish_profit_analysis executed with GROUP hydrated context (visible store roots 1+3; "
                            + "AiUserContext=GROUP_MANAGER for shouldRouteGroupWideBusinessOverview); "
                            + "not single-store AAA fallback");
        } else {
            root.put("plannerDishProfitAdapterHonesty", HONESTY_GROUP_TOOL_DEGRADED);
            StringBuilder note = new StringBuilder();
            if (dishStep != null) {
                note.append("dish_profit_step=").append(dishStep.getStatus());
                if (dishStep.getDegradedReason() != null) {
                    note.append("; ").append(dishStep.getDegradedReason());
                }
                if (dishStep.getErrorMessage() != null) {
                    note.append("; err=").append(dishStep.getErrorMessage());
                }
            } else {
                note.append("dish_profit_step_missing");
            }
            if (overall != null) {
                note.append("; overall=").append(overall);
            }
            root.put("plannerDishProfitAdapterNote", note.toString());
        }

        List<Integer> visibleRoots =
                executedPlan != null && executedPlan.getDishProfitExecutionContext() != null
                        ? BusinessScopeResolutionSupport.extractVisibleStoreRootDepartmentIds(
                                executedPlan.getDishProfitExecutionContext().getResolvedQueryContext())
                        : List.of();
        root.put("harnessDishProfitGroupVisibleStoreRootDepartmentIds", new ArrayList<>(visibleRoots));

        putDishProfitHarnessObservation(root, executedPlan);
        return root;
    }

    private static void putDishProfitHarnessObservation(Map<String, Object> root, PlannerExecutionPlan executedPlan) {
        if (executedPlan == null || executedPlan.getDishProfitExecutionContext() == null) {
            return;
        }
        AiRunState st = executedPlan.getDishProfitExecutionContext().getRunState();
        if (st == null) {
            return;
        }
        Object raw =
                st.getToolResults() != null ? st.getToolResults().get(AiBusinessToolIds.DISH_PROFIT_ANALYSIS) : null;
        root.put("harnessDishProfitAnalysisEnvelopePresent", raw instanceof Map<?, ?>);

        DishProfitAnswerPlan plan = st.getDishProfitAnswerPlan();
        if (plan == null) {
            return;
        }
        if (plan.getPlanType() != null) {
            root.put("harnessDishProfitAnswerPlanType", plan.getPlanType());
        }
        if (plan.getFocusRows() != null) {
            root.put("harnessDishProfitFocusRowsSize", plan.getFocusRows().size());
        }
        if (plan.getSecondaryRows() != null) {
            root.put("harnessDishProfitSecondaryRowsSize", plan.getSecondaryRows().size());
        }
        List<Integer> storeIdsFromFocus = extractStoreDepartmentIdsFromRows(plan.getFocusRows());
        List<Integer> storeIdsFromSecondary = extractStoreDepartmentIdsFromRows(plan.getSecondaryRows());
        if (!storeIdsFromFocus.isEmpty()) {
            root.put("harnessDishProfitFocusRowStoreDepartmentIds", storeIdsFromFocus);
        }
        if (!storeIdsFromSecondary.isEmpty()) {
            root.put("harnessDishProfitSecondaryRowStoreDepartmentIds", storeIdsFromSecondary);
        }
        if (plan.getFocusRows() != null && !plan.getFocusRows().isEmpty()) {
            Map<String, Object> r = plan.getFocusRows().get(0);
            if (r != null) {
                putIfNonNull(root, "harnessDishProfitFocusSalesAmount", r.get("listPriceRevenue"));
                putIfNonNull(root, "harnessDishProfitFocusCostAmount", r.get("actualCostAmount"));
                putIfNonNull(root, "harnessDishProfitFocusGrossProfitRate", r.get("blendedGrossMarginRateOnListPrice"));
                Object gp = r.get("portfolioGrossProfitAmount");
                if (gp == null) {
                    gp = r.get("grossProfitAmount");
                }
                putIfNonNull(root, "harnessDishProfitFocusGrossProfitAmount", gp);
            }
        }
        if (plan.getDebug() != null && !plan.getDebug().isEmpty()) {
            Object g = plan.getDebug().get("groupWideMendianAggregate");
            if (g != null) {
                root.put("harnessDishProfitPlanDebugGroupWideHint", g);
            }
        }
    }

    private static void putIfNonNull(Map<String, Object> root, String key, Object v) {
        if (v != null) {
            root.put(key, v);
        }
    }

    private static List<Integer> extractStoreDepartmentIdsFromRows(List<Map<String, Object>> rows) {
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        List<Integer> out = new ArrayList<>();
        for (Map<String, Object> r : rows) {
            if (r == null) {
                continue;
            }
            Object sid = r.get("storeDepartmentId");
            if (sid == null) {
                sid = r.get("departmentId");
            }
            if (sid instanceof Number n) {
                out.add(n.intValue());
            }
        }
        return out;
    }

    private static PlannerStepResult findStepResult(PlannerExecutorTrace tr, String stepId) {
        if (tr == null || tr.getStepResults() == null || stepId == null) {
            return null;
        }
        for (PlannerStepResult r : tr.getStepResults()) {
            if (stepId.equals(r.getStepId())) {
                return r;
            }
        }
        return null;
    }
}
