package com.nongxinle.ai.semantic.inheritance;

import com.nongxinle.ai.conversation.AiConversationTurnMemory;
import com.nongxinle.ai.semantic.AiQuerySemanticParseResult;
import com.nongxinle.ai.semantic.AiQuerySemanticSlotMerge;
import com.nongxinle.ai.semantic.contract.SemanticCapabilityContract;
import org.springframework.util.StringUtils;

import java.util.Locale;

/**
 * 显式实体追问：仅依赖 V2 / Intake 结构化字段，不做自然语言 contains 猜测。
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
        String dish = current.effectiveMentionedDishName();
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
        if (!StructuredTimeFollowUpSupport.isStructuredTimeOnlyFollowUp(current)) {
            return true;
        }
        if (AiQuerySemanticSlotMerge.ANCHOR_IGNORE_PREVIOUS.equals(anchorPolicy)) {
            return true;
        }
        String previousDish =
                previousTurn != null ? trim(previousTurn.getLastMentionedDishName()) : null;
        if (StringUtils.hasText(previousDish) && !dish.equals(previousDish)) {
            return true;
        }
        return false;
    }

    private static String anchorPolicy(AiQuerySemanticParseResult current) {
        if (current.getSemanticSlots() == null) {
            return null;
        }
        return normalize(current.getSemanticSlots().getAnchorPolicy());
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
