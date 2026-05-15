package com.nongxinle.ai.config;

import com.nongxinle.ai.core.AgentNode;
import com.nongxinle.ai.graph.business.BusinessFollowUpIntentResolveNode;
import com.nongxinle.ai.graph.business.BusinessDataPlannerNode;
import com.nongxinle.ai.graph.business.BusinessDiagnosisPlanNode;
import com.nongxinle.ai.graph.business.BusinessScopeIntersectNode;
import com.nongxinle.ai.graph.business.BusinessTimeWindowNode;
import com.nongxinle.ai.graph.business.BusinessToolExecutionNode;
import com.nongxinle.ai.graph.business.BusinessWorkspaceRouteNode;
import com.nongxinle.ai.graph.business.BusinessOverviewAgentNode;
import com.nongxinle.ai.graph.business.CostDiagnosisAgentNode;
import com.nongxinle.ai.graph.business.DishProfitAgentNode;
import com.nongxinle.ai.graph.business.StubAnswerComposerNode;
import com.nongxinle.ai.graph.business.StubOutcomeReviewNode;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class AiBusinessGraphConfig {

    /** Business 竖切：路由 → 追问继承（上轮时间替换）→ 范围求交 → 时间窗 → 规划 → Tools →（菜品毛利｜成本诊断｜经营概况）→ 审核 → Composer */
    @Bean
    @Qualifier("businessAgentNodes")
    List<AgentNode> businessAgentNodes(
            BusinessWorkspaceRouteNode workspaceRouteNode,
            BusinessFollowUpIntentResolveNode businessFollowUpIntentResolveNode,
            BusinessScopeIntersectNode businessScopeIntersectNode,
            BusinessTimeWindowNode businessTimeWindowNode,
            BusinessDataPlannerNode businessDataPlannerNode,
            BusinessToolExecutionNode businessToolExecutionNode,
            DishProfitAgentNode dishProfitAgentNode,
            BusinessDiagnosisPlanNode businessDiagnosisPlanNode,
            CostDiagnosisAgentNode costDiagnosisAgentNode,
            BusinessOverviewAgentNode businessOverviewAgentNode,
            StubOutcomeReviewNode outcomeReviewNode,
            StubAnswerComposerNode answerComposerNode
    ) {
        return List.of(
                workspaceRouteNode,
                businessFollowUpIntentResolveNode,
                businessScopeIntersectNode,
                businessTimeWindowNode,
                businessDataPlannerNode,
                businessToolExecutionNode,
                dishProfitAgentNode,
                businessDiagnosisPlanNode,
                costDiagnosisAgentNode,
                businessOverviewAgentNode,
                outcomeReviewNode,
                answerComposerNode
        );
    }
}
