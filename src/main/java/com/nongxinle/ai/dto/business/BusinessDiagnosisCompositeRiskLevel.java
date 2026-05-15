package com.nongxinle.ai.dto.business;

/**
 * C-37：组合经营诊断 Composite AnswerPlan — 风险档位（先最小规则；不调用 LLM）。
 */
public enum BusinessDiagnosisCompositeRiskLevel {
    NORMAL_OBSERVATION,
    UNKNOWN,
    INSUFFICIENT_DATA,
    MEDIUM,
    HIGH
}
