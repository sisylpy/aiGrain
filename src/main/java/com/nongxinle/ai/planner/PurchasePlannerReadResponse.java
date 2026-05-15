package com.nongxinle.ai.planner;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 采购只读桥接出参（C-16）。
 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class PurchasePlannerReadResponse {

    @Builder.Default
    private PurchasePlannerReadStatus status = PurchasePlannerReadStatus.DEGRADED;

    private BigDecimal purchaseAmount;

    private Long purchaseCount;

    private String purchaseSourceType;

    @Builder.Default
    private Map<String, Object> summary = new LinkedHashMap<>();

    @Builder.Default
    private List<Map<String, Object>> focusRows = new ArrayList<>();

    @Builder.Default
    private List<Map<String, Object>> secondaryRows = new ArrayList<>();

    private String errorCode;

    private String errorMessage;
}
