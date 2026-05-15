package com.nongxinle.ai.dto.business;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BusinessDiagnosisSignal {

    private String sourceStep;
    private BusinessDiagnosisSignalSeverity severity;
    private String reason;
    @Builder.Default
    private List<BusinessDiagnosisEvidenceRef> evidenceRefs = new ArrayList<>();
}
