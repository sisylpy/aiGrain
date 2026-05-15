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
import com.nongxinle.ai.graph.business.scope.BusinessScopeResolutionSupport;
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
import com.nongxinle.ai.tool.business.AiBusinessToolIds;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * {@link AiHarnessBuiltinCases#PLANNER_EXECUTOR_STOCK_REDUCE_ADAPTER_GROUP_HYDRATED_CORE}：物化最小
 * {@link AiRunState} + {@link AiResolvedQueryContext}（{@link AiResolvedOrgScope#SCOPE_GROUP}，双可见门店根），使
 * {@link com.nongxinle.ai.planner.StockReducePlannerRealReadBridge} 走真实 {@code stock_reduce_query}。
 *
 * <p><b>{@code groupStockReduceQuery}</b>：生产 {@link com.nongxinle.ai.graph.business.StockReduceQueryToolExecutor#
 * buildHarnessToolArgs} 在 {@code state.isGroupStockReduceQuery()} 为 true 时写入 {@code
 * ARG_GROUP_STOCK_REDUCE_AGGREGATION}，并从 {@code resolvedQueryContext} 解析多店 {@code ARG_RESOLVED_DEPARTMENT_IDS} /
 * {@code ARG_PARENT_STORE_COUNT}；为 false 时走单店 {@code ARG_DEPARTMENT_FATHER_ID}。**C-46 GROUP 探测须为
 * true**；**C-24 STORE Hydrated** 须为 **false**。
 *
 * <p>不接 Composite；不接 Revenue / Purchase / DishProfit；不调用 LLM。
 */
public final class AiPlannerExecutorStockReduceAdapterGroupHydratedGraphCase {

    public static final String CASE_ID =
            AiHarnessBuiltinCases.PLANNER_EXECUTOR_STOCK_REDUCE_ADAPTER_GROUP_HYDRATED_CORE;

    public static final String HONESTY_GROUP_TOOL_OK = "REAL_BRIDGE_HYDRATED_STOCK_REDUCE_GROUP_TOOL_OK";
    public static final String HONESTY_GROUP_TOOL_DEGRADED = "REAL_BRIDGE_HYDRATED_STOCK_REDUCE_GROUP_TOOL_DEGRADED";

    public static final String EXAMPLE_USER_MESSAGE =
            "Harness stock_reduce GROUP hydrated real-bridge（scopeType=GROUP；可见 AAA+汀兰；依赖 DB 与权限）";

    public static final String PLAN_ID = "plan-stock-reduce-adapter-group-hydrated-v1";
    public static final String PLAN_TYPE = "PLANNER_EXECUTOR_STOCK_REDUCE_ADAPTER_GROUP_HYDRATED_CORE_V1";
    public static final String FINAL_ANSWER_PLAN_TYPE =
            "MOCK_RECOMMENDATION_AFTER_STOCK_REDUCE_ADAPTER_GROUP_HYDRATED_V1";

    public static final String HARNESS_RESOLVED_CONTEXT_REF =
            "HARNESS_STOCK_REDUCE_ADAPTER_GROUP_HYDRATED_RESOLVED_CTX_REF";
    public static final String HARNESS_ANSWER_PLAN_REF_AFTER_STOCK_REDUCE =
            "HARNESS_ANSWER_PLAN_REF_AFTER_STOCK_REDUCE_GROUP_HYDRATED";

    public static final long HARNESS_STORE_AAA_DEPARTMENT_ID = 1L;
    /** 汀兰餐厅（门店根 gb_department_id=3），与 C-43 / 营收、采购 GROUP 一致。 */
    public static final long HARNESS_STORE_TINGLAN_DEPARTMENT_ID = 3L;

    public static final long HARNESS_STOCK_REDUCE_DISTRIBUTER_ID =
            AiPlannerExecutorStockReduceAdapterRealBridgeHydratedGraphCase.HARNESS_STOCK_REDUCE_DISTRIBUTER_ID;

    private static final LocalDate HARNESS_TIME_START = LocalDate.of(2026, 5, 1);
    private static final LocalDate HARNESS_TIME_END = LocalDate.of(2026, 5, 14);

    /** 与采购 GROUP（9_000_052）错开。 */
    private static final long HARNESS_SYNTHETIC_RUN_ID = 9_000_053L;

    private AiPlannerExecutorStockReduceAdapterGroupHydratedGraphCase() {
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
                        .timeLabel("2026-05-01..2026-05-14 (Harness stock-reduce GROUP hydrated)")
                        .build();
        AiResolvedQueryIntent qi =
                AiResolvedQueryIntent.builder()
                        .intentCode(AiResolvedQueryIntent.STOCK_REDUCE_QUERY)
                        .pathCode(AiResolvedQueryIntent.PATH_STOCK_REDUCE_QUERY)
                        .structuredIntentDetail(AiQuerySemanticLexicon.STRUCTURED_STOCK_REDUCE_OVERVIEW_SUMMARY)
                        .build();
        AiResolvedDataScope dataScope = AiResolvedDataScope.fromOrgScope(org);
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
                .dataScope(dataScope)
                .timeWindow(tw)
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
                .timeLabel("2026-05-01..2026-05-14 (Harness stock-reduce GROUP hydrated)")
                .scopeType(AiResolvedOrgScope.SCOPE_GROUP)
                .visibleStores(
                        List.of(
                                StockReducePlannerVisibleStore.builder()
                                        .departmentId(HARNESS_STORE_AAA_DEPARTMENT_ID)
                                        .displayLabel("AAA")
                                        .build(),
                                StockReducePlannerVisibleStore.builder()
                                        .departmentId(HARNESS_STORE_TINGLAN_DEPARTMENT_ID)
                                        .displayLabel("汀兰餐厅")
                                        .build()))
                .queryDepartmentIds(List.of(HARNESS_STORE_AAA_DEPARTMENT_ID, HARNESS_STORE_TINGLAN_DEPARTMENT_ID))
                .targetStoreDepartmentId(null)
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
                .departmentId(null)
                .distributerId(HARNESS_STOCK_REDUCE_DISTRIBUTER_ID)
                .resolvedQueryContext(rq)
                .toolResults(new HashMap<>())
                .groupStockReduceQuery(true)
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
                        .departmentId(null)
                        .distributerId(HARNESS_STOCK_REDUCE_DISTRIBUTER_ID)
                        .conversationId("0")
                        .runId(Long.toString(HARNESS_SYNTHETIC_RUN_ID))
                        .plannerReadRequest(slice)
                        .build();
        List<PlannerStep> steps =
                List.of(
                        PlannerStep.builder()
                                .stepId("step_stock_reduce_adapter_hydrated")
                                .stepName("stock_reduce_overview_group_hydrated")
                                .order(1)
                                .targetAgent(StockReducePlannerAgentAdapter.TARGET_AGENT)
                                .targetTool(StockReducePlannerAgentAdapter.TARGET_TOOL)
                                .inputSummary(
                                        "出库/核销只读 GROUP（Hydrated AiRunState + AiResolvedQueryContext + GROUP dataScope +"
                                                + " groupStockReduceQuery=true）")
                                .expectedOutput(
                                        "SUCCESS when stock_reduce_query returns group/multi-store payload; else DEGRADED")
                                .acceptanceCriteria(
                                        "scopeType=GROUP; visibleStores 1+3; targetStoreDepartmentId null; "
                                                + "groupStockReduceQuery=true per StockReduceQueryToolExecutor; "
                                                + "real stock_reduce_query")
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
        root.put("harnessReplayMode", AiHarnessReplayMode.PLANNER_EXECUTOR_STOCK_REDUCE_ADAPTER.name());

        PlannerExecutorTrace tr = result != null ? result.getTrace() : null;
        PlannerStepStatus overall = tr != null ? tr.getOverallStatus() : null;
        PlannerStepResult stockStep = findStepResult(tr, "step_stock_reduce_adapter_hydrated");

        boolean stockSuccess = stockStep != null && stockStep.getStatus() == PlannerStepStatus.SUCCESS;
        boolean fullSuccess = overall == PlannerStepStatus.SUCCESS;

        if (stockSuccess && fullSuccess) {
            root.put("plannerStockReduceAdapterHonesty", HONESTY_GROUP_TOOL_OK);
            root.put(
                    "plannerStockReduceAdapterNote",
                    "stock_reduce_query executed with GROUP hydrated context (groupStockReduceQuery=true; visible store"
                            + " roots 1+3); not single-store AAA fallback");
        } else {
            root.put("plannerStockReduceAdapterHonesty", HONESTY_GROUP_TOOL_DEGRADED);
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

        List<Integer> visibleRoots =
                executedPlan != null && executedPlan.getStockReduceExecutionContext() != null
                        ? BusinessScopeResolutionSupport.extractVisibleStoreRootDepartmentIds(
                                executedPlan.getStockReduceExecutionContext().getResolvedQueryContext())
                        : List.of();
        root.put("harnessStockReduceGroupVisibleStoreRootDepartmentIds", new ArrayList<>(visibleRoots));

        putStockReduceHarnessObservation(root, executedPlan);
        return root;
    }

    private static void putStockReduceHarnessObservation(Map<String, Object> root, PlannerExecutionPlan executedPlan) {
        if (executedPlan == null || executedPlan.getStockReduceExecutionContext() == null) {
            return;
        }
        AiRunState st = executedPlan.getStockReduceExecutionContext().getRunState();
        if (st == null) {
            return;
        }
        Object raw =
                st.getToolResults() != null ? st.getToolResults().get(AiBusinessToolIds.STOCK_REDUCE_QUERY) : null;
        root.put("harnessStockReduceQueryEnvelopePresent", raw instanceof Map<?, ?>);

        StockReduceAnswerPlan plan = st.getStockReduceAnswerPlan();
        if (plan != null) {
            if (plan.getPlanType() != null) {
                root.put("harnessStockReduceAnswerPlanType", plan.getPlanType());
            }
            Map<String, Object> su = plan.getSummary();
            if (su != null) {
                if (su.get("grandTotalFourTypes") != null) {
                    root.put("harnessStockReduceQueryGrandTotalFourTypes", su.get("grandTotalFourTypes"));
                }
                if (su.get("produceTotal") != null) {
                    root.put("harnessStockReduceQueryProduceTotal", su.get("produceTotal"));
                }
                if (su.get("wasteTotal") != null) {
                    root.put("harnessStockReduceQueryWasteTotal", su.get("wasteTotal"));
                }
                if (su.get("lossTotal") != null) {
                    root.put("harnessStockReduceQueryLossTotal", su.get("lossTotal"));
                }
                if (su.get("returnTotal") != null) {
                    root.put("harnessStockReduceQueryReturnTotal", su.get("returnTotal"));
                }
                if (su.get("totalsBasis") != null) {
                    root.put("harnessStockReduceTotalsBasis", su.get("totalsBasis"));
                }
            }
            if (plan.getFocusRows() != null) {
                root.put("harnessStockReduceFocusRowsSize", plan.getFocusRows().size());
            }
            if (plan.getSecondaryRows() != null) {
                root.put("harnessStockReduceSecondaryRowsSize", plan.getSecondaryRows().size());
            }
            List<Integer> storeIdsFromFocus = extractStoreDepartmentIdsFromRows(plan.getFocusRows());
            if (!storeIdsFromFocus.isEmpty()) {
                root.put("harnessStockReduceFocusRowStoreDepartmentIds", storeIdsFromFocus);
            }
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
