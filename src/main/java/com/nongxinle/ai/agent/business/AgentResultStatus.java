package com.nongxinle.ai.agent.business;

/**
 * 子 Agent 执行结果状态（阶段 A 骨架）。
 *
 * @see docs/ai/master-business-agent-design.md
 */
public enum AgentResultStatus {

    SUCCESS,
    NO_DATA,
    PARTIAL_SUCCESS,
    FAILED,
    SKIPPED,
    DEGRADED,
    PERMISSION_DENIED,
    NEED_CLARIFICATION
}
