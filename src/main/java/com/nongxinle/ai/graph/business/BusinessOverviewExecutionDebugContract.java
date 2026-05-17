package com.nongxinle.ai.graph.business;

import com.nongxinle.ai.agent.business.MasterBusinessAgent;
import com.nongxinle.ai.agent.business.MasterBusinessAgentResult;
import com.nongxinle.ai.context.AiResolvedQueryContext;
import com.nongxinle.ai.context.AiResolvedQueryIntent;
import com.nongxinle.ai.core.AiRunState;

import java.util.LinkedHashMap;

/**
 * Harness 稳定字段：经营概览 MULTI vs CLASSIC 互斥与跳过的可复盘原因（写入 {@link AiRunState#getMasterBusinessAgentDebug()}，
 * 并由 {@link com.nongxinle.ai.harness.AiHarnessResolvedContextSummarizer} 摊入 {@code resolvedQueryContextSummary}）。
 */
public final class BusinessOverviewExecutionDebugContract {

    public static final String EXECUTION_MODE_MULTI = "MULTI_AGENT";
    public static final String EXECUTION_MODE_CLASSIC = "CLASSIC";
    public static final String EXECUTION_MODE_NONE = "NONE";

    public static final String SKIP_MULTI_PRIORITY = "MULTI_AGENT_TAKES_PRIORITY";
    public static final String SKIP_NOT_OVERVIEW_PATH = "NOT_BUSINESS_OVERVIEW_PATH";
    public static final String SKIP_INTENT_PATH = "INTENT_OR_PATH_NOT_MATCHED";
    public static final String SKIP_NO_PLAN = "NO_DATA_PLAN_TOOLS";
    public static final String SKIP_MASTER_DISABLED = "MASTER_DISABLED";
    public static final String SKIP_UNKNOWN = "UNKNOWN";

    private BusinessOverviewExecutionDebugContract() {
    }

    /**
     * 在 {@link BusinessToolExecutionNode} 合并 Master debug 之后调用，补充契约字段。
     */
    public static void apply(
            LinkedHashMap<String, Object> dbg,
            AiRunState state,
            MasterBusinessAgentResult classicMr,
            MasterBusinessAgentResult multiMr) {
        if (dbg == null) {
            return;
        }
        boolean multiEligible = MasterBusinessAgent.eligibleForBusinessOverviewMultiAgentOrchestration(state);
        boolean classicEligibleStrict = MasterBusinessAgent.eligibleForClassicBusinessOverview(state);
        boolean classicPathTaken =
                classicMr != null && classicMr.isClassicBusinessOverviewMasterPath();

        AiResolvedQueryContext rq = state != null ? state.getResolvedQueryContext() : null;
        String effPath = rq != null ? rq.getEffectivePathCode() : null;
        boolean businessOverviewEffectivePath = AiResolvedQueryIntent.PATH_BUSINESS_OVERVIEW.equals(effPath);
        boolean overviewPath = state != null && state.isBusinessOverviewPath();
        /* 仅统计「经营概览专线」表面：诊断等 runs 也会拉四域 Multi 批次，但不应在契约顶层标成 MULTI_AGENT（易与 classic 跳过原因冲突）。 */
        String mode;
        if (!businessOverviewEffectivePath) {
            mode = EXECUTION_MODE_NONE;
        } else if (multiEligible) {
            mode = EXECUTION_MODE_MULTI;
        } else if (classicPathTaken) {
            mode = EXECUTION_MODE_CLASSIC;
        } else {
            mode = EXECUTION_MODE_NONE;
        }
        dbg.put("businessOverviewExecutionMode", mode);
        dbg.put("multiBusinessOverviewEligible", multiEligible);
        dbg.put("classicBusinessOverviewEligible", classicEligibleStrict);
        boolean intentPathOk = rq != null
                && AiResolvedQueryIntent.BUSINESS_OVERVIEW.equals(rq.getEffectiveIntentCode())
                && AiResolvedQueryIntent.PATH_BUSINESS_OVERVIEW.equals(rq.getEffectivePathCode());
        boolean planOk = state != null && state.getDataPlanTools() != null && !state.getDataPlanTools().isEmpty();

        boolean skipped;
        String skipReason;
        if (!overviewPath || !businessOverviewEffectivePath) {
            skipped = false;
            skipReason = SKIP_NOT_OVERVIEW_PATH;
        } else if (!intentPathOk) {
            skipped = false;
            skipReason = SKIP_INTENT_PATH;
        } else if (!planOk) {
            skipped = true;
            skipReason = SKIP_NO_PLAN;
        } else if (multiEligible) {
            skipped = true;
            skipReason = SKIP_MULTI_PRIORITY;
        } else if (classicPathTaken) {
            skipped = false;
            skipReason = null;
        } else {
            skipped = true;
            skipReason = resolveClassicNotTakenReason(classicMr);
        }

        dbg.put("classicBusinessOverviewSkipped", skipped);
        dbg.put("classicBusinessOverviewSkippedReason", skipReason);
    }

    private static String resolveClassicNotTakenReason(MasterBusinessAgentResult classicMr) {
        if (classicMr == null) {
            return SKIP_UNKNOWN;
        }
        if (!classicMr.isMasterAgentEnabled()) {
            return SKIP_MASTER_DISABLED;
        }
        if (classicMr.getDebug() != null) {
            Object r = classicMr.getDebug().get("classicBusinessOverviewReason");
            if ("supports_false".equals(r) || "business_overview_agent_not_registered".equals(r)) {
                return SKIP_MASTER_DISABLED;
            }
        }
        return SKIP_UNKNOWN;
    }
}
