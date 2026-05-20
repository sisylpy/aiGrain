package com.nongxinle.ai.config;

import com.nongxinle.ai.core.AgentNode;
import com.nongxinle.ai.graph.business.BusinessDataPlannerNode;
import com.nongxinle.ai.graph.business.BusinessScopeIntersectNode;
import com.nongxinle.ai.graph.business.BusinessTimeWindowNode;
import com.nongxinle.ai.graph.business.BusinessToolExecutionNode;
import com.nongxinle.ai.graph.business.StubAnswerComposerNode;
import com.nongxinle.ai.graph.business.StubOutcomeReviewNode;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class AiBusinessGraphConfig {

    /** Business 竖切：公共 Harness 链 → ToolExecution（Master + 子 Agent 独占编排）→ 审核聚合 → Composer */
    @Bean
    @Qualifier("businessAgentNodes")
    List<AgentNode> businessAgentNodes(
            BusinessScopeIntersectNode businessScopeIntersectNode,
            BusinessTimeWindowNode businessTimeWindowNode,
            BusinessDataPlannerNode businessDataPlannerNode,
            BusinessToolExecutionNode businessToolExecutionNode,
            StubOutcomeReviewNode outcomeReviewNode,
            StubAnswerComposerNode answerComposerNode
    ) {
        return List.of(
                businessScopeIntersectNode,
                businessTimeWindowNode,
                businessDataPlannerNode,
                businessToolExecutionNode,
                outcomeReviewNode,
                answerComposerNode
        );
    }
}
