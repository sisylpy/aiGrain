package com.nongxinle.ai.agent.business;

import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 成本洞察（五工具连跑）链路的注册身份。
 * 工具仍由 Planner + {@code BusinessToolExecutionNode} 执行；结构化诊断与 AnswerPlan 由
 * {@link com.nongxinle.ai.graph.business.CostDiagnosisAgentNode#applyIfApplicable} 在 OutcomeReview 挂载。
 */
@Component
public class CostInsightAgent implements BusinessSubAgent {

    @Override
    public String agentName() {
        return BusinessAgentNames.COST_INSIGHT;
    }

    @Override
    public AgentCapability capability() {
        return AgentCapability.builder()
                .agentName(agentName())
                .supportedIntentCodes(List.of())
                .supportedPathCodes(List.of())
                .supportsGroupScope(true)
                .supportsStoreCompare(true)
                .supportsMultiTurn(true)
                .build();
    }

    @Override
    public boolean supports(BusinessAgentRequest request) {
        return false;
    }

    @Override
    public AgentResultEnvelope execute(BusinessAgentRequest request) {
        throw new UnsupportedOperationException(
                "成本洞察链：工具由 Planner+ToolExecution 执行；诊断结构见 CostDiagnosisAgentNode.applyIfApplicable");
    }
}
