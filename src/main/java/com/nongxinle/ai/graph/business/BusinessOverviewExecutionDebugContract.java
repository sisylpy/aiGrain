package com.nongxinle.ai.graph.business;

import com.nongxinle.ai.agent.business.MasterBusinessAgent;
import com.nongxinle.ai.agent.business.MasterBusinessAgentResult;
import com.nongxinle.ai.context.AiResolvedQueryContext;
import com.nongxinle.ai.context.AiResolvedQueryIntent;
import com.nongxinle.ai.core.AiRunState;

import java.util.LinkedHashMap;

/**
 * Harness 稳定字段：经营概览 MULTI_AGENT 编排可复盘标记（写入 {@link AiRunState#getMasterBusinessAgentDebug()}，
 * 并由 {@link com.nongxinle.ai.harness.AiHarnessResolvedContextSummarizer} 摊入 {@code resolvedQueryContextSummary}）。
 */
public final class BusinessOverviewExecutionDebugContract {

    public static final String EXECUTION_MODE_MULTI = "MULTI_AGENT";
    public static final String EXECUTION_MODE_NONE = "NONE";

    private BusinessOverviewExecutionDebugContract() {
    }

    /**
     * 在 {@link BusinessToolExecutionNode} 合并 Master debug 之后调用，补充契约字段。
     */
    public static void apply(
            LinkedHashMap<String, Object> dbg,
            AiRunState state,
            MasterBusinessAgentResult multiMr) {
        if (dbg == null) {
            return;
        }
        boolean multiEligible = MasterBusinessAgent.eligibleForBusinessOverviewMultiAgentOrchestration(state);

        AiResolvedQueryContext rq = state != null ? state.getResolvedQueryContext() : null;
        String effPath = rq != null ? rq.getEffectivePathCode() : null;
        boolean businessOverviewEffectivePath = AiResolvedQueryIntent.PATH_BUSINESS_OVERVIEW.equals(effPath);
        String mode;
        if (!businessOverviewEffectivePath) {
            mode = EXECUTION_MODE_NONE;
        } else if (multiEligible) {
            mode = EXECUTION_MODE_MULTI;
        } else {
            mode = EXECUTION_MODE_NONE;
        }
        dbg.put("businessOverviewExecutionMode", mode);
        dbg.put("multiBusinessOverviewEligible", multiEligible);
    }
}
