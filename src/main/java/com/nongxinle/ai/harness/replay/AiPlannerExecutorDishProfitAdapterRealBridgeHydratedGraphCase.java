package com.nongxinle.ai.harness.replay;

import com.nongxinle.ai.context.AiResolvedDataScope;
import com.nongxinle.ai.context.AiResolvedOrgScope;
import com.nongxinle.ai.context.AiResolvedQueryContext;
import com.nongxinle.ai.context.AiResolvedQueryIntent;
import com.nongxinle.ai.context.AiResolvedTimeWindow;
import com.nongxinle.ai.context.AiStoreScopeDTO;
import com.nongxinle.ai.conversation.AiQuerySemanticLexicon;
import com.nongxinle.ai.core.AiRunState;
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
import com.nongxinle.ai.semantic.AiQuerySemanticParseResult;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * {@link AiHarnessBuiltinCases#PLANNER_EXECUTOR_DISH_PROFIT_ADAPTER_REAL_BRIDGE_HYDRATED_CORE}：物化最小
 * {@link AiRunState} + {@link AiResolvedQueryContext}（单店 {@link AiResolvedOrgScope#SCOPE_STORE} / AAA），使 Spring Bean
 * {@link com.nongxinle.ai.planner.DishProfitPlannerRealReadBridge} 走真实 {@code dish_profit_analysis}。
 *
 * <p><b>C-29 curl 验收（环境、DB、权限通过时曾观测）</b>：{@code overallStatus=SUCCESS}、{@code degradedSteps=[]}、
 * {@code step_dish_profit_adapter_hydrated} SUCCESS、{@code usedTools} 含 {@code dish_profit_analysis}、
 * {@code plannerDishProfitAdapterHonesty=REAL_BRIDGE_HYDRATED_DISH_PROFIT_TOOL_OK}；{@code AiRunState#dishProfitPath} 为 true。</p>
 *
 * <p>见 {@code docs/ai/dish-profit-planner-adapter-design.md} §7.0、§7.5、§7.7，及 {@code docs/ai/planner-executor-v1-design.md} §12。</p>
 */
public final class AiPlannerExecutorDishProfitAdapterRealBridgeHydratedGraphCase {

    public static final String CASE_ID =
            AiHarnessBuiltinCases.PLANNER_EXECUTOR_DISH_PROFIT_ADAPTER_REAL_BRIDGE_HYDRATED_CORE;

    public static final String HONESTY_HYDRATED_TOOL_OK = "REAL_BRIDGE_HYDRATED_DISH_PROFIT_TOOL_OK";
    public static final String HONESTY_HYDRATED_TOOL_DEGRADED = "REAL_BRIDGE_HYDRATED_DISH_PROFIT_TOOL_DEGRADED";

    public static final String EXAMPLE_USER_MESSAGE =
            "Harness dish-profit hydrated real-bridge（单店 STORE AAA；依赖 DB 有方可达 SUCCESS）";

    public static final String PLAN_ID = "plan-dish-profit-adapter-real-bridge-hydrated-v1";
    public static final String PLAN_TYPE = "PLANNER_EXECUTOR_DISH_PROFIT_ADAPTER_REAL_BRIDGE_HYDRATED_CORE_V1";
    public static final String FINAL_ANSWER_PLAN_TYPE =
            "MOCK_RECOMMENDATION_AFTER_DISH_PROFIT_ADAPTER_REAL_BRIDGE_HYDRATED_V1";

    public static final String HARNESS_RESOLVED_CONTEXT_REF =
            "HARNESS_DISH_PROFIT_ADAPTER_REAL_BRIDGE_HYDRATED_RESOLVED_CTX_REF";
    public static final String HARNESS_ANSWER_PLAN_REF_AFTER_DISH_PROFIT =
            "HARNESS_ANSWER_PLAN_REF_AFTER_DISH_PROFIT_HYDRATED";

    /** 单店 AAA（gb_department_id=1）。 */
    public static final long HARNESS_STORE_DEPARTMENT_ID = 1L;

    private static final long HARNESS_SYNTHETIC_RUN_ID = 9_000_000L;

    public static final long HARNESS_DISH_PROFIT_DISTRIBUTER_ID = 2L;

    private static final LocalDate HARNESS_TIME_START = LocalDate.of(2026, 5, 1);
    private static final LocalDate HARNESS_TIME_END = LocalDate.of(2026, 5, 14);

    private AiPlannerExecutorDishProfitAdapterRealBridgeHydratedGraphCase() {
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
                        .timeLabel("2026-05-01..2026-05-14 (Harness dish-profit hydrated)")
                        .build();
        AiResolvedQueryIntent qi =
                AiResolvedQueryIntent.builder()
                        .intentCode(AiResolvedQueryIntent.DISH_PROFIT)
                        .pathCode(AiResolvedQueryIntent.PATH_DISH_PROFIT)
                        .structuredIntentDetail(AiQuerySemanticLexicon.STRUCTURED_DISH_PROFIT_OVERVIEW)
                        .build();
        AiResolvedDataScope dataScope =
                AiResolvedDataScope.builder()
                        .queryScopeKind(AiResolvedDataScope.QUERY_SCOPE_KIND_STORE)
                        .queryStoreIds(List.of((int) HARNESS_STORE_DEPARTMENT_ID))
                        .expandedSqlDepartmentIds(List.of((int) HARNESS_STORE_DEPARTMENT_ID))
                        .storeRootDepartmentIds(List.of(HARNESS_STORE_DEPARTMENT_ID))
                        .visibleStoreIds(List.of(HARNESS_STORE_DEPARTMENT_ID))
                        .queryScopeMode(AiResolvedDataScope.QUERY_SCOPE_MODE_STORE)
                        .build();
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
                .harnessMultiStoreScopeDetected(false)
                .harnessMultiStoreScopeApplied(false)
                .harnessSingleStoreNarrowingBlocked(false)
                .build();
    }

    public static DishProfitPlannerReadRequest buildFullHarnessDishProfitReadRequest() {
        return DishProfitPlannerReadRequest.builder()
                .resolvedQueryContextRef(HARNESS_RESOLVED_CONTEXT_REF)
                .timeStart(HARNESS_TIME_START)
                .timeEnd(HARNESS_TIME_END)
                .timeLabel("2026-05-01..2026-05-14 (Harness dish-profit hydrated)")
                .scopeType(AiResolvedOrgScope.SCOPE_STORE)
                .visibleStores(
                        List.of(
                                DishProfitPlannerVisibleStore.builder()
                                        .departmentId(HARNESS_STORE_DEPARTMENT_ID)
                                        .displayLabel("AAA")
                                        .build()))
                .queryDepartmentIds(List.of(HARNESS_STORE_DEPARTMENT_ID))
                .targetStoreDepartmentId(HARNESS_STORE_DEPARTMENT_ID)
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
                .departmentId(HARNESS_STORE_DEPARTMENT_ID)
                .distributerId(HARNESS_DISH_PROFIT_DISTRIBUTER_ID)
                .resolvedQueryContext(rq)
                .toolResults(new HashMap<>())
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
                        .departmentId(HARNESS_STORE_DEPARTMENT_ID)
                        .distributerId(HARNESS_DISH_PROFIT_DISTRIBUTER_ID)
                        .conversationId("0")
                        .runId(Long.toString(HARNESS_SYNTHETIC_RUN_ID))
                        .plannerReadRequest(slice)
                        .build();
        List<PlannerStep> steps =
                List.of(
                        PlannerStep.builder()
                                .stepId("step_dish_profit_adapter_hydrated")
                                .stepName("dish_profit_overview_hydrated")
                                .order(1)
                                .targetAgent(DishProfitPlannerAgentAdapter.TARGET_AGENT)
                                .targetTool(DishProfitPlannerAgentAdapter.TARGET_TOOL)
                                .inputSummary(
                                        "菜品毛利只读（Hydrated AiRunState + AiResolvedQueryContext + DishProfitPlannerRealReadBridge）")
                                .expectedOutput("SUCCESS when DB has dish-profit signal; else honest DEGRADED")
                                .acceptanceCriteria(
                                        "single STORE AAA; dishProfitPath true; real dish_profit_analysis; no userMessage parse")
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

    public static Map<String, Object> toHarnessSummary(
            PlannerExecutorResult result,
            String replayMessage,
            long runId,
            long conversationId) {
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
            root.put("plannerDishProfitAdapterHonesty", HONESTY_HYDRATED_TOOL_OK);
            root.put(
                    "plannerDishProfitAdapterNote",
                    "dish_profit_analysis executed with hydrated minimal AiRunState + AiResolvedQueryContext (STORE AAA)");
        } else {
            root.put("plannerDishProfitAdapterHonesty", HONESTY_HYDRATED_TOOL_DEGRADED);
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
