package com.nongxinle.ai.harness.replay;

import com.nongxinle.ai.context.AiResolvedDataScope;
import com.nongxinle.ai.context.AiResolvedOrgScope;
import com.nongxinle.ai.context.AiResolvedQueryContext;
import com.nongxinle.ai.context.AiResolvedQueryIntent;
import com.nongxinle.ai.context.AiResolvedTimeWindow;
import com.nongxinle.ai.context.AiStoreScopeDTO;
import com.nongxinle.ai.conversation.AiQuerySemanticLexicon;
import com.nongxinle.ai.core.AiRunState;
import com.nongxinle.ai.dto.business.PurchaseAnswerPlan;
import com.nongxinle.ai.graph.business.scope.BusinessScopeResolutionSupport;
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
import com.nongxinle.ai.tool.business.AiBusinessToolIds;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * {@link AiHarnessBuiltinCases#PLANNER_EXECUTOR_PURCHASE_ADAPTER_GROUP_HYDRATED_CORE}：物化最小
 * {@link AiRunState} + {@link AiResolvedQueryContext}（{@link AiResolvedOrgScope#SCOPE_GROUP}，双可见门店根），使
 * {@link com.nongxinle.ai.planner.PurchasePlannerRealReadBridge} 走真实 {@code purchase_overview}。
 *
 * <p><b>{@code groupPurchaseOverview}</b>：生产侧 {@link com.nongxinle.ai.graph.business.PurchaseOverviewToolExecutor#
 * buildPurchaseOverviewToolArgs} 在 {@code state.isGroupPurchaseOverview()} 为 true 时写入 {@code
 * ARG_GROUP_PURCHASE_AGGREGATION}，并从 {@code resolvedQueryContext} 解析多店 {@code ARG_RESOLVED_DEPARTMENT_IDS}；为
 * false 时仅走单店 {@code ARG_DEPARTMENT_FATHER_ID}。本 Harness 为 GROUP 多店探测，故必须为 {@code true}；与 C-19
 * STORE Hydrated（单店、{@code false}）区分。
 *
 * <p>不接 Composite；不接 Revenue / Stock / DishProfit。
 */
public final class AiPlannerExecutorPurchaseAdapterGroupHydratedGraphCase {

    public static final String CASE_ID = AiHarnessBuiltinCases.PLANNER_EXECUTOR_PURCHASE_ADAPTER_GROUP_HYDRATED_CORE;

    public static final String HONESTY_GROUP_TOOL_OK = "REAL_BRIDGE_HYDRATED_PURCHASE_GROUP_TOOL_OK";
    public static final String HONESTY_GROUP_TOOL_DEGRADED = "REAL_BRIDGE_HYDRATED_PURCHASE_GROUP_TOOL_DEGRADED";

    public static final String EXAMPLE_USER_MESSAGE =
            "Harness purchase GROUP hydrated real-bridge（scopeType=GROUP；可见 AAA+汀兰；依赖 DB 与权限）";

    public static final String PLAN_ID = "plan-purchase-adapter-group-hydrated-v1";
    public static final String PLAN_TYPE = "PLANNER_EXECUTOR_PURCHASE_ADAPTER_GROUP_HYDRATED_CORE_V1";
    public static final String FINAL_ANSWER_PLAN_TYPE =
            "MOCK_RECOMMENDATION_AFTER_PURCHASE_ADAPTER_GROUP_HYDRATED_V1";

    public static final String HARNESS_RESOLVED_CONTEXT_REF =
            "HARNESS_PURCHASE_ADAPTER_GROUP_HYDRATED_RESOLVED_CTX_REF";
    public static final String HARNESS_ANSWER_PLAN_REF_AFTER_PURCHASE =
            "HARNESS_ANSWER_PLAN_REF_AFTER_PURCHASE_GROUP_HYDRATED";

    public static final long HARNESS_STORE_AAA_DEPARTMENT_ID = 1L;
    /** 汀兰餐厅（门店根 gb_department_id=3），与 C-43 / 营收 GROUP 一致。 */
    public static final long HARNESS_STORE_TINGLAN_DEPARTMENT_ID = 3L;

    /** 与 C-19 STORE Hydrated 文档一致的环境 disId。 */
    public static final long HARNESS_PURCHASE_DISTRIBUTER_ID =
            AiPlannerExecutorPurchaseAdapterRealBridgeHydratedGraphCase.HARNESS_PURCHASE_DISTRIBUTER_ID;

    private static final LocalDate HARNESS_TIME_START = LocalDate.of(2026, 5, 1);
    private static final LocalDate HARNESS_TIME_END = LocalDate.of(2026, 5, 14);

    /** 与营收 GROUP（9_000_051）错开。 */
    private static final long HARNESS_SYNTHETIC_RUN_ID = 9_000_052L;

    private AiPlannerExecutorPurchaseAdapterGroupHydratedGraphCase() {
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
                        .timeLabel("2026-05-01..2026-05-14 (Harness purchase GROUP hydrated)")
                        .build();
        AiResolvedQueryIntent qi =
                AiResolvedQueryIntent.builder()
                        .intentCode(AiResolvedQueryIntent.PURCHASE_OVERVIEW)
                        .pathCode(AiResolvedQueryIntent.PATH_PURCHASE_OVERVIEW)
                        .structuredIntentDetail(AiQuerySemanticLexicon.STRUCTURED_PURCHASE_OVERVIEW_SUMMARY)
                        .purchaseSourceType(AiQuerySemanticLexicon.SOURCE_ALL)
                        .build();
        AiResolvedDataScope dataScope = AiResolvedDataScope.fromOrgScope(org);
        return AiResolvedQueryContext.builder()
                .runId(HARNESS_SYNTHETIC_RUN_ID)
                .userId(1L)
                .orgScope(org)
                .dataScope(dataScope)
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
                .timeLabel("2026-05-01..2026-05-14 (Harness purchase GROUP hydrated)")
                .scopeType(AiResolvedOrgScope.SCOPE_GROUP)
                .visibleStores(
                        List.of(
                                PurchasePlannerVisibleStore.builder()
                                        .departmentId(HARNESS_STORE_AAA_DEPARTMENT_ID)
                                        .displayLabel("AAA")
                                        .build(),
                                PurchasePlannerVisibleStore.builder()
                                        .departmentId(HARNESS_STORE_TINGLAN_DEPARTMENT_ID)
                                        .displayLabel("汀兰餐厅")
                                        .build()))
                .queryDepartmentIds(List.of(HARNESS_STORE_AAA_DEPARTMENT_ID, HARNESS_STORE_TINGLAN_DEPARTMENT_ID))
                .targetStoreDepartmentId(null)
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
                .departmentId(null)
                .distributerId(HARNESS_PURCHASE_DISTRIBUTER_ID)
                .resolvedQueryContext(rq)
                .toolResults(new HashMap<>())
                .groupPurchaseOverview(true)
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
                        .departmentId(null)
                        .distributerId(HARNESS_PURCHASE_DISTRIBUTER_ID)
                        .conversationId("0")
                        .runId(Long.toString(HARNESS_SYNTHETIC_RUN_ID))
                        .plannerReadRequest(purchaseSlice)
                        .build();
        List<PlannerStep> steps =
                List.of(
                        PlannerStep.builder()
                                .stepId("step_purchase_adapter_hydrated")
                                .stepName("purchase_overview_group_hydrated")
                                .order(1)
                                .targetAgent(PurchasePlannerAgentAdapter.TARGET_AGENT)
                                .targetTool(PurchasePlannerAgentAdapter.TARGET_TOOL)
                                .inputSummary(
                                        "采购只读 GROUP（Hydrated AiRunState + AiResolvedQueryContext + GROUP dataScope +"
                                                + " groupPurchaseOverview=true）")
                                .expectedOutput(
                                        "SUCCESS when purchase_overview returns group/multi-store payload; else DEGRADED")
                                .acceptanceCriteria(
                                        "scopeType=GROUP; visibleStores 1+3; targetStoreDepartmentId null; "
                                                + "groupPurchaseOverview=true per ToolExecutor; real purchase_overview")
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
        root.put("harnessReplayMode", AiHarnessReplayMode.PLANNER_EXECUTOR_PURCHASE_ADAPTER.name());

        PlannerExecutorTrace tr = result != null ? result.getTrace() : null;
        PlannerStepStatus overall = tr != null ? tr.getOverallStatus() : null;
        PlannerStepResult purchaseStep = findStepResult(tr, "step_purchase_adapter_hydrated");

        boolean purchaseSuccess =
                purchaseStep != null && purchaseStep.getStatus() == PlannerStepStatus.SUCCESS;
        boolean fullSuccess = overall == PlannerStepStatus.SUCCESS;

        if (purchaseSuccess && fullSuccess) {
            root.put("plannerPurchaseAdapterHonesty", HONESTY_GROUP_TOOL_OK);
            root.put(
                    "plannerPurchaseAdapterNote",
                    "purchase_overview executed with GROUP hydrated context (groupPurchaseOverview=true; visible store"
                            + " roots 1+3); not single-store AAA fallback");
        } else {
            root.put("plannerPurchaseAdapterHonesty", HONESTY_GROUP_TOOL_DEGRADED);
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

        List<Integer> visibleRoots =
                executedPlan != null && executedPlan.getPurchaseExecutionContext() != null
                        ? BusinessScopeResolutionSupport.extractVisibleStoreRootDepartmentIds(
                                executedPlan.getPurchaseExecutionContext().getResolvedQueryContext())
                        : List.of();
        root.put("harnessPurchaseGroupVisibleStoreRootDepartmentIds", new ArrayList<>(visibleRoots));

        putPurchaseHarnessObservation(root, executedPlan);
        return root;
    }

    private static void putPurchaseHarnessObservation(Map<String, Object> root, PlannerExecutionPlan executedPlan) {
        if (executedPlan == null || executedPlan.getPurchaseExecutionContext() == null) {
            return;
        }
        AiRunState st = executedPlan.getPurchaseExecutionContext().getRunState();
        if (st == null) {
            return;
        }
        Object raw = st.getToolResults() != null ? st.getToolResults().get(AiBusinessToolIds.PURCHASE_OVERVIEW) : null;
        root.put("harnessPurchaseOverviewEnvelopePresent", raw instanceof Map<?, ?>);

        PurchaseAnswerPlan plan = st.getPurchaseAnswerPlan();
        if (plan != null) {
            if (plan.getPlanType() != null) {
                root.put("harnessPurchaseAnswerPlanType", plan.getPlanType());
            }
            Map<String, Object> su = plan.getSummary();
            if (su != null) {
                if (su.get("totalPurchaseAmount") != null) {
                    root.put("harnessPurchaseQueryTotalPurchaseAmount", su.get("totalPurchaseAmount"));
                }
                if (su.get("purchaseOrderCount") != null) {
                    root.put("harnessPurchaseQueryPurchaseOrderCount", su.get("purchaseOrderCount"));
                }
            }
            if (plan.getFocusRows() != null) {
                root.put("harnessPurchaseFocusRowsSize", plan.getFocusRows().size());
            }
            if (plan.getSecondaryRows() != null) {
                root.put("harnessPurchaseSecondaryRowsSize", plan.getSecondaryRows().size());
            }
            List<Integer> storeIdsFromFocus = extractStoreDepartmentIdsFromRows(plan.getFocusRows());
            if (!storeIdsFromFocus.isEmpty()) {
                root.put("harnessPurchaseFocusRowStoreDepartmentIds", storeIdsFromFocus);
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
