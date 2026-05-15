package com.nongxinle.ai.planner;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * C-2 / C-4：验证 skeleton 遍历与每步 mock 语义，不接 Graph / Agent / Tool。
 */
class PlannerExecutorSkeletonTest {

    @Test
    void execute_ordersSteps_respectsPerStepMockStatus() {
        PlannerExecutionPlan plan = PlannerExecutionPlan.builder()
                .planId("plan-1")
                .planType("MOCK_TEMPLATE")
                .failureStrategy(PlannerFailureStrategy.FAIL_FAST)
                .resolvedContextRef("ctx-hash")
                .steps(List.of(
                        PlannerStep.builder()
                                .stepId("s2")
                                .stepName("second")
                                .order(2)
                                .targetAgent("RevenueAgent")
                                .targetTool("revenue_overview")
                                .inputSummary("mock")
                                .expectedOutput("mock")
                                .acceptanceCriteria("mock")
                                .mockExecutionStatus(PlannerStepMockExecutionStatus.SKIPPED)
                                .build(),
                        PlannerStep.builder()
                                .stepId("s1")
                                .stepName("first")
                                .order(1)
                                .targetAgent("PurchaseAgent")
                                .targetTool("purchase_overview")
                                .build()
                ))
                .build();

        PlannerExecutorResult out = new PlannerExecutor().execute(plan);

        assertTrue(out.isOk());
        assertNotNull(out.getTrace());
        assertEquals(PlannerStepStatus.SUCCESS, out.getTrace().getOverallStatus());
        assertEquals(PlannerFailureStrategy.FAIL_FAST, out.getTrace().getAppliedFailureStrategy());
        assertEquals("plan-1", out.getTrace().getPlan().getPlanId());

        List<PlannerStepResult> results = out.getTrace().getStepResults();
        assertEquals(2, results.size());
        // order 1 first: index 0 -> SUCCESS
        assertEquals("s1", results.get(0).getStepId());
        assertEquals(PlannerStepStatus.SUCCESS, results.get(0).getStatus());
        assertTrue(results.get(0).getUsedAgents().contains("PurchaseAgent"));
        // order 2 second: explicit mock = SKIPPED
        assertEquals("s2", results.get(1).getStepId());
        assertEquals(PlannerStepStatus.SKIPPED, results.get(1).getStatus());
        assertTrue(results.get(1).getUsedAgents().isEmpty());
    }

    @Test
    void execute_failedWithFailFast_skipsFollowingSteps() {
        PlannerExecutionPlan plan = PlannerExecutionPlan.builder()
                .planId("ff")
                .planType("T")
                .failureStrategy(PlannerFailureStrategy.FAIL_FAST)
                .steps(List.of(
                        PlannerStep.builder()
                                .stepId("a")
                                .order(1)
                                .mockExecutionStatus(PlannerStepMockExecutionStatus.FAILED)
                                .mockErrorMessage("boom")
                                .build(),
                        PlannerStep.builder()
                                .stepId("b")
                                .order(2)
                                .mockExecutionStatus(PlannerStepMockExecutionStatus.SUCCESS)
                                .targetAgent("X")
                                .build()))
                .build();
        PlannerExecutorResult out = new PlannerExecutor().execute(plan);
        assertFalse(out.isOk());
        assertEquals(PlannerStepStatus.FAILED, out.getTrace().getOverallStatus());
        assertEquals(PlannerStepStatus.FAILED, out.getTrace().getStepResults().get(0).getStatus());
        assertEquals(PlannerStepStatus.SKIPPED, out.getTrace().getStepResults().get(1).getStatus());
        assertTrue(out.getTrace().getDegradedSteps().isEmpty());
    }

    @Test
    void execute_failedWithAskClarification_setsFlagAndSkipsRest() {
        PlannerExecutionPlan plan = PlannerExecutionPlan.builder()
                .planId("ask")
                .planType("T")
                .failureStrategy(PlannerFailureStrategy.ASK_CLARIFICATION)
                .steps(List.of(
                        PlannerStep.builder()
                                .stepId("a")
                                .order(1)
                                .mockExecutionStatus(PlannerStepMockExecutionStatus.FAILED)
                                .mockErrorMessage("need_input")
                                .build(),
                        PlannerStep.builder().stepId("b").order(2).build()))
                .build();
        PlannerExecutorResult out = new PlannerExecutor().execute(plan);
        assertFalse(out.isOk());
        assertTrue(out.getTrace().isClarificationRequested());
        assertEquals(PlannerStepStatus.FAILED, out.getTrace().getOverallStatus());
        assertEquals(PlannerStepStatus.SKIPPED, out.getTrace().getStepResults().get(1).getStatus());
    }

    @Test
    void execute_failedWithContinueWithDegraded_marksStepDegradedAndOverallDegraded() {
        PlannerExecutionPlan plan = PlannerExecutionPlan.builder()
                .planId("deg")
                .planType("T")
                .failureStrategy(PlannerFailureStrategy.CONTINUE_WITH_DEGRADED)
                .steps(List.of(
                        PlannerStep.builder()
                                .stepId("bad")
                                .order(1)
                                .mockExecutionStatus(PlannerStepMockExecutionStatus.FAILED)
                                .mockErrorMessage("soft_fail")
                                .build(),
                        PlannerStep.builder()
                                .stepId("ok")
                                .order(2)
                                .targetAgent("A")
                                .build()))
                .build();
        PlannerExecutorResult out = new PlannerExecutor().execute(plan);
        assertTrue(out.isOk());
        assertEquals(PlannerStepStatus.DEGRADED, out.getTrace().getOverallStatus());
        assertEquals(PlannerStepStatus.DEGRADED, out.getTrace().getStepResults().get(0).getStatus());
        assertEquals("soft_fail", out.getTrace().getStepResults().get(0).getDegradedReason());
        assertTrue(out.getTrace().getDegradedSteps().contains("bad"));
        assertEquals(PlannerStepStatus.SUCCESS, out.getTrace().getStepResults().get(1).getStatus());
    }

    @Test
    void execute_emptyPlan() {
        PlannerExecutorResult out = new PlannerExecutor().execute(
                PlannerExecutionPlan.builder().planId("empty").planType("EMPTY").steps(List.of()).build());
        assertTrue(out.isOk());
        assertTrue(out.getTrace().getStepResults().isEmpty());
    }
}
