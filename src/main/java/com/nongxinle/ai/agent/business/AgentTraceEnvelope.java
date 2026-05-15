package com.nongxinle.ai.agent.business;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * 一轮编排的 Trace 快照（阶段 A 骨架；用于后续 Replay 扩展）。
 *
 * @see docs/ai/master-business-agent-design.md
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentTraceEnvelope {

    private Long runId;
    private Long conversationId;

    /** 结构化语义摘要（可读字符串或后续改为强类型摘要）。 */
    private String semanticSummary;
    /** ResolvedQueryContext 确定性摘要。 */
    private String resolvedContextSummary;

    private BusinessAgentDispatchPlan dispatchPlan;

    @Builder.Default
    private List<AgentResultEnvelope> agentResults = new ArrayList<>();

    private Instant startedAt;
    private Instant finishedAt;
}
