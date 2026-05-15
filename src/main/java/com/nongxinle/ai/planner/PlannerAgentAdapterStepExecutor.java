package com.nongxinle.ai.planner;

import java.util.Objects;

/**
 * 将 {@link PlannerStepExecutor} 与 {@link PlannerAgentAdapterRegistry} 连接（C-6）：把
 * {@link PlannerStepExecutionRequest} 转为 {@link PlannerAgentAdapterRequest} 并委托注册表。
 * <p>无匹配时注册表返回 {@link PlannerStepStatus#FAILED}，{@link PlannerExecutor} 按 {@link PlannerFailureStrategy} 处理。</p>
 */
public final class PlannerAgentAdapterStepExecutor implements PlannerStepExecutor {

    private final PlannerAgentAdapterRegistry registry;

    public PlannerAgentAdapterStepExecutor(PlannerAgentAdapterRegistry registry) {
        this.registry = Objects.requireNonNull(registry, "registry");
    }

    @Override
    public PlannerStepExecutionResponse execute(PlannerStepExecutionRequest request) {
        Objects.requireNonNull(request, "request");
        PlannerAgentAdapterRequest adapterRequest = PlannerAgentAdapterRequest.fromPlannerStepExecution(request);
        return registry.invoke(adapterRequest);
    }
}
