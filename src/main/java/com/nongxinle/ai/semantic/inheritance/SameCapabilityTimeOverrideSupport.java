package com.nongxinle.ai.semantic.inheritance;

import com.nongxinle.ai.conversation.AiConversationTurnMemory;
import com.nongxinle.ai.semantic.intake.SemanticIntakeFollowUpIntent;
import com.nongxinle.ai.semantic.intake.SemanticIntakeFollowUpKind;
import com.nongxinle.ai.semantic.intake.SemanticIntakeResult;
import com.nongxinle.ai.semantic.intake.SemanticIntakeSovereignDomainSupport;
import org.springframework.util.StringUtils;

/**
 * 同 stable contract 的 structured time-only 追问：继承上一轮 Business Frame，仅覆盖 time /
 * salesBaselineWindow。不读 reason / rawMessage。
 */
public final class SameCapabilityTimeOverrideSupport {

    public static final String FOLLOW_UP_PATH_STRUCTURED_TIME_ONLY = "STRUCTURED_TIME_ONLY";

    private SameCapabilityTimeOverrideSupport() {}

    public static boolean isSameCapabilityTimeOverrideSignal(
            SemanticIntakeResult intake,
            SemanticIntakeFollowUpIntent followUpIntent,
            AiConversationTurnMemory previousTurn) {
        if (SemanticIntakeSovereignDomainSupport.intakeCrossFamilyFromPreviousTurn(
                intake, previousTurn)) {
            return false;
        }
        if (intake == null) {
            return false;
        }
        if (followUpIntent != null) {
            if (followUpIntent.getKind() == SemanticIntakeFollowUpKind.RANKING_TIME_OVERRIDE) {
                return false;
            }
            if (followUpIntent.getKind() == SemanticIntakeFollowUpKind.NAMED_ENTITY_SWAP) {
                return false;
            }
            if (followUpIntent.getKind() == SemanticIntakeFollowUpKind.GOODS_ANCHOR_STOCK) {
                return false;
            }
            if (followUpIntent.getKind() == SemanticIntakeFollowUpKind.RANKING_DIMENSION_SWITCH) {
                return false;
            }
            return followUpIntent.getKind() == SemanticIntakeFollowUpKind.SAME_CAPABILITY_TIME_OVERRIDE;
        }
        return false;
    }

    public static String resolveStableContractId(
            SemanticIntakeFollowUpIntent followUpIntent, AiConversationTurnMemory previousTurn) {
        if (followUpIntent != null
                && StringUtils.hasText(followUpIntent.getTargetContractId())) {
            return followUpIntent.getTargetContractId().trim();
        }
        return SemanticContractFamilySupport.contractIdFromPreviousTurn(previousTurn);
    }
}
