package com.nongxinle.ai.semantic.contract;

import com.nongxinle.ai.semantic.SemanticParserAllowedOutputContract;
import lombok.Builder;
import lombok.Value;

/** Step 2 合同选择结果（只读观测 + v2 parser 注入摘要）。 */
@Value
@Builder
public class DomainContractSelectionResult {

    String selectedDomain;
    int selectedCapabilityContractCount;
    int selectedActiveContractCount;
    int selectedKnownGapCount;
    boolean capabilityContractMissing;
    String contractSelectionSkippedReason;

    /** 注入 v2 的 allowed 摘要；capability 缺失时为 null。 */
    SemanticParserAllowedOutputContract parserAllowedOutputContract;
}
