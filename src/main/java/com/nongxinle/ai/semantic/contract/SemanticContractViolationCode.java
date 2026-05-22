package com.nongxinle.ai.semantic.contract;

/**
 * ContractValidator / Harness 违例码（P1-A 定义；P2 shadow / P3 enforce 再接入）。
 */
public enum SemanticContractViolationCode {
    MODEL_CONTRACT_VIOLATION,
    /** P4-J2：allowedContracts 非空但 Parser 未输出 semanticSlots.selectedContractId。 */
    MISSING_SELECTED_CONTRACT_ID,
    /** P4-J2：selectedContractId 不在 ACTIVE catalog，或与所选 entry 槽位不一致。 */
    UNSUPPORTED_CONTRACT,
    /** P4-J2：同 wire/槽位命中多条 ACTIVE 合同且无法唯一定位。 */
    AMBIGUOUS_CONTRACT_MATCH,
    UNSUPPORTED_WIRE,
    UNSUPPORTED_SLOT_COMBO,
    MISSING_REQUIRED_SLOT,
    ANCHOR_CONTRACT_MISMATCH,
    NO_CAPABILITY_CONTRACT,
    ROUTE_UNKNOWN,
    ROUTE_AMBIGUOUS,
    PLANNED_CAPABILITY_SELECTED
}
