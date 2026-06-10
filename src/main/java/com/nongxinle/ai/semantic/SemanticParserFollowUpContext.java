package com.nongxinle.ai.semantic;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** V2 结构化多轮上下文：辅助弱选，不替代 {@link com.nongxinle.ai.semantic.inheritance.SemanticContractTransitionPolicy} 主权。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SemanticParserFollowUpContext {

    /** 上一轮 stable ACTIVE contractId（来自 turn memory / Intake followUpIntent）。 */
    private String previousStableContractId;

    /** {@link com.nongxinle.ai.semantic.intake.SemanticIntakeFollowUpKind} 名称；NONE 时不填。 */
    private String intakeFollowUpKind;
}
