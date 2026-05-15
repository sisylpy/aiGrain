package com.nongxinle.ai.planner;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 与 {@link PlannerExecutorExecutionMode#MOCK} 语义一致：只读 {@link PlannerStep#getMockExecutionStatus()} 等 {@code mock*} 字段，合成 {@link PlannerStepExecutionResponse}。
 * <p>可在 {@link PlannerExecutorExecutionMode#ADAPTER} 下注入以复验适配路径，不接真实 Agent / Tool。</p>
 */
public final class MockPlannerStepExecutor implements PlannerStepExecutor {

    public static final MockPlannerStepExecutor INSTANCE = new MockPlannerStepExecutor();

    private MockPlannerStepExecutor() {
    }

    @Override
    public PlannerStepExecutionResponse execute(PlannerStepExecutionRequest request) {
        Objects.requireNonNull(request, "request");
        PlannerStep step = Objects.requireNonNull(request.getStep(), "step");
        PlannerFailureStrategy policy =
                request.getEffectiveFailureStrategy() != null
                        ? request.getEffectiveFailureStrategy()
                        : PlannerFailureStrategy.CONTINUE_WITH_DEGRADED;

        PlannerStepMockExecutionStatus mock =
                step.getMockExecutionStatus() != null
                        ? step.getMockExecutionStatus()
                        : PlannerStepMockExecutionStatus.SUCCESS;

        return switch (mock) {
            case SUCCESS -> successResponse(step);
            case SKIPPED -> skippedResponse();
            case DEGRADED -> degradedResponse(step);
            case FAILED -> failedResponse(step, policy);
        };
    }

    private static PlannerStepExecutionResponse successResponse(PlannerStep step) {
        List<String> agents = new ArrayList<>();
        List<String> tools = new ArrayList<>();
        if (step.getTargetAgent() != null && !step.getTargetAgent().isEmpty()) {
            agents.add(step.getTargetAgent());
        }
        if (step.getTargetTool() != null && !step.getTargetTool().isEmpty()) {
            tools.add(step.getTargetTool());
        }
        return PlannerStepExecutionResponse.builder()
                .status(PlannerStepStatus.SUCCESS)
                .errorMessage(null)
                .degradedReason(null)
                .usedAgents(agents)
                .usedTools(tools)
                .build();
    }

    private static PlannerStepExecutionResponse skippedResponse() {
        return PlannerStepExecutionResponse.builder()
                .status(PlannerStepStatus.SKIPPED)
                .errorMessage(null)
                .degradedReason(null)
                .usedAgents(new ArrayList<>())
                .usedTools(new ArrayList<>())
                .build();
    }

    private static PlannerStepExecutionResponse degradedResponse(PlannerStep step) {
        String reason =
                step.getMockDegradedReason() != null && !step.getMockDegradedReason().isEmpty()
                        ? step.getMockDegradedReason()
                        : "mock_degraded";
        return PlannerStepExecutionResponse.builder()
                .status(PlannerStepStatus.DEGRADED)
                .errorMessage(null)
                .degradedReason(reason)
                .usedAgents(new ArrayList<>())
                .usedTools(new ArrayList<>())
                .build();
    }

    private static PlannerStepExecutionResponse failedResponse(PlannerStep step, PlannerFailureStrategy policy) {
        String err =
                step.getMockErrorMessage() != null && !step.getMockErrorMessage().isEmpty()
                        ? step.getMockErrorMessage()
                        : "mock_failure";
        return switch (policy) {
            case CONTINUE_WITH_DEGRADED -> {
                String degReason =
                        step.getMockDegradedReason() != null && !step.getMockDegradedReason().isEmpty()
                                ? step.getMockDegradedReason()
                                : err;
                yield PlannerStepExecutionResponse.builder()
                        .status(PlannerStepStatus.DEGRADED)
                        .errorMessage(null)
                        .degradedReason(degReason)
                        .usedAgents(new ArrayList<>())
                        .usedTools(new ArrayList<>())
                        .build();
            }
            case ASK_CLARIFICATION -> PlannerStepExecutionResponse.builder()
                    .status(PlannerStepStatus.FAILED)
                    .errorMessage(err)
                    .degradedReason(null)
                    .usedAgents(new ArrayList<>())
                    .usedTools(new ArrayList<>())
                    .build();
            case FAIL_FAST -> PlannerStepExecutionResponse.builder()
                    .status(PlannerStepStatus.FAILED)
                    .errorMessage(err)
                    .degradedReason(null)
                    .usedAgents(new ArrayList<>())
                    .usedTools(new ArrayList<>())
                    .build();
        };
    }
}
