package com.nongxinle.ai.semantic.contract;

/**
 * 语义能力合同生命周期状态（P1-A 只读导出；主链尚未消费）。
 */
public enum SemanticCapabilityContractStatus {
    /** 已在 Matrix 登记且可导出为 allowed entry。 */
    ACTIVE,
    /** 设计已明确，Matrix 行待增补。 */
    PLANNED,
    /** 已废弃，不应再出现在 allowed 集合。 */
    DEPRECATED,
    /**
     * 运行时由 Lexicon/PlanBuilder 支持，但当前 Matrix 无对应行；
     * 仅用于 Catalog 缺口观测，不影响 Validator。
     */
    KNOWN_GAP,
    /** 历史能力：Catalog 观测用，不进入 Parser allowed 集合。 */
    HISTORICAL
}
