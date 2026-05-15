package com.nongxinle.ai.dto.business;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BusinessDiagnosisDomainCoverage {

    private BusinessDiagnosisDataDomain domain;
    private boolean success;
    private boolean realToolInvoked;
    private String stepId;
    private String usedTool;
    private String degradedReason;
}
