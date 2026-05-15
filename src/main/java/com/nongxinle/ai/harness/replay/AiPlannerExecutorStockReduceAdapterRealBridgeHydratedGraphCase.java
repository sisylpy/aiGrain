package com.nongxinle.ai.harness.replay;

import com.nongxinle.ai.context.AiResolvedDataScope;
import com.nongxinle.ai.context.AiResolvedOrgScope;
import com.nongxinle.ai.context.AiResolvedQueryContext;
import com.nongxinle.ai.context.AiResolvedQueryIntent;
import com.nongxinle.ai.context.AiResolvedTimeWindow;
import com.nongxinle.ai.context.AiStoreScopeDTO;
import com.nongxinle.ai.conversation.AiQuerySemanticLexicon;
import com.nongxinle.ai.core.AiRunState;
import com.nongxinle.ai.dto.business.StockReduceAnswerPlan;
import com.nongxinle.ai.planner.PlannerExecutionPlan;
import com.nongxinle.ai.planner.PlannerExecutorResult;
import com.nongxinle.ai.planner.PlannerExecutorTrace;
import com.nongxinle.ai.planner.PlannerFailureStrategy;
import com.nongxinle.ai.planner.PlannerStep;
import com.nongxinle.ai.planner.PlannerStepMockExecutionStatus;
import com.nongxinle.ai.planner.PlannerStepResult;
import com.nongxinle.ai.planner.PlannerStepStatus;
import com.nongxinle.ai.planner.RecommendationPlannerMockAgentAdapter;
import com.nongxinle.ai.planner.StockReducePlannerAgentAdapter;
import com.nongxinle.ai.planner.StockReducePlannerExecutionContext;
import com.nongxinle.ai.planner.StockReducePlannerReadRequest;
import com.nongxinle.ai.planner.StockReducePlannerVisibleStore;
import com.nongxinle.ai.semantic.AiQuerySemanticParseResult;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * {@link AiHarnessBuiltinCases#PLANNER_EXECUTOR_STOCK_REDUCE_ADAPTER_REAL_BRIDGE_HYDRATED_CORE}：物化最小
 * {@link AiRunState} + {@link AiResolvedQueryContext}（单店 {@link AiResolvedOrgScope#SCOPE_STORE} / 门店 AAA），使
 * {@link com.nongxinle.ai.planner.StockReducePlannerRealReadBridge}（Spring Bean）走真实 {@code stock_reduce_query}。
 *
 * <p>Harness <strong>curl Replay 已验收</strong>：{@code overallStatus=SUCCESS}、{@code degradedSteps=[]}、出库步
 * {@code SUCCESS}、trace {@code usedTools} 含 {@code stock_reduce_query}、摘要
 * {@code plannerStockReduceAdapterHonesty=REAL_BRIDGE_HYDRATED_STOCK_REDUCE_TOOL_OK}。完整观测与最小上下文字段见
 * {@code docs/ai/stock-reduce-planner-adapter-design.md} §7.3.</p>
 */
public final class AiPlannerExecutorStockReduceAdapterRealBridgeHydratedGraphCase {

    public static final String CASE_ID =
            AiHarnessBuiltinCases.PLANNER_EXECUTOR_STOCK_REDUCE_ADAPTER_REAL_BRIDGE_HYDRATED_CORE;

    public static final String HONESTY_HYDRATED_TOOL_OK = "REAL_BRIDGE_HYDRATED_STOCK_REDUCE_TOOL_OK";
    public static final String HONESTY_HYDRATED_TOOL_DEGRADED = "REAL_BRIDGE_HYDRATED_STOCK_REDUCE_TOOL_DEGRADED";

    public static final String EXAMPLE_USER_MESSAGE =
            "Harness stock-reduce hydrated real-bridge（单店 STORE AAA；依赖 DB 有方可达 SUCCESS）";

    public static final String PLAN_ID = "plan-stock-reduce-adapter-real-bridge-hydrated-v1";
    public static final String PLAN_TYPE = "PLANNER_EXECUTOR_STOCK_REDUCE_ADAPTER_REAL_BRIDGE_HYDRATED_CORE_V1";
    public static final String FINAL_ANSWER_PLAN_TYPE =
            "MOCK_RECOMMENDATION_AFTER_STOCK_REDUCE_ADAPTER_REAL_BRIDGE_HYDRATED_V1";

    public static final String HARNESS_RESOLVED_CONTEXT_REF =
            "HARNESS_STOCK_REDUCE_ADAPTER_REAL_BRIDGE_HYDRATED_RESOLVED_CTX_REF";
    public static final String HARNESS_ANSWER_PLAN_REF_AFTER_STOCK_REDUCE =
            "HARNESS_ANSWER_PLAN_REF_AFTER_STOCK_REDUCE_HYDRATED";

    /** 单店 AAA（gb_department_id=1）。 */
    public static final long HARNESS_STORE_DEPARTMENT_ID = 1L;

    /** 与 {@link AiHarnessReplayPlannerExecutorMock} 首轮 {@code SYNTHETIC_RUN_ID_BASE + 0} 对齐。 */
    private static final long HARNESS_SYNTHETIC_RUN_ID = 9_000_000L;

    public static final long HARNESS_STOCK_REDUCE_DISTRIBUTER_ID = 2L;

    private static final LocalDate HARNESS_TIME_START = LocalDate.of(2026, 5, 1);
    private static final LocalDate HARNESS_TIME_END = LocalDate.of(2026, 5, 14);

    private AiPlannerExecutorStockReduceAdapterRealBridgeHydratedGraphCase() {
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
                        .timeLabel("2026-05-01..2026-05-14 (Harness stock-reduce hydrated)")
                        .build();
        AiResolvedQueryIntent qi =
                AiResolvedQueryIntent.builder()
                        .intentCode(AiResolvedQueryIntent.STOCK_REDUCE_QUERY)
                        .pathCode(AiResolvedQueryIntent.PATH_STOCK_REDUCE_QUERY)
                        .structuredIntentDetail(AiQuerySemanticLexicon.STRUCTURED_STOCK_REDUCE_OVERVIEW_SUMMARY)
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
                                        .stockReduceType(StockReduceAnswerPlan.REDUCE_TYPE_ALL)
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
                .effectiveIntentCode(AiResolvedQueryIntent.STOCK_REDUCE_QUERY)
                .effectivePathCode(AiResolvedQueryIntent.PATH_STOCK_REDUCE_QUERY)
                .build();
    }

    public static StockReducePlannerReadRequest buildFullHarnessStockReduceReadRequest() {
        return StockReducePlannerReadRequest.builder()
                .resolvedQueryContextRef(HARNESS_RESOLVED_CONTEXT_REF)
                .timeStart(HARNESS_TIME_START)
                .timeEnd(HARNESS_TIME_END)
                .timeLabel("2026-05-01..2026-05-14 (Harness stock-reduce hydrated)")
                .scopeType(AiResolvedOrgScope.SCOPE_STORE)
                .visibleStores(
                        List.of(
                                StockReducePlannerVisibleStore.builder()
                                        .departmentId(HARNESS_STORE_DEPARTMENT_ID)
                                        .displayLabel("AAA")
                                        .build()))
                .queryDepartmentIds(List.of(HARNESS_STORE_DEPARTMENT_ID))
                .targetStoreDepartmentId(HARNESS_STORE_DEPARTMENT_ID)
                .reduceType(StockReduceAnswerPlan.REDUCE_TYPE_ALL)
                .structuredIntentDetail(AiQuerySemanticLexicon.STRUCTURED_STOCK_REDUCE_OVERVIEW_SUMMARY)
                .totalsBasis("CALENDAR_NATURAL_DAY")
                .answerPlanRef(HARNESS_ANSWER_PLAN_REF_AFTER_STOCK_REDUCE)
                .build();
    }

    public static AiRunState buildHydratedRunState(AiResolvedQueryContext rq) {
        return AiRunState.builder()
                .runId(HARNESS_SYNTHETIC_RUN_ID)
                .conversationId(0L)
                .userId(1L)
                .departmentId(HARNESS_STORE_DEPARTMENT_ID)
                .distributerId(HARNESS_STOCK_REDUCE_DISTRIBUTER_ID)
                .resolvedQueryContext(rq)
                .toolResults(new HashMap<>())
                .groupStockReduceQuery(false)
                .build();
    }

    public static PlannerExecutionPlan buildPlan() {
        StockReducePlannerReadRequest slice = buildFullHarnessStockReduceReadRequest();
        AiResolvedQueryContext rq = buildHydratedResolvedQueryContext();
        AiRunState runState = buildHydratedRunState(rq);
        StockReducePlannerExecutionContext stockExec =
                StockReducePlannerExecutionContext.builder()
                        .runState(runState)
                        .resolvedQueryContext(rq)
                        .resolvedQueryContextRef(HARNESS_RESOLVED_CONTEXT_REF)
                        .userId(1L)
                        .departmentId(HARNESS_STORE_DEPARTMENT_ID)
                        .distributerId(HARNESS_STOCK_REDUCE_DISTRIBUTER_ID)
                        .conversationId("0")
                        .runId(Long.toString(HARNESS_SYNTHETIC_RUN_ID))
                        .plannerReadRequest(slice)
                        .build();
        List<PlannerStep> steps =
                List.of(
                        PlannerStep.builder()
                                .stepId("step_stock_reduce_adapter_hydrated")
                                .stepName("stock_reduce_overview_hydrated")
                                .order(1)
                                .targetAgent(StockReducePlannerAgentAdapter.TARGET_AGENT)
                                .targetTool(StockReducePlannerAgentAdapter.TARGET_TOOL)
                                .inputSummary(
                                        "出库/核销只读（Hydrated AiRunState + AiResolvedQueryContext + StockReducePlannerRealReadBridge）")
                                .expectedOutput("SUCCESS when DB has stock-reduce rows; else honest DEGRADED")
                                .acceptanceCriteria("single STORE AAA; groupStockReduceQuery false; real stock_reduce_query")
                                .mockExecutionStatus(null)
                                .answerPlanRef(HARNESS_ANSWER_PLAN_REF_AFTER_STOCK_REDUCE)
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
                .stockReduceReadRequest(slice)
                .stockReduceExecutionContext(stockExec)
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
        root.put("harnessReplayMode", AiHarnessReplayMode.PLANNER_EXECUTOR_STOCK_REDUCE_ADAPTER.name());

        PlannerExecutorTrace tr = result != null ? result.getTrace() : null;
        PlannerStepStatus overall = tr != null ? tr.getOverallStatus() : null;
        PlannerStepResult stockStep = findStepResult(tr, "step_stock_reduce_adapter_hydrated");

        boolean stockSuccess = stockStep != null && stockStep.getStatus() == PlannerStepStatus.SUCCESS;
        boolean fullSuccess = overall == PlannerStepStatus.SUCCESS;

        if (stockSuccess && fullSuccess) {
            root.put("plannerStockReduceAdapterHonesty", HONESTY_HYDRATED_TOOL_OK);
            root.put(
                    "plannerStockReduceAdapterNote",
                    "stock_reduce_query executed with hydrated minimal AiRunState + AiResolvedQueryContext (STORE AAA)");
        } else {
            root.put("plannerStockReduceAdapterHonesty", HONESTY_HYDRATED_TOOL_DEGRADED);
            StringBuilder note = new StringBuilder();
            if (stockStep != null) {
                note.append("stock_reduce_step=").append(stockStep.getStatus());
                if (stockStep.getDegradedReason() != null) {
                    note.append("; ").append(stockStep.getDegradedReason());
                }
                if (stockStep.getErrorMessage() != null) {
                    note.append("; err=").append(stockStep.getErrorMessage());
                }
            } else {
                note.append("stock_reduce_step_missing");
            }
            if (overall != null) {
                note.append("; overall=").append(overall);
            }
            root.put("plannerStockReduceAdapterNote", note.toString());
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
