package com.nongxinle.ai.semantic.intake;

/** SemanticIntake LLM 输出状态（Java 仅做 schema 校验，不修正语义）。 */
public enum SemanticIntakeStatus {
    READY,
    NEED_CLARIFICATION,
    INVALID
}
