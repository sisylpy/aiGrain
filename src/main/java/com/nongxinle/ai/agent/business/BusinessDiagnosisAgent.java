package com.nongxinle.ai.agent.business;

import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 经营诊断 / 四域 Harness 的注册名与子 Agent 身份；实际四域批处理由
 * {@link MasterBusinessAgent#tryOrchestrateBusinessOverviewMultiAgent} 触发（不向 Graph 注册重复的 AgentNode）。
 */
@Component
public class BusinessDiagnosisAgent implements BusinessSubAgent {

    @Override
    public String agentName() {
        return BusinessAgentNames.BUSINESS_DIAGNOSIS;
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
                "BUSINESS_DIAGNOSIS harness：请使用 MasterBusinessAgent.tryOrchestrateBusinessOverviewMultiAgent");
    }
}
