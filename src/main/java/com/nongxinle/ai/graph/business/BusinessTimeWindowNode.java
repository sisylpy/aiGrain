package com.nongxinle.ai.graph.business;

import com.nongxinle.ai.context.AiResolvedQueryContext;
import com.nongxinle.ai.context.AiResolvedTimeWindow;
import com.nongxinle.ai.core.AgentNode;
import com.nongxinle.ai.core.AiRunState;
import com.nongxinle.ai.resolver.AiMultiTurnTimeWindowPolicy;
import com.nongxinle.ai.trace.AiSseEventPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
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
        AiResolvedTimeWindow tw = resolveMirrorOrFallbackWindow(ctx);
        state.setStatStartDate(tw.getStartDate().toString());
        state.setStatEndDate(tw.getEndDate().toString());
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

    /**
     * 与 {@link AiResolvedQueryContext} 一致：优先完整起止日；否则用语义 timeLabel + 上轮窗 materialize；否则本月至今（锚点与 Resolver 物化策略对齐）。
     */
    private static AiResolvedTimeWindow resolveMirrorOrFallbackWindow(AiResolvedQueryContext ctx) {
        LocalDate anchor = anchorForMaterialization(ctx);
        if (ctx == null) {
            return AiResolvedTimeWindow.defaultMonthToDate(LocalDate.now());
        }
        AiResolvedTimeWindow tw = ctx.getTimeWindow();
        if (tw != null && tw.getStartDate() != null && tw.getEndDate() != null) {
            return tw;
        }
        if (tw != null && StringUtils.hasText(tw.getTimeLabel())) {
            AiResolvedTimeWindow previous =
                    AiMultiTurnTimeWindowPolicy.timeWindowFromPreviousTurn(ctx.getPreviousTurn());
            AiResolvedTimeWindow materialized =
                    AiResolvedTimeWindow.fromSemanticTimeType(tw.getTimeLabel().trim(), anchor, previous);
            if (materialized != null
                    && materialized.getStartDate() != null
                    && materialized.getEndDate() != null) {
                return materialized;
            }
        }
        return AiResolvedTimeWindow.defaultMonthToDate(anchor);
    }

    /**
     * Materialize 与默认 MTD 的「今天」锚点：优先 Resolver 已给出的窗终点/起点，否则上一轮记忆终点，否则系统当前日。
     */
    private static LocalDate anchorForMaterialization(AiResolvedQueryContext ctx) {
        if (ctx != null && ctx.getTimeWindow() != null && ctx.getTimeWindow().getEndDate() != null) {
            return ctx.getTimeWindow().getEndDate();
        }
        if (ctx != null && ctx.getTimeWindow() != null && ctx.getTimeWindow().getStartDate() != null) {
            return ctx.getTimeWindow().getStartDate();
        }
        if (ctx != null && AiMultiTurnTimeWindowPolicy.hasTurnMemoryDates(ctx.getPreviousTurn())) {
            LocalDate e = AiResolvedTimeWindow.parseIsoDateOrNull(ctx.getPreviousTurn().getLastEndDate());
            if (e != null) {
                return e;
            }
        }
        return LocalDate.now();
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
