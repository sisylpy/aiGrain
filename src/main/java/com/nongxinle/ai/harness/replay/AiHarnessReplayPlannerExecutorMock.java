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
import com.nongxinle.ai.planner.CompositeBusinessDiagnosisRevenueHybridPlannerStepExecutor;
import com.nongxinle.ai.planner.CompositeBusinessDiagnosisRevenuePurchaseHybridPlannerStepExecutor;
import com.nongxinle.ai.planner.CompositeBusinessDiagnosisRevenuePurchaseStockHybridPlannerStepExecutor;
import com.nongxinle.ai.planner.DishProfitPlannerAgentAdapter;
import com.nongxinle.ai.planner.DishProfitPlannerRealReadBridge;
import com.nongxinle.ai.planner.FakeDishProfitPlannerReadBridge;
import com.nongxinle.ai.planner.FakePurchasePlannerReadBridge;
import com.nongxinle.ai.planner.FakeRevenuePlannerReadBridge;
import com.nongxinle.ai.planner.FakeStockReducePlannerReadBridge;
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
import com.nongxinle.ai.planner.RecommendationPlannerMockAgentAdapter;
import com.nongxinle.ai.planner.RevenuePlannerAgentAdapter;
import com.nongxinle.ai.planner.RevenuePlannerRealReadBridge;

/**
 * DB-free Harness Replay：{@link AiHarnessBuiltinCases#isPlannerExecutorMockHarnessCase(String)} 为 true 时走本类，
 * 不创建会话、不跑 Resolver / 生产图。{@link AiHarnessBuiltinCases#PLANNER_EXECUTOR_PURCHASE_ADAPTER_CORE} 使用无 Bridge 的
 * {@link PurchasePlannerAgentAdapter}；{@link AiHarnessBuiltinCases#PLANNER_EXECUTOR_PURCHASE_ADAPTER_FAKE_OK_CORE} 注入
 * {@link FakePurchasePlannerReadBridge}；{@link AiHarnessBuiltinCases#PLANNER_EXECUTOR_PURCHASE_ADAPTER_REAL_BRIDGE_CORE} /
 * {@link AiHarnessBuiltinCases#PLANNER_EXECUTOR_PURCHASE_ADAPTER_REAL_BRIDGE_HYDRATED_CORE} /
 * {@link AiHarnessBuiltinCases#PLANNER_EXECUTOR_PURCHASE_ADAPTER_GROUP_HYDRATED_CORE} 注入
 * {@link PurchasePlannerRealReadBridge}（须由 {@link AiHarnessReplayService} 传入 Spring Bean；Hydrated / GROUP Hydrated 调真实
 * {@code PurchaseOverviewToolExecutor}）。{@link AiHarnessBuiltinCases#PLANNER_EXECUTOR_REVENUE_ADAPTER_CORE} 使用无 Bridge 的
 * {@link RevenuePlannerAgentAdapter}；{@link AiHarnessBuiltinCases#PLANNER_EXECUTOR_REVENUE_ADAPTER_FAKE_OK_CORE} 注入
 * {@link FakeRevenuePlannerReadBridge}；{@link AiHarnessBuiltinCases#PLANNER_EXECUTOR_REVENUE_ADAPTER_REAL_BRIDGE_CORE} /
 * {@link AiHarnessBuiltinCases#PLANNER_EXECUTOR_REVENUE_ADAPTER_REAL_BRIDGE_HYDRATED_CORE} /
 * {@link AiHarnessBuiltinCases#PLANNER_EXECUTOR_REVENUE_ADAPTER_GROUP_HYDRATED_CORE} 注入
 * {@link RevenuePlannerRealReadBridge}（须由 {@link AiHarnessReplayService} 传入 Spring Bean）。
 * {@link AiHarnessBuiltinCases#PLANNER_EXECUTOR_STOCK_REDUCE_ADAPTER_CORE} 使用无 Bridge 的
 * {@link StockReducePlannerAgentAdapter}；{@link AiHarnessBuiltinCases#PLANNER_EXECUTOR_STOCK_REDUCE_ADAPTER_FAKE_OK_CORE} 注入
 * {@link FakeStockReducePlannerReadBridge}（C-21：不接真实 {@code StockReduceQueryToolExecutor}）。
 * {@link AiHarnessBuiltinCases#PLANNER_EXECUTOR_STOCK_REDUCE_ADAPTER_REAL_BRIDGE_CORE} 注入
 * {@link StockReducePlannerRealReadBridge}（C-22：Harness 内 {@code new}；默认计划不 Hydrate → 诚实降级，不调用
 * {@code StockReduceQueryToolExecutor}）。{@link AiHarnessBuiltinCases#PLANNER_EXECUTOR_STOCK_REDUCE_ADAPTER_REAL_BRIDGE_HYDRATED_CORE}
 * 注入 Spring Bean {@link StockReducePlannerRealReadBridge}（C-24：物化最小上下文 → 真实 {@code stock_reduce_query}）。
 * {@link AiHarnessBuiltinCases#PLANNER_EXECUTOR_STOCK_REDUCE_ADAPTER_GROUP_HYDRATED_CORE} 同上 Bean（C-46：GROUP 双店 +
 * {@code groupStockReduceQuery=true} → 真实 {@code stock_reduce_query}）。
 * {@link AiHarnessBuiltinCases#PLANNER_EXECUTOR_DISH_PROFIT_ADAPTER_CORE} 使用无 Bridge 的 {@link DishProfitPlannerAgentAdapter}；
 * {@link AiHarnessBuiltinCases#PLANNER_EXECUTOR_DISH_PROFIT_ADAPTER_FAKE_OK_CORE} 注入 {@link FakeDishProfitPlannerReadBridge}
 * （C-26：不接真实 {@code DishProfitQueryToolExecutor}）。{@link AiHarnessBuiltinCases#PLANNER_EXECUTOR_DISH_PROFIT_ADAPTER_REAL_BRIDGE_CORE}
 * 注入 Harness 内 {@code new} {@link DishProfitPlannerRealReadBridge}（C-27：默认不 Hydrate → 诚实降级，不调用 {@code DishProfitQueryToolExecutor}）。
 * {@link AiHarnessBuiltinCases#PLANNER_EXECUTOR_DISH_PROFIT_ADAPTER_REAL_BRIDGE_HYDRATED_CORE} 注入 Spring Bean
 * {@link DishProfitPlannerRealReadBridge}（C-29：物化最小上下文 → 真实 {@code dish_profit_analysis}）。{@link
 * AiHarnessBuiltinCases#PLANNER_EXECUTOR_DISH_PROFIT_ADAPTER_GROUP_HYDRATED_CORE} 同上 Bean（C-47：GROUP 双店 + 真实 {@code
 * dish_profit_analysis}）。
 * {@link AiHarnessBuiltinCases#PLANNER_EXECUTOR_BUSINESS_DIAGNOSIS_COMPOSITE_REVENUE_CORE}（C-32：Composite 六步中
 * 仅 {@code step_revenue_hydrated} → {@code revenue_query} 走 {@link RevenuePlannerRealReadBridge}；其余 mock）。
 * {@link AiHarnessBuiltinCases#PLANNER_EXECUTOR_BUSINESS_DIAGNOSIS_COMPOSITE_REVENUE_PURCHASE_CORE}（C-33：营收 +
 * 采购 Hydrated 真实；出库 / 菜品 / 诊断 / 建议 mock）。
 * {@link AiHarnessBuiltinCases#PLANNER_EXECUTOR_BUSINESS_DIAGNOSIS_COMPOSITE_REVENUE_PURCHASE_STOCK_CORE}（C-34：营收 +
 * 采购 + 出库 Hydrated 真实；菜品 / 诊断 / 建议 mock）。
 * {@link AiHarnessBuiltinCases#PLANNER_EXECUTOR_BUSINESS_DIAGNOSIS_COMPOSITE_ALL_REAL_CORE}（C-35：四数据域 Hydrated
 * 真实；诊断 / 建议 mock）。{@link AiHarnessBuiltinCases#PLANNER_EXECUTOR_BUSINESS_DIAGNOSIS_COMPOSITE_GROUP_CORE}（C-48：四域
 * **GROUP** Hydrated + 确定性诊断 + mock 建议）。
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
            if (AiHarnessBuiltinCases.PLANNER_EXECUTOR_PURCHASE_ADAPTER_CORE.equals(caseKey)) {
                PlannerExecutor adapterExecutor =
                        new PlannerExecutor(
                                PlannerExecutorExecutionMode.ADAPTER,
                                new PlannerAgentAdapterStepExecutor(
                                        new PlannerAgentAdapterRegistry(
                                                List.of(
                                                        new PurchasePlannerAgentAdapter(),
                                                        new RecommendationPlannerMockAgentAdapter()))));
                result = adapterExecutor.execute(AiPlannerExecutorPurchaseAdapterGraphCase.buildPlan());
                summary =
                        AiPlannerExecutorPurchaseAdapterGraphCase.toHarnessSummary(
                                result, msg, SYNTHETIC_RUN_ID_BASE + i, 0L);
            } else if (AiHarnessBuiltinCases.PLANNER_EXECUTOR_PURCHASE_ADAPTER_FAKE_OK_CORE.equals(caseKey)) {
                PlannerExecutor adapterExecutor =
                        new PlannerExecutor(
                                PlannerExecutorExecutionMode.ADAPTER,
                                new PlannerAgentAdapterStepExecutor(
                                        new PlannerAgentAdapterRegistry(
                                                List.of(
                                                        new PurchasePlannerAgentAdapter(
                                                                FakePurchasePlannerReadBridge.instance()),
                                                        new RecommendationPlannerMockAgentAdapter()))));
                result = adapterExecutor.execute(AiPlannerExecutorPurchaseAdapterFakeOkGraphCase.buildPlan());
                summary =
                        AiPlannerExecutorPurchaseAdapterFakeOkGraphCase.toHarnessSummary(
                                result, msg, SYNTHETIC_RUN_ID_BASE + i, 0L);
            } else if (AiHarnessBuiltinCases.PLANNER_EXECUTOR_STOCK_REDUCE_ADAPTER_CORE.equals(caseKey)) {
                PlannerExecutor adapterExecutor =
                        new PlannerExecutor(
                                PlannerExecutorExecutionMode.ADAPTER,
                                new PlannerAgentAdapterStepExecutor(
                                        new PlannerAgentAdapterRegistry(
                                                List.of(
                                                        new StockReducePlannerAgentAdapter(),
                                                        new RecommendationPlannerMockAgentAdapter()))));
                result = adapterExecutor.execute(AiPlannerExecutorStockReduceAdapterGraphCase.buildPlan());
                summary =
                        AiPlannerExecutorStockReduceAdapterGraphCase.toHarnessSummary(
                                result, msg, SYNTHETIC_RUN_ID_BASE + i, 0L);
            } else if (AiHarnessBuiltinCases.PLANNER_EXECUTOR_STOCK_REDUCE_ADAPTER_FAKE_OK_CORE.equals(caseKey)) {
                PlannerExecutor adapterExecutor =
                        new PlannerExecutor(
                                PlannerExecutorExecutionMode.ADAPTER,
                                new PlannerAgentAdapterStepExecutor(
                                        new PlannerAgentAdapterRegistry(
                                                List.of(
                                                        new StockReducePlannerAgentAdapter(
                                                                FakeStockReducePlannerReadBridge.instance()),
                                                        new RecommendationPlannerMockAgentAdapter()))));
                result = adapterExecutor.execute(AiPlannerExecutorStockReduceAdapterFakeOkGraphCase.buildPlan());
                summary =
                        AiPlannerExecutorStockReduceAdapterFakeOkGraphCase.toHarnessSummary(
                                result, msg, SYNTHETIC_RUN_ID_BASE + i, 0L);
            } else if (AiHarnessBuiltinCases.PLANNER_EXECUTOR_DISH_PROFIT_ADAPTER_CORE.equals(caseKey)) {
                PlannerExecutor adapterExecutor =
                        new PlannerExecutor(
                                PlannerExecutorExecutionMode.ADAPTER,
                                new PlannerAgentAdapterStepExecutor(
                                        new PlannerAgentAdapterRegistry(
                                                List.of(
                                                        new DishProfitPlannerAgentAdapter(),
                                                        new RecommendationPlannerMockAgentAdapter()))));
                result = adapterExecutor.execute(AiPlannerExecutorDishProfitAdapterGraphCase.buildPlan());
                summary =
                        AiPlannerExecutorDishProfitAdapterGraphCase.toHarnessSummary(
                                result, msg, SYNTHETIC_RUN_ID_BASE + i, 0L);
            } else if (AiHarnessBuiltinCases.PLANNER_EXECUTOR_DISH_PROFIT_ADAPTER_FAKE_OK_CORE.equals(caseKey)) {
                PlannerExecutor adapterExecutor =
                        new PlannerExecutor(
                                PlannerExecutorExecutionMode.ADAPTER,
                                new PlannerAgentAdapterStepExecutor(
                                        new PlannerAgentAdapterRegistry(
                                                List.of(
                                                        new DishProfitPlannerAgentAdapter(
                                                                FakeDishProfitPlannerReadBridge.instance()),
                                                        new RecommendationPlannerMockAgentAdapter()))));
                result = adapterExecutor.execute(AiPlannerExecutorDishProfitAdapterFakeOkGraphCase.buildPlan());
                summary =
                        AiPlannerExecutorDishProfitAdapterFakeOkGraphCase.toHarnessSummary(
                                result, msg, SYNTHETIC_RUN_ID_BASE + i, 0L);
            } else if (AiHarnessBuiltinCases.PLANNER_EXECUTOR_DISH_PROFIT_ADAPTER_REAL_BRIDGE_CORE.equals(caseKey)) {
                PlannerExecutor adapterExecutor =
                        new PlannerExecutor(
                                PlannerExecutorExecutionMode.ADAPTER,
                                new PlannerAgentAdapterStepExecutor(
                                        new PlannerAgentAdapterRegistry(
                                                List.of(
                                                        new DishProfitPlannerAgentAdapter(
                                                                new DishProfitPlannerRealReadBridge()),
                                                        new RecommendationPlannerMockAgentAdapter()))));
                result = adapterExecutor.execute(AiPlannerExecutorDishProfitAdapterRealBridgeGraphCase.buildPlan());
                summary =
                        AiPlannerExecutorDishProfitAdapterRealBridgeGraphCase.toHarnessSummary(
                                result, msg, SYNTHETIC_RUN_ID_BASE + i, 0L);
            } else if (AiHarnessBuiltinCases.PLANNER_EXECUTOR_DISH_PROFIT_ADAPTER_REAL_BRIDGE_HYDRATED_CORE.equals(
                    caseKey)) {
                if (dishProfitRealBridge == null) {
                    throw new IllegalStateException(
                            "PLANNER_EXECUTOR_DISH_PROFIT_ADAPTER_REAL_BRIDGE_HYDRATED_CORE requires "
                                    + "DishProfitPlannerRealReadBridge; use AiHarnessReplayService.replay");
                }
                PlannerExecutor adapterExecutor =
                        new PlannerExecutor(
                                PlannerExecutorExecutionMode.ADAPTER,
                                new PlannerAgentAdapterStepExecutor(
                                        new PlannerAgentAdapterRegistry(
                                                List.of(
                                                        new DishProfitPlannerAgentAdapter(dishProfitRealBridge),
                                                        new RecommendationPlannerMockAgentAdapter()))));
                result =
                        adapterExecutor.execute(
                                AiPlannerExecutorDishProfitAdapterRealBridgeHydratedGraphCase.buildPlan());
                summary =
                        AiPlannerExecutorDishProfitAdapterRealBridgeHydratedGraphCase.toHarnessSummary(
                                result, msg, SYNTHETIC_RUN_ID_BASE + i, 0L);
                PlannerExecutorTrace trDp = result != null ? result.getTrace() : null;
                roundPass = trDp != null && trDp.getOverallStatus() == PlannerStepStatus.SUCCESS;
            } else if (AiHarnessBuiltinCases.PLANNER_EXECUTOR_DISH_PROFIT_ADAPTER_GROUP_HYDRATED_CORE.equals(caseKey)) {
                if (dishProfitRealBridge == null) {
                    throw new IllegalStateException(
                            "PLANNER_EXECUTOR_DISH_PROFIT_ADAPTER_GROUP_HYDRATED_CORE requires "
                                    + "DishProfitPlannerRealReadBridge; use AiHarnessReplayService.replay");
                }
                PlannerExecutor adapterExecutor =
                        new PlannerExecutor(
                                PlannerExecutorExecutionMode.ADAPTER,
                                new PlannerAgentAdapterStepExecutor(
                                        new PlannerAgentAdapterRegistry(
                                                List.of(
                                                        new DishProfitPlannerAgentAdapter(dishProfitRealBridge),
                                                        new RecommendationPlannerMockAgentAdapter()))));
                PlannerExecutionPlan groupDishPlan =
                        AiPlannerExecutorDishProfitAdapterGroupHydratedGraphCase.buildPlan();
                result = adapterExecutor.execute(groupDishPlan);
                summary =
                        AiPlannerExecutorDishProfitAdapterGroupHydratedGraphCase.toHarnessSummary(
                                result, msg, SYNTHETIC_RUN_ID_BASE + i, 0L, groupDishPlan);
                PlannerExecutorTrace trDpg = result != null ? result.getTrace() : null;
                roundPass = trDpg != null && trDpg.getOverallStatus() == PlannerStepStatus.SUCCESS;
            } else if (AiHarnessBuiltinCases.PLANNER_EXECUTOR_STOCK_REDUCE_ADAPTER_REAL_BRIDGE_CORE.equals(caseKey)) {
                PlannerExecutor adapterExecutor =
                        new PlannerExecutor(
                                PlannerExecutorExecutionMode.ADAPTER,
                                new PlannerAgentAdapterStepExecutor(
                                        new PlannerAgentAdapterRegistry(
                                                List.of(
                                                        new StockReducePlannerAgentAdapter(
                                                                new StockReducePlannerRealReadBridge()),
                                                        new RecommendationPlannerMockAgentAdapter()))));
                result =
                        adapterExecutor.execute(
                                AiPlannerExecutorStockReduceAdapterRealBridgeGraphCase.buildPlan());
                summary =
                        AiPlannerExecutorStockReduceAdapterRealBridgeGraphCase.toHarnessSummary(
                                result, msg, SYNTHETIC_RUN_ID_BASE + i, 0L);
            } else if (AiHarnessBuiltinCases.PLANNER_EXECUTOR_STOCK_REDUCE_ADAPTER_REAL_BRIDGE_HYDRATED_CORE.equals(
                    caseKey)) {
                if (stockReduceRealBridge == null) {
                    throw new IllegalStateException(
                            "PLANNER_EXECUTOR_STOCK_REDUCE_ADAPTER_REAL_BRIDGE_HYDRATED_CORE requires "
                                    + "StockReducePlannerRealReadBridge; use AiHarnessReplayService.replay");
                }
                PlannerExecutor adapterExecutor =
                        new PlannerExecutor(
                                PlannerExecutorExecutionMode.ADAPTER,
                                new PlannerAgentAdapterStepExecutor(
                                        new PlannerAgentAdapterRegistry(
                                                List.of(
                                                        new StockReducePlannerAgentAdapter(stockReduceRealBridge),
                                                        new RecommendationPlannerMockAgentAdapter()))));
                result =
                        adapterExecutor.execute(
                                AiPlannerExecutorStockReduceAdapterRealBridgeHydratedGraphCase.buildPlan());
                summary =
                        AiPlannerExecutorStockReduceAdapterRealBridgeHydratedGraphCase.toHarnessSummary(
                                result, msg, SYNTHETIC_RUN_ID_BASE + i, 0L);
                PlannerExecutorTrace trHydr = result != null ? result.getTrace() : null;
                roundPass = trHydr != null && trHydr.getOverallStatus() == PlannerStepStatus.SUCCESS;
            } else if (AiHarnessBuiltinCases.PLANNER_EXECUTOR_STOCK_REDUCE_ADAPTER_GROUP_HYDRATED_CORE.equals(caseKey)) {
                if (stockReduceRealBridge == null) {
                    throw new IllegalStateException(
                            "PLANNER_EXECUTOR_STOCK_REDUCE_ADAPTER_GROUP_HYDRATED_CORE requires "
                                    + "StockReducePlannerRealReadBridge; use AiHarnessReplayService.replay");
                }
                PlannerExecutor adapterExecutor =
                        new PlannerExecutor(
                                PlannerExecutorExecutionMode.ADAPTER,
                                new PlannerAgentAdapterStepExecutor(
                                        new PlannerAgentAdapterRegistry(
                                                List.of(
                                                        new StockReducePlannerAgentAdapter(stockReduceRealBridge),
                                                        new RecommendationPlannerMockAgentAdapter()))));
                PlannerExecutionPlan groupStockPlan =
                        AiPlannerExecutorStockReduceAdapterGroupHydratedGraphCase.buildPlan();
                result = adapterExecutor.execute(groupStockPlan);
                summary =
                        AiPlannerExecutorStockReduceAdapterGroupHydratedGraphCase.toHarnessSummary(
                                result, msg, SYNTHETIC_RUN_ID_BASE + i, 0L, groupStockPlan);
                PlannerExecutorTrace trSg = result != null ? result.getTrace() : null;
                roundPass = trSg != null && trSg.getOverallStatus() == PlannerStepStatus.SUCCESS;
            } else if (AiHarnessBuiltinCases.PLANNER_EXECUTOR_PURCHASE_ADAPTER_REAL_BRIDGE_HYDRATED_CORE.equals(caseKey)) {
                if (purchaseRealBridge == null) {
                    throw new IllegalStateException(
                            "PLANNER_EXECUTOR_PURCHASE_ADAPTER_REAL_BRIDGE_HYDRATED_CORE requires "
                                    + "PurchasePlannerRealReadBridge; use AiHarnessReplayService.replay");
                }
                PlannerExecutor adapterExecutor =
                        new PlannerExecutor(
                                PlannerExecutorExecutionMode.ADAPTER,
                                new PlannerAgentAdapterStepExecutor(
                                        new PlannerAgentAdapterRegistry(
                                                List.of(
                                                        new PurchasePlannerAgentAdapter(purchaseRealBridge),
                                                        new RecommendationPlannerMockAgentAdapter()))));
                result =
                        adapterExecutor.execute(
                                AiPlannerExecutorPurchaseAdapterRealBridgeHydratedGraphCase.buildPlan());
                summary =
                        AiPlannerExecutorPurchaseAdapterRealBridgeHydratedGraphCase.toHarnessSummary(
                                result, msg, SYNTHETIC_RUN_ID_BASE + i, 0L);
                PlannerExecutorTrace tr = result != null ? result.getTrace() : null;
                roundPass = tr != null && tr.getOverallStatus() == PlannerStepStatus.SUCCESS;
            } else if (AiHarnessBuiltinCases.PLANNER_EXECUTOR_PURCHASE_ADAPTER_GROUP_HYDRATED_CORE.equals(caseKey)) {
                if (purchaseRealBridge == null) {
                    throw new IllegalStateException(
                            "PLANNER_EXECUTOR_PURCHASE_ADAPTER_GROUP_HYDRATED_CORE requires "
                                    + "PurchasePlannerRealReadBridge; use AiHarnessReplayService.replay");
                }
                PlannerExecutor adapterExecutor =
                        new PlannerExecutor(
                                PlannerExecutorExecutionMode.ADAPTER,
                                new PlannerAgentAdapterStepExecutor(
                                        new PlannerAgentAdapterRegistry(
                                                List.of(
                                                        new PurchasePlannerAgentAdapter(purchaseRealBridge),
                                                        new RecommendationPlannerMockAgentAdapter()))));
                PlannerExecutionPlan groupPurchasePlan =
                        AiPlannerExecutorPurchaseAdapterGroupHydratedGraphCase.buildPlan();
                result = adapterExecutor.execute(groupPurchasePlan);
                summary =
                        AiPlannerExecutorPurchaseAdapterGroupHydratedGraphCase.toHarnessSummary(
                                result, msg, SYNTHETIC_RUN_ID_BASE + i, 0L, groupPurchasePlan);
                PlannerExecutorTrace trPg = result != null ? result.getTrace() : null;
                roundPass = trPg != null && trPg.getOverallStatus() == PlannerStepStatus.SUCCESS;
            } else if (AiHarnessBuiltinCases.PLANNER_EXECUTOR_PURCHASE_ADAPTER_REAL_BRIDGE_CORE.equals(caseKey)) {
                if (purchaseRealBridge == null) {
                    throw new IllegalStateException(
                            "PLANNER_EXECUTOR_PURCHASE_ADAPTER_REAL_BRIDGE_CORE requires "
                                    + "PurchasePlannerRealReadBridge; use AiHarnessReplayService.replay");
                }
                PlannerExecutor adapterExecutor =
                        new PlannerExecutor(
                                PlannerExecutorExecutionMode.ADAPTER,
                                new PlannerAgentAdapterStepExecutor(
                                        new PlannerAgentAdapterRegistry(
                                                List.of(
                                                        new PurchasePlannerAgentAdapter(purchaseRealBridge),
                                                        new RecommendationPlannerMockAgentAdapter()))));
                result =
                        adapterExecutor.execute(AiPlannerExecutorPurchaseAdapterRealBridgeGraphCase.buildPlan());
                summary =
                        AiPlannerExecutorPurchaseAdapterRealBridgeGraphCase.toHarnessSummary(
                                result, msg, SYNTHETIC_RUN_ID_BASE + i, 0L);
            } else if (AiHarnessBuiltinCases.PLANNER_EXECUTOR_REVENUE_ADAPTER_CORE.equals(caseKey)) {
                PlannerExecutor adapterExecutor =
                        new PlannerExecutor(
                                PlannerExecutorExecutionMode.ADAPTER,
                                new PlannerAgentAdapterStepExecutor(
                                        new PlannerAgentAdapterRegistry(
                                                List.of(
                                                        new RevenuePlannerAgentAdapter(),
                                                        new RecommendationPlannerMockAgentAdapter()))));
                result = adapterExecutor.execute(AiPlannerExecutorRevenueAdapterGraphCase.buildPlan());
                summary =
                        AiPlannerExecutorRevenueAdapterGraphCase.toHarnessSummary(
                                result, msg, SYNTHETIC_RUN_ID_BASE + i, 0L);
            } else if (AiHarnessBuiltinCases.PLANNER_EXECUTOR_REVENUE_ADAPTER_FAKE_OK_CORE.equals(caseKey)) {
                PlannerExecutor adapterExecutor =
                        new PlannerExecutor(
                                PlannerExecutorExecutionMode.ADAPTER,
                                new PlannerAgentAdapterStepExecutor(
                                        new PlannerAgentAdapterRegistry(
                                                List.of(
                                                        new RevenuePlannerAgentAdapter(
                                                                FakeRevenuePlannerReadBridge.instance()),
                                                        new RecommendationPlannerMockAgentAdapter()))));
                result = adapterExecutor.execute(AiPlannerExecutorRevenueAdapterFakeOkGraphCase.buildPlan());
                summary =
                        AiPlannerExecutorRevenueAdapterFakeOkGraphCase.toHarnessSummary(
                                result, msg, SYNTHETIC_RUN_ID_BASE + i, 0L);
            } else if (AiHarnessBuiltinCases.PLANNER_EXECUTOR_REVENUE_ADAPTER_REAL_BRIDGE_CORE.equals(caseKey)) {
                if (revenueRealBridge == null) {
                    throw new IllegalStateException(
                            "PLANNER_EXECUTOR_REVENUE_ADAPTER_REAL_BRIDGE_CORE requires "
                                    + "RevenuePlannerRealReadBridge; use AiHarnessReplayService.replay");
                }
                PlannerExecutor adapterExecutor =
                        new PlannerExecutor(
                                PlannerExecutorExecutionMode.ADAPTER,
                                new PlannerAgentAdapterStepExecutor(
                                        new PlannerAgentAdapterRegistry(
                                                List.of(
                                                        new RevenuePlannerAgentAdapter(revenueRealBridge),
                                                        new RecommendationPlannerMockAgentAdapter()))));
                result = adapterExecutor.execute(AiPlannerExecutorRevenueAdapterRealBridgeGraphCase.buildPlan());
                summary =
                        AiPlannerExecutorRevenueAdapterRealBridgeGraphCase.toHarnessSummary(
                                result, msg, SYNTHETIC_RUN_ID_BASE + i, 0L);
            } else if (AiHarnessBuiltinCases.PLANNER_EXECUTOR_REVENUE_ADAPTER_REAL_BRIDGE_HYDRATED_CORE.equals(
                    caseKey)) {
                if (revenueRealBridge == null) {
                    throw new IllegalStateException(
                            "PLANNER_EXECUTOR_REVENUE_ADAPTER_REAL_BRIDGE_HYDRATED_CORE requires "
                                    + "RevenuePlannerRealReadBridge; use AiHarnessReplayService.replay");
                }
                PlannerExecutor adapterExecutor =
                        new PlannerExecutor(
                                PlannerExecutorExecutionMode.ADAPTER,
                                new PlannerAgentAdapterStepExecutor(
                                        new PlannerAgentAdapterRegistry(
                                                List.of(
                                                        new RevenuePlannerAgentAdapter(revenueRealBridge),
                                                        new RecommendationPlannerMockAgentAdapter()))));
                result =
                        adapterExecutor.execute(
                                AiPlannerExecutorRevenueAdapterRealBridgeHydratedGraphCase.buildPlan());
                summary =
                        AiPlannerExecutorRevenueAdapterRealBridgeHydratedGraphCase.toHarnessSummary(
                                result, msg, SYNTHETIC_RUN_ID_BASE + i, 0L);
                PlannerExecutorTrace tr = result != null ? result.getTrace() : null;
                roundPass = tr != null && tr.getOverallStatus() == PlannerStepStatus.SUCCESS;
            } else if (AiHarnessBuiltinCases.PLANNER_EXECUTOR_REVENUE_ADAPTER_GROUP_HYDRATED_CORE.equals(caseKey)) {
                if (revenueRealBridge == null) {
                    throw new IllegalStateException(
                            "PLANNER_EXECUTOR_REVENUE_ADAPTER_GROUP_HYDRATED_CORE requires "
                                    + "RevenuePlannerRealReadBridge; use AiHarnessReplayService.replay");
                }
                PlannerExecutor adapterExecutor =
                        new PlannerExecutor(
                                PlannerExecutorExecutionMode.ADAPTER,
                                new PlannerAgentAdapterStepExecutor(
                                        new PlannerAgentAdapterRegistry(
                                                List.of(
                                                        new RevenuePlannerAgentAdapter(revenueRealBridge),
                                                        new RecommendationPlannerMockAgentAdapter()))));
                PlannerExecutionPlan groupPlan = AiPlannerExecutorRevenueAdapterGroupHydratedGraphCase.buildPlan();
                result = adapterExecutor.execute(groupPlan);
                summary =
                        AiPlannerExecutorRevenueAdapterGroupHydratedGraphCase.toHarnessSummary(
                                result, msg, SYNTHETIC_RUN_ID_BASE + i, 0L, groupPlan);
                PlannerExecutorTrace trGroup = result != null ? result.getTrace() : null;
                roundPass = trGroup != null && trGroup.getOverallStatus() == PlannerStepStatus.SUCCESS;
            } else if (AiHarnessBuiltinCases.PLANNER_EXECUTOR_BUSINESS_DIAGNOSIS_COMPOSITE_REVENUE_CORE.equals(
                    caseKey)) {
                if (revenueRealBridge == null) {
                    throw new IllegalStateException(
                            "PLANNER_EXECUTOR_BUSINESS_DIAGNOSIS_COMPOSITE_REVENUE_CORE requires "
                                    + "RevenuePlannerRealReadBridge; use AiHarnessReplayService.replay");
                }
                PlannerStepExecutor revenueOnly =
                        new PlannerAgentAdapterStepExecutor(
                                new PlannerAgentAdapterRegistry(
                                        List.of(new RevenuePlannerAgentAdapter(revenueRealBridge))));
                PlannerExecutor hybridExecutor =
                        new PlannerExecutor(
                                PlannerExecutorExecutionMode.ADAPTER,
                                new CompositeBusinessDiagnosisRevenueHybridPlannerStepExecutor(revenueOnly));
                result =
                        hybridExecutor.execute(
                                AiPlannerExecutorBusinessDiagnosisCompositeRevenueGraphCase.buildPlan());
                summary =
                        AiPlannerExecutorBusinessDiagnosisCompositeRevenueGraphCase.toHarnessSummary(
                                result, msg, SYNTHETIC_RUN_ID_BASE + i, 0L);
                PlannerExecutorTrace tr = result != null ? result.getTrace() : null;
                roundPass = tr != null && tr.getOverallStatus() == PlannerStepStatus.SUCCESS;
            } else if (AiHarnessBuiltinCases.PLANNER_EXECUTOR_BUSINESS_DIAGNOSIS_COMPOSITE_REVENUE_PURCHASE_CORE.equals(
                    caseKey)) {
                if (revenueRealBridge == null || purchaseRealBridge == null) {
                    throw new IllegalStateException(
                            "PLANNER_EXECUTOR_BUSINESS_DIAGNOSIS_COMPOSITE_REVENUE_PURCHASE_CORE requires "
                                    + "RevenuePlannerRealReadBridge and PurchasePlannerRealReadBridge; "
                                    + "use AiHarnessReplayService.replay");
                }
                PlannerStepExecutor revenuePurchaseRegistry =
                        new PlannerAgentAdapterStepExecutor(
                                new PlannerAgentAdapterRegistry(
                                        List.of(
                                                new RevenuePlannerAgentAdapter(revenueRealBridge),
                                                new PurchasePlannerAgentAdapter(purchaseRealBridge))));
                PlannerExecutor hybridExecutor =
                        new PlannerExecutor(
                                PlannerExecutorExecutionMode.ADAPTER,
                                new CompositeBusinessDiagnosisRevenuePurchaseHybridPlannerStepExecutor(
                                        revenuePurchaseRegistry));
                result =
                        hybridExecutor.execute(
                                AiPlannerExecutorBusinessDiagnosisCompositeRevenuePurchaseGraphCase.buildPlan());
                summary =
                        AiPlannerExecutorBusinessDiagnosisCompositeRevenuePurchaseGraphCase.toHarnessSummary(
                                result, msg, SYNTHETIC_RUN_ID_BASE + i, 0L);
                PlannerExecutorTrace tr = result != null ? result.getTrace() : null;
                roundPass = tr != null && tr.getOverallStatus() == PlannerStepStatus.SUCCESS;
            } else if (AiHarnessBuiltinCases.PLANNER_EXECUTOR_BUSINESS_DIAGNOSIS_COMPOSITE_REVENUE_PURCHASE_STOCK_CORE.equals(
                    caseKey)) {
                if (revenueRealBridge == null || purchaseRealBridge == null || stockReduceRealBridge == null) {
                    throw new IllegalStateException(
                            "PLANNER_EXECUTOR_BUSINESS_DIAGNOSIS_COMPOSITE_REVENUE_PURCHASE_STOCK_CORE requires "
                                    + "RevenuePlannerRealReadBridge, PurchasePlannerRealReadBridge, and "
                                    + "StockReducePlannerRealReadBridge; use AiHarnessReplayService.replay");
                }
                PlannerStepExecutor revenuePurchaseStockRegistry =
                        new PlannerAgentAdapterStepExecutor(
                                new PlannerAgentAdapterRegistry(
                                        List.of(
                                                new RevenuePlannerAgentAdapter(revenueRealBridge),
                                                new PurchasePlannerAgentAdapter(purchaseRealBridge),
                                                new StockReducePlannerAgentAdapter(stockReduceRealBridge))));
                PlannerExecutor hybridExecutor =
                        new PlannerExecutor(
                                PlannerExecutorExecutionMode.ADAPTER,
                                new CompositeBusinessDiagnosisRevenuePurchaseStockHybridPlannerStepExecutor(
                                        revenuePurchaseStockRegistry));
                result =
                        hybridExecutor.execute(
                                AiPlannerExecutorBusinessDiagnosisCompositeRevenuePurchaseStockGraphCase.buildPlan());
                summary =
                        AiPlannerExecutorBusinessDiagnosisCompositeRevenuePurchaseStockGraphCase.toHarnessSummary(
                                result, msg, SYNTHETIC_RUN_ID_BASE + i, 0L);
                PlannerExecutorTrace tr = result != null ? result.getTrace() : null;
                roundPass = tr != null && tr.getOverallStatus() == PlannerStepStatus.SUCCESS;
            } else if (AiHarnessBuiltinCases.PLANNER_EXECUTOR_BUSINESS_DIAGNOSIS_COMPOSITE_ALL_REAL_CORE.equals(caseKey)) {
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
