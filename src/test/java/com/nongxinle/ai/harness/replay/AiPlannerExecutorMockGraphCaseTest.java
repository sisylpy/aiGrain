package com.nongxinle.ai.harness.replay;

import com.nongxinle.ai.planner.PlannerExecutorResult;
import com.nongxinle.ai.planner.PlannerExecutorTrace;
import com.nongxinle.ai.planner.PlannerStepStatus;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * C-3：mock Graph / Replay 摘要。不依赖 Spring、数据库、真实 Agent。
 */
class AiPlannerExecutorMockGraphCaseTest {

    @Test
    void buildPlan_stepOrderAndSemanticTypes() {
        var plan = AiPlannerExecutorMockGraphCase.buildPlan();
        assertEquals(AiPlannerExecutorMockGraphCase.MOCK_PLAN_ID, plan.getPlanId());
        assertEquals(AiPlannerExecutorMockGraphCase.MOCK_PLAN_TYPE, plan.getPlanType());
        assertEquals(AiPlannerExecutorMockGraphCase.MOCK_FINAL_ANSWER_PLAN_TYPE, plan.getFinalAnswerPlanType());
        List<?> steps = plan.getSteps();
        assertEquals(6, steps.size());
        assertEquals("step_revenue_mtd", plan.getSteps().get(0).getStepId());
        assertEquals(1, plan.getSteps().get(0).getOrder());
        assertEquals("step_recommendation", plan.getSteps().get(5).getStepId());
        assertEquals(6, plan.getSteps().get(5).getOrder());
    }

    @Test
    void mockGraphNode_producesTraceWithRequiredFields() {
        PlannerExecutorResult result = AiPlannerExecutorMockGraphNode.run(new com.nongxinle.ai.planner.PlannerExecutor());
        PlannerExecutorTrace trace = result.getTrace();
        assertNotNull(trace);
        assertNotNull(trace.getPlan());
        assertEquals(6, trace.getPlan().getSteps().size());
        assertEquals(6, trace.getStepResults().size());
        assertEquals(PlannerStepStatus.SUCCESS, trace.getOverallStatus());
        assertTrue(result.isOk());
        assertEquals(AiPlannerExecutorMockGraphCase.MOCK_FINAL_ANSWER_PLAN_TYPE, trace.getFinalAnswerPlanType());
        assertFalse(trace.isClarificationRequested());
        assertNotNull(trace.getAppliedFailureStrategy());
    }

    @Test
    void mockGraphNode_coreCase_allSixStepsSuccess() {
        PlannerExecutorResult result = AiPlannerExecutorMockGraphNode.run(new com.nongxinle.ai.planner.PlannerExecutor());
        for (int i = 0; i < 6; i++) {
            assertEquals(PlannerStepStatus.SUCCESS, result.getTrace().getStepResults().get(i).getStatus());
        }
        assertTrue(result.getTrace().getDegradedSteps().isEmpty());
    }

    @Test
    void mockGraphNode_degradedCase_overallDegradedAndPurchaseInDegradedSteps() {
        PlannerExecutorResult result =
                AiPlannerExecutorMockGraphNode.run(
                        new com.nongxinle.ai.planner.PlannerExecutor(),
                        AiHarnessBuiltinCases.PLANNER_EXECUTOR_MOCK_DEGRADED_CORE);
        assertEquals(PlannerStepStatus.DEGRADED, result.getTrace().getOverallStatus());
        assertTrue(result.isOk());
        assertTrue(result.getTrace().getDegradedSteps().contains("step_purchase_mtd"));
        assertEquals(
                PlannerStepStatus.DEGRADED,
                result.getTrace().getStepResults().get(1).getStatus());
        assertEquals(
                AiPlannerExecutorMockGraphCase.MOCK_PURCHASE_FAILURE_MESSAGE,
                result.getTrace().getStepResults().get(1).getDegradedReason());
    }

    @Test
    void toHarnessSummary_containsPlannerExecutorTraceShape() {
        PlannerExecutorResult result = AiPlannerExecutorMockGraphNode.run(new com.nongxinle.ai.planner.PlannerExecutor());
        Map<String, Object> summary =
                AiPlannerExecutorMockGraphCase.toHarnessSummary(
                        result,
                        AiPlannerExecutorMockGraphCase.EXAMPLE_USER_MESSAGE,
                        9000001L,
                        0L,
                        AiPlannerExecutorMockGraphCase.CASE_ID);
        assertEquals(AiHarnessReplayMode.PLANNER_EXECUTOR_MOCK.name(), summary.get("harnessReplayMode"));
        assertEquals(AiPlannerExecutorMockGraphCase.CASE_ID, summary.get("harnessMockGraphCaseId"));
        @SuppressWarnings("unchecked")
        Map<String, Object> pxt = (Map<String, Object>) summary.get("plannerExecutorTrace");
        assertNotNull(pxt);
        assertEquals(AiPlannerExecutorMockGraphCase.MOCK_PLAN_ID, ((Map<?, ?>) pxt.get("plan")).get("planId"));
        assertEquals(AiPlannerExecutorMockGraphCase.MOCK_PLAN_TYPE, ((Map<?, ?>) pxt.get("plan")).get("planType"));
        assertEquals(PlannerStepStatus.SUCCESS.name(), pxt.get("overallStatus"));
        @SuppressWarnings("unchecked")
        List<String> deg = (List<String>) pxt.get("degradedSteps");
        assertTrue(deg.isEmpty());
        assertEquals(AiPlannerExecutorMockGraphCase.MOCK_FINAL_ANSWER_PLAN_TYPE, pxt.get("finalAnswerPlanType"));
        Map<?, ?> planMap = (Map<?, ?>) pxt.get("plan");
        assertNotNull(planMap);
        assertNotNull(planMap.get("steps"));
        assertEquals(6, ((List<?>) pxt.get("steps")).size());
        List<?> sr = (List<?>) pxt.get("stepResults");
        assertEquals(6, sr.size());
    }

    @Test
    void harnessReplay_degradedCase_traceListsPurchaseStepId() {
        AiHarnessReplayRequest req = new AiHarnessReplayRequest();
        req.setUserId(1L);
        req.setCaseId(AiHarnessBuiltinCases.PLANNER_EXECUTOR_MOCK_DEGRADED_CORE);
        req.setFrozenClockDate("2026-05-11");
        req.getMessages().add(AiPlannerExecutorMockGraphCase.EXAMPLE_USER_MESSAGE);

        AiHarnessReplayResponse resp = AiHarnessReplayPlannerExecutorMock.replay(req);
        assertEquals(Boolean.TRUE, resp.getOverallPass());
        @SuppressWarnings("unchecked")
        Map<String, Object> pxt =
                (Map<String, Object>)
                        resp.getRounds()
                                .get(0)
                                .getResolvedQueryContextSummary()
                                .get("plannerExecutorTrace");
        assertNotNull(pxt);
        assertEquals(PlannerStepStatus.DEGRADED.name(), pxt.get("overallStatus"));
        @SuppressWarnings("unchecked")
        List<String> deg = (List<String>) pxt.get("degradedSteps");
        assertTrue(deg.contains("step_purchase_mtd"));
    }

    @Test
    void harnessReplayPlannerExecutorMock_dbFreeResponse() {
        AiHarnessReplayRequest req = new AiHarnessReplayRequest();
        req.setUserId(1L);
        req.setCaseId(AiHarnessBuiltinCases.PLANNER_EXECUTOR_MOCK_CORE);
        req.setFrozenClockDate("2026-05-11");
        req.getMessages().add(AiPlannerExecutorMockGraphCase.EXAMPLE_USER_MESSAGE);

        AiHarnessReplayResponse resp = AiHarnessReplayPlannerExecutorMock.replay(req);
        assertEquals(Boolean.TRUE, resp.getOverallPass());
        assertEquals(0L, resp.getConversationId());
        assertEquals(1, resp.getRounds().size());
        assertTrue(resp.getRounds().get(0).getResolvedQueryContextSummary().containsKey("plannerExecutorTrace"));
    }
}
