package com.nongxinle.ai.agent.business;

import com.nongxinle.ai.context.AiResolvedQueryContext;
import com.nongxinle.ai.context.AiResolvedQueryIntent;
import com.nongxinle.ai.core.AiRunState;
import com.nongxinle.ai.dto.business.DishProfitAnswerPlan;
import com.nongxinle.ai.graph.business.DishProfitAnswerPlanBuilder;
import com.nongxinle.ai.graph.business.DishProfitQueryToolExecutor;
import com.nongxinle.ai.graph.business.toolrequest.BusinessToolExecutionRequestResolver;
import com.nongxinle.ai.graph.business.toolrequest.DishProfitToolRequestContext;
import com.nongxinle.ai.tool.ToolResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;

/**
 * 菜品毛利专线子 Agent：仅 DISH_PROFIT + dish_profit_path；{@link com.nongxinle.ai.graph.business.DishProfitAgentNode}
 * 仍负责从 {@link AiRunState#getToolResults()} 衍生 overview / AnswerPlan。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DishProfitAgent implements BusinessSubAgent {

    private final DishProfitQueryToolExecutor dishProfitQueryToolExecutor;
    private final BusinessToolExecutionRequestResolver toolExecutionRequestResolver;

    @Override
    public String agentName() {
        return BusinessAgentNames.DISH_PROFIT_ANALYSIS;
    }

    @Override
    public AgentCapability capability() {
        return AgentCapability.builder()
                .agentName(agentName())
                .supportedIntentCodes(List.of(AiResolvedQueryIntent.DISH_PROFIT))
                .supportedPathCodes(List.of(AiResolvedQueryIntent.PATH_DISH_PROFIT))
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
                    AiResolvedQueryIntent.DISH_PROFIT,
                    AiResolvedQueryIntent.PATH_DISH_PROFIT);
        }
        return AiResolvedQueryIntent.DISH_PROFIT.equals(rq.getEffectiveIntentCode())
                && AiResolvedQueryIntent.PATH_DISH_PROFIT.equals(rq.getEffectivePathCode());
    }

    @Override
    public AgentResultEnvelope execute(BusinessAgentRequest request) {
        long t0 = System.nanoTime();
        AiRunState state = request == null ? null : request.getExecutionContext();
        if (state == null) {
            return failureEnvelope(t0);
        }
        long rid = state.getRunId();
        AiResolvedQueryContext rqCtx = request.getResolvedQueryContext();
        DishProfitToolRequestContext dpCtx = toolExecutionRequestResolver.buildDishProfitRequestContext(state, rqCtx);
        state.setStatStartDate(dpCtx.getStartDateIso());
        state.setStatEndDate(dpCtx.getEndDateIso());

        Long dis = state.getDistributerId();
        Long deptScoped = dpCtx.getDepartmentFatherIdForScopedTools();
        Long deptBuild = dpCtx.getDepartmentFatherIdForBuildInsight();

        ToolResult executed = dishProfitQueryToolExecutor.executeDishProfitAnalysis(
                rid,
                state,
                deptScoped,
                deptBuild,
                dis,
                dpCtx.getStartDateIso(),
                dpCtx.getStopDateIso(),
                new LinkedHashMap<>());
        if (executed == null) {
            return AgentResultEnvelope.builder()
                    .agentName(agentName())
                    .status(AgentResultStatus.PERMISSION_DENIED)
                    .resultType(null)
                    .answerPlan(null)
                    .warnings(new ArrayList<>())
                    .errors(List.of("permission_denied_dish_profit_analysis"))
                    .degraded(false)
                    .durationMs(elapsedMs(t0))
                    .traceId(traceId(rid))
                    .revenueQueryToolSuccess(null)
                    .purchaseOverviewToolSuccess(null)
                    .stockReduceQueryToolSuccess(null)
                    .dishProfitAnalysisToolSuccess(false)
                    .build();
        }

        DishProfitAnswerPlan plan = null;
        if (executed.isSuccess()) {
            DishProfitAnswerPlanBuilder.attachForAgentEnvelope(state, request.isOrchestratedBusinessOverviewMultiAgent());
            plan = state.getDishProfitAnswerPlan();
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
                        executed.getMessage() == null ? "dish_profit_tool_failed" : executed.getMessage()))
                .degraded(degraded)
                .durationMs(elapsedMs(t0))
                .traceId(traceId(rid))
                .revenueQueryToolSuccess(null)
                .purchaseOverviewToolSuccess(null)
                .stockReduceQueryToolSuccess(null)
                .dishProfitAnalysisToolSuccess(executed.isSuccess())
                .build();
    }

    private static AgentResultEnvelope failureEnvelope(long t0) {
        return AgentResultEnvelope.builder()
                .agentName(BusinessAgentNames.DISH_PROFIT_ANALYSIS)
                .status(AgentResultStatus.FAILED)
                .errors(List.of("missing_execution_context"))
                .warnings(new ArrayList<>())
                .durationMs(elapsedMs(t0))
                .traceId("no-run")
                .revenueQueryToolSuccess(null)
                .purchaseOverviewToolSuccess(null)
                .stockReduceQueryToolSuccess(null)
                .dishProfitAnalysisToolSuccess(false)
                .build();
    }

    private static long elapsedMs(long t0Nano) {
        return (System.nanoTime() - t0Nano) / 1_000_000L;
    }

    private static String traceId(long runId) {
        return runId + "-" + BusinessAgentNames.DISH_PROFIT_ANALYSIS + "-" + UUID.randomUUID();
    }
}
