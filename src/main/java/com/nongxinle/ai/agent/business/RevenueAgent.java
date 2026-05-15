package com.nongxinle.ai.agent.business;

import com.nongxinle.ai.context.AiResolvedQueryContext;
import com.nongxinle.ai.context.AiResolvedQueryIntent;
import com.nongxinle.ai.core.AiRunState;
import com.nongxinle.ai.dto.business.DailyRevenueAnswerPlan;
import com.nongxinle.ai.graph.business.DailyRevenueAnswerPlanBuilder;
import com.nongxinle.ai.graph.business.RevenueQueryToolExecutor;
import com.nongxinle.ai.graph.business.toolrequest.BusinessToolExecutionRequestResolver;
import com.nongxinle.ai.graph.business.toolrequest.RevenueToolRequestResolution;
import com.nongxinle.ai.tool.ToolResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;

/**
 * 日营业额 / 营收专线子 Agent：仅 REVENUE_OVERVIEW + revenue_overview_path。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RevenueAgent implements BusinessSubAgent {

    private final RevenueQueryToolExecutor revenueQueryToolExecutor;
    private final BusinessToolExecutionRequestResolver toolExecutionRequestResolver;

    @Override
    public String agentName() {
        return BusinessAgentNames.REVENUE_OVERVIEW;
    }

    @Override
    public AgentCapability capability() {
        return AgentCapability.builder()
                .agentName(agentName())
                .supportedIntentCodes(List.of(AiResolvedQueryIntent.REVENUE_OVERVIEW))
                .supportedPathCodes(List.of(AiResolvedQueryIntent.PATH_REVENUE_OVERVIEW))
                .supportsGroupScope(true)
                .supportsStoreCompare(true)
                .supportsMultiTurn(true)
                .build();
    }

    @Override
    public boolean supports(BusinessAgentRequest request) {
        if (request == null || request.getResolvedQueryContext() == null) {
            return false;
        }
        AiResolvedQueryContext rq = request.getResolvedQueryContext();
        if (request.isOrchestratedBusinessOverviewMultiAgent()) {
            return BusinessFourDomainHarnessSupport.harnessTargetMatchesDomain(
                    request,
                    AiResolvedQueryIntent.REVENUE_OVERVIEW,
                    AiResolvedQueryIntent.PATH_REVENUE_OVERVIEW);
        }
        return AiResolvedQueryIntent.REVENUE_OVERVIEW.equals(rq.getEffectiveIntentCode())
                && AiResolvedQueryIntent.PATH_REVENUE_OVERVIEW.equals(rq.getEffectivePathCode());
    }

    @Override
    public AgentResultEnvelope execute(BusinessAgentRequest request) {
        long t0 = System.nanoTime();
        AiRunState state = request == null ? null : request.getExecutionContext();
        if (state == null) {
            return failureEnvelope("missing_execution_context", AgentResultStatus.FAILED, t0);
        }
        long rid = state.getRunId();
        AiResolvedQueryContext rqCtx = request.getResolvedQueryContext();
        RevenueToolRequestResolution revenueReq =
                toolExecutionRequestResolver.resolveRevenueToolRequest(state, rqCtx);
        state.setStatStartDate(revenueReq.getStartDateIso());
        state.setStatEndDate(revenueReq.getStopDateIso());

        Long dis = state.getDistributerId();

        ToolResult executed = revenueQueryToolExecutor.executeRevenueQuery(
                rid,
                state,
                revenueReq.getDepartmentFatherIdForScopedTools(),
                revenueReq.getDepartmentFatherIdForBuildInsight(),
                dis,
                revenueReq.getStartDateIso(),
                revenueReq.getStopDateIso(),
                new LinkedHashMap<>());
        if (executed == null) {
            return AgentResultEnvelope.builder()
                    .agentName(agentName())
                    .status(AgentResultStatus.PERMISSION_DENIED)
                    .resultType(null)
                    .answerPlan(null)
                    .warnings(new ArrayList<>())
                    .errors(List.of("permission_denied_revenue_query"))
                    .degraded(false)
                    .durationMs(elapsedMs(t0))
                    .traceId(traceId(rid))
                    .revenueQueryToolSuccess(false)
                    .build();
        }

        DailyRevenueAnswerPlan plan = null;
        if (executed.isSuccess()) {
            DailyRevenueAnswerPlanBuilder.attachIfApplicable(state);
            plan = state.getRevenueAnswerPlan();
        }

        AgentResultStatus st = executed.isSuccess() ? AgentResultStatus.SUCCESS : AgentResultStatus.FAILED;
        boolean degraded = plan != null && plan.getDebug() != null
                && Boolean.TRUE.equals(plan.getDebug().get("degraded"));

        return AgentResultEnvelope.builder()
                .agentName(agentName())
                .status(st)
                .resultType(plan != null ? plan.getPlanType() : null)
                .answerPlan(plan)
                .warnings(new ArrayList<>())
                .errors(executed.isSuccess() ? new ArrayList<>() : List.of(
                        executed.getMessage() == null ? "revenue_tool_failed" : executed.getMessage()))
                .degraded(degraded)
                .durationMs(elapsedMs(t0))
                .traceId(traceId(rid))
                .revenueQueryToolSuccess(executed.isSuccess())
                .build();
    }

    private static AgentResultEnvelope failureEnvelope(String err, AgentResultStatus status, long t0) {
        return AgentResultEnvelope.builder()
                .agentName(BusinessAgentNames.REVENUE_OVERVIEW)
                .status(status)
                .errors(List.of(err))
                .warnings(new ArrayList<>())
                .durationMs(elapsedMs(t0))
                .traceId("no-run")
                .revenueQueryToolSuccess(false)
                .build();
    }

    private static long elapsedMs(long t0Nano) {
        return (System.nanoTime() - t0Nano) / 1_000_000L;
    }

    private static String traceId(long runId) {
        return runId + "-" + BusinessAgentNames.REVENUE_OVERVIEW + "-" + UUID.randomUUID();
    }
}
