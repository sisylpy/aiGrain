package com.nongxinle.ai.agent.business;

import com.nongxinle.ai.context.AiResolvedQueryContext;
import com.nongxinle.ai.context.AiResolvedQueryIntent;
import com.nongxinle.ai.core.AiRunState;
import com.nongxinle.ai.dto.business.AiBusinessOverviewResult;
import com.nongxinle.ai.dto.business.BusinessOverviewAnswerPlan;
import com.nongxinle.ai.graph.business.BusinessOverviewAgentNode;
import com.nongxinle.ai.graph.business.ClassicBusinessOverviewToolRunner;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;

/**
 * 经典 {@code business_overview_path}：Planner 列六工具，本 Agent 顺序拉数并挂载
 * {@link BusinessOverviewAnswerPlan#PLAN_TYPE_BUSINESS_OVERVIEW_CLASSIC_V1}；由 {@link MasterBusinessAgent}
 * Discover 后分发。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class BusinessOverviewAgent implements BusinessSubAgent {

    private final ClassicBusinessOverviewToolRunner classicBusinessOverviewToolRunner;
    private final BusinessOverviewAgentNode businessOverviewAgentNode;

    @Override
    public String agentName() {
        return BusinessAgentNames.BUSINESS_OVERVIEW;
    }

    @Override
    public AgentCapability capability() {
        return AgentCapability.builder()
                .agentName(agentName())
                .supportedIntentCodes(List.of(AiResolvedQueryIntent.BUSINESS_OVERVIEW))
                .supportedPathCodes(List.of(AiResolvedQueryIntent.PATH_BUSINESS_OVERVIEW))
                .supportsGroupScope(true)
                .supportsStoreCompare(true)
                .supportsMultiTurn(true)
                .build();
    }

    @Override
    public boolean supports(BusinessAgentRequest request) {
        if (request == null || request.getExecutionContext() == null) {
            return false;
        }
        if (request.isOrchestratedBusinessOverviewMultiAgent()) {
            return false;
        }
        AiRunState state = request.getExecutionContext();
        return MasterBusinessAgent.eligibleForClassicBusinessOverview(state);
    }

    @Override
    public AgentResultEnvelope execute(BusinessAgentRequest request) {
        long t0 = System.nanoTime();
        AiRunState state = request != null ? request.getExecutionContext() : null;
        if (state == null) {
            return failureEnvelope("missing_execution_context", AgentResultStatus.FAILED, t0);
        }
        long rid = state.getRunId();
        AiResolvedQueryContext rq = request.getResolvedQueryContext();

        try {
            classicBusinessOverviewToolRunner.run(state);
            businessOverviewAgentNode.aggregateIfApplicable(state);
            AiBusinessOverviewResult overview = state.getBusinessOverviewResult();

            LinkedHashMap<String, Object> dbg = new LinkedHashMap<>();
            dbg.put("planSource", "BusinessOverviewAgent");
            dbg.put("dispatchedBy", "MasterBusinessAgent");
            dbg.put("classicToolChain", AiResolvedQueryIntent.PATH_BUSINESS_OVERVIEW);

            BusinessOverviewAnswerPlan plan = BusinessOverviewAnswerPlan.builder()
                    .planType(BusinessOverviewAnswerPlan.PLAN_TYPE_BUSINESS_OVERVIEW_CLASSIC_V1)
                    .timeLabel(rq != null ? rq.getTimeWindowLabel() : null)
                    .scopeLabel(rq != null ? rq.getQueryScopeBanner() : null)
                    .classicOverviewResult(overview)
                    .warnings(new ArrayList<>())
                    .missingSections(new ArrayList<>())
                    .debug(dbg)
                    .build();
            state.setBusinessOverviewAnswerPlan(plan);

            AgentResultStatus st = overview != null ? AgentResultStatus.SUCCESS : AgentResultStatus.FAILED;
            return AgentResultEnvelope.builder()
                    .agentName(agentName())
                    .status(st)
                    .resultType(plan.getPlanType())
                    .answerPlan(plan)
                    .warnings(new ArrayList<>())
                    .errors(overview != null ? new ArrayList<>() : List.of("classic_business_overview_aggregate_empty"))
                    .degraded(false)
                    .durationMs(elapsedMs(t0))
                    .traceId(traceId(rid))
                    .build();
        } catch (Exception ex) {
            log.warn("[BusinessOverviewAgent] classic overview failed runId={}", rid, ex);
            return AgentResultEnvelope.builder()
                    .agentName(agentName())
                    .status(AgentResultStatus.FAILED)
                    .errors(List.of(ex.getClass().getSimpleName()))
                    .warnings(new ArrayList<>())
                    .durationMs(elapsedMs(t0))
                    .traceId(traceId(rid))
                    .build();
        }
    }

    private static AgentResultEnvelope failureEnvelope(String err, AgentResultStatus status, long t0Nano) {
        return AgentResultEnvelope.builder()
                .agentName(BusinessAgentNames.BUSINESS_OVERVIEW)
                .status(status)
                .errors(List.of(err))
                .warnings(new ArrayList<>())
                .durationMs(elapsedMs(t0Nano))
                .traceId("no-run")
                .build();
    }

    private static long elapsedMs(long t0Nano) {
        return (System.nanoTime() - t0Nano) / 1_000_000L;
    }

    private static String traceId(long runId) {
        return runId + "-" + BusinessAgentNames.BUSINESS_OVERVIEW + "-" + UUID.randomUUID();
    }
}
