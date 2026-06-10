package com.nongxinle.ai.semantic;

import com.nongxinle.ai.semantic.inheritance.SemanticSlotInheritanceDecision;
import com.nongxinle.ai.semantic.inheritance.SemanticSlotInheritanceMode;
import com.nongxinle.ai.semantic.intake.SemanticIntakeResult;

/**
 * Time Layer 与 Business Frame 分层：Intake 判定的上下文延续信号，供 {@link SemanticTimeContractCheck} 独立决策是否继承上一轮时间窗。
 *
 * <p>Business Frame sovereign 只锁定当前合同；不等于 time 必须重置为 DEFAULT_MONTH_TO_DATE。
 * 跨 contract family 的新能力主权切换时，{@link #contextContinuesFromPreviousTurn()} 仍可为 true（门店/scope），
 * 但 {@link #suppressPreviousTurnTimeInheritance()} 为 true，禁止上一轮能力专属时间口径污染当前合同默认时间。
 */
public record TimeLayerContextSignals(
        boolean intakeFollowUp,
        boolean intakeUsedPreviousContext,
        boolean suppressPreviousTurnTimeInheritance) {

    public static TimeLayerContextSignals empty() {
        return new TimeLayerContextSignals(false, false, false);
    }

    public static TimeLayerContextSignals fromIntake(SemanticIntakeResult intake) {
        if (intake == null) {
            return empty();
        }
        return new TimeLayerContextSignals(
                Boolean.TRUE.equals(intake.getIsFollowUp()),
                Boolean.TRUE.equals(intake.getUsedPreviousContext()),
                false);
    }

    /** Intake 上下文 + Business Frame 继承决策：Time Layer 唯一合成入口（Adoption Pipeline）。 */
    public static TimeLayerContextSignals from(
            SemanticIntakeResult intake, SemanticSlotInheritanceDecision inheritanceDecision) {
        if (intake == null && inheritanceDecision == null) {
            return empty();
        }
        boolean followUp =
                intake != null && Boolean.TRUE.equals(intake.getIsFollowUp());
        boolean usedContext =
                intake != null && Boolean.TRUE.equals(intake.getUsedPreviousContext());
        boolean suppressTime = suppressPreviousTurnTimeInheritance(inheritanceDecision);
        return new TimeLayerContextSignals(followUp, usedContext, suppressTime);
    }

    /** Intake 已判定本句在延续上一轮上下文（门店/scope 等；与当前业务合同是否 OVERRIDE 无关）。 */
    public boolean contextContinuesFromPreviousTurn() {
        return intakeFollowUp || intakeUsedPreviousContext;
    }

    /**
     * 跨族业务主权切换：禁止继承上一轮 time 窗，当前轮无显式时间时走 {@link SemanticTimeContractCheck#SOURCE_DEFAULT_MONTH_TO_DATE}
     * 或 V2/合同补齐规则。
     */
    public boolean suppressPreviousTurnTimeInheritance() {
        return suppressPreviousTurnTimeInheritance;
    }

    private static boolean suppressPreviousTurnTimeInheritance(
            SemanticSlotInheritanceDecision decision) {
        if (decision == null || !decision.isCrossFamily()) {
            return false;
        }
        return !preservesPreviousTurnTimeInheritance(decision.getMode());
    }

    /** 已注册的同族/同能力 time-only 继承模式：仍允许复制上一轮 time 窗。 */
    private static boolean preservesPreviousTurnTimeInheritance(SemanticSlotInheritanceMode mode) {
        if (mode == null) {
            return false;
        }
        return mode == SemanticSlotInheritanceMode.INHERIT_SAME_FAMILY_TIME_FOLLOWUP
                || mode == SemanticSlotInheritanceMode.INHERIT_SAME_CAPABILITY_TIME_FOLLOWUP
                || mode == SemanticSlotInheritanceMode.INHERIT_COVER_DAYS_SALES_BASELINE_FOLLOWUP;
    }
}
