package com.nongxinle.ai.harness.replay;

import com.nongxinle.ai.context.AiResolvedOrgScope;
import com.nongxinle.ai.context.AiResolvedQueryContext;
import com.nongxinle.ai.context.AiResolvedQueryIntent;
import com.nongxinle.ai.context.AiResolvedTimeWindow;
import com.nongxinle.ai.context.AiStoreScopeDTO;
import com.nongxinle.ai.conversation.AiQuerySemanticLexicon;
import com.nongxinle.ai.core.AiRunState;
import com.nongxinle.ai.planner.PlannerExecutionPlan;
import com.nongxinle.ai.planner.PlannerExecutorResult;
import com.nongxinle.ai.planner.PlannerExecutorTrace;
import com.nongxinle.ai.planner.PlannerFailureStrategy;
import com.nongxinle.ai.planner.PlannerStep;
import com.nongxinle.ai.planner.PlannerStepMockExecutionStatus;
import com.nongxinle.ai.planner.PlannerStepResult;
import com.nongxinle.ai.planner.PlannerStepStatus;
import com.nongxinle.ai.planner.PurchasePlannerAgentAdapter;
import com.nongxinle.ai.planner.PurchasePlannerExecutionContext;
import com.nongxinle.ai.planner.PurchasePlannerReadRequest;
import com.nongxinle.ai.planner.PurchasePlannerVisibleStore;
import com.nongxinle.ai.planner.RecommendationPlannerMockAgentAdapter;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * {@link AiHarnessBuiltinCases#PLANNER_EXECUTOR_PURCHASE_ADAPTER_REAL_BRIDGE_HYDRATED_CORE}：物化最小
 * {@link AiRunState} + {@link AiResolvedQueryContext}（单店 {@link AiResolvedOrgScope#SCOPE_STORE}），使
 * {@link com.nongxinle.ai.planner.PurchasePlannerRealReadBridge} 走真实 {@code purchase_overview}。
 *
 * <p><strong>C-19 收口</strong>：curl Harness 已成功路径（{@code overallStatus=SUCCESS}、{@code degradedSteps=[]}、
 * {@code plannerPurchaseAdapterHonesty=REAL_BRIDGE_HYDRATED_PURCHASE_TOOL_OK}）见
 * {@code docs/ai/purchase-planner-adapter-design.md} §12 与 {@code docs/ai/planner-executor-v1-design.md} §24。</p>
 *
 * <p>C-19：<strong>未</strong>设置 {@link AiRunState#purchaseOverviewPath}（默认 false）；与营收 Hydrated 一致，
 * {@link com.nongxinle.ai.graph.business.PurchaseOverviewToolExecutor} 仅依赖 Resolver 解析的部门锚点 + Tool args。
 * 若未来实测某分支必须置 true，应<strong>仅</strong>在本类 {@link #buildHydratedRunState} 中显式写入并更新设计文档。</p>
 */
public final class AiPlannerExecutorPurchaseAdapterRealBridgeHydratedGraphCase {

    public static final String CASE_ID = AiHarnessBuiltinCases.PLANNER_EXECUTOR_PURCHASE_ADAPTER_REAL_BRIDGE_HYDRATED_CORE;

    public static final String HONESTY_HYDRATED_TOOL_OK = "REAL_BRIDGE_HYDRATED_PURCHASE_TOOL_OK";
    public static final String HONESTY_HYDRATED_TOOL_DEGRADED = "REAL_BRIDGE_HYDRATED_PURCHASE_TOOL_DEGRADED";

    public static final String EXAMPLE_USER_MESSAGE =
            "Harness purchase hydrated real-bridge（单店 STORE；依赖 DB 有采购数据方可达 SUCCESS）";

    public static final String PLAN_ID = "plan-purchase-adapter-real-bridge-hydrated-v1";
    public static final String PLAN_TYPE = "PLANNER_EXECUTOR_PURCHASE_ADAPTER_REAL_BRIDGE_HYDRATED_CORE_V1";
    public static final String FINAL_ANSWER_PLAN_TYPE =
            "MOCK_RECOMMENDATION_AFTER_PURCHASE_ADAPTER_REAL_BRIDGE_HYDRATED_V1";

    public static final String HARNESS_RESOLVED_CONTEXT_REF =
            "HARNESS_PURCHASE_ADAPTER_REAL_BRIDGE_HYDRATED_RESOLVED_CTX_REF";
    public static final String HARNESS_ANSWER_PLAN_REF_AFTER_PURCHASE =
            "HARNESS_ANSWER_PLAN_REF_AFTER_PURCHASE_HYDRATED";

    /** 本地 AAA 门店根（gb_department_id=1）。 */
    public static final long HARNESS_STORE_DEPARTMENT_ID = 1L;

    /** Harness：与 {@link AiHarnessReplayPlannerExecutorMock} 首轮 {@code SYNTHETIC_RUN_ID_BASE + 0} 对齐。 */
    private static final long HARNESS_SYNTHETIC_RUN_ID = 9_000_000L;

    /** C-19：显式 disId，满足 Tool 非空校验（环境不一致时需换为库内真实分销商 ID）。 */
    public static final long HARNESS_PURCHASE_DISTRIBUTER_ID = 2L;

    private static final LocalDate HARNESS_TIME_START = LocalDate.of(2026, 5, 1);
    private static final LocalDate HARNESS_TIME_END = LocalDate.of(2026, 5, 14);

    private AiPlannerExecutorPurchaseAdapterRealBridgeHydratedGraphCase() {
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
                        .timeLabel("2026-05-01..2026-05-14 (Harness purchase hydrated)")
                        .build();
        AiResolvedQueryIntent qi =
                AiResolvedQueryIntent.builder()
                        .intentCode(AiResolvedQueryIntent.PURCHASE_OVERVIEW)
                        .pathCode(AiResolvedQueryIntent.PATH_PURCHASE_OVERVIEW)
                        .structuredIntentDetail(AiQuerySemanticLexicon.STRUCTURED_PURCHASE_OVERVIEW_SUMMARY)
                        .purchaseSourceType(AiQuerySemanticLexicon.SOURCE_ALL)
                        .build();
        return AiResolvedQueryContext.builder()
                .runId(HARNESS_SYNTHETIC_RUN_ID)
                .userId(1L)
                .orgScope(org)
                .timeWindow(tw)
                .queryIntent(qi)
                .effectiveIntentCode(AiResolvedQueryIntent.PURCHASE_OVERVIEW)
                .effectivePathCode(AiResolvedQueryIntent.PATH_PURCHASE_OVERVIEW)
                .build();
    }

    public static PurchasePlannerReadRequest buildFullHarnessPurchaseReadRequest() {
        return PurchasePlannerReadRequest.builder()
                .resolvedQueryContextRef(HARNESS_RESOLVED_CONTEXT_REF)
                .timeStart(HARNESS_TIME_START)
                .timeEnd(HARNESS_TIME_END)
                .timeLabel("2026-05-01..2026-05-14 (Harness purchase hydrated)")
                .scopeType(AiResolvedOrgScope.SCOPE_STORE)
                .visibleStores(
                        List.of(
                                PurchasePlannerVisibleStore.builder()
                                        .departmentId(HARNESS_STORE_DEPARTMENT_ID)
                                        .displayLabel("AAA")
                                        .build()))
                .queryDepartmentIds(List.of(HARNESS_STORE_DEPARTMENT_ID))
                .targetStoreDepartmentId(HARNESS_STORE_DEPARTMENT_ID)
                .purchaseSourceType(AiQuerySemanticLexicon.SOURCE_ALL)
                .structuredIntentDetail(AiQuerySemanticLexicon.STRUCTURED_PURCHASE_OVERVIEW_SUMMARY)
                .answerPlanRef(HARNESS_ANSWER_PLAN_REF_AFTER_PURCHASE)
                .build();
    }

    public static AiRunState buildHydratedRunState(AiResolvedQueryContext rq) {
        return AiRunState.builder()
                .runId(HARNESS_SYNTHETIC_RUN_ID)
                .conversationId(0L)
                .userId(1L)
                .departmentId(HARNESS_STORE_DEPARTMENT_ID)
                .distributerId(HARNESS_PURCHASE_DISTRIBUTER_ID)
                .resolvedQueryContext(rq)
                .toolResults(new HashMap<>())
                .groupPurchaseOverview(false)
                .build();
    }

    public static PlannerExecutionPlan buildPlan() {
        PurchasePlannerReadRequest purchaseSlice = buildFullHarnessPurchaseReadRequest();
        AiResolvedQueryContext rq = buildHydratedResolvedQueryContext();
        AiRunState runState = buildHydratedRunState(rq);
        PurchasePlannerExecutionContext purchaseExec =
                PurchasePlannerExecutionContext.builder()
                        .runState(runState)
                        .resolvedQueryContext(rq)
                        .resolvedQueryContextRef(HARNESS_RESOLVED_CONTEXT_REF)
                        .userId(1L)
                        .departmentId(HARNESS_STORE_DEPARTMENT_ID)
                        .distributerId(HARNESS_PURCHASE_DISTRIBUTER_ID)
                        .conversationId("0")
                        .runId(Long.toString(HARNESS_SYNTHETIC_RUN_ID))
                        .plannerReadRequest(purchaseSlice)
                        .build();
        List<PlannerStep> steps =
                List.of(
                        PlannerStep.builder()
                                .stepId("step_purchase_adapter_hydrated")
                                .stepName("purchase_overview_month_hydrated")
                                .order(1)
                                .targetAgent(PurchasePlannerAgentAdapter.TARGET_AGENT)
                                .targetTool(PurchasePlannerAgentAdapter.TARGET_TOOL)
                                .inputSummary("采购只读（Hydrated AiRunState + AiResolvedQueryContext + PurchasePlannerRealReadBridge）")
                                .expectedOutput("SUCCESS when DB has purchase rows; else honest DEGRADED")
                                .acceptanceCriteria("single STORE; groupPurchaseOverview false; real purchase_overview")
                                .mockExecutionStatus(null)
                                .answerPlanRef(HARNESS_ANSWER_PLAN_REF_AFTER_PURCHASE)
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
                .purchaseReadRequest(purchaseSlice)
                .purchaseExecutionContext(purchaseExec)
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
        root.put("harnessReplayMode", AiHarnessReplayMode.PLANNER_EXECUTOR_PURCHASE_ADAPTER.name());

        PlannerExecutorTrace tr = result != null ? result.getTrace() : null;
        PlannerStepStatus overall = tr != null ? tr.getOverallStatus() : null;
        PlannerStepResult purchaseStep = findStepResult(tr, "step_purchase_adapter_hydrated");

        boolean purchaseSuccess =
                purchaseStep != null && purchaseStep.getStatus() == PlannerStepStatus.SUCCESS;
        boolean fullSuccess = overall == PlannerStepStatus.SUCCESS;

        if (purchaseSuccess && fullSuccess) {
            root.put("plannerPurchaseAdapterHonesty", HONESTY_HYDRATED_TOOL_OK);
            root.put(
                    "plannerPurchaseAdapterNote",
                    "purchase_overview executed with hydrated minimal AiRunState + AiResolvedQueryContext (STORE)");
        } else {
            root.put("plannerPurchaseAdapterHonesty", HONESTY_HYDRATED_TOOL_DEGRADED);
            StringBuilder note = new StringBuilder();
            if (purchaseStep != null) {
                note.append("purchase_step=").append(purchaseStep.getStatus());
                if (purchaseStep.getDegradedReason() != null) {
                    note.append("; ").append(purchaseStep.getDegradedReason());
                }
                if (purchaseStep.getErrorMessage() != null) {
                    note.append("; err=").append(purchaseStep.getErrorMessage());
                }
            } else {
                note.append("purchase_step_missing");
            }
            if (overall != null) {
                note.append("; overall=").append(overall);
            }
            root.put("plannerPurchaseAdapterNote", note.toString());
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
