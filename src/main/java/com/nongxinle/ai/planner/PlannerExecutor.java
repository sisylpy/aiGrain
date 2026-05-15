package com.nongxinle.ai.planner;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * PlannerExecutor：编排计划步顺序、汇总 trace；单步具体如何执行由 {@link PlannerExecutorExecutionMode} 决定（C-5）。
 * <ul>
 *   <li>{@link PlannerExecutorExecutionMode#MOCK}：委托 {@link MockPlannerStepExecutor}，仅读 {@code mock*} 字段，<strong>不接</strong>真实 Agent / Tool。</li>
 *   <li>{@link PlannerExecutorExecutionMode#ADAPTER}：委托注入的 {@link PlannerStepExecutor}（v1：{@link MockPlannerStepExecutor} 或 C-6 {@link PlannerAgentAdapterStepExecutor}+{@link PlannerAgentAdapterRegistry}）。</li>
 * </ul>
 * <p>
 * {@code mockExecutionStatus == null} 时由 {@link MockPlannerStepExecutor} 按 {@link PlannerStepMockExecutionStatus#SUCCESS} 处理，
 * 仅为 <strong>Harness / 单测缺省</strong>，不得类推为生产默认行为。
 * </p>
 *
 * @see docs/ai/planner-executor-v1-design.md
 */
public class PlannerExecutor {

    private final PlannerExecutorExecutionMode executionMode;
    private final PlannerStepExecutor stepExecutor;

    /** 默认 {@link PlannerExecutorExecutionMode#MOCK}，与 C-3/C-4 Harness 行为一致。 */
    public PlannerExecutor() {
        this(PlannerExecutorExecutionMode.MOCK, null);
    }

    public PlannerExecutor(PlannerExecutorExecutionMode executionMode, PlannerStepExecutor stepExecutor) {
        this.executionMode = executionMode != null ? executionMode : PlannerExecutorExecutionMode.MOCK;
        if (this.executionMode == PlannerExecutorExecutionMode.ADAPTER && stepExecutor == null) {
            throw new IllegalArgumentException("ADAPTER mode requires a non-null PlannerStepExecutor");
        }
        this.stepExecutor = stepExecutor;
    }

    public PlannerExecutorResult execute(PlannerExecutionPlan plan) {
        Objects.requireNonNull(plan, "plan");
        PlannerExecutionPlan snapshot = copyPlan(plan);

        List<PlannerStep> ordered = plan.getSteps() == null
                ? List.of()
                : plan.getSteps().stream()
                .sorted(Comparator.comparing(s -> s.getOrder() == null ? Integer.MAX_VALUE : s.getOrder()))
                .collect(Collectors.toList());

        List<PlannerStepResult> stepResults = new ArrayList<>();
        List<String> degradedStepIds = new ArrayList<>();
        Set<String> aggAgents = new LinkedHashSet<>();
        Set<String> aggTools = new LinkedHashSet<>();

        PlannerFailureStrategy planPolicy = plan.getFailureStrategy() != null
                ? plan.getFailureStrategy()
                : PlannerFailureStrategy.CONTINUE_WITH_DEGRADED;

        boolean failFastAbort = false;
        boolean clarificationRequested = false;

        for (PlannerStep step : ordered) {
            if (failFastAbort || clarificationRequested) {
                stepResults.add(skippedResult(step));
                continue;
            }

            PlannerFailureStrategy stepPolicy =
                    step.getFailureStrategy() != null ? step.getFailureStrategy() : planPolicy;

            PlannerStepExecutionRequest req =
                    PlannerStepExecutionRequest.builder()
                            .step(step)
                            .effectiveFailureStrategy(stepPolicy)
                            .planId(snapshot.getPlanId())
                            .planType(snapshot.getPlanType())
                            .resolvedQueryContextRef(snapshot.getResolvedContextRef())
                            .answerPlanRef(step.getAnswerPlanRef())
                            .revenueReadRequest(snapshot.getRevenueReadRequest())
                            .revenueExecutionContext(snapshot.getRevenueExecutionContext())
                            .purchaseReadRequest(snapshot.getPurchaseReadRequest())
                            .purchaseExecutionContext(snapshot.getPurchaseExecutionContext())
                            .stockReduceReadRequest(snapshot.getStockReduceReadRequest())
                            .stockReduceExecutionContext(snapshot.getStockReduceExecutionContext())
                            .dishProfitReadRequest(snapshot.getDishProfitReadRequest())
                            .dishProfitExecutionContext(snapshot.getDishProfitExecutionContext())
                            .planSnapshot(snapshot)
                            .priorStepResults(new ArrayList<>(stepResults))
                            .degradedStepsSoFar(new ArrayList<>(degradedStepIds))
                            .build();

            PlannerStepExecutionResponse raw =
                    executionMode == PlannerExecutorExecutionMode.MOCK
                            ? MockPlannerStepExecutor.INSTANCE.execute(req)
                            : Objects.requireNonNull(
                                    stepExecutor.execute(req),
                                    "PlannerStepExecutor returned null");

            PlannerStepResult result =
                    absorbFailedPerPolicy(stepPolicy, fromResponse(step, raw));
            mergeUsage(aggAgents, aggTools, raw);

            stepResults.add(result);

            if (result.getStatus() == PlannerStepStatus.DEGRADED && step.getStepId() != null) {
                degradedStepIds.add(step.getStepId());
            }

            if (result.getStatus() == PlannerStepStatus.FAILED) {
                if (stepPolicy == PlannerFailureStrategy.FAIL_FAST) {
                    failFastAbort = true;
                } else if (stepPolicy == PlannerFailureStrategy.ASK_CLARIFICATION) {
                    clarificationRequested = true;
                }
            }
        }

        boolean anyFailed = stepResults.stream().anyMatch(r -> r.getStatus() == PlannerStepStatus.FAILED);
        boolean anyDegraded = stepResults.stream().anyMatch(r -> r.getStatus() == PlannerStepStatus.DEGRADED);

        PlannerStepStatus overall;
        if (anyFailed) {
            overall = PlannerStepStatus.FAILED;
        } else if (anyDegraded) {
            overall = PlannerStepStatus.DEGRADED;
        } else {
            overall = PlannerStepStatus.SUCCESS;
        }

        PlannerExecutorTrace trace = PlannerExecutorTrace.builder()
                .plan(sanitizePlanForTrace(snapshot))
                .stepResults(stepResults)
                .degradedSteps(degradedStepIds)
                .usedAgents(new ArrayList<>(aggAgents))
                .usedTools(new ArrayList<>(aggTools))
                .finalAnswerPlanType(snapshot.getFinalAnswerPlanType())
                .appliedFailureStrategy(planPolicy)
                .overallStatus(overall)
                .clarificationRequested(clarificationRequested)
                .build();

        return PlannerExecutorResult.builder()
                .trace(trace)
                .ok(overall != PlannerStepStatus.FAILED)
                .build();
    }

    private static void mergeUsage(
            Set<String> aggAgents, Set<String> aggTools, PlannerStepExecutionResponse raw) {
        if (raw.getUsedAgents() != null) {
            for (String a : raw.getUsedAgents()) {
                if (a != null && !a.isEmpty()) {
                    aggAgents.add(a);
                }
            }
        }
        if (raw.getUsedTools() != null) {
            for (String t : raw.getUsedTools()) {
                if (t != null && !t.isEmpty()) {
                    aggTools.add(t);
                }
            }
        }
    }

    /**
     * Registry / 未来真实 adapter 可能返回 {@link PlannerStepStatus#FAILED}；与 mock 路径对齐：
     * {@link PlannerFailureStrategy#CONTINUE_WITH_DEGRADED} 下吸收为 {@link PlannerStepStatus#DEGRADED}。
     */
    private static PlannerStepResult absorbFailedPerPolicy(
            PlannerFailureStrategy stepPolicy, PlannerStepResult result) {
        if (result.getStatus() != PlannerStepStatus.FAILED) {
            return result;
        }
        if (stepPolicy == PlannerFailureStrategy.CONTINUE_WITH_DEGRADED) {
            String reason =
                    result.getErrorMessage() != null && !result.getErrorMessage().isEmpty()
                            ? result.getErrorMessage()
                            : "step_failed";
            return PlannerStepResult.builder()
                    .stepId(result.getStepId())
                    .status(PlannerStepStatus.DEGRADED)
                    .errorMessage(null)
                    .degradedReason(reason)
                    .usedAgents(copyList(result.getUsedAgents()))
                    .usedTools(copyList(result.getUsedTools()))
                    .businessDiagnosisCompositeAnswerPlan(result.getBusinessDiagnosisCompositeAnswerPlan())
                    .build();
        }
        return result;
    }

    private static PlannerStepResult fromResponse(PlannerStep step, PlannerStepExecutionResponse raw) {
        return PlannerStepResult.builder()
                .stepId(step.getStepId())
                .status(raw.getStatus())
                .errorMessage(raw.getErrorMessage())
                .degradedReason(raw.getDegradedReason())
                .usedAgents(copyList(raw.getUsedAgents()))
                .usedTools(copyList(raw.getUsedTools()))
                .businessDiagnosisCompositeAnswerPlan(raw.getBusinessDiagnosisCompositeAnswerPlan())
                .build();
    }

    private static List<String> copyList(List<String> in) {
        return in == null || in.isEmpty() ? new ArrayList<>() : new ArrayList<>(in);
    }

    private static PlannerStepResult skippedResult(PlannerStep step) {
        return PlannerStepResult.builder()
                .stepId(step.getStepId())
                .status(PlannerStepStatus.SKIPPED)
                .errorMessage(null)
                .degradedReason(null)
                .usedAgents(new ArrayList<>())
                .usedTools(new ArrayList<>())
                .build();
    }

    private static PlannerExecutionPlan copyPlan(PlannerExecutionPlan src) {
        List<PlannerStep> stepsCopy = src.getSteps() == null
                ? new ArrayList<>()
                : new ArrayList<>(src.getSteps());
        return PlannerExecutionPlan.builder()
                .planId(src.getPlanId())
                .planType(src.getPlanType())
                .steps(stepsCopy)
                .failureStrategy(src.getFailureStrategy())
                .resolvedContextRef(src.getResolvedContextRef())
                .revenueReadRequest(src.getRevenueReadRequest())
                .revenueExecutionContext(src.getRevenueExecutionContext())
                .purchaseReadRequest(src.getPurchaseReadRequest())
                .purchaseExecutionContext(src.getPurchaseExecutionContext())
                .stockReduceReadRequest(src.getStockReduceReadRequest())
                .stockReduceExecutionContext(src.getStockReduceExecutionContext())
                .dishProfitReadRequest(src.getDishProfitReadRequest())
                .dishProfitExecutionContext(src.getDishProfitExecutionContext())
                .finalAnswerPlanType(src.getFinalAnswerPlanType())
                .build();
    }

    /**
     * Trace 内 {@link PlannerExecutionPlan} 不携带 {@link com.nongxinle.ai.core.AiRunState} /
     * {@link com.nongxinle.ai.context.AiResolvedQueryContext} 完整对象（仅保留 ref 与小字段），避免 JSON / 摘要膨胀。
     */
    private static PlannerExecutionPlan sanitizePlanForTrace(PlannerExecutionPlan src) {
        if (src == null) {
            return null;
        }
        PlannerRevenueExecutionContext exec = src.getRevenueExecutionContext();
        PlannerRevenueExecutionContext traceExec = null;
        if (exec != null) {
            traceExec =
                    exec.toBuilder()
                            .runState(null)
                            .resolvedQueryContext(null)
                            .build();
        }
        PurchasePlannerExecutionContext pExec = src.getPurchaseExecutionContext();
        PurchasePlannerExecutionContext tracePurchaseExec = null;
        if (pExec != null) {
            tracePurchaseExec =
                    pExec.toBuilder()
                            .runState(null)
                            .resolvedQueryContext(null)
                            .build();
        }
        StockReducePlannerExecutionContext srExec = src.getStockReduceExecutionContext();
        StockReducePlannerExecutionContext traceStockReduceExec = null;
        if (srExec != null) {
            traceStockReduceExec =
                    srExec.toBuilder()
                            .runState(null)
                            .resolvedQueryContext(null)
                            .build();
        }
        DishProfitPlannerExecutionContext dpExec = src.getDishProfitExecutionContext();
        DishProfitPlannerExecutionContext traceDishProfitExec = null;
        if (dpExec != null) {
            traceDishProfitExec =
                    dpExec.toBuilder()
                            .runState(null)
                            .resolvedQueryContext(null)
                            .build();
        }
        List<PlannerStep> stepsCopy =
                src.getSteps() == null ? new ArrayList<>() : new ArrayList<>(src.getSteps());
        return PlannerExecutionPlan.builder()
                .planId(src.getPlanId())
                .planType(src.getPlanType())
                .steps(stepsCopy)
                .failureStrategy(src.getFailureStrategy())
                .resolvedContextRef(src.getResolvedContextRef())
                .revenueReadRequest(src.getRevenueReadRequest())
                .revenueExecutionContext(traceExec)
                .purchaseReadRequest(src.getPurchaseReadRequest())
                .purchaseExecutionContext(tracePurchaseExec)
                .stockReduceReadRequest(src.getStockReduceReadRequest())
                .stockReduceExecutionContext(traceStockReduceExec)
                .dishProfitReadRequest(src.getDishProfitReadRequest())
                .dishProfitExecutionContext(traceDishProfitExec)
                .finalAnswerPlanType(src.getFinalAnswerPlanType())
                .build();
    }
}
