package com.nongxinle.ai.semantic.contract;

import lombok.Builder;
import lombok.Singular;
import lombok.Value;

import java.util.List;

/** 只读导出统计（Harness / debug 观测；不驱动 Validator）。 */
@Value
@Builder
public class SemanticCapabilityContractExportSummary {

    /** 通用域码（PURCHASE 等 exporter 均可填）。 */
    String domainCode;

    int exportedContractCount;
    int activeContractCount;
    int plannedContractCount;
    int knownGapContractCount;

    /** 采购域 legacy 字段（Harness 兼容）。 */
    int exportedPurchaseContractCount;
    int activePurchaseContractCount;
    int plannedPurchaseContractCount;
    int knownGapPurchaseContractCount;

    @Singular("knownGapMarker")
    List<String> knownGapMarkers;
}
