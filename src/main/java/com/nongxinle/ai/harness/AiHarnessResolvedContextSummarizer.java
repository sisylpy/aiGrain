package com.nongxinle.ai.harness;

import com.nongxinle.ai.context.AiResolvedQueryContext;
import com.nongxinle.ai.core.AiRunState;
import com.nongxinle.ai.harness.replay.AiHarnessReplayContextProbes;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 将 {@link AiResolvedQueryContext} 压成 GET /api/ai/runs/{id} 可用的调试摘要（仅 harness / local 开启开关时下发）。
 */
public final class AiHarnessResolvedContextSummarizer {

    private AiHarnessResolvedContextSummarizer() {
    }

    public static Map<String, Object> summarize(AiResolvedQueryContext ctx, Long conversationId) {
        return summarize(ctx, conversationId, null);
    }

    public static Map<String, Object> summarize(AiResolvedQueryContext ctx, Long conversationId, AiRunState state) {
        LinkedHashMap<String, Object> out = new LinkedHashMap<>();
        if (ctx == null) {
            return out;
        }
        AiHarnessBasicSummaryAppender.appendBasicFields(out, ctx, conversationId, state);
        AiHarnessSemanticSummaryAppender.appendSemanticFields(out, ctx);
        AiHarnessTimeScopeSummaryAppender.appendTimeAndScopeFields(out, ctx);
        AiHarnessFollowUpSummaryAppender.appendFollowUpFields(out, ctx);
        if (state == null) {
            AiHarnessReplayContextProbes.appendResolvedOnlyProbes(out, ctx);
        }
        appendExecutionHints(out, state);
        if (state == null && ctx.getDataScope() != null) {
            AiHarnessTimeScopeSummaryAppender.overlayReplayResolvedExecutionMirrorsFromDataScope(
                    out, ctx.getDataScope());
        }
        AiHarnessFollowUpSummaryAppender.reconcileFollowUpTargetEntityIdForHarness(out, ctx, state);
        return out;
    }

    private static void appendExecutionHints(LinkedHashMap<String, Object> out, AiRunState state) {
        if (state == null) {
            AiHarnessAnswerPlanSummaryAppender.putNullStateAnswerPlanExecutionDefaults(out);
            AiHarnessMasterAgentDebugSummaryAppender.putMasterBusinessAgentDebugDefaults(out);
            AiHarnessReplayProbeSummaryAppender.putHarnessReplayGraphRunStateProbeDefaults(out);
            return;
        }
        AiHarnessAnswerPlanSummaryAppender.appendAnswerPlanExecutionFields(out, state);
        AiHarnessMasterAgentDebugSummaryAppender.mergeMasterBusinessAgentDebug(out, state);
        AiHarnessMasterAgentDebugSummaryAppender.mirrorDishProfitGraphToolEnvelopeSuccessProbes(out, state);
        AiHarnessCompositeSummaryAppender.mergeCompositeProductionGateHarnessFields(out, state);
        AiHarnessCompositeSummaryAppender.mergeCompositeHarnessExecutionFields(out, state);
        AiHarnessReplayProbeSummaryAppender.appendAnswerPreviewAndDiagnosisPlanWireFields(out, state);
        AiHarnessReplayProbeSummaryAppender.mirrorHarnessReplayProbesPresenceFromAnswerPlans(out, state);
        AiHarnessReplayProbeSummaryAppender.appendHarnessReplayGraphRunStateProbes(out, state);
        AiHarnessReplayProbeSummaryAppender.appendHarnessToolRequestOnlyFields(out, state);
    }

    /**
     * C-60：普通 Run / SSE — 摊平 Composite Gate（C-55）与 Composite Execution（C-58/C-60），不重跑摘要全量路径；
     * C-61：`compositeShadow*`（仅 SHADOW executed）。
     */
    public static Map<String, Object> summarizeCompositeGateAndExecutionOnly(AiRunState state) {
        LinkedHashMap<String, Object> out = new LinkedHashMap<>();
        AiHarnessCompositeSummaryAppender.mergeCompositeProductionGateHarnessFields(out, state);
        AiHarnessCompositeSummaryAppender.mergeCompositeHarnessExecutionFields(out, state);
        return out;
    }
}
