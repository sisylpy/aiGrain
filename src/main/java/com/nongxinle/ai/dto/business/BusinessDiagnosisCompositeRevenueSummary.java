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
public class BusinessDiagnosisCompositeRevenueSummary {

    private Double totalRevenue;
    @Builder.Default
    private List<Map<String, Object>> storeRows = new ArrayList<>();
    private Double priorPeriodTotalRevenue;
    private String compareLabel;
    private String trendDirection;
}
