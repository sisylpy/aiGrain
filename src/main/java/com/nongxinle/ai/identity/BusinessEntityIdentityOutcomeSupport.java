package com.nongxinle.ai.identity;

import com.nongxinle.ai.context.AiResolvedQueryContext;
import com.nongxinle.ai.core.AiRunState;
import com.nongxinle.ai.dto.business.AiResultAnchor;
import com.nongxinle.ai.graph.business.execution.PurchaseSemanticExecutionIntent;
import com.nongxinle.ai.graph.business.execution.PurchaseSemanticExecutionIntentResolver;
import com.nongxinle.ai.graph.business.execution.ToolRequestContractExecutionParamSupport;
import com.nongxinle.ai.semantic.contract.SemanticContractCompletionEngine;
import org.springframework.util.StringUtils;

/**
 * GOODS 实体身份解析结果对用户可见成功态的公共边界：未 OK 的身份不得包装为「已整理/成功」。
 */
public final class BusinessEntityIdentityOutcomeSupport {

    private BusinessEntityIdentityOutcomeSupport() {}

    public static boolean blocksSuccessfulGoodsAnchoredPresentation(AiResolvedQueryContext ctx) {
        if (ctx == null
                || ctx.getQuerySemanticParse() == null
                || !SemanticContractCompletionEngine.isContractLockedParse(ctx.getQuerySemanticParse())) {
            return false;
        }
        if (!requiresGoodsIdentityForLockedContract(ctx)) {
            return false;
        }
        return isUnresolvedGoodsIdentity(BusinessEntityIdentityBridge.resolveGoods(ctx));
    }

    public static String goodsIdentityUserFacingFailureMessage(AiResolvedQueryContext ctx) {
        ResolvedEntityIdentity goods = ctx == null ? null : BusinessEntityIdentityBridge.resolveGoods(ctx);
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

    private static boolean requiresGoodsIdentityForLockedContract(AiResolvedQueryContext ctx) {
        if (ToolRequestContractExecutionParamSupport.isPurchaseGoodsBusinessAnalysisContract(ctx)) {
            return true;
        }
        PurchaseSemanticExecutionIntent intent = PurchaseSemanticExecutionIntentResolver.resolve(ctx);
        return intent.isActive()
                && AiResultAnchor.ENTITY_TYPE_GOODS.equalsIgnoreCase(trim(intent.getAnchorType()));
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

    private static String trim(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        return raw.trim();
    }
}
