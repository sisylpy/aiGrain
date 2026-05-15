package com.nongxinle.ai.dto.business;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BusinessDiagnosisCompositeStockReduceSummary {

    private Double grandTotalAmount;
    private Double produceTotal;
    private Double wasteTotal;
    private Double lossTotal;
    private Double returnTotal;
    private String totalsBasis;
}
