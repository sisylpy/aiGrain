package com.nongxinle.ai.planner;

/**
 * C-7：第二步「建议」占位步，仍走 {@link MockPlannerStepExecutor}（仅读 {@code mock*}），不接真实 recommendation Agent。
 * <p><strong>Mock recommendation adapter：当前仅作为 Planner 建议步骤占位，不接真实 LLM/建议 Agent。后续若接真实建议 Agent，应新增真实 Adapter 替换，不要在本类内堆业务逻辑。</strong></p>
 */
public final class RecommendationPlannerMockAgentAdapter implements PlannerAgentAdapter {

    public static final String TARGET_AGENT = "recommendation_planner_v1";
    public static final String TARGET_TOOL = "mock_build_recommendation_plan";

    @Override
    public boolean supports(String targetAgent, String targetTool) {
        return TARGET_AGENT.equals(targetAgent) || TARGET_TOOL.equals(targetTool);
    }

    @Override
    public PlannerStepExecutionResponse invoke(PlannerAgentAdapterRequest request) {
        PlannerStepExecutionRequest r =
                PlannerStepExecutionRequest.builder()
                        .step(request.getStep())
                        .effectiveFailureStrategy(request.getEffectiveFailureStrategy())
                        .planId(request.getPlanId())
                        .planType(request.getPlanType())
                        .resolvedQueryContextRef(request.getResolvedQueryContextRef())
                        .answerPlanRef(request.getAnswerPlanRef())
                        .revenueReadRequest(request.getRevenueReadRequest())
                        .revenueExecutionContext(request.getRevenueExecutionContext())
                        .purchaseReadRequest(request.getPurchaseReadRequest())
                        .purchaseExecutionContext(request.getPurchaseExecutionContext())
                        .stockReduceReadRequest(request.getStockReduceReadRequest())
                        .stockReduceExecutionContext(request.getStockReduceExecutionContext())
                        .build();
        return MockPlannerStepExecutor.INSTANCE.execute(r);
    }
}
