package com.nongxinle.ai.dto.business;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BusinessDiagnosisCompositeDishProfitSummary {

    private Double grossProfitAmount;
    private Double grossProfitRate;
    private Double salesAmount;
    private Double costAmount;
    @Builder.Default
    private List<Map<String, Object>> focusRows = new ArrayList<>();
}
