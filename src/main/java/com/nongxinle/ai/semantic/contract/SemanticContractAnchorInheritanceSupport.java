package com.nongxinle.ai.semantic.contract;

import com.nongxinle.ai.conversation.AiConversationTurnMemory;
import com.nongxinle.ai.dto.business.AiResultAnchor;
import com.nongxinle.ai.semantic.AiQuerySemanticParseResult;
import com.nongxinle.ai.semantic.AiQuerySemanticSlotMerge;
import org.springframework.util.StringUtils;

import java.util.Locale;

/**
 * requiresAnchor 合同在 {@code anchorPolicy=USE_PREVIOUS_ANCHOR} 时，从结构化上下文继承 DISH anchor。
 * 不读 rawMessage / completedUserQuery / contains。
 */
public final class SemanticContractAnchorInheritanceSupport {

    private SemanticContractAnchorInheritanceSupport() {}

    public static boolean isUsePreviousAnchorPolicy(AiQuerySemanticParseResult parse) {
        if (parse == null || parse.getSemanticSlots() == null) {
            return false;
        }
        return AiQuerySemanticSlotMerge.ANCHOR_USE_PREVIOUS.equals(
                normalizeToken(parse.getSemanticSlots().getAnchorPolicy()));
    }

    public static String resolveStructuredDishAnchor(
            AiConversationTurnMemory previousTurn,
            String rewriteInheritedAnchorType,
            String rewriteInheritedAnchorName) {
        if (StringUtils.hasText(rewriteInheritedAnchorName)) {
            if (!StringUtils.hasText(rewriteInheritedAnchorType)
                    || AiResultAnchor.ENTITY_TYPE_DISH.equalsIgnoreCase(
                            rewriteInheritedAnchorType.trim())) {
                return rewriteInheritedAnchorName.trim();
            }
        }
        if (previousTurn != null && StringUtils.hasText(previousTurn.getLastMentionedDishName())) {
            return previousTurn.getLastMentionedDishName().trim();
        }
        AiResultAnchor dish = firstStructuredDishResultAnchor(previousTurn);
        if (dish != null && StringUtils.hasText(dish.getEntityName())) {
            return dish.getEntityName().trim();
        }
        return null;
    }

    public static String resolveInheritedDishAnchorWhenUsePrevious(
            AiQuerySemanticParseResult parse,
            AiConversationTurnMemory previousTurn,
            String rewriteInheritedAnchorType,
            String rewriteInheritedAnchorName) {
        if (!isUsePreviousAnchorPolicy(parse)) {
            return null;
        }
        return resolveStructuredDishAnchor(
                previousTurn, rewriteInheritedAnchorType, rewriteInheritedAnchorName);
    }

    public static String resolveStructuredGoodsAnchor(
            AiConversationTurnMemory previousTurn,
            String rewriteInheritedAnchorType,
            String rewriteInheritedAnchorName) {
        if (StringUtils.hasText(rewriteInheritedAnchorName)
                && isGoodsRewriteType(rewriteInheritedAnchorType)) {
            return rewriteInheritedAnchorName.trim();
        }
        if (previousTurn != null && previousTurn.getLastSemanticSlots() != null
                && StringUtils.hasText(previousTurn.getLastSemanticSlots().getMentionedGoodsName())) {
            return previousTurn.getLastSemanticSlots().getMentionedGoodsName().trim();
        }
        AiResultAnchor goods = firstStructuredGoodsResultAnchor(previousTurn);
        if (goods != null && StringUtils.hasText(goods.getEntityName())) {
            return goods.getEntityName().trim();
        }
        return null;
    }

    public static String resolveInheritedGoodsAnchorWhenUsePrevious(
            AiQuerySemanticParseResult parse,
            AiConversationTurnMemory previousTurn,
            String rewriteInheritedAnchorType,
            String rewriteInheritedAnchorName) {
        if (!isUsePreviousAnchorPolicy(parse)) {
            return null;
        }
        return resolveStructuredGoodsAnchor(
                previousTurn, rewriteInheritedAnchorType, rewriteInheritedAnchorName);
    }

    private static AiResultAnchor firstStructuredGoodsResultAnchor(AiConversationTurnMemory previousTurn) {
        if (previousTurn == null || previousTurn.getLastResultAnchors() == null) {
            return null;
        }
        for (AiResultAnchor anchor : previousTurn.getLastResultAnchors()) {
            if (anchor == null || !StringUtils.hasText(anchor.getEntityType())) {
                continue;
            }
            if (!AiResultAnchor.ENTITY_TYPE_GOODS.equalsIgnoreCase(anchor.getEntityType().trim())) {
                continue;
            }
            if (StringUtils.hasText(anchor.getEntityName()) || StringUtils.hasText(anchor.getEntityId())) {
                return anchor;
            }
        }
        return null;
    }

    private static boolean isGoodsRewriteType(String rewriteType) {
        return StringUtils.hasText(rewriteType)
                && AiResultAnchor.ENTITY_TYPE_GOODS.equalsIgnoreCase(rewriteType.trim());
    }

    public static boolean hasStructuredDishAnchorEvidenceWhenUsePrevious(
            AiQuerySemanticParseResult parse,
            AiConversationTurnMemory previousTurn,
            String rewriteInheritedAnchorType,
            String rewriteInheritedAnchorName) {
        if (parse != null && StringUtils.hasText(parse.effectiveMentionedDishName())) {
            return true;
        }
        if (!isUsePreviousAnchorPolicy(parse)) {
            return false;
        }
        return StringUtils.hasText(
                resolveStructuredDishAnchor(
                        previousTurn, rewriteInheritedAnchorType, rewriteInheritedAnchorName));
    }

    private static AiResultAnchor firstStructuredDishResultAnchor(AiConversationTurnMemory previousTurn) {
        if (previousTurn == null || previousTurn.getLastResultAnchors() == null) {
            return null;
        }
        for (AiResultAnchor anchor : previousTurn.getLastResultAnchors()) {
            if (anchor == null || !StringUtils.hasText(anchor.getEntityType())) {
                continue;
            }
            if (!AiResultAnchor.ENTITY_TYPE_DISH.equalsIgnoreCase(anchor.getEntityType().trim())) {
                continue;
            }
            if (StringUtils.hasText(anchor.getEntityName()) || StringUtils.hasText(anchor.getEntityId())) {
                return anchor;
            }
        }
        return null;
    }

    private static String normalizeToken(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        return raw.trim().toUpperCase(Locale.ROOT).replace('-', '_');
    }
}
