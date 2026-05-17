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

//  旧「报表/营销/任务」关键词工作台路由已从代码删除；workspaceMode 在 Run 构造阶段默认 BUSINESS_CHAT，主路由唯一直读 AiResolvedQueryContext。
//  短句追问与上一轮合并由 Graph 外 AiResolvedQueryContextResolver + conversation memory 收口；主图不再含 no-op FollowUp 节点。
//  节点 1:  businessScopeIntersectNode     ← 门店范围求交（用户说的门店 × 用户权限范围）
//  节点 2:  businessTimeWindowNode         ← 时间窗口解析（"上个月"→ 具体日期）
//  节点 3:  businessDataPlannerNode       ← 数据规划（要查哪些表？用什么指标？）
//  节点 4:  businessToolExecutionNode     ← MasterBusinessAgent + 工具链（唯一业务拉数入口）
//  节点 5:  outcomeReviewNode             ← 结果审核 / AnswerPlan·诊断聚合
//  节点 6:  answerComposerNode             ← 最终组装（只读 Plan / 结构化结果）

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
