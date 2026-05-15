package com.nongxinle.ai.planner;

import com.nongxinle.ai.context.AiResolvedQueryContext;
import com.nongxinle.ai.core.AiRunState;
import com.nongxinle.ai.dto.business.BusinessDiagnosisCompositeAnswerPlan;
import com.nongxinle.ai.dto.business.BusinessDiagnosisCompositeComposeResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Composite PlannerExecutor（四域 RealBridge + Hybrid）+ 只读 Composer。
 * {@link BusinessDiagnosisCompositeExecutionMode#HARNESS_ONLY Harness-only} — C-58 Harness {@code GRAPH_RUN}；
 * {@link BusinessDiagnosisCompositeExecutionMode#SHADOW SHADOW} — C-60 普通 {@code /api/ai/runs}（不替换 {@code AiRunState#finalAnswerText}）。
 */
@Service
@RequiredArgsConstructor
public final class BusinessDiagnosisCompositeExecutionService {

    private static final String ERR_COMPOSER_EMPTY = "COMPOSITE_EMPTY_FINAL_ANSWER";
    private static final String ERR_GATE_DISALLOWED = "COMPOSITE_GATE_DISALLOWED";
    private static final String ERR_GATE_NULL = "COMPOSITE_GATE_NULL";
    private static final String ERR_PLANNER_NOT_SUCCESS = "COMPOSITE_PLANNER_NOT_SUCCESS";

    private final BusinessDiagnosisCompositePlanFactory compositePlanFactory;
    private final RevenuePlannerRealReadBridge revenuePlannerRealReadBridge;
    private final PurchasePlannerRealReadBridge purchasePlannerRealReadBridge;
    private final StockReducePlannerRealReadBridge stockReducePlannerRealReadBridge;
    private final DishProfitPlannerRealReadBridge dishProfitPlannerRealReadBridge;

    /** @param resolvedQueryContext 可选；{@code null} 时用 {@code state#getResolvedQueryContext()} */
    public BusinessDiagnosisCompositeExecutionResult tryExecute(
            AiRunState state,
            AiResolvedQueryContext resolvedQueryContext,
            BusinessDiagnosisCompositeGateResult gateResult,
            BusinessDiagnosisCompositeExecutionMode mode) {
        if (mode != BusinessDiagnosisCompositeExecutionMode.HARNESS_ONLY
                && mode != BusinessDiagnosisCompositeExecutionMode.SHADOW) {
            return BusinessDiagnosisCompositeExecutionResult.builder()
                    .mode(mode)
                    .executed(false)
                    .success(false)
                    .fallbackRequired(false)
                    .fallbackReason(null)
                    .businessDiagnosisCompositeAnswerPlan(null)
                    .composeResult(null)
                    .plannerExecutorTrace(null)
                    .plannerOverallStatus(null)
                    .degradedSteps(List.of())
                    .build();
        }

        if (gateResult == null) {
            return BusinessDiagnosisCompositeExecutionResult.builder()
                    .mode(mode)
                    .executed(false)
                    .success(false)
                    .fallbackRequired(false)
                    .fallbackReason(null)
                    .errorCode(ERR_GATE_NULL)
                    .plannerExecutorTrace(null)
                    .plannerOverallStatus(null)
                    .degradedSteps(List.of())
                    .build();
        }

        if (!gateResult.isAllowed()) {
            return BusinessDiagnosisCompositeExecutionResult.builder()
                    .mode(mode)
                    .executed(false)
                    .success(false)
                    .fallbackRequired(false)
                    .fallbackReason(null)
                    .errorCode(ERR_GATE_DISALLOWED)
                    .plannerExecutorTrace(null)
                    .plannerOverallStatus(null)
                    .degradedSteps(List.of())
                    .build();
        }

        PlannerExecutionPlan plan =
                compositePlanFactory.buildPlan(state, resolvedQueryContext, gateResult);

        PlannerStepExecutor registry =
                new PlannerAgentAdapterStepExecutor(
                        new PlannerAgentAdapterRegistry(
                                List.of(
                                        new RevenuePlannerAgentAdapter(revenuePlannerRealReadBridge),
                                        new PurchasePlannerAgentAdapter(purchasePlannerRealReadBridge),
                                        new StockReducePlannerAgentAdapter(stockReducePlannerRealReadBridge),
                                        new DishProfitPlannerAgentAdapter(dishProfitPlannerRealReadBridge))));
        PlannerExecutor executor =
                new PlannerExecutor(
                        PlannerExecutorExecutionMode.ADAPTER,
                        new CompositeBusinessDiagnosisAllDataRealHybridPlannerStepExecutor(registry));

        PlannerExecutorResult runResult = executor.execute(plan);
        PlannerExecutorTrace trace = runResult != null ? runResult.getTrace() : null;
        PlannerStepStatus overall = trace != null ? trace.getOverallStatus() : null;
        List<String> degraded = trace != null && trace.getDegradedSteps() != null
                ? new ArrayList<>(trace.getDegradedSteps())
                : new ArrayList<>();

        BusinessDiagnosisCompositeAnswerPlan ap = extractCompositePlan(trace);

        BusinessDiagnosisCompositeComposeResult compose = null;
        if (ap != null) {
            compose = BusinessDiagnosisCompositeReadonlyComposer.compose(ap);
        }

        boolean plannerSuccess = overall == PlannerStepStatus.SUCCESS;
        boolean emptyFinal =
                compose == null
                        || compose.getFinalAnswerText() == null
                        || compose.getFinalAnswerText().trim().isEmpty();

        if (!plannerSuccess) {
            String reason =
                    "planner overallStatus="
                            + (overall != null ? overall.name() : "null")
                            + "; degradedSteps="
                            + degraded;
            return BusinessDiagnosisCompositeExecutionResult.builder()
                    .executed(true)
                    .mode(mode)
                    .success(false)
                    .fallbackRequired(true)
                    .fallbackReason(reason)
                    .errorCode(ERR_PLANNER_NOT_SUCCESS)
                    .errorMessage(reason)
                    .businessDiagnosisCompositeAnswerPlan(ap)
                    .composeResult(compose)
                    .plannerExecutorTrace(trace)
                    .plannerOverallStatus(overall)
                    .degradedSteps(degraded)
                    .build();
        }

        if (emptyFinal) {
            return BusinessDiagnosisCompositeExecutionResult.builder()
                    .executed(true)
                    .mode(mode)
                    .success(false)
                    .fallbackRequired(true)
                    .fallbackReason(ERR_COMPOSER_EMPTY)
                    .errorCode(ERR_COMPOSER_EMPTY)
                    .errorMessage("Composer returned empty finalAnswerText")
                    .businessDiagnosisCompositeAnswerPlan(ap)
                    .composeResult(compose)
                    .plannerExecutorTrace(trace)
                    .plannerOverallStatus(overall)
                    .degradedSteps(degraded)
                    .build();
        }

        return BusinessDiagnosisCompositeExecutionResult.builder()
                .executed(true)
                .mode(mode)
                .success(true)
                .fallbackRequired(false)
                .fallbackReason(null)
                .errorCode(null)
                .errorMessage(null)
                .businessDiagnosisCompositeAnswerPlan(ap)
                .composeResult(compose)
                .plannerExecutorTrace(trace)
                .plannerOverallStatus(overall)
                .degradedSteps(Collections.unmodifiableList(degraded))
                .build();
    }

    private static BusinessDiagnosisCompositeAnswerPlan extractCompositePlan(PlannerExecutorTrace trace) {
        if (trace == null || trace.getStepResults() == null) {
            return null;
        }
        for (PlannerStepResult sr : trace.getStepResults()) {
            if (sr == null || sr.getStepId() == null) {
                continue;
            }
            if (!"step_diagnosis_compose".equals(sr.getStepId().trim())) {
                continue;
            }
            return sr.getBusinessDiagnosisCompositeAnswerPlan();
        }
        return null;
    }
}
