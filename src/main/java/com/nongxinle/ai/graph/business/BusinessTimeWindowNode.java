package com.nongxinle.ai.graph.business;

import com.nongxinle.ai.context.AiResolvedQueryContext;
import com.nongxinle.ai.context.AiResolvedTimeWindow;
import com.nongxinle.ai.core.AgentNode;
import com.nongxinle.ai.core.AiRunState;
import com.nongxinle.ai.trace.AiSseEventPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 将 {@link AiResolvedQueryContext#getTimeWindow()} 镜像到 {@link AiRunState} 的查库起止日与说明。
 * 不在此节点用 Java 规则从用户话术重算时间；时间语义唯一定稿于 {@link com.nongxinle.ai.resolver.AiResolvedQueryContextResolver}。
 */
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
                "displayText", "正在落地 Resolver 时间窗…"
        ));

        AiResolvedQueryContext ctx = state.getResolvedQueryContext();
        AiResolvedTimeWindow tw = mirrorTimeWindow(ctx);
        if (tw.getStartDate() != null && tw.getEndDate() != null) {
            state.setStatStartDate(tw.getStartDate().toString());
            state.setStatEndDate(tw.getEndDate().toString());
        }
        state.setTimeWindowResolutionNote(buildResolutionNote(tw, ctx));

        LinkedHashMap<String, Object> fin = new LinkedHashMap<>(8);
        fin.put("agent", "TimeWindowNode");
        fin.put("displayText", state.getTimeWindowResolutionNote());
        fin.put("startDate", state.getStatStartDate());
        fin.put("endDate", state.getStatEndDate());
        if (ctx != null && StringUtils.hasText(ctx.getEffectiveTimeWindowSource())) {
            fin.put("effectiveTimeWindowSource", ctx.getEffectiveTimeWindowSource());
        }
        if (StringUtils.hasText(tw.getTimeLabel())) {
            fin.put("timeLabel", tw.getTimeLabel());
        }
        publisher.publish(rid, "agent_finished", fin);
        return state;
    }

    /** 镜像 Resolver 已解析时间窗；不在 Graph 侧根据 timeLabel 推算起止日。 */
    private static AiResolvedTimeWindow mirrorTimeWindow(AiResolvedQueryContext ctx) {
        if (ctx == null || ctx.getTimeWindow() == null) {
            return AiResolvedTimeWindow.builder().build();
        }
        return ctx.getTimeWindow();
    }

    private static String buildResolutionNote(AiResolvedTimeWindow tw, AiResolvedQueryContext ctx) {
        if (tw != null && StringUtils.hasText(tw.getDisplayText())) {
            return tw.getDisplayText();
        }
        if (ctx != null && StringUtils.hasText(ctx.getTimeWindowLabel())) {
            return ctx.getTimeWindowLabel();
        }
        if (tw != null && tw.getStartDate() != null && tw.getEndDate() != null) {
            return "时间窗 " + tw.getStartDate() + "～" + tw.getEndDate();
        }
        return "时间窗已落地";
    }
}
