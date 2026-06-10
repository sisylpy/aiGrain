package com.nongxinle.ai.graph.business;

import com.nongxinle.ai.agent.business.MasterBusinessAgent;
import com.nongxinle.ai.context.AiResolvedQueryContext;
import com.nongxinle.ai.context.AiResolvedQueryIntent;
import com.nongxinle.ai.conversation.AiQuerySemanticLexicon;
import com.nongxinle.ai.core.AiRunState;
import org.springframework.util.StringUtils;

/**
 * 经营概览 / 经营诊断 MULTI_AGENT 多域编排：子域 AnswerPlan 从 Tool 信封建 Plan 时，
 * 主合同 wire 为 {@code business_overview_*} 或 {@code business_diagnosis_summary} 等编排表面，
 * 不应被各域 Matrix canonical wire 门禁拒绝。
 */
public final class BusinessOverviewSubPlanAttachSupport {

    /** debug：多域编排子计划旁路挂载（概览 + 诊断 Multi-Agent 共用）。 */
    public static final String ATTACH_MODE = "multi_domain_orchestration_sub_plan";

    /** @deprecated 兼容 Harness 观测；与 {@link #ATTACH_MODE} 同义。 */
    @Deprecated
    public static final String LEGACY_ATTACH_MODE = "business_overview_four_domain_sub_plan";

    private BusinessOverviewSubPlanAttachSupport() {
    }

    /**
     * 多域编排子计划挂载：经营概览四域 wire 或经营诊断 Multi-Agent（与
     * {@link MasterBusinessAgent#eligibleForBusinessOverviewMultiAgentOrchestration} 对齐）。
     */
    public static boolean isMultiDomainOrchestrationSubPlanAttach(AiRunState state, AiResolvedQueryContext rq) {
        if (MasterBusinessAgent.eligibleForBusinessOverviewMultiAgentOrchestration(state)) {
            return true;
        }
        return isLegacyBusinessOverviewFourDomainSubPlanAttach(state, rq);
    }

    /**
     * @deprecated 使用 {@link #isMultiDomainOrchestrationSubPlanAttach}。
     */
    @Deprecated
    public static boolean isFourDomainSubPlanAttach(AiRunState state, AiResolvedQueryContext rq) {
        return isMultiDomainOrchestrationSubPlanAttach(state, rq);
    }

    private static boolean isLegacyBusinessOverviewFourDomainSubPlanAttach(
            AiRunState state, AiResolvedQueryContext rq) {
        if (state == null || rq == null || !state.isBusinessOverviewPath()) {
            return false;
        }
        if (!AiResolvedQueryIntent.PATH_BUSINESS_OVERVIEW.equals(rq.getEffectivePathCode())) {
            AiResolvedQueryIntent qi = rq.getQueryIntent();
            if (qi == null || !AiResolvedQueryIntent.PATH_BUSINESS_OVERVIEW.equals(qi.getPathCode())) {
                return false;
            }
        }
        return AiQuerySemanticLexicon.isStructuredBusinessOverviewFourDomainOrchestrationSurface(
                contractCompletedWire(rq));
    }

    /** contract-owned wire：仅读 {@code queryIntent.structuredIntentDetail}。 */
    public static String contractCompletedWire(AiResolvedQueryContext rq) {
        if (rq == null || rq.getQueryIntent() == null) {
            return "";
        }
        String wire = rq.getQueryIntent().getStructuredIntentDetail();
        if (!StringUtils.hasText(wire)) {
            return "";
        }
        String canon = AiQuerySemanticLexicon.canonicalStructuredIntentDetailWire(wire.trim());
        return StringUtils.hasText(canon) ? canon : wire.trim();
    }
}
