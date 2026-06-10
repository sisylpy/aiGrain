package com.nongxinle.ai.semantic.inheritance;

import com.nongxinle.ai.conversation.AiConversationTurnMemory;
import com.nongxinle.ai.dto.business.AiResultAnchor;
import com.nongxinle.ai.identity.CanonicalResultAnchorIdentitySupport;
import com.nongxinle.ai.identity.EntityAnchorSovereigntySupport;
import com.nongxinle.ai.semantic.AiQuerySemanticParseResult;
import com.nongxinle.ai.semantic.AiQuerySemanticSlotMerge;
import com.nongxinle.ai.semantic.contract.SemanticCapabilityContract;
import org.springframework.util.StringUtils;

import java.util.Locale;

/**
 * 显式实体追问：仅依赖 V2 / Intake 结构化字段，不做自然语言 contains 猜测。
 * GOODS / DISH 共用 anchor 主权边界。
 */
public final class ExplicitEntityFollowUpSupport {

    private ExplicitEntityFollowUpSupport() {}

    public static boolean isExplicitEntityFollowUp(
            AiQuerySemanticParseResult current,
            AiConversationTurnMemory previousTurn,
            String domainHint) {
        if (current == null) {
            return false;
        }
        if (isExplicitGoodsEntityFollowUp(current, previousTurn, domainHint)) {
            return true;
        }
        return isExplicitDishEntityFollowUp(current, previousTurn, domainHint);
    }

    public static boolean isExplicitGoodsEntityFollowUp(
            AiQuerySemanticParseResult current,
            AiConversationTurnMemory previousTurn,
            String domainHint) {
        if (current == null) {
            return false;
        }
        String goods = EntityAnchorSovereigntySupport.resolveCurrentTurnGoodsName(current);
        String anchorPolicy = anchorPolicy(current);
        if (!StringUtils.hasText(goods)) {
            return false;
        }
        if (AiQuerySemanticSlotMerge.ANCHOR_IGNORE_PREVIOUS.equals(anchorPolicy)) {
            return true;
        }
        SemanticCapabilityContract contract =
                SemanticContractFamilySupport.lookupActiveContract(
                        SemanticContractFamilySupport.contractIdFromParse(current), domainHint);
        if (contract == null || !contract.isRequiresAnchor()) {
            return false;
        }
        if (!AiResultAnchorTypeMatches.isGoodsAnchorType(contract.getAnchorType())) {
            return false;
        }
        if (!StructuredTimeFollowUpSupport.isStructuredTimeOnlyFollowUp(current)) {
            return true;
        }
        String previousGoods = resolvePreviousTurnGoodsName(previousTurn);
        return StringUtils.hasText(previousGoods) && !goods.equals(previousGoods);
    }

    public static boolean isExplicitDishEntityFollowUp(
            AiQuerySemanticParseResult current,
            AiConversationTurnMemory previousTurn,
            String domainHint) {
        if (current == null) {
            return false;
        }
        String dish = EntityAnchorSovereigntySupport.resolveCurrentTurnDishName(current);
        String anchorPolicy = anchorPolicy(current);
        String intentAction = normalize(current.getIntentAction());

        if (AiQuerySemanticSlotMerge.ANCHOR_IGNORE_PREVIOUS.equals(anchorPolicy)
                && StringUtils.hasText(dish)) {
            return true;
        }
        if ("OVERRIDE".equals(intentAction) && StringUtils.hasText(dish)) {
            return true;
        }
        if (!StringUtils.hasText(dish)) {
            return false;
        }
        SemanticCapabilityContract contract =
                SemanticContractFamilySupport.lookupActiveContract(
                        SemanticContractFamilySupport.contractIdFromParse(current), domainHint);
        if (contract == null || !contract.isRequiresAnchor()) {
            return false;
        }
        if (!AiResultAnchorTypeMatches.isDishAnchorType(contract.getAnchorType())) {
            return false;
        }
        if (!StructuredTimeFollowUpSupport.isStructuredTimeOnlyFollowUp(current)) {
            return true;
        }
        if (AiQuerySemanticSlotMerge.ANCHOR_IGNORE_PREVIOUS.equals(anchorPolicy)) {
            return true;
        }
        String previousDish =
                previousTurn != null ? trim(previousTurn.getLastMentionedDishName()) : null;
        return StringUtils.hasText(previousDish) && !dish.equals(previousDish);
    }

    /** 避免 identity 包依赖 dto；轻量 anchorType 匹配。 */
    private static final class AiResultAnchorTypeMatches {
        private AiResultAnchorTypeMatches() {}

        static boolean isGoodsAnchorType(String anchorType) {
            return StringUtils.hasText(anchorType) && "GOODS".equalsIgnoreCase(anchorType.trim());
        }

        static boolean isDishAnchorType(String anchorType) {
            return StringUtils.hasText(anchorType) && "DISH".equalsIgnoreCase(anchorType.trim());
        }
    }

    private static String resolvePreviousTurnGoodsName(AiConversationTurnMemory previousTurn) {
        if (previousTurn == null) {
            return null;
        }
        AiResultAnchor anchor = CanonicalResultAnchorIdentitySupport.firstTrustworthyGoodsAnchor(previousTurn);
        if (anchor != null && StringUtils.hasText(anchor.getEntityName())) {
            return anchor.getEntityName().trim();
        }
        if (previousTurn.getLastSemanticSlots() != null
                && StringUtils.hasText(previousTurn.getLastSemanticSlots().getMentionedGoodsName())) {
            return previousTurn.getLastSemanticSlots().getMentionedGoodsName().trim();
        }
        return null;
    }

    private static String anchorPolicy(AiQuerySemanticParseResult current) {
        return EntityAnchorSovereigntySupport.anchorPolicyFromParse(current);
    }

    private static String normalize(String token) {
        if (!StringUtils.hasText(token)) {
            return null;
        }
        return token.trim().toUpperCase(Locale.ROOT).replace('-', '_');
    }

    private static String trim(String s) {
        return StringUtils.hasText(s) ? s.trim() : null;
    }
}
