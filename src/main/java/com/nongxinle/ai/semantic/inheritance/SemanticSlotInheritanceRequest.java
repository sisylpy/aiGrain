package com.nongxinle.ai.semantic.inheritance;

import com.nongxinle.ai.conversation.AiConversationTurnMemory;
import com.nongxinle.ai.semantic.AiQuerySemanticParseResult;
import com.nongxinle.ai.semantic.contract.DomainContractSelectionResult;
import com.nongxinle.ai.semantic.dimension.BareRankingDimensionSwitchPlan;
import com.nongxinle.ai.semantic.intake.SemanticIntakeResult;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class SemanticSlotInheritanceRequest {
    AiQuerySemanticParseResult currentParse;
    AiConversationTurnMemory previousTurn;
    DomainContractSelectionResult contractSelection;
    boolean followUpRewriteApplied;
    BareRankingDimensionSwitchPlan bareRankingDimensionSwitchPlan;
    /** Intake 结构化 reason / primaryDomain，用于同能力换菜名门禁。 */
    SemanticIntakeResult semanticIntake;
}
