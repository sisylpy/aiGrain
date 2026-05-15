package com.nongxinle.ai.agent.business;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 子 Agent 能力声明：供 Registry / Master 调度索引（阶段 A 骨架；不含 NLP）。
 *
 * @see docs/ai/master-business-agent-design.md
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentCapability {

    private String agentName;

    @Builder.Default
    private List<String> supportedIntentCodes = new ArrayList<>();

    @Builder.Default
    private List<String> supportedPathCodes = new ArrayList<>();

    private boolean supportsGroupScope;
    private boolean supportsStoreCompare;
    private boolean supportsMultiTurn;
}
