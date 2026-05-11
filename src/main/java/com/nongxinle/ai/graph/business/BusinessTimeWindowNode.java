package com.nongxinle.ai.graph.business;

import com.nongxinle.ai.core.AgentNode;
import com.nongxinle.ai.core.AiRunState;
import com.nongxinle.ai.time.AiUserQueryTimeWindowResolver;
import com.nongxinle.ai.trace.AiSseEventPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class BusinessTimeWindowNode implements AgentNode {

    private final AiSseEventPublisher publisher;

    @Override
    public String name() {
        return "TimeWindow";
    }

    @Override
    public boolean shouldRun(AiRunState state) {
        return true;
    }

    @Override
    public AiRunState run(AiRunState state) {
        long rid = state.getRunId();
        publisher.publish(rid, "agent_started", Map.of(
                "agent", "TimeWindowNode",
                "displayText", "正在解析查询时间区间…"
        ));
        AiUserQueryTimeWindowResolver.Window w =
                AiUserQueryTimeWindowResolver.resolve(state.getNormalizedUserInput(), LocalDate.now());
        state.setStatStartDate(w.startInclusive().toString());
        state.setStatEndDate(w.endInclusive().toString());
        state.setTimeWindowResolutionNote(w.resolutionNote());
        publisher.publish(rid, "agent_finished", Map.of(
                "agent", "TimeWindowNode",
                "displayText", w.resolutionNote(),
                "startDate", state.getStatStartDate(),
                "endDate", state.getStatEndDate()
        ));
        return state;
    }
}
