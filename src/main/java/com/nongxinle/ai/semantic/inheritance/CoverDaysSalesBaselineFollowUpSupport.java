package com.nongxinle.ai.semantic.inheritance;

import com.nongxinle.ai.conversation.AiConversationTurnMemory;

/**
 * Cover-days 实体锚点读取（Applier 用）；transition 判定见
 * {@link SameCapabilityTimeOverrideSupport}。
 */
public final class CoverDaysSalesBaselineFollowUpSupport {

    private CoverDaysSalesBaselineFollowUpSupport() {}

    public static String previousDishName(AiConversationTurnMemory previousTurn) {
        if (previousTurn == null) {
            return null;
        }
        if (previousTurn.getLastSemanticSlots() != null
                && org.springframework.util.StringUtils.hasText(
                        previousTurn.getLastSemanticSlots().getMentionedDishName())) {
            return previousTurn.getLastSemanticSlots().getMentionedDishName().trim();
        }
        if (org.springframework.util.StringUtils.hasText(previousTurn.getLastMentionedDishName())) {
            return previousTurn.getLastMentionedDishName().trim();
        }
        return null;
    }

    public static String previousGoodsName(AiConversationTurnMemory previousTurn) {
        String goods = GoodsAnchorSameEntityFollowUpSupport.previousGoodsName(previousTurn);
        if (org.springframework.util.StringUtils.hasText(goods)) {
            return goods.trim();
        }
        return null;
    }

    public static boolean previousTurnWasGoodsCover(AiConversationTurnMemory previousTurn) {
        return com.nongxinle.ai.semantic.matrix.WarehouseSemanticCapabilityMatrix
                .CONTRACT_GOODS_SUPPORTED_DISH_COVER.equals(
                        SemanticContractFamilySupport.contractIdFromPreviousTurn(previousTurn));
    }
}
