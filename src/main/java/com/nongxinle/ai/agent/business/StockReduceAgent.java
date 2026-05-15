package com.nongxinle.ai.agent.business;

import com.nongxinle.ai.context.AiResolvedQueryContext;
import com.nongxinle.ai.context.AiResolvedQueryIntent;
import com.nongxinle.ai.core.AiRunState;
import com.nongxinle.ai.dto.business.StockReduceAnswerPlan;
import com.nongxinle.ai.graph.business.StockReduceAnswerPlanBuilder;
import com.nongxinle.ai.graph.business.StockReduceQueryToolExecutor;
import com.nongxinle.ai.graph.business.toolrequest.BusinessToolExecutionRequestResolver;
import com.nongxinle.ai.graph.business.toolrequest.StockReduceToolRequestContext;
import com.nongxinle.ai.tool.ToolResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;

/**
 * 出库/核销专线子 Agent：仅 STOCK_REDUCE_QUERY + stock_reduce_query_path。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class StockReduceAgent implements BusinessSubAgent {

    private final StockReduceQueryToolExecutor stockReduceQueryToolExecutor;
    private final BusinessToolExecutionRequestResolver toolExecutionRequestResolver;

    @Override
    public String agentName() {
        return BusinessAgentNames.STOCK_REDUCE_QUERY;
    }

    @Override
    public AgentCapability capability() {
        return AgentCapability.builder()
                .agentName(agentName())
                .supportedIntentCodes(List.of(AiResolvedQueryIntent.STOCK_REDUCE_QUERY))
                .supportedPathCodes(List.of(AiResolvedQueryIntent.PATH_STOCK_REDUCE_QUERY))
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
                    AiResolvedQueryIntent.STOCK_REDUCE_QUERY,
                    AiResolvedQueryIntent.PATH_STOCK_REDUCE_QUERY);
        }
        return AiResolvedQueryIntent.STOCK_REDUCE_QUERY.equals(rq.getEffectiveIntentCode())
                && AiResolvedQueryIntent.PATH_STOCK_REDUCE_QUERY.equals(rq.getEffectivePathCode());
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
        StockReduceToolRequestContext srCtx = toolExecutionRequestResolver.buildStockReduceRequestContext(state, rqCtx);
        state.setStatStartDate(srCtx.getStartDateIso());
        state.setStatEndDate(srCtx.getEndDateIso());

        Long dis = state.getDistributerId();
        Long deptScoped = srCtx.getDepartmentFatherIdForScopedTools();

        ToolResult executed = stockReduceQueryToolExecutor.executeStockReduceQuery(
                rid,
                state,
                deptScoped,
                dis,
                srCtx.getStartDateIso(),
                srCtx.getEndDateIso(),
                new LinkedHashMap<>());
        if (executed == null) {
            return AgentResultEnvelope.builder()
                    .agentName(agentName())
                    .status(AgentResultStatus.PERMISSION_DENIED)
                    .resultType(null)
                    .answerPlan(null)
                    .warnings(new ArrayList<>())
                    .errors(List.of("permission_denied_stock_reduce_query"))
                    .degraded(false)
                    .durationMs(elapsedMs(t0))
                    .traceId(traceId(rid))
                    .revenueQueryToolSuccess(null)
                    .purchaseOverviewToolSuccess(null)
                    .stockReduceQueryToolSuccess(false)
                    .build();
        }

        StockReduceAnswerPlan plan = null;
        if (executed.isSuccess()) {
            StockReduceAnswerPlanBuilder.attachIfApplicable(state);
            plan = state.getStockReduceAnswerPlan();
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
                        executed.getMessage() == null ? "stock_reduce_tool_failed" : executed.getMessage()))
                .degraded(degraded)
                .durationMs(elapsedMs(t0))
                .traceId(traceId(rid))
                .revenueQueryToolSuccess(null)
                .purchaseOverviewToolSuccess(null)
                .stockReduceQueryToolSuccess(executed.isSuccess())
                .build();
    }

    private static AgentResultEnvelope failureEnvelope(String err, AgentResultStatus status, long t0) {
        return AgentResultEnvelope.builder()
                .agentName(BusinessAgentNames.STOCK_REDUCE_QUERY)
                .status(status)
                .errors(List.of(err))
                .warnings(new ArrayList<>())
                .durationMs(elapsedMs(t0))
                .traceId("no-run")
                .revenueQueryToolSuccess(null)
                .purchaseOverviewToolSuccess(null)
                .stockReduceQueryToolSuccess(false)
                .build();
    }

    private static long elapsedMs(long t0Nano) {
        return (System.nanoTime() - t0Nano) / 1_000_000L;
    }

    private static String traceId(long runId) {
        return runId + "-" + BusinessAgentNames.STOCK_REDUCE_QUERY + "-" + UUID.randomUUID();
    }
}
