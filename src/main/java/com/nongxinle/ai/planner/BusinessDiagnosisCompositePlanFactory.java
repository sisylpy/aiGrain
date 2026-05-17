package com.nongxinle.ai.planner;

import com.nongxinle.ai.agent.business.BusinessAgentNames;
import com.nongxinle.ai.context.AiResolvedOrgScope;
import com.nongxinle.ai.context.AiResolvedQueryContext;
import com.nongxinle.ai.context.AiResolvedQueryIntent;
import com.nongxinle.ai.context.AiResolvedTimeWindow;
import com.nongxinle.ai.context.AiStoreScopeDTO;
import com.nongxinle.ai.core.AiRunState;
import com.nongxinle.ai.dto.business.BusinessDiagnosisCompositeAnswerPlan;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import org.springframework.stereotype.Component;

/**
 * C-58：依据真实 {@link AiRunState}、{@link AiResolvedQueryContext}、Gate 通过的 {@link BusinessDiagnosisCompositeGateResult}
 * 生成 Composite 六步 {@link PlannerExecutionPlan}。**不**调用 Harness GraphCase **不**写死租户/门店/时间。
 */
@Component
public final class BusinessDiagnosisCompositePlanFactory {

    private static final String PLAN_ID_SUFFIX = "plan-bd-composite-harness-exec-v1";
    private static final String PLAN_TYPE = "BUSINESS_DIAGNOSIS_COMPOSITE_HARNESS_EXEC_V1";
    /** 诊断 compose 占位 agent（与 Harness mock diagnosis 对齐，非 Master 路由）。 */
    private static final String MOCK_AGENT_DIAGNOSIS = "business_diagnosis_v1";

    public PlannerExecutionPlan buildPlan(
            AiRunState state,
            AiResolvedQueryContext resolvedQueryContext,
            BusinessDiagnosisCompositeGateResult gateResult) {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(gateResult, "gateResult");
        AiResolvedQueryContext rq = resolvedQueryContext != null ? resolvedQueryContext : state.getResolvedQueryContext();
        Objects.requireNonNull(rq, "resolvedQueryContext");
        BusinessDiagnosisCompositeGateResult.RecommendedCaseKind kind = gateResult.getRecommendedCaseKind();
        if (!gateResult.isAllowed()
                || kind == null
                || kind == BusinessDiagnosisCompositeGateResult.RecommendedCaseKind.NONE) {
            throw new IllegalArgumentException(
                    "buildPlan requires allowed gate with STORE/GROUP recommendedCaseKind");
        }

        AiResolvedOrgScope org = rq.getOrgScope();
        Objects.requireNonNull(org, "orgScope required");
        long runLong = state.getRunId() != null ? state.getRunId() : -1L;
        String resolvedRef =
                runLong >= 0
                        ? ("BD_COMPOSITE_EXEC_CTX:run:" + runLong)
                        : "BD_COMPOSITE_EXEC_CTX:run:unknown";

        LocalDateParts dates = resolveTime(rq);
        String timeLabelHuman = resolveTimeLabel(rq);

        RevenuePlannerReadRequest revenueSlice = buildRevenueReadRequest(kind, org, rq, state, resolvedRef, dates, timeLabelHuman);
        PurchasePlannerReadRequest purchaseSlice =
                buildPurchaseReadRequest(kind, org, rq, state, resolvedRef, dates, timeLabelHuman);
        StockReducePlannerReadRequest stockSlice =
                buildStockReadRequest(kind, org, rq, state, resolvedRef, dates, timeLabelHuman);
        DishProfitPlannerReadRequest dishSlice =
                buildDishReadRequest(kind, org, rq, state, resolvedRef, dates, timeLabelHuman);

        AiRunState revenueRun = plannerShellRunState(state);
        AiRunState purchaseRun = plannerShellRunState(state);
        AiRunState stockRun = plannerShellRunState(state);
        AiRunState dishRun = plannerShellRunState(state);

        String conv = state.getConversationId() != null ? Long.toString(state.getConversationId()) : null;
        String runStr = state.getRunId() != null ? Long.toString(state.getRunId()) : null;

        Long groupUserId = state.getUserId();
        Long groupDist = resolveDistributerId(org, state);
        Long deptForStoreScoped =
                kind == BusinessDiagnosisCompositeGateResult.RecommendedCaseKind.STORE
                        ? resolveStoreDepartmentId(org, state)
                        : null;

        PlannerRevenueExecutionContext revenueExec =
                PlannerRevenueExecutionContext.builder()
                        .runState(revenueRun)
                        .resolvedQueryContext(rq)
                        .resolvedQueryContextRef(resolvedRef)
                        .userId(groupUserId)
                        .departmentId(deptForStoreScoped)
                        .distributerId(groupDist)
                        .conversationId(conv)
                        .runId(runStr)
                        .plannerReadRequest(revenueSlice)
                        .build();
        PurchasePlannerExecutionContext purchaseExec =
                PurchasePlannerExecutionContext.builder()
                        .runState(purchaseRun)
                        .resolvedQueryContext(rq)
                        .resolvedQueryContextRef(resolvedRef)
                        .userId(groupUserId)
                        .departmentId(deptForStoreScoped)
                        .distributerId(groupDist)
                        .conversationId(conv)
                        .runId(runStr)
                        .plannerReadRequest(purchaseSlice)
                        .build();
        StockReducePlannerExecutionContext stockExec =
                StockReducePlannerExecutionContext.builder()
                        .runState(stockRun)
                        .resolvedQueryContext(rq)
                        .resolvedQueryContextRef(resolvedRef)
                        .userId(groupUserId)
                        .departmentId(deptForStoreScoped)
                        .distributerId(groupDist)
                        .conversationId(conv)
                        .runId(runStr)
                        .plannerReadRequest(stockSlice)
                        .build();
        DishProfitPlannerExecutionContext dishExec =
                DishProfitPlannerExecutionContext.builder()
                        .runState(dishRun)
                        .resolvedQueryContext(rq)
                        .resolvedQueryContextRef(resolvedRef)
                        .userId(groupUserId)
                        .departmentId(deptForStoreScoped)
                        .distributerId(groupDist)
                        .conversationId(conv)
                        .runId(runStr)
                        .plannerReadRequest(dishSlice)
                        .build();

        List<PlannerStep> steps =
                buildCompositeSteps(kind, resolvedRef, runLong, rq.getQueryIntent());

        String planId = PLAN_ID_SUFFIX + ':' + kind.name().toLowerCase(Locale.ROOT) + ':' + runLong;
        return PlannerExecutionPlan.builder()
                .planId(planId)
                .planType(PLAN_TYPE)
                .steps(steps)
                .failureStrategy(PlannerFailureStrategy.CONTINUE_WITH_DEGRADED)
                .resolvedContextRef(resolvedRef)
                .revenueReadRequest(revenueSlice)
                .revenueExecutionContext(revenueExec)
                .purchaseReadRequest(purchaseSlice)
                .purchaseExecutionContext(purchaseExec)
                .stockReduceReadRequest(stockSlice)
                .stockReduceExecutionContext(stockExec)
                .dishProfitReadRequest(dishSlice)
                .dishProfitExecutionContext(dishExec)
                .finalAnswerPlanType(BusinessDiagnosisCompositeAnswerPlan.TYPE_BUSINESS_DIAGNOSIS_COMPOSITE)
                .build();
    }

    private static List<PlannerStep> buildCompositeSteps(
            BusinessDiagnosisCompositeGateResult.RecommendedCaseKind kind,
            String resolvedRef,
            long runLong,
            AiResolvedQueryIntent qi) {

        String refRev = diagnosisRef(runLong, "revenue");
        String refPur = diagnosisRef(runLong, "purchase");
        String refStk = diagnosisRef(runLong, "stock_reduce");
        String refDish = diagnosisRef(runLong, "dish_profit");
        String refDiag = diagnosisRef(runLong, "diagnosis_compose");

        String scopePhrase =
                kind == BusinessDiagnosisCompositeGateResult.RecommendedCaseKind.GROUP ? "GROUP" : "STORE";
        String structuredNote = "";
        if (qi != null && qi.getStructuredIntentDetail() != null && !qi.getStructuredIntentDetail().isBlank()) {
            structuredNote = "; structuredIntentDetail=" + qi.getStructuredIntentDetail().trim();
        }

        List<PlannerStep> steps = new ArrayList<>();
        steps.add(
                PlannerStep.builder()
                        .stepId(
                                CompositeBusinessDiagnosisRevenuePurchaseHybridPlannerStepExecutor.COMPOSITE_STEP_ID_REVENUE_HYDRATED)
                        .stepName("revenue_hydrated_real_" + scopePhrase.toLowerCase(Locale.ROOT))
                        .order(1)
                        .targetAgent(BusinessAgentNames.REVENUE_OVERVIEW)
                        .targetTool(RevenuePlannerAgentAdapter.TARGET_TOOL)
                        .inputSummary(scopePhrase + " 营收 Hydrated bridge → revenue_query [" + resolvedRef + "]")
                        .expectedOutput("DailyRevenueAnswerPlan")
                        .acceptanceCriteria("resolved orgScope + timeWindow from context" + structuredNote)
                        .mockExecutionStatus(null)
                        .answerPlanRef(refRev)
                        .build());
        steps.add(
                PlannerStep.builder()
                        .stepId(
                                CompositeBusinessDiagnosisRevenuePurchaseHybridPlannerStepExecutor.COMPOSITE_STEP_ID_PURCHASE_HYDRATED)
                        .stepName("purchase_hydrated_real_" + scopePhrase.toLowerCase(Locale.ROOT))
                        .order(2)
                        .targetAgent(PurchasePlannerAgentAdapter.TARGET_AGENT)
                        .targetTool(PurchasePlannerAgentAdapter.TARGET_TOOL)
                        .inputSummary(scopePhrase + " 采购 Hydrated bridge → purchase_overview")
                        .expectedOutput("PurchaseAnswerPlan")
                        .acceptanceCriteria("context-bound request; no Harness GraphCase")
                        .mockExecutionStatus(null)
                        .answerPlanRef(refPur)
                        .build());
        steps.add(
                PlannerStep.builder()
                        .stepId(
                                CompositeBusinessDiagnosisRevenuePurchaseStockHybridPlannerStepExecutor
                                        .COMPOSITE_STEP_ID_STOCK_REDUCE_HYDRATED)
                        .stepName("stock_reduce_hydrated_real_" + scopePhrase.toLowerCase(Locale.ROOT))
                        .order(3)
                        .targetAgent(StockReducePlannerAgentAdapter.TARGET_AGENT)
                        .targetTool(StockReducePlannerAgentAdapter.TARGET_TOOL)
                        .inputSummary(scopePhrase + " 出库/核销 Hydrated bridge → stock_reduce_query")
                        .expectedOutput("StockReduceAnswerPlan")
                        .acceptanceCriteria("context-bound request; no Harness GraphCase")
                        .mockExecutionStatus(null)
                        .answerPlanRef(refStk)
                        .build());
        steps.add(
                PlannerStep.builder()
                        .stepId(CompositeBusinessDiagnosisAllDataRealHybridPlannerStepExecutor.COMPOSITE_STEP_ID_DISH_PROFIT_HYDRATED)
                        .stepName("dish_profit_hydrated_real_" + scopePhrase.toLowerCase(Locale.ROOT))
                        .order(4)
                        .targetAgent(DishProfitPlannerAgentAdapter.TARGET_AGENT)
                        .targetTool(DishProfitPlannerAgentAdapter.TARGET_TOOL)
                        .inputSummary(scopePhrase + " 菜品毛利 Hydrated bridge → dish_profit_analysis")
                        .expectedOutput("DishProfitAnswerPlan")
                        .acceptanceCriteria("visibleStores/query from resolved orgScope")
                        .mockExecutionStatus(null)
                        .answerPlanRef(refDish)
                        .build());
        steps.add(
                PlannerStep.builder()
                        .stepId("step_diagnosis_compose")
                        .stepName("diagnosis_compose_deterministic")
                        .order(5)
                        .targetAgent(MOCK_AGENT_DIAGNOSIS)
                        .targetTool(CompositeBusinessDiagnosisAllDataRealHybridPlannerStepExecutor.COMPOSITE_MOCK_TOOL_DIAGNOSIS_COMPOSE)
                        .inputSummary("Deterministic BusinessDiagnosisCompositeAnswerPlan skeleton (C-58)")
                        .expectedOutput("BusinessDiagnosisCompositeAnswerPlan JSON")
                        .acceptanceCriteria("no LLM; mock_diagnosis_compose marking only")
                        .mockExecutionStatus(PlannerStepMockExecutionStatus.SUCCESS)
                        .answerPlanRef(refDiag)
                        .build());
        steps.add(
                PlannerStep.builder()
                        .stepId("step_recommendation")
                        .stepName("recommendation_mock")
                        .order(6)
                        .targetAgent(RecommendationPlannerMockAgentAdapter.TARGET_AGENT)
                        .targetTool(RecommendationPlannerMockAgentAdapter.TARGET_TOOL)
                        .inputSummary("Recommendation mock tail (Harness composite parity)")
                        .expectedOutput("Mock recommendation plan placeholder")
                        .acceptanceCriteria("no production recommendation agent")
                        .mockExecutionStatus(PlannerStepMockExecutionStatus.SUCCESS)
                        .build());

        return steps;
    }

    private static String diagnosisRef(long runId, String stepKey) {
        return "BD_COMPOSITE_REF_" + stepKey + ":" + runId;
    }

    /** 与各域并行执行隔离：清空 toolResults 与上轮 AnswerPlan，避免交叉污染；保留 Resolver 结构化字段 mirror。 */
    private static AiRunState plannerShellRunState(AiRunState ended) {
        return ended.toBuilder()
                .toolResults(new LinkedHashMap<>())
                .revenueAnswerPlan(null)
                .purchaseAnswerPlan(null)
                .stockReduceAnswerPlan(null)
                .dishProfitAnswerPlan(null)
                .businessOverviewAnswerPlan(null)
                .diagnosisPlan(null)
                .build();
    }

    private static Long resolveStoreDepartmentId(AiResolvedOrgScope org, AiRunState state) {
        if (state.getDepartmentId() != null) {
            return state.getDepartmentId();
        }
        if (org == null) {
            return null;
        }
        if (org.getCurrentStoreDepartmentId() != null) {
            return org.getCurrentStoreDepartmentId();
        }
        if (org.getRequestDepartmentId() != null) {
            return org.getRequestDepartmentId();
        }
        List<Long> roots = visibleStoreDeptIds(org);
        return roots.isEmpty() ? null : roots.get(0);
    }

    private static Long resolveDistributerId(AiResolvedOrgScope org, AiRunState state) {
        if (state.getDistributerId() != null) {
            return state.getDistributerId();
        }
        return org != null ? org.getDistributerId() : null;
    }

    private static List<Long> visibleStoreDeptIds(AiResolvedOrgScope org) {
        if (org == null || org.getVisibleStores() == null) {
            return List.of();
        }
        List<Long> out = new ArrayList<>();
        for (AiStoreScopeDTO s : org.getVisibleStores()) {
            if (s != null && s.getStoreDepartmentId() != null) {
                out.add(s.getStoreDepartmentId());
            }
        }
        return out;
    }

    private static List<RevenuePlannerVisibleStore> mapRevenueVisible(AiResolvedOrgScope org) {
        List<RevenuePlannerVisibleStore> out = new ArrayList<>();
        if (org == null || org.getVisibleStores() == null) {
            return out;
        }
        for (AiStoreScopeDTO s : org.getVisibleStores()) {
            if (s == null || s.getStoreDepartmentId() == null) {
                continue;
            }
            out.add(
                    RevenuePlannerVisibleStore.builder()
                            .departmentId(s.getStoreDepartmentId())
                            .displayLabel(s.getStoreName())
                            .build());
        }
        return out;
    }

    private static List<PurchasePlannerVisibleStore> mapPurchaseVisible(AiResolvedOrgScope org) {
        List<PurchasePlannerVisibleStore> out = new ArrayList<>();
        if (org == null || org.getVisibleStores() == null) {
            return out;
        }
        for (AiStoreScopeDTO s : org.getVisibleStores()) {
            if (s == null || s.getStoreDepartmentId() == null) {
                continue;
            }
            out.add(
                    PurchasePlannerVisibleStore.builder()
                            .departmentId(s.getStoreDepartmentId())
                            .displayLabel(s.getStoreName())
                            .build());
        }
        return out;
    }

    private static List<StockReducePlannerVisibleStore> mapStockVisible(AiResolvedOrgScope org) {
        List<StockReducePlannerVisibleStore> out = new ArrayList<>();
        if (org == null || org.getVisibleStores() == null) {
            return out;
        }
        for (AiStoreScopeDTO s : org.getVisibleStores()) {
            if (s == null || s.getStoreDepartmentId() == null) {
                continue;
            }
            out.add(
                    StockReducePlannerVisibleStore.builder()
                            .departmentId(s.getStoreDepartmentId())
                            .displayLabel(s.getStoreName())
                            .build());
        }
        return out;
    }

    private static List<DishProfitPlannerVisibleStore> mapDishVisible(AiResolvedOrgScope org) {
        List<DishProfitPlannerVisibleStore> out = new ArrayList<>();
        if (org == null || org.getVisibleStores() == null) {
            return out;
        }
        for (AiStoreScopeDTO s : org.getVisibleStores()) {
            if (s == null || s.getStoreDepartmentId() == null) {
                continue;
            }
            out.add(
                    DishProfitPlannerVisibleStore.builder()
                            .departmentId(s.getStoreDepartmentId())
                            .displayLabel(s.getStoreName())
                            .build());
        }
        return out;
    }

    private static String scopeTypeString(AiResolvedOrgScope org) {
        if (org != null && org.getScopeType() != null && !org.getScopeType().isBlank()) {
            return org.getScopeType().trim();
        }
        return AiResolvedOrgScope.SCOPE_STORE;
    }

    private record LocalDateParts(java.time.LocalDate start, java.time.LocalDate end) {}

    private static LocalDateParts resolveTime(AiResolvedQueryContext rq) {
        AiResolvedTimeWindow tw = rq != null ? rq.getTimeWindow() : null;
        if (tw == null) {
            return new LocalDateParts(null, null);
        }
        return new LocalDateParts(tw.getStartDate(), tw.getEndDate());
    }

    private static String resolveTimeLabel(AiResolvedQueryContext rq) {
        AiResolvedTimeWindow tw = rq != null ? rq.getTimeWindow() : null;
        if (tw == null) {
            return null;
        }
        if (tw.getDisplayText() != null && !tw.getDisplayText().isBlank()) {
            return tw.getDisplayText().trim();
        }
        if (tw.getTimeLabel() != null && !tw.getTimeLabel().isBlank()) {
            return tw.getTimeLabel().trim();
        }
        return null;
    }

    private static RevenuePlannerReadRequest buildRevenueReadRequest(
            BusinessDiagnosisCompositeGateResult.RecommendedCaseKind kind,
            AiResolvedOrgScope org,
            AiResolvedQueryContext rq,
            AiRunState state,
            String resolvedRef,
            LocalDateParts dates,
            String timeLabelHuman) {
        String st = scopeTypeString(org);
        List<RevenuePlannerVisibleStore> vis = mapRevenueVisible(org);
        List<Long> queryDeptIds = new ArrayList<>();
        Long targetStore = null;

        if (kind == BusinessDiagnosisCompositeGateResult.RecommendedCaseKind.STORE) {
            Long dept = resolveStoreDepartmentId(org, state);
            if (dept != null) {
                queryDeptIds.add(dept);
            }
            targetStore = dept;
        } else {
            queryDeptIds.addAll(visibleStoreDeptIds(org));
        }

        long rid = state.getRunId() != null ? state.getRunId() : -1L;
        return RevenuePlannerReadRequest.builder()
                .resolvedQueryContextRef(resolvedRef)
                .timeStart(dates.start)
                .timeEnd(dates.end)
                .timeLabel(timeLabelHuman)
                .scopeType(st)
                .visibleStores(vis)
                .queryDepartmentIds(queryDeptIds)
                .targetStoreDepartmentId(targetStore)
                .answerPlanRef(diagnosisRef(rid, "revenue_slice"))
                .build();
    }

    private static PurchasePlannerReadRequest buildPurchaseReadRequest(
            BusinessDiagnosisCompositeGateResult.RecommendedCaseKind kind,
            AiResolvedOrgScope org,
            AiResolvedQueryContext rq,
            AiRunState state,
            String resolvedRef,
            LocalDateParts dates,
            String timeLabelHuman) {
        AiResolvedQueryIntent qi = rq != null ? rq.getQueryIntent() : null;
        String pst = qi != null ? qi.getPurchaseSourceType() : null;
        String sid = qi != null ? qi.getStructuredIntentDetail() : null;

        String st = scopeTypeString(org);
        List<PurchasePlannerVisibleStore> vis = mapPurchaseVisible(org);
        List<Long> queryDeptIds = new ArrayList<>();
        Long targetStore = null;
        if (kind == BusinessDiagnosisCompositeGateResult.RecommendedCaseKind.STORE) {
            Long dept = resolveStoreDepartmentId(org, state);
            if (dept != null) {
                queryDeptIds.add(dept);
            }
            targetStore = dept;
        } else {
            queryDeptIds.addAll(visibleStoreDeptIds(org));
        }

        long rid = state.getRunId() != null ? state.getRunId() : -1L;
        return PurchasePlannerReadRequest.builder()
                .resolvedQueryContextRef(resolvedRef)
                .timeStart(dates.start)
                .timeEnd(dates.end)
                .timeLabel(timeLabelHuman)
                .scopeType(st)
                .visibleStores(vis)
                .queryDepartmentIds(queryDeptIds)
                .targetStoreDepartmentId(targetStore)
                .purchaseSourceType(pst)
                .structuredIntentDetail(sid)
                .answerPlanRef(diagnosisRef(rid, "purchase_slice"))
                .build();
    }

    private static StockReducePlannerReadRequest buildStockReadRequest(
            BusinessDiagnosisCompositeGateResult.RecommendedCaseKind kind,
            AiResolvedOrgScope org,
            AiResolvedQueryContext rq,
            AiRunState state,
            String resolvedRef,
            LocalDateParts dates,
            String timeLabelHuman) {
        AiResolvedQueryIntent qi = rq != null ? rq.getQueryIntent() : null;
        String sid = qi != null ? qi.getStructuredIntentDetail() : null;
        String st = scopeTypeString(org);
        List<StockReducePlannerVisibleStore> vis = mapStockVisible(org);
        List<Long> queryDeptIds = new ArrayList<>();
        Long targetStore = null;
        if (kind == BusinessDiagnosisCompositeGateResult.RecommendedCaseKind.STORE) {
            Long dept = resolveStoreDepartmentId(org, state);
            if (dept != null) {
                queryDeptIds.add(dept);
            }
            targetStore = dept;
        } else {
            queryDeptIds.addAll(visibleStoreDeptIds(org));
        }
        long rid = state.getRunId() != null ? state.getRunId() : -1L;
        return StockReducePlannerReadRequest.builder()
                .resolvedQueryContextRef(resolvedRef)
                .timeStart(dates.start)
                .timeEnd(dates.end)
                .timeLabel(timeLabelHuman)
                .scopeType(st)
                .visibleStores(vis)
                .queryDepartmentIds(queryDeptIds)
                .targetStoreDepartmentId(targetStore)
                .structuredIntentDetail(sid)
                .answerPlanRef(diagnosisRef(rid, "stock_slice"))
                .build();
    }

    private static DishProfitPlannerReadRequest buildDishReadRequest(
            BusinessDiagnosisCompositeGateResult.RecommendedCaseKind kind,
            AiResolvedOrgScope org,
            AiResolvedQueryContext rq,
            AiRunState state,
            String resolvedRef,
            LocalDateParts dates,
            String timeLabelHuman) {
        AiResolvedQueryIntent qi = rq != null ? rq.getQueryIntent() : null;
        String sid = qi != null ? qi.getStructuredIntentDetail() : null;
        String st = scopeTypeString(org);
        List<DishProfitPlannerVisibleStore> vis = mapDishVisible(org);
        List<Long> queryDeptIds = new ArrayList<>();
        Long targetStore = null;
        if (kind == BusinessDiagnosisCompositeGateResult.RecommendedCaseKind.STORE) {
            Long dept = resolveStoreDepartmentId(org, state);
            if (dept != null) {
                queryDeptIds.add(dept);
            }
            targetStore = dept;
        } else {
            queryDeptIds.addAll(visibleStoreDeptIds(org));
        }
        long rid = state.getRunId() != null ? state.getRunId() : -1L;
        return DishProfitPlannerReadRequest.builder()
                .resolvedQueryContextRef(resolvedRef)
                .timeStart(dates.start)
                .timeEnd(dates.end)
                .timeLabel(timeLabelHuman)
                .scopeType(st)
                .visibleStores(vis)
                .queryDepartmentIds(queryDeptIds)
                .targetStoreDepartmentId(targetStore)
                .structuredIntentDetail(sid)
                .mentionedDishName(rq.getMentionedDishName())
                .dishProfitMetricType(rq.getDishProfitMetricType())
                .answerPlanRef(diagnosisRef(rid, "dish_slice"))
                .build();
    }
}