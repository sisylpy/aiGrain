package com.nongxinle.ai.semantic.intake;

import com.nongxinle.ai.conversation.AiConversationTurnMemory;
import com.nongxinle.ai.semantic.dimension.BareRankingDimensionSwitchPlan;
import com.nongxinle.ai.semantic.dimension.BareRankingDimensionSwitchSupport;

/**
 * Intake 后处理薄封装：委托 {@link BareRankingDimensionSwitchSupport}。
 *
 * @deprecated 新代码请直接使用 {@link BareRankingDimensionSwitchSupport}。
 */
@Deprecated
public final class SemanticIntakeDishRankingDimensionSwitchSupport {

    private SemanticIntakeDishRankingDimensionSwitchSupport() {}

    public static SemanticIntakeResult reconcile(SemanticIntakeInput input, SemanticIntakeResult intake) {
        return BareRankingDimensionSwitchSupport.reconcileIntakeDomain(input, intake);
    }

    public static boolean isBareRankingDimensionSwitch(
            SemanticIntakeInput input, SemanticIntakeResult intake) {
        return BareRankingDimensionSwitchSupport.isBareRankingDimensionSwitch(input, intake);
    }

    public static SemanticIntakeInput intakeInputFromPreviousTurn(
            SemanticIntakeResult intake, AiConversationTurnMemory previousTurn) {
        return BareRankingDimensionSwitchSupport.intakeInputFromPreviousTurn(intake, previousTurn);
    }

    public static boolean isBareRankingDimensionSwitchFromPreviousTurn(
            SemanticIntakeResult intake, AiConversationTurnMemory previousTurn) {
        return BareRankingDimensionSwitchSupport.isBareRankingDimensionSwitchFromPreviousTurn(
                intake, previousTurn);
    }

    public static String resolveTargetRankingDomainForSwitch(
            SemanticIntakeInput input, SemanticIntakeResult intake) {
        BareRankingDimensionSwitchPlan plan =
                BareRankingDimensionSwitchSupport.buildPlan(input, intake, null);
        return plan.isActive() ? plan.getTargetDomain() : null;
    }
}
