package com.nongxinle.ai.semantic.intake;

import lombok.Builder;
import lombok.Value;

/**
 * Intake 结构化追问意图；由 {@link SemanticIntakeFollowUpIntentNormalizer} 唯一写入。
 * {@code reason} 字段仅 debug，不得被 Policy / Sovereignty 解析。
 */
@Value
@Builder(toBuilder = true)
public class SemanticIntakeFollowUpIntent {

    SemanticIntakeFollowUpKind kind;
    /** ACTIVE catalog contractId；time override 时为上一轮 stable contract。 */
    String targetContractId;
    String targetStructuredIntentDetailWire;
    /** {@code USE_PREVIOUS_ANCHOR} / {@code IGNORE_PREVIOUS_ANCHOR}；可空。 */
    String anchorPolicy;

    public boolean isActive() {
        return kind != null && kind != SemanticIntakeFollowUpKind.NONE;
    }
}
