package com.nongxinle.ai.harness.replay;

import com.nongxinle.ai.context.AiResolvedOrgScope;
import com.nongxinle.ai.context.AiResolvedQueryContext;
import com.nongxinle.ai.context.AiResolvedTimeWindow;
import com.nongxinle.ai.context.AiStoreScopeDTO;
import com.nongxinle.ai.core.AiRunState;
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

import java.time.LocalDate;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * {@link AiHarnessBuiltinCases#PLANNER_EXECUTOR_REVENUE_ADAPTER_REAL_BRIDGE_HYDRATED_CORE}：物化最小
 * {@link AiRunState} + {@link AiResolvedQueryContext}（单店 {@link AiResolvedOrgScope#SCOPE_STORE}），使
 * {@link com.nongxinle.ai.planner.RevenuePlannerRealReadBridge} 走真实 {@code revenue_query}；path 旗标均 false。
 * <p>C-14 收口说明与 curl 实测见 {@code docs/ai/planner-executor-v1-design.md} §22.8–22.12。
 */
public final class AiPlannerExecutorRevenueAdapterRealBridgeHydratedGraphCase {

    public static final String CASE_ID = AiHarnessBuiltinCases.PLANNER_EXECUTOR_REVENUE_ADAPTER_REAL_BRIDGE_HYDRATED_CORE;

    public static final String HONESTY_HYDRATED_TOOL_OK = "REAL_BRIDGE_HYDRATED_REVENUE_TOOL_OK";
    public static final String HONESTY_HYDRATED_TOOL_DEGRADED = "REAL_BRIDGE_HYDRATED_REVENUE_TOOL_DEGRADED";

    public static final String EXAMPLE_USER_MESSAGE =
            "Harness revenue hydrated real-bridge（单店 STORE；依赖 DB 有营业额数据方可达 SUCCESS）";

    public static final String PLAN_ID = "plan-revenue-adapter-real-bridge-hydrated-v1";
    public static final String PLAN_TYPE = "PLANNER_EXECUTOR_REVENUE_ADAPTER_REAL_BRIDGE_HYDRATED_CORE_V1";
    public static final String FINAL_ANSWER_PLAN_TYPE =
            "MOCK_RECOMMENDATION_AFTER_REVENUE_ADAPTER_REAL_BRIDGE_HYDRATED_V1";

    public static final String HARNESS_RESOLVED_CONTEXT_REF =
            "HARNESS_REVENUE_ADAPTER_REAL_BRIDGE_HYDRATED_RESOLVED_CTX_REF";
    public static final String HARNESS_ANSWER_PLAN_REF_AFTER_REVENUE =
            "HARNESS_ANSWER_PLAN_REF_AFTER_REVENUE_HYDRATED";

    /** 本地 AAA 门店根部门（gb_department_id=1）；子部门 2、5 由现有 Tool 解析展开，不在此展开。 */
    public static final long HARNESS_STORE_DEPARTMENT_ID = 1L;

    /** 与 {@link AiHarnessReplayPlannerExecutorMock} 首轮 {@code SYNTHETIC_RUN_ID_BASE + 0} 对齐。 */
    private static final long HARNESS_SYNTHETIC_RUN_ID = 9_000_000L;

    private static final LocalDate HARNESS_TIME_START = LocalDate.of(2026, 5, 1);
    private static final LocalDate HARNESS_TIME_END = LocalDate.of(2026, 5, 14);

    private AiPlannerExecutorRevenueAdapterRealBridgeHydratedGraphCase() {
    }

    public static AiResolvedQueryContext buildHydratedResolvedQueryContext() {
        List<AiStoreScopeDTO> stores =
                List.of(
                        AiStoreScopeDTO.builder()
                                .storeDepartmentId(HARNESS_STORE_DEPARTMENT_ID)
                                .storeName("AAA")
                                .build());
        AiResolvedOrgScope org =
                AiResolvedOrgScope.builder()
                        .scopeType(AiResolvedOrgScope.SCOPE_STORE)
                        .currentStoreDepartmentId(HARNESS_STORE_DEPARTMENT_ID)
                        .requestDepartmentId(HARNESS_STORE_DEPARTMENT_ID)
                        .visibleStores(stores)
                        .build();
        AiResolvedTimeWindow tw =
                AiResolvedTimeWindow.builder()
                        .startDate(HARNESS_TIME_START)
                        .endDate(HARNESS_TIME_END)
                        .timeLabel("2026-05-01..2026-05-14 (Harness hydrated)")
                        .build();
        return AiResolvedQueryContext.builder()
                .runId(HARNESS_SYNTHETIC_RUN_ID)
                .userId(1L)
                .orgScope(org)
                .timeWindow(tw)
                .build();
    }

    public static RevenuePlannerReadRequest buildFullHarnessRevenueReadRequest() {
        return RevenuePlannerReadRequest.builder()
                .resolvedQueryContextRef(HARNESS_RESOLVED_CONTEXT_REF)
                .timeStart(HARNESS_TIME_START)
                .timeEnd(HARNESS_TIME_END)
                .timeLabel("2026-05-01..2026-05-14 (Harness hydrated)")
                .scopeType(AiResolvedOrgScope.SCOPE_STORE)
                .visibleStores(
                        List.of(
                                RevenuePlannerVisibleStore.builder()
                                        .departmentId(HARNESS_STORE_DEPARTMENT_ID)
                                        .displayLabel("AAA")
                                        .build()))
                .queryDepartmentIds(List.of(HARNESS_STORE_DEPARTMENT_ID))
                .targetStoreDepartmentId(HARNESS_STORE_DEPARTMENT_ID)
                .answerPlanRef(HARNESS_ANSWER_PLAN_REF_AFTER_REVENUE)
                .build();
    }

    public static AiRunState buildHydratedRunState(AiResolvedQueryContext rq) {
        return AiRunState.builder()
                .runId(HARNESS_SYNTHETIC_RUN_ID)
                .conversationId(0L)
                .userId(1L)
                .departmentId(HARNESS_STORE_DEPARTMENT_ID)
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
                        .departmentId(HARNESS_STORE_DEPARTMENT_ID)
                        .distributerId(null)
                        .conversationId("0")
                        .runId(Long.toString(HARNESS_SYNTHETIC_RUN_ID))
                        .plannerReadRequest(revenueSlice)
                        .build();
        List<PlannerStep> steps =
                List.of(
                        PlannerStep.builder()
                                .stepId("step_revenue_adapter_hydrated")
                                .stepName("revenue_overview_month_hydrated")
                                .order(1)
                                .targetAgent(RevenuePlannerAgentAdapter.TARGET_AGENT)
                                .targetTool(RevenuePlannerAgentAdapter.TARGET_TOOL)
                                .inputSummary("营收只读（Hydrated AiRunState + AiResolvedQueryContext + RevenuePlannerRealReadBridge）")
                                .expectedOutput("SUCCESS when DB has revenue rows; else honest DEGRADED")
                                .acceptanceCriteria("single STORE; path flags false; real revenue_query")
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

    public static Map<String, Object> toHarnessSummary(
            PlannerExecutorResult result,
            String replayMessage,
            long runId,
            long conversationId) {
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
            root.put("plannerRevenueAdapterHonesty", HONESTY_HYDRATED_TOOL_OK);
            root.put(
                    "plannerRevenueAdapterNote",
                    "revenue_query executed with hydrated minimal AiRunState + AiResolvedQueryContext (STORE)");
        } else {
            root.put("plannerRevenueAdapterHonesty", HONESTY_HYDRATED_TOOL_DEGRADED);
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
        return root;
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
