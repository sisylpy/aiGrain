package com.nongxinle.ai.agent.business;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 调度计划中的一步（阶段 A 骨架）。
 *
 * @see docs/ai/master-business-agent-design.md
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BusinessAgentDispatchStep {

    private String agentName;
    private boolean required;
    private long timeoutMs;
    private AgentFailurePolicy failurePolicy;
    private int order;
}
