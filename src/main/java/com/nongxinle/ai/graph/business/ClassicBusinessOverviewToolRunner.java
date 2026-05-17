package com.nongxinle.ai.graph.business;

import com.nongxinle.ai.core.AiRunState;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

/**
 * 打破 {@link BusinessToolExecutionNode} ↔ {@link com.nongxinle.ai.agent.business.BusinessOverviewAgent} 循环依赖：
 * 经典六工具执行委托给 Tool 节点上的 {@link BusinessToolExecutionNode#runClassicBusinessOverviewToolChain(AiRunState)}。
 */
@Component
@Slf4j
public class ClassicBusinessOverviewToolRunner {

    private final BusinessToolExecutionNode businessToolExecutionNode;

    public ClassicBusinessOverviewToolRunner(@Lazy BusinessToolExecutionNode businessToolExecutionNode) {
        this.businessToolExecutionNode = businessToolExecutionNode;
    }

    public void run(AiRunState state) {
        if (state == null) {
            return;
        }
        if (log.isDebugEnabled()) {
            log.debug("[ClassicBusinessOverviewToolRunner] run runId={}", state.getRunId());
        }
        businessToolExecutionNode.runClassicBusinessOverviewToolChain(state);
    }
}
