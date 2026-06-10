package com.nongxinle.ai.identity;

import com.nongxinle.ai.context.AiResolvedQueryContext;
import com.nongxinle.ai.core.AiRunState;
import com.nongxinle.ai.graph.business.execution.RequiresAnchorExecutionGateSupport;
import org.springframework.util.StringUtils;

/**
 * requiresAnchor 合同：未 OK 的实体 identity 不得包装为「已整理/成功」。
 */
public final class BusinessEntityIdentityOutcomeSupport {

    private BusinessEntityIdentityOutcomeSupport() {}

    public static boolean blocksSuccessfulGoodsAnchoredPresentation(AiResolvedQueryContext ctx) {
        return RequiresAnchorExecutionGateSupport.blocksToolExecution(ctx);
    }

    public static String goodsIdentityUserFacingFailureMessage(AiResolvedQueryContext ctx) {
        RequiresAnchorExecutionGateSupport.Decision gate = RequiresAnchorExecutionGateSupport.evaluate(ctx);
        if (gate.blocksToolExecution() && StringUtils.hasText(gate.getClarificationMessage())) {
            return gate.getClarificationMessage().trim();
        }
        ResolvedEntityIdentity goods = ctx == null ? null : ctx.getResolvedGoodsIdentity();
        if (goods != null && StringUtils.hasText(goods.getClarificationMessage())) {
            return goods.getClarificationMessage().trim();
        }
        return BusinessEntityExistenceLookup.CLARIFICATION_GOODS_NOT_FOUND;
    }

    public static String composeGoodsIdentityFailureAnswer(AiRunState state) {
        if (state == null) {
            return BusinessEntityExistenceLookup.CLARIFICATION_GOODS_NOT_FOUND;
        }
        if (StringUtils.hasText(state.getClarificationQuestion())) {
            return state.getClarificationQuestion().trim();
        }
        return goodsIdentityUserFacingFailureMessage(state.getResolvedQueryContext());
    }

    static boolean isUnresolvedGoodsIdentity(ResolvedEntityIdentity goods) {
        if (goods == null || goods.getResolutionStatus() == EntityIdentityResolutionStatus.SKIPPED) {
            return false;
        }
        return switch (goods.getResolutionStatus()) {
            case NOT_FOUND, NEED_CLARIFICATION -> true;
            case UNRESOLVED -> goods.hasExplicitMention();
            default -> false;
        };
    }
}
