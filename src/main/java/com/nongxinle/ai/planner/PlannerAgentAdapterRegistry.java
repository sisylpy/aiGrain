package com.nongxinle.ai.planner;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 按 {@link PlannerStep#getTargetAgent()} / {@link PlannerStep#getTargetTool()} 解析 {@link PlannerAgentAdapter}（C-6）。
 * <p>不设用户原文路由；匹配键仅来自 {@link PlannerStep} 与计划元数据。</p>
 */
public final class PlannerAgentAdapterRegistry {

    private final List<PlannerAgentAdapter> adapters;

    public PlannerAgentAdapterRegistry(List<PlannerAgentAdapter> adapters) {
        this.adapters = adapters == null ? List.of() : List.copyOf(adapters);
    }

    /**
     * 依次尝试 {@link PlannerAgentAdapter#supports(String, String)}，命中则 {@link PlannerAgentAdapter#invoke}。
     * 无匹配时返回 {@link PlannerStepStatus#FAILED}，由 {@link PlannerExecutor} 按 {@link PlannerFailureStrategy} 吸收或中断。
     */
    public PlannerStepExecutionResponse invoke(PlannerAgentAdapterRequest request) {
        Objects.requireNonNull(request, "request");
        PlannerStep step = request.getStep();
        String agent = step != null ? trimToNull(step.getTargetAgent()) : null;
        String tool = step != null ? trimToNull(step.getTargetTool()) : null;

        for (PlannerAgentAdapter a : adapters) {
            if (a != null && a.supports(agent, tool)) {
                PlannerStepExecutionResponse out = a.invoke(request);
                return Objects.requireNonNull(out, "PlannerAgentAdapter returned null");
            }
        }
        return missingAdapterResponse(agent, tool);
    }

    private static PlannerStepExecutionResponse missingAdapterResponse(String agent, String tool) {
        String key = (agent != null ? agent : "?") + ":" + (tool != null ? tool : "?");
        return PlannerStepExecutionResponse.builder()
                .status(PlannerStepStatus.FAILED)
                .errorMessage("planner_agent_adapter_not_registered:" + key)
                .degradedReason(null)
                .usedAgents(new ArrayList<>())
                .usedTools(new ArrayList<>())
                .build();
    }

    private static String trimToNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
