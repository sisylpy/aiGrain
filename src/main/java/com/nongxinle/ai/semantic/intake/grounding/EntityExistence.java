package com.nongxinle.ai.semantic.intake.grounding;

/** 实体 DB 存在性探测结果（只读同名匹配，不做 NL 推断）。 */
public enum EntityExistence {
    NOT_FOUND,
    UNIQUE,
    AMBIGUOUS
}
