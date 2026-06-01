package com.nongxinle.ai.graph.business;

import com.nongxinle.ai.context.AiResolvedQueryContext;
import com.nongxinle.ai.context.AiResolvedQueryIntent;
import com.nongxinle.ai.conversation.AiQuerySemanticLexicon;
import com.nongxinle.ai.core.AiRunState;
import org.springframework.util.StringUtils;

/**
 * 经营概览 MULTI_AGENT 四域编排：子域 AnswerPlan 从 Tool 信封建 Plan 时，
 * contract-completed wire 为 {@code business_overview_*}，不应被各域 Matrix wire 门禁拒绝。
 */
public final class BusinessOverviewSubPlanAttachSupport {

    /** debug：经营概览四域子计划旁路挂载（与 DishProfit portfolio fallback 对齐）。 */
    public static final String ATTACH_MODE = "business_overview_four_domain_sub_plan";

    private BusinessOverviewSubPlanAttachSupport() {
    }

    /**
     * {@link AiRunState#isBusinessOverviewPath()} + contract-completed wire 属于四域编排表面。
     */
    public static boolean isFourDomainSubPlanAttach(AiRunState state, AiResolvedQueryContext rq) {
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
