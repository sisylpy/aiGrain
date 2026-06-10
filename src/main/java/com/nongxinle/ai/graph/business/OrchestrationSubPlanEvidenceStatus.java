package com.nongxinle.ai.graph.business;

/**
 * 多域编排子域 AnswerPlan 在 Diagnosis 侧的证据态。
 */
public enum OrchestrationSubPlanEvidenceStatus {
    /** 具备该 planType 可供 Diagnosis 消费的核心事实。 */
    VALID,
    /** 合法执行但业务无数据（可显式标注域空，不得当有效证据）。 */
    EXPLICIT_EMPTY,
    /** 空壳、占位、UNKNOWN、仅 debug/非核心字段。 */
    INVALID
}
