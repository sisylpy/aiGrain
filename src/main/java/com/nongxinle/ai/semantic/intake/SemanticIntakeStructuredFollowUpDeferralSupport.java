package com.nongxinle.ai.semantic.intake;

import com.nongxinle.ai.context.AiResolvedQueryIntent;
import com.nongxinle.ai.conversation.AiConversationTurnMemory;
import com.nongxinle.ai.conversation.AiQuerySemanticLexicon;
import com.nongxinle.ai.semantic.AiQuerySemanticParseResult;
import com.nongxinle.ai.semantic.intake.route.SemanticDomainRouteResult;
import com.nongxinle.ai.semantic.intake.route.SemanticDomainRouteType;
import org.springframework.util.StringUtils;

/**
 * Intake 无法选域时，若 {@code previousTurn} 已 contract-locked 为单菜 DishCost，将澄清延后至 V2
 * （由 {@code timeAction}/{@code anchorPolicy} 等结构化字段决定，Java 不读用户原文）。
 */
public final class SemanticIntakeStructuredFollowUpDeferralSupport {

    private SemanticIntakeStructuredFollowUpDeferralSupport() {}

    public static boolean shouldDeferIntakeClarificationToV2(
            SemanticIntakeResult intake, AiConversationTurnMemory previousTurn) {
        if (intake == null || previousTurn == null) {
            return false;
        }
        if (intake.getStatus() == SemanticIntakeStatus.INVALID) {
            return false;
        }
        if (intake.getQuestionMode() == SemanticIntakeQuestionMode.MULTI_QUESTION) {
            return false;
        }
        boolean intakeNeedsClarification =
                intake.getStatus() == SemanticIntakeStatus.NEED_CLARIFICATION
                        || Boolean.TRUE.equals(intake.getNeedClarification());
        if (!intakeNeedsClarification) {
            return false;
        }
        return isStructuredDishCostPreviousTurn(previousTurn);
    }

    public static SemanticDomainRouteResult buildDeferredRouteFromPreviousTurn(
            AiConversationTurnMemory previousTurn) {
        return SemanticDomainRouteResult.builder()
                .routeType(SemanticDomainRouteType.INHERITED)
                .primaryDomain(SemanticIntakePrimaryDomain.DISH_COST)
                .usedPreviousContext(true)
                .needsClarification(false)
                .reasonCode("structured_previous_turn_dish_cost_defer_v2")
                .build();
    }

    static boolean isStructuredDishCostPreviousTurn(AiConversationTurnMemory previousTurn) {
        if (previousTurn == null) {
            return false;
        }
        if (!AiResolvedQueryIntent.PATH_DISH_COST_ANALYSIS.equals(
                trim(previousTurn.getLastPathCode()))) {
            return false;
        }
        return hasStructuredDishCostWire(previousTurn);
    }

    private static boolean hasStructuredDishCostWire(AiConversationTurnMemory previousTurn) {
        String structured = trim(previousTurn.getLastStructuredIntentDetail());
        if (StringUtils.hasText(structured)) {
            String canon =
                    AiQuerySemanticLexicon.canonicalStructuredIntentDetailWire(structured);
            if (AiQuerySemanticLexicon.isStructuredDishCostAnalysisDetail(canon)) {
                return true;
            }
        }
        AiQuerySemanticParseResult.SemanticSlotsPart slots = previousTurn.getLastSemanticSlots();
        if (slots != null && StringUtils.hasText(slots.getStructuredIntentDetailWire())) {
            String canon =
                    AiQuerySemanticLexicon.canonicalStructuredIntentDetailWire(
                            slots.getStructuredIntentDetailWire().trim());
            return AiQuerySemanticLexicon.isStructuredDishCostAnalysisDetail(canon);
        }
        return false;
    }

    private static String trim(String s) {
        return StringUtils.hasText(s) ? s.trim() : null;
    }
}
