package com.nongxinle.ai.agent.business;

/**
 * 经营域子 Agent 契约（阶段 A 骨架；未接入 Graph）。
 * <p>
 * 实现类禁止解析用户原文语义；仅消费 {@link BusinessAgentRequest} 中已解析上下文。
 *
 * @see docs/ai/master-business-agent-design.md
 */
public interface BusinessSubAgent {

    String agentName();

    AgentCapability capability();

    boolean supports(BusinessAgentRequest request);

    AgentResultEnvelope execute(BusinessAgentRequest request);
}
