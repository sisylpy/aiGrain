package com.nongxinle.ai.harness.replay;

import com.nongxinle.ai.context.AiResolvedDataScope;
import com.nongxinle.ai.context.AiResolvedOrgScope;
import com.nongxinle.ai.context.AiResolvedQueryContext;
import com.nongxinle.ai.context.AiResolvedTimeWindow;
import com.nongxinle.ai.context.AiStoreScopeDTO;
import com.nongxinle.ai.core.AiRunState;
import com.nongxinle.ai.graph.business.scope.BusinessScopeResolutionSupport;
import com.nongxinle.ai.planner.PlannerExecutionPlan;
import com.nongxinle.ai.planner.PlannerExecutorResult;
import com.nongxinle.ai.planner.PlannerExecutorTrace;
import com.nongxinle.ai.planner.PlannerFailureStrategy;
import com.nongxinle.ai.planner.PlannerRevenueExecutionContext;
import com.nongxinle.ai.planner.PlannerStep;
import com.nongxinle.ai.planner.PlannerStepMockExecutionStatus;
import com.nongxinle.ai.planner.PlannerStepResult;
import com.nongxinle.ai.planner.PlannerStepStatus;
import com.nongxinle.ai.planner.RecommendationPlannerMockAgentAdapter;
import com.nongxinle.ai.planner.RevenuePlannerAgentAdapter;
import com.nongxinle.ai.planner.RevenuePlannerReadRequest;
import com.nongxinle.ai.planner.RevenuePlannerVisibleStore;
import com.nongxinle.ai.tool.business.AiBusinessToolIds;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * {@link AiHarnessBuiltinCases#PLANNER_EXECUTOR_REVENUE_ADAPTER_GROUP_HYDRATED_CORE}：物化最小
 * {@link AiRunState} + {@link AiResolvedQueryContext}（{@link AiResolvedOrgScope#SCOPE_GROUP}，双可见门店根），使
 * {@link com.nongxinle.ai.planner.RevenuePlannerRealReadBridge} 走真实 {@code revenue_query}；{@code
 * departmentId} 不当作单店查询锚点（RunState / ExecutionContext 可为 null）；{@code dataScope} 由
 * {@link AiResolvedDataScope#fromOrgScope(org)} 推导。
 *
 * <p>不接 Composite；不接 Purchase / Stock / DishProfit。
 */
public final class AiPlannerExecutorRevenueAdapterGroupHydratedGraphCase {

    public static final String CASE_ID = AiHarnessBuiltinCases.PLANNER_EXECUTOR_REVENUE_ADAPTER_GROUP_HYDRATED_CORE;

    public static final String HONESTY_GROUP_TOOL_OK = "REAL_BRIDGE_HYDRATED_REVENUE_GROUP_TOOL_OK";
    public static final String HONESTY_GROUP_TOOL_DEGRADED = "REAL_BRIDGE_HYDRATED_REVENUE_GROUP_TOOL_DEGRADED";

    public static final String EXAMPLE_USER_MESSAGE =
            "Harness revenue GROUP hydrated real-bridge（scopeType=GROUP；可见 AAA+汀兰；依赖 DB 与权限）";

    public static final String PLAN_ID = "plan-revenue-adapter-group-hydrated-v1";
    public static final String PLAN_TYPE = "PLANNER_EXECUTOR_REVENUE_ADAPTER_GROUP_HYDRATED_CORE_V1";
    public static final String FINAL_ANSWER_PLAN_TYPE =
            "MOCK_RECOMMENDATION_AFTER_REVENUE_ADAPTER_GROUP_HYDRATED_V1";

    public static final String HARNESS_RESOLVED_CONTEXT_REF =
            "HARNESS_REVENUE_ADAPTER_GROUP_HYDRATED_RESOLVED_CTX_REF";
    public static final String HARNESS_ANSWER_PLAN_REF_AFTER_REVENUE =
            "HARNESS_ANSWER_PLAN_REF_AFTER_REVENUE_GROUP_HYDRATED";

    public static final long HARNESS_STORE_AAA_DEPARTMENT_ID = 1L;
    /** 汀兰餐厅（门店根 gb_department_id=3），与 C-43 设计一致。 */
    public static final long HARNESS_STORE_TINGLAN_DEPARTMENT_ID = 3L;

    private static final LocalDate HARNESS_TIME_START = LocalDate.of(2026, 5, 1);
    private static final LocalDate HARNESS_TIME_END = LocalDate.of(2026, 5, 14);

    /** 与 STORE Hydrated 错开 runId，便于日志区分。 */
    private static final long HARNESS_SYNTHETIC_RUN_ID = 9_000_051L;

    private AiPlannerExecutorRevenueAdapterGroupHydratedGraphCase() {
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
                        .timeLabel("2026-05-01..2026-05-14 (Harness GROUP hydrated)")
                        .build();
        AiResolvedDataScope dataScope = AiResolvedDataScope.fromOrgScope(org);
        return AiResolvedQueryContext.builder()
                .runId(HARNESS_SYNTHETIC_RUN_ID)
                .userId(1L)
                .orgScope(org)
                .dataScope(dataScope)
                .timeWindow(tw)
                .build();
    }

    public static RevenuePlannerReadRequest buildFullHarnessRevenueReadRequest() {
        return RevenuePlannerReadRequest.builder()
                .resolvedQueryContextRef(HARNESS_RESOLVED_CONTEXT_REF)
                .timeStart(HARNESS_TIME_START)
                .timeEnd(HARNESS_TIME_END)
                .timeLabel("2026-05-01..2026-05-14 (Harness GROUP hydrated)")
                .scopeType(AiResolvedOrgScope.SCOPE_GROUP)
                .visibleStores(
                        List.of(
                                RevenuePlannerVisibleStore.builder()
                                        .departmentId(HARNESS_STORE_AAA_DEPARTMENT_ID)
                                        .displayLabel("AAA")
                                        .build(),
                                RevenuePlannerVisibleStore.builder()
                                        .departmentId(HARNESS_STORE_TINGLAN_DEPARTMENT_ID)
                                        .displayLabel("汀兰餐厅")
                                        .build()))
                .queryDepartmentIds(List.of(HARNESS_STORE_AAA_DEPARTMENT_ID, HARNESS_STORE_TINGLAN_DEPARTMENT_ID))
                .targetStoreDepartmentId(null)
                .answerPlanRef(HARNESS_ANSWER_PLAN_REF_AFTER_REVENUE)
                .build();
    }

    public static AiRunState buildHydratedRunState(AiResolvedQueryContext rq) {
        return AiRunState.builder()
                .runId(HARNESS_SYNTHETIC_RUN_ID)
                .conversationId(0L)
                .userId(1L)
                .departmentId(null)
                .distributerId(null)
                .resolvedQueryContext(rq)
                .toolResults(new HashMap<>())
                .build();
    }

    public static PlannerExecutionPlan buildPlan() {
        RevenuePlannerReadRequest revenueSlice = buildFullHarnessRevenueReadRequest();
        AiResolvedQueryContext rq = buildHydratedResolvedQueryContext();
        AiRunState runState = buildHydratedRunState(rq);
        PlannerRevenueExecutionContext revenueExec =
                PlannerRevenueExecutionContext.builder()
                        .runState(runState)
                        .resolvedQueryContext(rq)
                        .resolvedQueryContextRef(HARNESS_RESOLVED_CONTEXT_REF)
                        .userId(1L)
                        .departmentId(null)
                        .distributerId(null)
                        .conversationId("0")
                        .runId(Long.toString(HARNESS_SYNTHETIC_RUN_ID))
                        .plannerReadRequest(revenueSlice)
                        .build();
        List<PlannerStep> steps =
                List.of(
                        PlannerStep.builder()
                                .stepId("step_revenue_adapter_hydrated")
                                .stepName("revenue_overview_group_hydrated")
                                .order(1)
                                .targetAgent(RevenuePlannerAgentAdapter.TARGET_AGENT)
                                .targetTool(RevenuePlannerAgentAdapter.TARGET_TOOL)
                                .inputSummary(
                                        "营收只读 GROUP（Hydrated AiRunState + AiResolvedQueryContext + GROUP dataScope）")
                                .expectedOutput("SUCCESS when revenue_query returns multi-store payload; else DEGRADED")
                                .acceptanceCriteria(
                                        "scopeType=GROUP; visibleStores 1+3; targetStoreDepartmentId null; real revenue_query")
                                .mockExecutionStatus(null)
                                .answerPlanRef(HARNESS_ANSWER_PLAN_REF_AFTER_REVENUE)
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
                .revenueReadRequest(revenueSlice)
                .revenueExecutionContext(revenueExec)
                .finalAnswerPlanType(FINAL_ANSWER_PLAN_TYPE)
                .build();
    }

    /**
     * @param executedPlan same instance passed to {@link com.nongxinle.ai.planner.PlannerExecutor#execute}，以便读取
     *     执行后 {@link AiRunState#getToolResults()} 中的 {@code revenue_query} 观测字段。
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
        root.put("harnessReplayMode", AiHarnessReplayMode.PLANNER_EXECUTOR_REVENUE_ADAPTER.name());

        PlannerExecutorTrace tr = result != null ? result.getTrace() : null;
        PlannerStepStatus overall = tr != null ? tr.getOverallStatus() : null;
        PlannerStepResult revenueStep = findStepResult(tr, "step_revenue_adapter_hydrated");

        boolean revenueSuccess =
                revenueStep != null && revenueStep.getStatus() == PlannerStepStatus.SUCCESS;
        boolean fullSuccess = overall == PlannerStepStatus.SUCCESS;

        if (revenueSuccess && fullSuccess) {
            root.put("plannerRevenueAdapterHonesty", HONESTY_GROUP_TOOL_OK);
            root.put(
                    "plannerRevenueAdapterNote",
                    "revenue_query executed with GROUP hydrated context (visible store roots 1+3); not single-store fallback");
        } else {
            root.put("plannerRevenueAdapterHonesty", HONESTY_GROUP_TOOL_DEGRADED);
            StringBuilder note = new StringBuilder();
            if (revenueStep != null) {
                note.append("revenue_step=").append(revenueStep.getStatus());
                if (revenueStep.getDegradedReason() != null) {
                    note.append("; ").append(revenueStep.getDegradedReason());
                }
                if (revenueStep.getErrorMessage() != null) {
                    note.append("; err=").append(revenueStep.getErrorMessage());
                }
            } else {
                note.append("revenue_step_missing");
            }
            if (overall != null) {
                note.append("; overall=").append(overall);
            }
            root.put("plannerRevenueAdapterNote", note.toString());
        }

        List<Integer> visibleRoots =
                executedPlan != null && executedPlan.getRevenueExecutionContext() != null
                        ? BusinessScopeResolutionSupport.extractVisibleStoreRootDepartmentIds(
                                executedPlan.getRevenueExecutionContext().getResolvedQueryContext())
                        : List.of();
        root.put("harnessRevenueGroupVisibleStoreRootDepartmentIds", new ArrayList<>(visibleRoots));

        putRevenueToolObservation(root, executedPlan);
        return root;
    }

    @SuppressWarnings("unchecked")
    private static void putRevenueToolObservation(Map<String, Object> root, PlannerExecutionPlan executedPlan) {
        if (executedPlan == null || executedPlan.getRevenueExecutionContext() == null) {
            return;
        }
        AiRunState st = executedPlan.getRevenueExecutionContext().getRunState();
        if (st == null || st.getToolResults() == null) {
            root.put("harnessRevenueQueryEnvelopePresent", Boolean.FALSE);
            return;
        }
        Object raw = st.getToolResults().get(AiBusinessToolIds.REVENUE_QUERY);
        if (!(raw instanceof Map<?, ?> envelope)) {
            root.put("harnessRevenueQueryEnvelopePresent", Boolean.FALSE);
            return;
        }
        root.put("harnessRevenueQueryEnvelopePresent", Boolean.TRUE);
        Object dataRaw = envelope.get("data");
        Map<String, Object> inner = null;
        if (dataRaw instanceof Map<?, ?> m) {
            inner = (Map<String, Object>) m;
        }
        if (inner != null) {
            if (inner.get("totalRevenue") != null) {
                root.put("harnessRevenueQueryTotalRevenue", inner.get("totalRevenue"));
            }
            Object ranking = inner.get("storeRevenueRanking");
            if (ranking instanceof List<?> list) {
                root.put("harnessRevenueQueryStoreRevenueRankingSize", list.size());
                List<Integer> deptIds = new ArrayList<>();
                for (Object row : list) {
                    if (row instanceof Map<?, ?> rm) {
                        Object sid = rm.get("storeDepartmentId");
                        if (sid instanceof Number n) {
                            deptIds.add(n.intValue());
                        }
                    }
                }
                if (!deptIds.isEmpty()) {
                    root.put("harnessRevenueQueryRankingStoreDepartmentIds", deptIds);
                }
            }
        }
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
