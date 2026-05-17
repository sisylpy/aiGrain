package com.nongxinle.ai.graph.business;

import com.nongxinle.ai.agent.business.MasterBusinessAgent;
import com.nongxinle.ai.core.AgentNode;
import com.nongxinle.ai.core.AiRunState;
import com.nongxinle.ai.dto.business.BusinessOverviewAnswerPlan;
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
    private final CostDiagnosisAgentNode costDiagnosisAgentNode;
    private final BusinessOverviewAgentNode businessOverviewAgentNode;

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
        dishProfitAgentNode.aggregateIfApplicable(state);
        costDiagnosisAgentNode.applyIfApplicable(state);
        if (!hasClassicBusinessOverviewAnswerPlan(state)) {
            businessOverviewAgentNode.aggregateIfApplicable(state);
        }
        masterBusinessAgent.refreshBusinessOverviewMultiAgentPlanIfApplicable(state);
        DiagnosisPlanBuilder.attachIfApplicable(state);
        publisher.publish(rid, "review_finished", Map.of(
                "agent", "OutcomeReviewAgent",
                "displayText", "审核完成",
                "passed", true,
                "score", 85
        ));
        return state;
    }

    private static boolean hasClassicBusinessOverviewAnswerPlan(AiRunState state) {
        if (state == null) {
            return false;
        }
        BusinessOverviewAnswerPlan b = state.getBusinessOverviewAnswerPlan();
        return b != null
                && b.getPlanType() != null
                && BusinessOverviewAnswerPlan.PLAN_TYPE_BUSINESS_OVERVIEW_CLASSIC_V1.equals(b.getPlanType().trim());
    }
}
