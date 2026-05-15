package com.nongxinle.ai.planner;

/**
 * C-6 占位：不参与任何 {@code targetAgent}/{@code targetTool} 匹配，不接真实 Agent。
 * <p>可随 {@link PlannerAgentAdapterRegistry} 一起注册以保持 Bean 结构完整；因 {@link #supports} 恒为 false，永不执行 {@link #invoke}。</p>
 */
public final class NoopPlannerAgentAdapter implements PlannerAgentAdapter {

    public static final NoopPlannerAgentAdapter INSTANCE = new NoopPlannerAgentAdapter();

    private NoopPlannerAgentAdapter() {
    }

    @Override
    public boolean supports(String targetAgent, String targetTool) {
        return false;
    }

    @Override
    public PlannerStepExecutionResponse invoke(PlannerAgentAdapterRequest request) {
        throw new UnsupportedOperationException("NoopPlannerAgentAdapter does not invoke");
    }
}
