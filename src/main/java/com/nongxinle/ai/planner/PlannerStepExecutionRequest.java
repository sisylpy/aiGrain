package com.nongxinle.ai.planner;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 单步执行入参：适配层与 {@link PlannerExecutor} 之间的边界 DTO（C-5）。
 * <p>不包含用户原文路由字段；后续真实适配器可扩展 context 句柄（解析结果 id、权限范围等）。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlannerStepExecutionRequest {

    /** 当前步定义（含 {@code mock*} 字段；ADAPTER 模式下由具体 Executor 决定是否读取）。 */
    private PlannerStep step;

    /** 步级覆盖后的有效失败策略（已合并计划级默认值）。 */
    private PlannerFailureStrategy effectiveFailureStrategy;

    /** 可选：所属计划 id，便于 trace / 日志。 */
    private String planId;

    /** 可选：计划级类型，仅占位。 */
    private String planType;

    /**
     * 与 {@link PlannerExecutionPlan#getResolvedContextRef()} / 上游 {@code AiResolvedQueryContext} 对齐；
     * {@link PlannerAgentAdapter} 真实实现应依赖此句柄（及 {@link #answerPlanRef}），不得单独依赖用户原文。
     */
    private String resolvedQueryContextRef;

    /** 可选：AnswerPlan / 子计划引用，由编排层在 ADAPTER 路径注入。 */
    private String answerPlanRef;

    /** 可选：与本步共享的营收只读切片；通常来自 {@link PlannerExecutionPlan#getRevenueReadRequest()}。 */
    private RevenuePlannerReadRequest revenueReadRequest;

    /**
     * 可选：与计划级 {@link PlannerExecutionPlan#getRevenueExecutionContext()} 同源；供
     * {@link RevenuePlannerRealReadBridge#readWithExecutionContext} 路径。
     */
    private PlannerRevenueExecutionContext revenueExecutionContext;

    /** 可选：与本步共享的采购只读切片；通常来自 {@link PlannerExecutionPlan#getPurchaseReadRequest()}（C-16）。 */
    private PurchasePlannerReadRequest purchaseReadRequest;

    /** 可选：与计划级 {@link PlannerExecutionPlan#getPurchaseExecutionContext()} 同源（C-16）。 */
    private PurchasePlannerExecutionContext purchaseExecutionContext;

    /** 可选：与本步共享的出库/核销只读切片；通常来自 {@link PlannerExecutionPlan#getStockReduceReadRequest()}（C-21）。 */
    private StockReducePlannerReadRequest stockReduceReadRequest;

    /** 可选：与计划级 {@link PlannerExecutionPlan#getStockReduceExecutionContext()} 同源（C-21）。 */
    private StockReducePlannerExecutionContext stockReduceExecutionContext;

    /** 可选：与本步共享的菜品毛利只读切片；通常来自 {@link PlannerExecutionPlan#getDishProfitReadRequest()}（C-26）。 */
    private DishProfitPlannerReadRequest dishProfitReadRequest;

    /** 可选：与计划级 {@link PlannerExecutionPlan#getDishProfitExecutionContext()} 同源（C-26）。 */
    private DishProfitPlannerExecutionContext dishProfitExecutionContext;

    /**
     * C-37：当前内存中的计划快照（与 {@link PlannerExecutor} 迭代共用引用，便于读取已物化的各域 AnswerPlan）；
     * trace 内计划经 {@link PlannerExecutor#sanitizePlanForTrace} 剥离大对象。
     */
    private PlannerExecutionPlan planSnapshot;

    /** 已完成步的 {@link PlannerStepResult} 列表（不含当前步）。 */
    @Builder.Default
    private List<PlannerStepResult> priorStepResults = new ArrayList<>();

    /**
     * 截止当前步之前、已出现的降级步 id（与 {@link PlannerExecutorTrace#getDegradedSteps()} 前缀一致）。
     */
    @Builder.Default
    private List<String> degradedStepsSoFar = new ArrayList<>();
}
