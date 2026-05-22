package com.nongxinle.ai.semantic.contract;

/**
 * ContractValidator / Harness 违例码（P1-A 定义；P2 shadow / P3 enforce 再接入）。
 */
public enum SemanticContractViolationCode {
    MODEL_CONTRACT_VIOLATION,
    UNSUPPORTED_WIRE,
    UNSUPPORTED_SLOT_COMBO,
    MISSING_REQUIRED_SLOT,
    ANCHOR_CONTRACT_MISMATCH,
    NO_CAPABILITY_CONTRACT,
    ROUTE_UNKNOWN,
    ROUTE_AMBIGUOUS,
    PLANNED_CAPABILITY_SELECTED
}
