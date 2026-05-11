package com.nongxinle.ai.graph.business;

import com.nongxinle.ai.core.AgentNode;
import com.nongxinle.ai.core.AiRunState;
import com.nongxinle.ai.followup.FollowUpIntentResolveService;
import com.nongxinle.ai.trace.AiSseEventPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 工作台短句追问：在上游 Workspace 判定为 BUSINESS_CHAT 后，按需把 normalizedUserInput 扩写为与上一轮语义对齐的完整问句。
 */
@Component
@RequiredArgsConstructor
public class BusinessFollowUpIntentResolveNode implements AgentNode {

    private final FollowUpIntentResolveService followUpIntentResolveService;
    private final AiSseEventPublisher publisher;

    @Override
    public String name() {
        return "FollowUpIntentResolve";
    }

    @Override
    public boolean shouldRun(AiRunState state) {
        return true;
    }

    @Override
    public AiRunState run(AiRunState state) {
        long rid = state.getRunId();
        publisher.publish(rid, "agent_started", Map.of(
                "agent", "FollowUpIntentResolver",
                "displayText", "正在检查是否继承上一轮语义…"
        ));
        boolean applied = followUpIntentResolveService.applyIfFollowUp(state);
        publisher.publish(rid, "agent_finished", Map.of(
                "agent", "FollowUpIntentResolver",
                "displayText", applied ? "已根据上一轮话题补全问句（仅时间窗变化）" : "非短句追问，按原输入继续"
        ));
        return state;
    }
}
