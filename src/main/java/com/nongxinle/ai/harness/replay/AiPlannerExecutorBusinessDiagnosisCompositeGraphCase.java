package com.nongxinle.ai.harness.replay;

import com.nongxinle.ai.agent.business.BusinessAgentNames;
import com.nongxinle.ai.planner.PlannerExecutionPlan;
import com.nongxinle.ai.planner.PlannerFailureStrategy;
import com.nongxinle.ai.planner.PlannerStep;
import com.nongxinle.ai.planner.PlannerStepMockExecutionStatus;
import java.util.ArrayList;
import java.util.List;

/**
 * {@link AiHarnessBuiltinCases#PLANNER_EXECUTOR_BUSINESS_DIAGNOSIS_COMPOSITE_CORE}（C-31 / **C-31.1**）：六步
 * <strong>全部 MOCK</strong> 的组合型经营诊断 Plan 骨架 — <strong>不</strong>调用四条 Hydrated RealBridge、
 * <strong>不</strong>调用真实 Tool / LLM / SQL。前四步 {@code targetTool} 为 {@code mock_*_hydrated_adapter}（C-31.1），使
 * trace {@code usedTools} <strong>不</strong>出现 {@code revenue_query} 等生产 Tool id，避免误读为已真实执行；真实 Tool 名仅写在
 * {@code inputSummary}/{@code acceptanceCriteria} 中作未来接线说明。
 *
 * @see docs/ai/business-diagnosis-composite-plan-design.md
 */
public final class AiPlannerExecutorBusinessDiagnosisCompositeGraphCase {

    public static final String CASE_ID = AiHarnessBuiltinCases.PLANNER_EXECUTOR_BUSINESS_DIAGNOSIS_COMPOSITE_CORE;

    /** Harness Replay 根摘要：本 case 仅为骨架。 */
    public static final String PLANNER_COMPOSITE_HONESTY_SKELETON_ONLY = "COMPOSITE_SKELETON_ONLY";

    public static final String PLANNER_COMPOSITE_NOTE_SKELETON =
            "skeleton only; real hydrated adapters not invoked";

    public static final String PLAN_ID = "plan-business-diagnosis-composite-skeleton-v1";
    public static final String PLAN_TYPE = "PLANNER_EXECUTOR_BUSINESS_DIAGNOSIS_COMPOSITE_CORE_V1";

    /** 与 C-30 设计一致；Composer 实装前仅作 trace / 契约占位。 */
    public static final String FINAL_ANSWER_PLAN_TYPE = "BUSINESS_DIAGNOSIS_COMPOSITE";

    public static final String RESOLVED_CONTEXT_REF = "HARNESS_COMPOSITE_BUSINESS_DIAGNOSIS_RESOLVED_CTX_REF";
    public static final String DIAGNOSIS_ANSWER_PLAN_REF = "HARNESS_COMPOSITE_DIAGNOSIS_ANSWER_PLAN_REF";

    /** 诊断汇总步占位工具（MOCK 执行器合成 usage；非生产 ToolId）。 */
    public static final String MOCK_TOOL_DIAGNOSIS_COMPOSE = "mock_diagnosis_compose";

    /**
     * C-31.1：骨架 case 前四步 {@code targetTool} — 与生产 {@code revenue_query} 等区分，trace {@code usedTools} 仅为 mock 占位 echo。
     */
    public static final String MOCK_TOOL_REVENUE_HYDRATED_ADAPTER = "mock_revenue_hydrated_adapter";

    public static final String MOCK_TOOL_PURCHASE_HYDRATED_ADAPTER = "mock_purchase_hydrated_adapter";

    public static final String MOCK_TOOL_STOCK_REDUCE_HYDRATED_ADAPTER = "mock_stock_reduce_hydrated_adapter";

    public static final String MOCK_TOOL_DISH_PROFIT_HYDRATED_ADAPTER = "mock_dish_profit_hydrated_adapter";

    private AiPlannerExecutorBusinessDiagnosisCompositeGraphCase() {
    }

    /**
     * 六步均为 {@link PlannerStepMockExecutionStatus#SUCCESS}；步 id 与 C-30 设计对齐（hydrated 命名仅表达未来接线，当前仍 mock）。
     */
    public static PlannerExecutionPlan buildPlan() {
        List<PlannerStep> steps = new ArrayList<>();
        steps.add(
                step(
                        "step_revenue_hydrated",
                        "revenue_hydrated_skeleton",
                        1,
                        BusinessAgentNames.REVENUE_OVERVIEW,
                        MOCK_TOOL_REVENUE_HYDRATED_ADAPTER,
                        "STORE AAA 营收（skeleton mock）；未来接真实 Tool revenue_query + Hydrated Revenue RealBridge",
                        "revenue summary 占位",
                        "本步未执行 revenue_query；全链路接线后由 Adapter 调用",
                        null));
        steps.add(
                step(
                        "step_purchase_hydrated",
                        "purchase_hydrated_skeleton",
                        2,
                        BusinessAgentNames.PURCHASE_OVERVIEW,
                        MOCK_TOOL_PURCHASE_HYDRATED_ADAPTER,
                        "STORE AAA 采购（skeleton mock）；未来接真实 Tool purchase_overview + Hydrated Purchase RealBridge",
                        "purchase summary 占位",
                        "本步未执行 purchase_overview；全链路接线后由 Adapter 调用",
                        null));
        steps.add(
                step(
                        "step_stock_reduce_hydrated",
                        "stock_reduce_hydrated_skeleton",
                        3,
                        BusinessAgentNames.STOCK_REDUCE_QUERY,
                        MOCK_TOOL_STOCK_REDUCE_HYDRATED_ADAPTER,
                        "STORE AAA 出库/核销（skeleton mock）；未来接真实 Tool stock_reduce_query + Hydrated StockReduce RealBridge",
                        "stock_reduce summary 占位",
                        "本步未执行 stock_reduce_query；全链路接线后由 Adapter 调用",
                        null));
        steps.add(
                step(
                        "step_dish_profit_hydrated",
                        "dish_profit_hydrated_skeleton",
                        4,
                        BusinessAgentNames.DISH_PROFIT_ANALYSIS,
                        MOCK_TOOL_DISH_PROFIT_HYDRATED_ADAPTER,
                        "STORE AAA 菜品毛利（skeleton mock）；未来接真实 Tool dish_profit_analysis + Hydrated DishProfit RealBridge",
                        "dish_profit summary 占位",
                        "本步未执行 dish_profit_analysis；全链路接线后由 Adapter 调用",
                        null));
        steps.add(
                PlannerStep.builder()
                        .stepId("step_diagnosis_compose")
                        .stepName("diagnosis_compose_skeleton")
                        .order(5)
                        .targetAgent(AiPlannerExecutorMockGraphCase.MOCK_AGENT_DIAGNOSIS)
                        .targetTool(MOCK_TOOL_DIAGNOSIS_COMPOSE)
                        .inputSummary(
                                "聚合四域 summary → DiagnosisPlan（skeleton mock；Harness mock：仅 MockPlanner 合成 trace；"
                                        + "无 LLM / 无生产诊断；targetAgent 仅为占位名）")
                        .expectedOutput("summary / answerPlanRef 占位")
                        .acceptanceCriteria("mock only；usedTools=mock_diagnosis_compose；非生产诊断")
                        .mockExecutionStatus(PlannerStepMockExecutionStatus.SUCCESS)
                        .answerPlanRef(DIAGNOSIS_ANSWER_PLAN_REF)
                        .build());
        steps.add(
                step(
                        "step_recommendation",
                        "recommendation_three_skeleton",
                        6,
                        AiPlannerExecutorMockGraphCase.MOCK_AGENT_RECOMMENDATION,
                        AiPlannerExecutorMockGraphCase.MOCK_TOOL_RECOMMENDATION,
                        "建议生成（skeleton mock；Harness mock：无真实 Action/通知/调价/下单；targetAgent 仅为占位名）",
                        "RecommendationPlan 占位",
                        "mock only；usedTools=mock_build_recommendation_plan",
                        null));

        return PlannerExecutionPlan.builder()
                .planId(PLAN_ID)
                .planType(PLAN_TYPE)
                .steps(steps)
                .failureStrategy(PlannerFailureStrategy.CONTINUE_WITH_DEGRADED)
                .resolvedContextRef(RESOLVED_CONTEXT_REF)
                .finalAnswerPlanType(FINAL_ANSWER_PLAN_TYPE)
                .build();
    }

    private static PlannerStep step(
            String stepId,
            String stepName,
            int order,
            String targetAgent,
            String targetTool,
            String inputSummary,
            String expectedOutput,
            String acceptanceCriteria,
            String answerPlanRef) {
        var b =
                PlannerStep.builder()
                        .stepId(stepId)
                        .stepName(stepName)
                        .order(order)
                        .targetAgent(targetAgent)
                        .targetTool(targetTool)
                        .inputSummary(inputSummary)
                        .expectedOutput(expectedOutput)
                        .acceptanceCriteria(acceptanceCriteria)
                        .mockExecutionStatus(PlannerStepMockExecutionStatus.SUCCESS);
        if (answerPlanRef != null) {
            b.answerPlanRef(answerPlanRef);
        }
        return b.build();
    }
}
