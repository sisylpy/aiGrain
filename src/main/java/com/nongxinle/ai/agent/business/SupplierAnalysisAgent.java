package com.nongxinle.ai.agent.business;

import com.nongxinle.ai.context.AiResolvedQueryContext;
import com.nongxinle.ai.context.AiResolvedQueryIntent;
import com.nongxinle.ai.conversation.AiQuerySemanticLexicon;
import com.nongxinle.ai.core.AiRunState;
import com.nongxinle.ai.dto.business.PurchaseAnswerPlan;
import com.nongxinle.ai.graph.business.PurchaseAnswerPlanBuilder;
import com.nongxinle.ai.graph.business.PurchaseOverviewToolExecutor;
import com.nongxinle.ai.graph.business.toolrequest.BusinessToolExecutionRequestResolver;
import com.nongxinle.ai.graph.business.toolrequest.PurchaseToolRequestContext;
import com.nongxinle.ai.tool.ToolResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;

/**
 * 供货商 / 供货占比 / 供货商排行专项：与 {@link PurchaseAgent} 共用 {@code purchase_overview} Tool 与
 * {@link PurchaseAnswerPlan}，由 {@link MasterBusinessAgent} 按意图在两条子 Agent 间二选一。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SupplierAnalysisAgent implements BusinessSubAgent {

    private final PurchaseOverviewToolExecutor purchaseOverviewToolExecutor;
    private final BusinessToolExecutionRequestResolver toolExecutionRequestResolver;

    @Override
    public String agentName() {
        return BusinessAgentNames.SUPPLIER_ANALYSIS;
    }

    @Override
    public AgentCapability capability() {
        return AgentCapability.builder()
                .agentName(agentName())
                .supportedIntentCodes(List.of(AiResolvedQueryIntent.PURCHASE_OVERVIEW))
                .supportedPathCodes(List.of(AiResolvedQueryIntent.PATH_PURCHASE_OVERVIEW))
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
        if (request.isOrchestratedBusinessOverviewMultiAgent()) {
            return false;
        }
        AiResolvedQueryContext rq = request.getResolvedQueryContext();
        if (!AiResolvedQueryIntent.PURCHASE_OVERVIEW.equals(rq.getEffectiveIntentCode())
                || !AiResolvedQueryIntent.PATH_PURCHASE_OVERVIEW.equals(rq.getEffectivePathCode())) {
            return false;
        }
        String sid = null;
        if (rq.getQueryIntent() != null) {
            sid = rq.getQueryIntent().getStructuredIntentDetail();
        }
        if (AiQuerySemanticLexicon.STRUCTURED_PURCHASE_SOURCE_AMOUNT_QUERY.equals(sid)) {
            return true;
        }
        return AiQuerySemanticLexicon.isSupplierAmountRankingDetail(sid);
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
        PurchaseToolRequestContext purchaseCtx = toolExecutionRequestResolver.buildPurchaseRequestContext(state, rqCtx);
        state.setStatStartDate(purchaseCtx.getStartDateIso());
        state.setStatEndDate(purchaseCtx.getEndDateIso());

        Long dis = state.getDistributerId();
        Long deptScoped = purchaseCtx.getDepartmentFatherIdForScopedTools();

        ToolResult executed = purchaseOverviewToolExecutor.executePurchaseOverview(
                rid,
                state,
                deptScoped,
                dis,
                purchaseCtx.getStartDateIso(),
                purchaseCtx.getEndDateIso(),
                new LinkedHashMap<>());
        if (executed == null) {
            return AgentResultEnvelope.builder()
                    .agentName(agentName())
                    .status(AgentResultStatus.PERMISSION_DENIED)
                    .errors(List.of("permission_denied_purchase_overview"))
                    .warnings(new ArrayList<>())
                    .durationMs(elapsedMs(t0))
                    .traceId(traceId(rid))
                    .purchaseOverviewToolSuccess(false)
                    .build();
        }

        PurchaseAnswerPlan plan = null;
        if (PurchaseAnswerPlanBuilder.shouldAttachPlanAfterToolExecution(state, executed.isSuccess())) {
            PurchaseAnswerPlanBuilder.attachIfApplicable(state);
            plan = state.getPurchaseAnswerPlan();
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
                        executed.getMessage() == null ? "purchase_tool_failed" : executed.getMessage()))
                .degraded(degraded)
                .durationMs(elapsedMs(t0))
                .traceId(traceId(rid))
                .purchaseOverviewToolSuccess(executed.isSuccess())
                .build();
    }

    private static AgentResultEnvelope failureEnvelope(long t0) {
        return AgentResultEnvelope.builder()
                .agentName(BusinessAgentNames.SUPPLIER_ANALYSIS)
                .status(AgentResultStatus.FAILED)
                .errors(List.of("missing_execution_context"))
                .warnings(new ArrayList<>())
                .durationMs(elapsedMs(t0))
                .traceId("no-run")
                .purchaseOverviewToolSuccess(false)
                .build();
    }

    private static long elapsedMs(long t0Nano) {
        return (System.nanoTime() - t0Nano) / 1_000_000L;
    }

    private static String traceId(long runId) {
        return runId + "-" + BusinessAgentNames.SUPPLIER_ANALYSIS + "-" + UUID.randomUUID();
    }
}
