package com.nongxinle.ai.agent.business;

/**
 * 单步子 Agent 失败时的策略占位（阶段 A 骨架）。
 *
 * @see docs/ai/master-business-agent-design.md
 */
public enum AgentFailurePolicy {

    FAIL_FAST,
    DEGRADE,
    SKIP,
    NEED_CLARIFICATION,
    FALLBACK_TO_SINGLE_AGENT
}
