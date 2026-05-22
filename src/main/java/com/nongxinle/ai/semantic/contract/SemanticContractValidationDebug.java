package com.nongxinle.ai.semantic.contract;

import lombok.Builder;
import lombok.Value;

import java.util.List;

/** 合同观测结果（P2.5：只记录，不强拦截主链）。 */
@Value
@Builder
public class SemanticContractValidationDebug {

    boolean modelContractViolation;
    String unsupportedWire;
    SemanticContractViolationCode violationCode;
    String violationReason;
    String selectedDomain;
    List<String> allowedWires;
    int allowedContractCount;
    String matchedContractId;
    /** 槽位校验缺项（{@link SemanticContractViolationCode#MISSING_REQUIRED_SLOT}）。 */
    List<String> missingSlots;
}
