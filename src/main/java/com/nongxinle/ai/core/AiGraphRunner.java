package com.nongxinle.ai.core;

import com.nongxinle.ai.trace.AiAgentTraceService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 线性执行 {@link AgentNode} 列表；并行/分支后续扩展。
 */
@Slf4j
@Component
public class AiGraphRunner {

    private final List<AgentNode> businessNodes;
    private final AiAgentTraceService agentTraceService;

    public AiGraphRunner(@Qualifier("businessAgentNodes") List<AgentNode> businessNodes,
                         AiAgentTraceService agentTraceService) {
        this.businessNodes = businessNodes;
        this.agentTraceService = agentTraceService;
    }

    public AiRunState runBusinessGraph(AiRunState state) {
        Long runId = state.getRunId();
        AiRunState cur = state;
        int stepOrder = 0;
        for (AgentNode node : businessNodes) {
            if (cur.isCancelled()) {
                log.info("[AiGraphRunner] run cancelled before node={} runId={}", node.name(), runId);
                break;
            }
            if (!node.shouldRun(cur)) {
                continue;
            }
            stepOrder++;
            long t0 = System.currentTimeMillis();
            var inputSnap = AiAgentTraceService.summarizeStateBefore(cur);
            try {
                log.debug("[AiGraphRunner] runId={} node={} start", runId, node.name());
                cur = node.run(cur);
                long ms = System.currentTimeMillis() - t0;
                var outSnap = AiAgentTraceService.summarizeStateAfter(cur, node.name());
                if (runId != null) {
                    agentTraceService.insertStep(runId, stepOrder, "AGENT_NODE", node.name(),
                            inputSnap, outSnap, "SUCCESS", (int) Math.min(ms, Integer.MAX_VALUE), null);
                }
                log.debug("[AiGraphRunner] runId={} node={} end", runId, node.name());
                if (cur.isHarnessToolRequestOnly() && "ToolExecution".equals(node.name())) {
                    log.debug("[AiGraphRunner] runId={} harness TOOL_REQUEST_ONLY stop after {}", runId, node.name());
                    break;
                }
            } catch (Exception ex) {
                long ms = System.currentTimeMillis() - t0;
                if (runId != null) {
                    agentTraceService.insertStep(runId, stepOrder, "AGENT_NODE", node.name(),
                            inputSnap, AiAgentTraceService.summarizeStateAfter(cur, node.name()),
                            "FAILED", (int) Math.min(ms, Integer.MAX_VALUE), ex.getMessage());
                }
                throw ex;
            }
        }
        return cur;
    }
}
