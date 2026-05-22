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
        AiHarnessFollowUpSummaryAppender.reconcileFocusGoodsEntityIdForHarness(out, ctx, state);
        appendPhase2ResolvedQueryPreview(out, ctx, state);
        return out;
    }

    /**
     * Phase2 准备：单块观测「准备用什么参数查」（路由 + 范围 + Tool request 摘要），不验答案正确性。
     */
    private static void appendPhase2ResolvedQueryPreview(
            LinkedHashMap<String, Object> out, AiResolvedQueryContext ctx, AiRunState state) {
        if (ctx == null) {
            out.put("phase2ResolvedQueryPreview", null);
            return;
        }
        LinkedHashMap<String, Object> p = new LinkedHashMap<>();
        p.put("effectiveIntentCode", out.get("effectiveIntentCode"));
        p.put("effectivePathCode", out.get("effectivePathCode"));
        p.put("selectedTools", out.get("orchestrationSelectedTools"));
        p.put("queryObject", out.get("queryObject"));
        p.put("operation", out.get("operation"));
        p.put("metric", out.get("metric"));
        p.put("sourceFacet", out.get("sourceFacet"));
        p.put("structuredIntentDetailWire", out.get("structuredIntentDetailWire"));
        p.put("selectedContractId", out.get("selectedContractId"));
        p.put("canonicalStructuredIntentDetailWire", out.get("canonicalStructuredIntentDetailWire"));
        p.put("answerPlanType", out.get("answerPlanType"));
        p.put("startDate", out.get("startDate"));
        p.put("endDate", out.get("endDate"));
        p.put("timeSource", out.get("timeSource"));
        p.put("scopeType", out.get("scopeType"));
        p.put("visibleStores", out.get("visibleStores"));
        p.put("queryStoreIds", out.get("queryStoreIds"));
        p.put("queryRealDepartmentIds", out.get("queryRealDepartmentIds"));
        p.put("expandedSqlDepartmentIds", out.get("expandedSqlDepartmentIds"));
        p.put("followUpRewriteApplied", out.get("followUpRewriteApplied"));
        p.put("completedUserQuery", out.get("completedUserQuery"));
        p.put("previousTurnResultAnchorsCount", out.get("previousTurnResultAnchorsCount"));
        p.put("rewritePromptResultAnchorsCount", out.get("rewritePromptResultAnchorsCount"));
        p.put("purchaseAnswerPlanResultAnchorsCount", out.get("purchaseAnswerPlanResultAnchorsCount"));
        p.put("turnMemoryPersistResultAnchorsCount", out.get("turnMemoryPersistResultAnchorsCount"));
        if (state != null && state.isHarnessToolRequestOnly()) {
            p.put("dryRunStage", out.get("dryRunStage"));
            p.put("plannedToolArgsByToolId", out.get("plannedToolArgsByToolId"));
        }
        out.put("phase2ResolvedQueryPreview", p);
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
