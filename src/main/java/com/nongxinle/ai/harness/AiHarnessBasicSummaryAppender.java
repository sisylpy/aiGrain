package com.nongxinle.ai.harness;

import com.nongxinle.ai.context.AiResolvedQueryContext;
import com.nongxinle.ai.core.AiRunState;

import java.util.LinkedHashMap;

/**
 * 摘要顶层身份与编排观测字段（conversation / run / effective path-intent / orchestration）。
 */
final class AiHarnessBasicSummaryAppender {

    private AiHarnessBasicSummaryAppender() {
    }

    static void appendBasicFields(
            LinkedHashMap<String, Object> out,
            AiResolvedQueryContext ctx,
            Long conversationId,
            AiRunState state) {
        Long cid = conversationId;
        if (cid == null && ctx.getPreviousTurn() != null) {
            cid = ctx.getPreviousTurn().getConversationId();
        }
        out.put("conversationId", cid);
        out.put("runId", ctx.getRunId());
        out.put("advisorId", state != null ? state.getAdvisorId() : null);
        out.put("effectiveIntentCode", AiHarnessSummaryUtils.blankToNull(ctx.getEffectiveIntentCode()));
        out.put("effectivePathCode", AiHarnessSummaryUtils.blankToNull(ctx.getEffectivePathCode()));
        out.put("intent", AiHarnessSummaryUtils.blankToNull(ctx.getEffectiveIntentCode()));
        out.put("path", AiHarnessSummaryUtils.blankToNull(ctx.getEffectivePathCode()));
        String productionTimeSrc = AiHarnessSummaryUtils.blankToNull(ctx.getEffectiveTimeWindowSource());
        out.put("effectiveTimeWindowSource", productionTimeSrc);
        out.put("timeSource", productionTimeSrc);
        out.put("effectiveIntentSource", AiHarnessSummaryUtils.blankToNull(ctx.getEffectiveIntentSource()));
        out.put("effectiveScopeSource", AiHarnessSummaryUtils.blankToNull(ctx.getEffectiveScopeSource()));

        out.put("orchestrationTaskMode", AiHarnessSummaryUtils.blankToNull(ctx.getOrchestrationTaskMode()));
        out.put(
                "orchestrationSelectedAgents",
                ctx.getOrchestrationSelectedAgents() == null || ctx.getOrchestrationSelectedAgents().isEmpty()
                        ? null
                        : new java.util.ArrayList<>(ctx.getOrchestrationSelectedAgents()));
        out.put(
                "orchestrationSelectedTools",
                ctx.getOrchestrationSelectedTools() == null || ctx.getOrchestrationSelectedTools().isEmpty()
                        ? null
                        : new java.util.ArrayList<>(ctx.getOrchestrationSelectedTools()));
        out.put("orchestrationPlannerRequired", ctx.getOrchestrationPlannerRequired());
        out.put("orchestrationMultiAgentRequired", ctx.getOrchestrationMultiAgentRequired());
        out.put("orchestrationApprovalRequired", ctx.getOrchestrationApprovalRequired());
        out.put("orchestrationClarificationRequired", ctx.getOrchestrationClarificationRequired());
        out.put("orchestrationClarificationQuestion", AiHarnessSummaryUtils.blankToNull(ctx.getOrchestrationClarificationQuestion()));
        out.put("orchestrationConfidence", ctx.getOrchestrationConfidence());
        out.put("orchestrationReason", AiHarnessSummaryUtils.blankToNull(ctx.getOrchestrationReason()));

        out.put("multiStoreScopeDetected", ctx.isHarnessMultiStoreScopeDetected());
        out.put("multiStoreScopeApplied", ctx.isHarnessMultiStoreScopeApplied());
        out.put("multiStoreScopeSource", AiHarnessSummaryUtils.blankToNull(ctx.getHarnessMultiStoreScopeSource()));
        out.put(
                "multiStoreMatchedStores",
                ctx.getHarnessMultiStoreMatchedStores() == null
                        ? null
                        : new java.util.ArrayList<>(ctx.getHarnessMultiStoreMatchedStores()));
        out.put("singleStoreNarrowingBlocked", ctx.isHarnessSingleStoreNarrowingBlocked());
    }
}
