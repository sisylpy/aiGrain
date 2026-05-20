package com.nongxinle.ai.harness.replay;

import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.nongxinle.ai.planner.CompositeBusinessDiagnosisAllDataRealHybridPlannerStepExecutor;
import com.nongxinle.ai.planner.CompositeBusinessDiagnosisStockDegradedHarnessHybridPlannerStepExecutor;
import com.nongxinle.ai.planner.DishProfitPlannerAgentAdapter;
import com.nongxinle.ai.planner.DishProfitPlannerRealReadBridge;
import com.nongxinle.ai.planner.PlannerAgentAdapterRegistry;
import com.nongxinle.ai.planner.PlannerAgentAdapterStepExecutor;
import com.nongxinle.ai.planner.PlannerExecutionPlan;
import com.nongxinle.ai.planner.PlannerExecutor;
import com.nongxinle.ai.planner.PlannerExecutorExecutionMode;
import com.nongxinle.ai.planner.PlannerExecutorResult;
import com.nongxinle.ai.planner.PlannerExecutorTrace;
import com.nongxinle.ai.planner.PlannerStepExecutor;
import com.nongxinle.ai.planner.PlannerStepStatus;
import com.nongxinle.ai.planner.PurchasePlannerAgentAdapter;
import com.nongxinle.ai.planner.PurchasePlannerRealReadBridge;
import com.nongxinle.ai.planner.StockReducePlannerAgentAdapter;
import com.nongxinle.ai.planner.StockReducePlannerRealReadBridge;
import com.nongxinle.ai.planner.RevenuePlannerAgentAdapter;
import com.nongxinle.ai.planner.RevenuePlannerRealReadBridge;

/**
 * DB-free Harness Replay：{@link AiHarnessBuiltinCases#isPlannerExecutorMockHarnessCase(String)} 为 true 时走本类，
 * 不创建会话、不跑 Resolver / 生产图。
 * <p>P1-B Final：单域 Adapter 演进轴已摘除；Planner 主验收仅 Composite strict（C-35 / C-48 / C-42），须由
 * {@link AiHarnessReplayService} 注入四域 Real Bridge Bean。</p>
 * <p>其余 case（{@link AiHarnessBuiltinCases#PLANNER_EXECUTOR_MOCK_CORE} /
 * {@link AiHarnessBuiltinCases#PLANNER_EXECUTOR_MOCK_DEGRADED_CORE}）走 {@link AiPlannerExecutorMockGraphCase}。</p>
 */
public final class AiHarnessReplayPlannerExecutorMock {

    private static final long SYNTHETIC_RUN_ID_BASE = 9_000_000L;

    private AiHarnessReplayPlannerExecutorMock() {
    }

    public static AiHarnessReplayResponse replay(AiHarnessReplayRequest req) {
        return replay(req, null, null, null, null);
    }

    /**
     * @param revenueRealBridge 真实营收桥；营收 RealBridge / Hydrated caseId 时必填
     */
    public static AiHarnessReplayResponse replay(
            AiHarnessReplayRequest req, RevenuePlannerRealReadBridge revenueRealBridge) {
        return replay(req, revenueRealBridge, null, null, null);
    }

    /**
     * @param revenueRealBridge 营收 RealBridge；采购 RealBridge case 可传 null
     * @param purchaseRealBridge 采购 RealBridge 骨架；采购 RealBridge caseId 时必填
     */
    public static AiHarnessReplayResponse replay(
            AiHarnessReplayRequest req,
            RevenuePlannerRealReadBridge revenueRealBridge,
            PurchasePlannerRealReadBridge purchaseRealBridge) {
        return replay(req, revenueRealBridge, purchaseRealBridge, null, null);
    }

    /**
     * @param stockReduceRealBridge 出库/核销 Hydrated case 须非 null（经由 {@link AiHarnessReplayService}）
     */
    public static AiHarnessReplayResponse replay(
            AiHarnessReplayRequest req,
            RevenuePlannerRealReadBridge revenueRealBridge,
            PurchasePlannerRealReadBridge purchaseRealBridge,
            StockReducePlannerRealReadBridge stockReduceRealBridge) {
        return replay(req, revenueRealBridge, purchaseRealBridge, stockReduceRealBridge, null);
    }

    /**
     * @param dishProfitRealBridge 菜品毛利 Hydrated case 须非 null（经由 {@link AiHarnessReplayService}）
     */
    public static AiHarnessReplayResponse replay(
            AiHarnessReplayRequest req,
            RevenuePlannerRealReadBridge revenueRealBridge,
            PurchasePlannerRealReadBridge purchaseRealBridge,
            StockReducePlannerRealReadBridge stockReduceRealBridge,
            DishProfitPlannerRealReadBridge dishProfitRealBridge) {
        if (req == null || req.getUserId() == null) {
            throw new IllegalArgumentException("userId required");
        }
        if (req.getMessages() == null || req.getMessages().isEmpty()) {
            throw new IllegalArgumentException("messages required");
        }

        LocalDate today = resolveToday(req.getFrozenClockDate());
        List<AiHarnessReplayRoundResult> rounds = new ArrayList<>();

        for (int i = 0; i < req.getMessages().size(); i++) {
            String raw = req.getMessages().get(i);
            String msg = StringUtils.hasText(raw) ? raw.trim() : "";
            if (!StringUtils.hasText(msg)) {
                rounds.add(AiHarnessReplayRoundResult.builder()
                        .roundIndex(i + 1)
                        .message("")
                        .runId(SYNTHETIC_RUN_ID_BASE + i)
                        .conversationId(0L)
                        .resolvedQueryContextSummary(new LinkedHashMap<>())
                        .pass(true)
                        .failedFields(List.of())
                        .build());
                continue;
            }

            String caseKey =
                    req.getCaseId() != null
                            ? req.getCaseId().trim()
                            : AiHarnessBuiltinCases.PLANNER_EXECUTOR_MOCK_CORE;

            PlannerExecutorResult result;
            Map<String, Object> summary;
            boolean roundPass = true;
            if (AiHarnessBuiltinCases.PLANNER_EXECUTOR_BUSINESS_DIAGNOSIS_COMPOSITE_ALL_REAL_CORE.equals(caseKey)) {
                if (revenueRealBridge == null
                        || purchaseRealBridge == null
                        || stockReduceRealBridge == null
                        || dishProfitRealBridge == null) {
                    throw new IllegalStateException(
                            "PLANNER_EXECUTOR_BUSINESS_DIAGNOSIS_COMPOSITE_ALL_REAL_CORE requires "
                                    + "RevenuePlannerRealReadBridge, PurchasePlannerRealReadBridge, "
                                    + "StockReducePlannerRealReadBridge, and DishProfitPlannerRealReadBridge; "
                                    + "use AiHarnessReplayService.replay");
                }
                PlannerStepExecutor allDataRegistry =
                        new PlannerAgentAdapterStepExecutor(
                                new PlannerAgentAdapterRegistry(
                                        List.of(
                                                new RevenuePlannerAgentAdapter(revenueRealBridge),
                                                new PurchasePlannerAgentAdapter(purchaseRealBridge),
                                                new StockReducePlannerAgentAdapter(stockReduceRealBridge),
                                                new DishProfitPlannerAgentAdapter(dishProfitRealBridge))));
                PlannerExecutor hybridExecutor =
                        new PlannerExecutor(
                                PlannerExecutorExecutionMode.ADAPTER,
                                new CompositeBusinessDiagnosisAllDataRealHybridPlannerStepExecutor(allDataRegistry));
                result =
                        hybridExecutor.execute(
                                AiPlannerExecutorBusinessDiagnosisCompositeAllRealGraphCase.buildPlan());
                summary =
                        AiPlannerExecutorBusinessDiagnosisCompositeAllRealGraphCase.toHarnessSummary(
                                result, msg, SYNTHETIC_RUN_ID_BASE + i, 0L);
                PlannerExecutorTrace trAll = result != null ? result.getTrace() : null;
                roundPass = trAll != null && trAll.getOverallStatus() == PlannerStepStatus.SUCCESS;
            // P1-B 当前主验收：Composite GROUP 四域 Hydrated 真实（C-48）
            } else if (AiHarnessBuiltinCases.PLANNER_EXECUTOR_BUSINESS_DIAGNOSIS_COMPOSITE_GROUP_CORE.equals(caseKey)) {
                if (revenueRealBridge == null
                        || purchaseRealBridge == null
                        || stockReduceRealBridge == null
                        || dishProfitRealBridge == null) {
                    throw new IllegalStateException(
                            "PLANNER_EXECUTOR_BUSINESS_DIAGNOSIS_COMPOSITE_GROUP_CORE requires "
                                    + "RevenuePlannerRealReadBridge, PurchasePlannerRealReadBridge, "
                                    + "StockReducePlannerRealReadBridge, and DishProfitPlannerRealReadBridge; "
                                    + "use AiHarnessReplayService.replay");
                }
                PlannerStepExecutor allDataRegistry =
                        new PlannerAgentAdapterStepExecutor(
                                new PlannerAgentAdapterRegistry(
                                        List.of(
                                                new RevenuePlannerAgentAdapter(revenueRealBridge),
                                                new PurchasePlannerAgentAdapter(purchaseRealBridge),
                                                new StockReducePlannerAgentAdapter(stockReduceRealBridge),
                                                new DishProfitPlannerAgentAdapter(dishProfitRealBridge))));
                PlannerExecutor hybridExecutor =
                        new PlannerExecutor(
                                PlannerExecutorExecutionMode.ADAPTER,
                                new CompositeBusinessDiagnosisAllDataRealHybridPlannerStepExecutor(allDataRegistry));
                PlannerExecutionPlan groupCompositePlan =
                        AiPlannerExecutorBusinessDiagnosisCompositeGroupGraphCase.buildPlan();
                result = hybridExecutor.execute(groupCompositePlan);
                summary =
                        AiPlannerExecutorBusinessDiagnosisCompositeGroupGraphCase.toHarnessSummary(
                                result, msg, SYNTHETIC_RUN_ID_BASE + i, 0L, groupCompositePlan);
                PlannerExecutorTrace trGrp = result != null ? result.getTrace() : null;
                roundPass = trGrp != null && trGrp.getOverallStatus() == PlannerStepStatus.SUCCESS;
            // P1-B 当前主验收：Composite 出库步故意 DEGRADED（C-42）
            } else if (AiHarnessBuiltinCases.PLANNER_EXECUTOR_BUSINESS_DIAGNOSIS_COMPOSITE_STOCK_DEGRADED_CORE.equals(
                    caseKey)) {
                if (revenueRealBridge == null
                        || purchaseRealBridge == null
                        || stockReduceRealBridge == null
                        || dishProfitRealBridge == null) {
                    throw new IllegalStateException(
                            "PLANNER_EXECUTOR_BUSINESS_DIAGNOSIS_COMPOSITE_STOCK_DEGRADED_CORE requires "
                                    + "RevenuePlannerRealReadBridge, PurchasePlannerRealReadBridge, "
                                    + "StockReducePlannerRealReadBridge, and DishProfitPlannerRealReadBridge; "
                                    + "use AiHarnessReplayService.replay");
                }
                PlannerStepExecutor allDataRegistry =
                        new PlannerAgentAdapterStepExecutor(
                                new PlannerAgentAdapterRegistry(
                                        List.of(
                                                new RevenuePlannerAgentAdapter(revenueRealBridge),
                                                new PurchasePlannerAgentAdapter(purchaseRealBridge),
                                                new StockReducePlannerAgentAdapter(stockReduceRealBridge),
                                                new DishProfitPlannerAgentAdapter(dishProfitRealBridge))));
                PlannerExecutor hybridExecutor =
                        new PlannerExecutor(
                                PlannerExecutorExecutionMode.ADAPTER,
                                new CompositeBusinessDiagnosisStockDegradedHarnessHybridPlannerStepExecutor(
                                        allDataRegistry));
                result =
                        hybridExecutor.execute(
                                AiPlannerExecutorBusinessDiagnosisCompositeStockDegradedGraphCase.buildPlan());
                summary =
                        AiPlannerExecutorBusinessDiagnosisCompositeStockDegradedGraphCase.toHarnessSummary(
                                result, msg, SYNTHETIC_RUN_ID_BASE + i, 0L);
                PlannerExecutorTrace trDeg = result != null ? result.getTrace() : null;
                roundPass =
                        trDeg != null
                                && trDeg.getOverallStatus() != PlannerStepStatus.FAILED
                                && trDeg.getOverallStatus() == PlannerStepStatus.DEGRADED;
            } else {
                // 默认：MOCK_CORE / MOCK_DEGRADED → AiPlannerExecutorMockGraphCase
                PlannerExecutor executor = new PlannerExecutor();
                result = AiPlannerExecutorMockGraphNode.run(executor, caseKey);
                summary =
                        AiPlannerExecutorMockGraphCase.toHarnessSummary(
                                result, msg, SYNTHETIC_RUN_ID_BASE + i, 0L, caseKey);
            }

            rounds.add(AiHarnessReplayRoundResult.builder()
                    .roundIndex(i + 1)
                    .message(msg)
                    .runId(SYNTHETIC_RUN_ID_BASE + i)
                    .conversationId(0L)
                    .resolvedQueryContextSummary(new LinkedHashMap<>(summary))
                    .pass(roundPass)
                    .failedFields(List.of())
                    .build());
        }

        boolean overallPass = rounds.stream().allMatch(AiHarnessReplayRoundResult::isPass);
        return AiHarnessReplayResponse.builder()
                .conversationId(0L)
                .overallPass(overallPass)
                .frozenClockDate(today.toString())
                .caseId(req.getCaseId())
                .rounds(rounds)
                .build();
    }

    private static LocalDate resolveToday(String frozenClockDate) {
        if (!StringUtils.hasText(frozenClockDate)) {
            return LocalDate.now();
        }
        try {
            return LocalDate.parse(frozenClockDate.trim());
        } catch (DateTimeParseException ex) {
            throw new IllegalArgumentException("invalid frozenClockDate (yyyy-MM-dd): " + frozenClockDate);
        }
    }
}
