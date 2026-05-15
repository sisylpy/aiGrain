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
public class BusinessDiagnosisCompositePurchaseSummary {

    private Double purchaseAmount;
    private Long purchaseCount;
    private String purchaseSourceType;
    @Builder.Default
    private List<Map<String, Object>> focusRows = new ArrayList<>();
}
