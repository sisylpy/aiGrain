package com.nongxinle.ai.dto.business;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BusinessDiagnosisSignals {

    private BusinessDiagnosisSignal revenueWeakSignal;
    private BusinessDiagnosisSignal purchaseHighSignal;
    private BusinessDiagnosisSignal stockReduceHighSignal;
    private BusinessDiagnosisSignal dishProfitLowSignal;
    private BusinessDiagnosisSignal dataIncompleteSignal;
}
