package com.nongxinle.ai.harness.replay;

import com.nongxinle.ai.agent.business.BusinessAgentNames;
import com.nongxinle.ai.planner.PlannerExecutionPlan;
import com.nongxinle.ai.planner.PlannerExecutorResult;
import com.nongxinle.ai.planner.PlannerExecutorTrace;
import com.nongxinle.ai.planner.PlannerFailureStrategy;
import com.nongxinle.ai.planner.PlannerStep;
import com.nongxinle.ai.planner.PlannerStepMockExecutionStatus;
import com.nongxinle.ai.planner.PlannerStepResult;
import com.nongxinle.ai.tool.business.AiBusinessToolIds;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * PlannerExecutor {@link AiHarnessBuiltinCases#PLANNER_EXECUTOR_MOCK_CORE}：固定多步计划 + mock 执行结果，
 * 不调用 Resolver / Master / 真实 Agent / Tool / SQL。
 *
 * @see AiHarnessReplayPlannerExecutorMock
 */
public final class AiPlannerExecutorMockGraphCase {

    /** Harness caseId（与文档、Replay API 对齐）。 */
    public static final String CASE_ID = AiHarnessBuiltinCases.PLANNER_EXECUTOR_MOCK_CORE;

    /** 采购步 mock 失败 + CONTINUE_WITH_DEGRADED。 */
    public static final String CASE_ID_DEGRADED = AiHarnessBuiltinCases.PLANNER_EXECUTOR_MOCK_DEGRADED_CORE;

    /**
     * 文档示例原话（case 不基于原文做 contains/regex 路由；仅作文档与单测输入示例）。
     */
    public static final String EXAMPLE_USER_MESSAGE =
            "帮我分析 AAA 这个月成本为什么偏高，并给我三条改进建议";

    public static final String MOCK_PLAN_ID = "plan-mock-cost-aaa-mtd-v1";
    public static final String MOCK_PLAN_TYPE = "PLANNER_EXECUTOR_MOCK_COST_DIAGNOSIS_V1";
    public static final String MOCK_FINAL_ANSWER_PLAN_TYPE = "MOCK_RECOMMENDATION_PLAN_V1";
    public static final String MOCK_RESOLVED_CONTEXT_REF = "MOCK_RESOLVED_CTX_REF_AAA_MONTH_TO_DATE";

    public static final String MOCK_DEGRADED_PLAN_ID = "plan-mock-degraded-purchase-failure-v1";
    public static final String MOCK_DEGRADED_PLAN_TYPE = "PLANNER_EXECUTOR_MOCK_DEGRADED_PURCHASE_V1";
    public static final String MOCK_DEGRADED_FINAL_ANSWER_PLAN_TYPE = "MOCK_RECOMMENDATION_PLAN_DEGRADED_V1";
    public static final String MOCK_PURCHASE_FAILURE_MESSAGE = "mock_purchase_overview_tool_failure";

    public static final String MOCK_AGENT_DIAGNOSIS = "business_diagnosis_v1";
    public static final String MOCK_TOOL_DIAGNOSIS_AGGREGATE = "mock_aggregate_diagnosis_plan";
    public static final String MOCK_AGENT_RECOMMENDATION = "recommendation_planner_v1";
    public static final String MOCK_TOOL_RECOMMENDATION = "mock_build_recommendation_plan";

    private AiPlannerExecutorMockGraphCase() {
    }

    public static PlannerExecutionPlan planForHarnessCase(String harnessCaseId) {
        String id = harnessCaseId != null ? harnessCaseId.trim() : "";
        if (AiHarnessBuiltinCases.PLANNER_EXECUTOR_MOCK_DEGRADED_CORE.equals(id)) {
            return buildDegradedPlan();
        }
        return buildPlan();
    }

    /**
     * 固定 6 步且均为 {@link PlannerStepMockExecutionStatus#SUCCESS}。
     */
    public static PlannerExecutionPlan buildPlan() {
        List<PlannerStep> steps = new ArrayList<>();
        steps.add(step(
                "step_revenue_mtd",
                "revenue_month_to_date",
                1,
                BusinessAgentNames.REVENUE_OVERVIEW,
                AiBusinessToolIds.REVENUE_QUERY,
                "AAA 本月营业额（mock）",
                "DailyRevenueAnswerPlan 占位",
                "Tool 返回成功或显式无数据",
                PlannerStepMockExecutionStatus.SUCCESS));
        steps.add(step(
                "step_purchase_mtd",
                "purchase_month_to_date",
                2,
                BusinessAgentNames.PURCHASE_OVERVIEW,
                AiBusinessToolIds.PURCHASE_OVERVIEW,
                "AAA 本月采购（mock）",
                "PurchaseAnswerPlan 占位",
                "采购结构化事实齐全",
                PlannerStepMockExecutionStatus.SUCCESS));
        steps.add(step(
                "step_stock_reduce_mtd",
                "stock_reduce_month_to_date",
                3,
                BusinessAgentNames.STOCK_REDUCE_QUERY,
                AiBusinessToolIds.STOCK_REDUCE_QUERY,
                "AAA 本月出库/核销（mock）",
                "StockReduceAnswerPlan 占位",
                "出库分型口径与 Harness 一致",
                PlannerStepMockExecutionStatus.SUCCESS));
        steps.add(step(
                "step_dish_profit_mtd",
                "dish_profit_month_to_date",
                4,
                BusinessAgentNames.DISH_PROFIT_ANALYSIS,
                AiBusinessToolIds.DISH_PROFIT_ANALYSIS,
                "AAA 本月菜品毛利（mock）",
                "DishProfitAnswerPlan 占位",
                "毛利字段来自 insight，非 Composer 心算",
                PlannerStepMockExecutionStatus.SUCCESS));
        steps.add(step(
                "step_diagnosis",
                "diagnosis_aggregate",
                5,
                MOCK_AGENT_DIAGNOSIS,
                MOCK_TOOL_DIAGNOSIS_AGGREGATE,
                "聚合四域 AnswerPlan → DiagnosisPlan（mock）",
                "DiagnosisPlan 占位",
                "仅读子计划，不回扫 tool dump",
                PlannerStepMockExecutionStatus.SUCCESS));
        steps.add(step(
                "step_recommendation",
                "recommendation_three",
                6,
                MOCK_AGENT_RECOMMENDATION,
                MOCK_TOOL_RECOMMENDATION,
                "生成不超过三条可解释建议（mock）",
                "RecommendationPlan 占位",
                "每条绑定证据 stepId",
                PlannerStepMockExecutionStatus.SUCCESS));

        return PlannerExecutionPlan.builder()
                .planId(MOCK_PLAN_ID)
                .planType(MOCK_PLAN_TYPE)
                .steps(steps)
                .failureStrategy(PlannerFailureStrategy.CONTINUE_WITH_DEGRADED)
                .resolvedContextRef(MOCK_RESOLVED_CONTEXT_REF)
                .finalAnswerPlanType(MOCK_FINAL_ANSWER_PLAN_TYPE)
                .build();
    }

    /**
     * 采购步 {@link PlannerStepMockExecutionStatus#FAILED}，计划 {@link PlannerFailureStrategy#CONTINUE_WITH_DEGRADED}：
     * 结果中为 DEGRADED，{@code degradedSteps} 含 {@code step_purchase_mtd}。
     */
    public static PlannerExecutionPlan buildDegradedPlan() {
        List<PlannerStep> steps = new ArrayList<>();
        steps.add(step(
                "step_revenue_mtd",
                "revenue_month_to_date",
                1,
                BusinessAgentNames.REVENUE_OVERVIEW,
                AiBusinessToolIds.REVENUE_QUERY,
                "AAA 本月营业额（mock）",
                "DailyRevenueAnswerPlan 占位",
                "Tool 返回成功或显式无数据",
                PlannerStepMockExecutionStatus.SUCCESS));
        steps.add(PlannerStep.builder()
                .stepId("step_purchase_mtd")
                .stepName("purchase_month_to_date")
                .order(2)
                .targetAgent(BusinessAgentNames.PURCHASE_OVERVIEW)
                .targetTool(AiBusinessToolIds.PURCHASE_OVERVIEW)
                .inputSummary("AAA 本月采购（mock）")
                .expectedOutput("PurchaseAnswerPlan 占位")
                .acceptanceCriteria("采购结构化事实齐全")
                .mockExecutionStatus(PlannerStepMockExecutionStatus.FAILED)
                .mockErrorMessage(MOCK_PURCHASE_FAILURE_MESSAGE)
                .build());
        steps.add(step(
                "step_stock_reduce_mtd",
                "stock_reduce_month_to_date",
                3,
                BusinessAgentNames.STOCK_REDUCE_QUERY,
                AiBusinessToolIds.STOCK_REDUCE_QUERY,
                "AAA 本月出库/核销（mock）",
                "StockReduceAnswerPlan 占位",
                "出库分型口径与 Harness 一致",
                PlannerStepMockExecutionStatus.SUCCESS));
        steps.add(step(
                "step_dish_profit_mtd",
                "dish_profit_month_to_date",
                4,
                BusinessAgentNames.DISH_PROFIT_ANALYSIS,
                AiBusinessToolIds.DISH_PROFIT_ANALYSIS,
                "AAA 本月菜品毛利（mock）",
                "DishProfitAnswerPlan 占位",
                "毛利字段来自 insight，非 Composer 心算",
                PlannerStepMockExecutionStatus.SUCCESS));
        steps.add(step(
                "step_diagnosis",
                "diagnosis_aggregate",
                5,
                MOCK_AGENT_DIAGNOSIS,
                MOCK_TOOL_DIAGNOSIS_AGGREGATE,
                "聚合四域 AnswerPlan → DiagnosisPlan（mock）",
                "DiagnosisPlan 占位",
                "仅读子计划，不回扫 tool dump",
                PlannerStepMockExecutionStatus.SUCCESS));
        steps.add(step(
                "step_recommendation",
                "recommendation_three",
                6,
                MOCK_AGENT_RECOMMENDATION,
                MOCK_TOOL_RECOMMENDATION,
                "生成不超过三条可解释建议（mock）",
                "RecommendationPlan 占位",
                "每条绑定证据 stepId",
                PlannerStepMockExecutionStatus.SUCCESS));

        return PlannerExecutionPlan.builder()
                .planId(MOCK_DEGRADED_PLAN_ID)
                .planType(MOCK_DEGRADED_PLAN_TYPE)
                .steps(steps)
                .failureStrategy(PlannerFailureStrategy.CONTINUE_WITH_DEGRADED)
                .resolvedContextRef(MOCK_RESOLVED_CONTEXT_REF)
                .finalAnswerPlanType(MOCK_DEGRADED_FINAL_ANSWER_PLAN_TYPE)
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
            PlannerStepMockExecutionStatus mockExecutionStatus) {
        return PlannerStep.builder()
                .stepId(stepId)
                .stepName(stepName)
                .order(order)
                .targetAgent(targetAgent)
                .targetTool(targetTool)
                .inputSummary(inputSummary)
                .expectedOutput(expectedOutput)
                .acceptanceCriteria(acceptanceCriteria)
                .mockExecutionStatus(mockExecutionStatus)
                .build();
    }

    /**
     * 与 {@link AiHarnessResolvedContextSummarizer} 同级的调试 Map：根节点含 {@code plannerExecutorTrace}，
     * 并附 {@code harnessPlan*} / {@code harnessPlanner*} 浅表字段，便于 Replay 面板区分「case / 计划类型 / 执行汇总」而无需深钻嵌套。
     */
    public static Map<String, Object> toHarnessSummary(
            PlannerExecutorResult result,
            String replayMessage,
            long runId,
            long conversationId,
            String harnessCaseId) {
        LinkedHashMap<String, Object> root = new LinkedHashMap<>();
        root.put("conversationId", conversationId);
        root.put("runId", runId);
        root.put("harnessReplayMode", AiHarnessReplayMode.PLANNER_EXECUTOR_MOCK.name());
        root.put("harnessMockGraphCaseId", harnessCaseId != null ? harnessCaseId.trim() : CASE_ID);
        root.put("harnessReplayInputMessage", replayMessage);
        if (result != null && result.getTrace() != null) {
            PlannerExecutorTrace tr = result.getTrace();
            root.put("plannerExecutorTrace", traceToMap(tr));
            putHarnessReplayShallowFields(root, tr);
        } else {
            root.put("plannerExecutorTrace", null);
            putHarnessReplayShallowFields(root, null);
        }
        return root;
    }

    /**
     * 与 {@link #planToMap} 中 {@code plan.planType}、{@code plan.finalAnswerPlanType} 及 {@link #traceToMap} 中
     * {@code overallStatus}、{@code degradedSteps} 对应；仅用于 Harness 可读性。
     */
    private static void putHarnessReplayShallowFields(
            LinkedHashMap<String, Object> root, PlannerExecutorTrace tr) {
        if (tr == null) {
            root.put("harnessPlanType", null);
            root.put("harnessPlanFinalAnswerPlanType", null);
            root.put("harnessPlannerOverallStatus", null);
            root.put("harnessPlannerDegradedSteps", new ArrayList<String>());
            return;
        }
        PlannerExecutionPlan p = tr.getPlan();
        root.put("harnessPlanType", p != null ? p.getPlanType() : null);
        root.put("harnessPlanFinalAnswerPlanType", p != null ? p.getFinalAnswerPlanType() : null);
        root.put("harnessPlannerOverallStatus", enumName(tr.getOverallStatus()));
        root.put("harnessPlannerDegradedSteps", copyStrList(tr.getDegradedSteps()));
    }

    @Deprecated
    public static Map<String, Object> toHarnessSummary(
            PlannerExecutorResult result, String replayMessage, long runId, long conversationId) {
        return toHarnessSummary(result, replayMessage, runId, conversationId, CASE_ID);
    }

    private static Map<String, Object> traceToMap(PlannerExecutorTrace trace) {
        LinkedHashMap<String, Object> m = new LinkedHashMap<>();
        m.put("appliedFailureStrategy", enumName(trace.getAppliedFailureStrategy()));
        m.put("overallStatus", enumName(trace.getOverallStatus()));
        m.put("finalAnswerPlanType", trace.getFinalAnswerPlanType());
        m.put("clarificationRequested", trace.isClarificationRequested());
        m.put("degradedSteps", copyStrList(trace.getDegradedSteps()));
        m.put("usedAgents", copyStrList(trace.getUsedAgents()));
        m.put("usedTools", copyStrList(trace.getUsedTools()));
        Map<String, Object> planMap = planToMap(trace.getPlan());
        m.put("plan", planMap);
        if (planMap != null && planMap.get("steps") != null) {
            m.put("steps", planMap.get("steps"));
        }
        m.put("stepResults", stepResultsToList(trace.getStepResults()));
        return m;
    }

    private static Map<String, Object> planToMap(PlannerExecutionPlan plan) {
        if (plan == null) {
            return null;
        }
        LinkedHashMap<String, Object> p = new LinkedHashMap<>();
        p.put("planId", plan.getPlanId());
        p.put("planType", plan.getPlanType());
        p.put("failureStrategy", enumName(plan.getFailureStrategy()));
        p.put("resolvedContextRef", plan.getResolvedContextRef());
        p.put("finalAnswerPlanType", plan.getFinalAnswerPlanType());
        List<Map<String, Object>> stepMaps = new ArrayList<>();
        if (plan.getSteps() != null) {
            for (PlannerStep s : plan.getSteps()) {
                stepMaps.add(stepToMap(s));
            }
        }
        p.put("steps", stepMaps);
        return p;
    }

    private static Map<String, Object> stepToMap(PlannerStep s) {
        LinkedHashMap<String, Object> x = new LinkedHashMap<>();
        x.put("stepId", s.getStepId());
        x.put("stepName", s.getStepName());
        x.put("order", s.getOrder());
        x.put("targetAgent", s.getTargetAgent());
        x.put("targetTool", s.getTargetTool());
        x.put("inputSummary", s.getInputSummary());
        x.put("expectedOutput", s.getExpectedOutput());
        x.put("acceptanceCriteria", s.getAcceptanceCriteria());
        x.put("failureStrategy", enumName(s.getFailureStrategy()));
        x.put("mockExecutionStatus", enumName(s.getMockExecutionStatus()));
        x.put("mockDegradedReason", s.getMockDegradedReason());
        x.put("mockErrorMessage", s.getMockErrorMessage());
        x.put("answerPlanRef", s.getAnswerPlanRef());
        return x;
    }

    private static List<Map<String, Object>> stepResultsToList(List<PlannerStepResult> results) {
        List<Map<String, Object>> list = new ArrayList<>();
        if (results == null) {
            return list;
        }
        for (PlannerStepResult r : results) {
            LinkedHashMap<String, Object> x = new LinkedHashMap<>();
            x.put("stepId", r.getStepId());
            x.put("status", enumName(r.getStatus()));
            x.put("errorMessage", r.getErrorMessage());
            x.put("degradedReason", r.getDegradedReason());
            x.put("usedAgents", copyStrList(r.getUsedAgents()));
            x.put("usedTools", copyStrList(r.getUsedTools()));
            list.add(x);
        }
        return list;
    }

    private static List<String> copyStrList(List<String> in) {
        return in == null || in.isEmpty() ? new ArrayList<>() : new ArrayList<>(in);
    }

    private static String enumName(Enum<?> e) {
        return e == null ? null : e.name();
    }
}
