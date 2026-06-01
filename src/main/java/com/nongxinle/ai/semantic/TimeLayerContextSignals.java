package com.nongxinle.ai.semantic;

import com.nongxinle.ai.semantic.intake.SemanticIntakeResult;

/**
 * Time Layer 与 Business Frame 分层：Intake 判定的上下文延续信号，供 {@link SemanticTimeContractCheck} 独立决策是否继承上一轮时间窗。
 * <p>Business Frame sovereign 只锁定当前合同；不等于 time 必须重置为 DEFAULT_MONTH_TO_DATE。
 */
public record TimeLayerContextSignals(boolean intakeFollowUp, boolean intakeUsedPreviousContext) {

    public static TimeLayerContextSignals empty() {
        return new TimeLayerContextSignals(false, false);
    }

    public static TimeLayerContextSignals fromIntake(SemanticIntakeResult intake) {
        if (intake == null) {
            return empty();
        }
        return new TimeLayerContextSignals(
                Boolean.TRUE.equals(intake.getIsFollowUp()),
                Boolean.TRUE.equals(intake.getUsedPreviousContext()));
    }

    /** Intake 已判定本句在延续上一轮上下文（与当前业务合同是否 OVERRIDE 无关）。 */
    public boolean contextContinuesFromPreviousTurn() {
        return intakeFollowUp || intakeUsedPreviousContext;
    }
}
