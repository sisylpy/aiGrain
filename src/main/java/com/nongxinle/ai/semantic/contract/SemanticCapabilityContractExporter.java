package com.nongxinle.ai.semantic.contract;

import java.util.List;

/**
 * 各域 Matrix → 机器可读合同（P1-A 只读；主链不消费）。
 */
public interface SemanticCapabilityContractExporter {

    String domain();

    List<SemanticCapabilityContract> exportActiveContracts();

    List<SemanticCapabilityContract> exportKnownGapContracts();

    List<SemanticCapabilityContract> exportPlannedContracts();

    default List<SemanticCapabilityContract> exportAllContracts() {
        List<SemanticCapabilityContract> active = exportActiveContracts();
        List<SemanticCapabilityContract> planned = exportPlannedContracts();
        List<SemanticCapabilityContract> gaps = exportKnownGapContracts();
        int size = active.size() + planned.size() + gaps.size();
        java.util.ArrayList<SemanticCapabilityContract> all = new java.util.ArrayList<>(size);
        all.addAll(active);
        all.addAll(planned);
        all.addAll(gaps);
        return all;
    }

    SemanticCapabilityContractExportSummary exportSummary();
}
