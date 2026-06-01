package com.nongxinle.ai.graph.business;

import com.nongxinle.ai.agent.business.MasterBusinessAgent;
import com.nongxinle.ai.context.AiResolvedQueryContext;
import com.nongxinle.ai.context.AiResolvedQueryIntent;
import com.nongxinle.ai.core.AgentNode;
import com.nongxinle.ai.core.AiRunState;
import com.nongxinle.ai.platform.AiCardPayloadWireSupport;
import com.nongxinle.ai.trace.AiSseEventPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class StubOutcomeReviewNode implements AgentNode {

    private final AiSseEventPublisher publisher;
    private final MasterBusinessAgent masterBusinessAgent;
    private final DishProfitAgentNode dishProfitAgentNode;
    private final MenuOperationAgentNode menuOperationAgentNode;
    private final DishProfitPrescriptionAgentNode dishProfitPrescriptionAgentNode;
    private final DishIngredientCoverAgentNode dishIngredientCoverAgentNode;
    private final GoodsSupportedDishCoverAgentNode goodsSupportedDishCoverAgentNode;
    private final CostDiagnosisAgentNode costDiagnosisAgentNode;

    @Override
    public String name() {
        return "OutcomeReviewStub";
    }

    @Override
    public boolean shouldRun(AiRunState state) {
        return true;
    }

    @Override
    public AiRunState run(AiRunState state) {
        long rid = state.getRunId();
        publisher.publish(rid, "review_started", Map.of(
                "agent", "OutcomeReviewAgent",
                "displayText", "正在审核输出…"
        ));
        state.setOutcomeReviewStub(Map.of("passed", true, "score", 85));
        if (!shouldSkipDishProfitAggregateOnDiagnosisMultiAgentMainline(state)) {
            dishProfitAgentNode.aggregateIfApplicable(state);
        }
        menuOperationAgentNode.aggregateIfApplicable(state);
        dishProfitPrescriptionAgentNode.aggregateIfApplicable(state);
        dishIngredientCoverAgentNode.aggregateIfApplicable(state);
        goodsSupportedDishCoverAgentNode.aggregateIfApplicable(state);
        costDiagnosisAgentNode.applyIfApplicable(state);
        masterBusinessAgent.refreshBusinessOverviewMultiAgentPlanIfApplicable(state);
        DiagnosisPlanBuilder.attachIfApplicable(state);
        AiCardPayloadWireSupport.refreshAllCardPayloads(state);
        publisher.publish(rid, "review_finished", Map.of(
                "agent", "OutcomeReviewAgent",
                "displayText", "审核完成",
                "passed", true,
                "score", 85
        ));
        return state;
    }

    /**
     * 经营诊断 MULTI_AGENT 主线上 Dish 域已由 {@link MasterBusinessAgent#tryOrchestrateBusinessOverviewMultiAgent}
     * 子 Agent 挂载 AnswerPlan；跳过后续 {@link DishProfitAgentNode} 重聚合。
     */
    private static boolean shouldSkipDishProfitAggregateOnDiagnosisMultiAgentMainline(AiRunState state) {
        if (state == null || !state.isBusinessDiagnosisPath()) {
            return false;
        }
        AiResolvedQueryContext rq = state.getResolvedQueryContext();
        if (rq == null) {
            return false;
        }
        if (!AiResolvedQueryIntent.BUSINESS_DIAGNOSIS.equals(rq.getEffectiveIntentCode())
                || !AiResolvedQueryIntent.PATH_BUSINESS_DIAGNOSIS.equals(rq.getEffectivePathCode())) {
            return false;
        }
        String rtm = rq.getOrchestrationTaskMode();
        if (rtm != null && "MULTI_AGENT".equalsIgnoreCase(rtm.trim())) {
            return true;
        }
        return Boolean.TRUE.equals(rq.getOrchestrationMultiAgentRequired());
    }
}
