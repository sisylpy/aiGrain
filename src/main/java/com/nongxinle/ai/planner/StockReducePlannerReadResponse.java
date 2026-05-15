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
 * 出库/核销只读桥接出参（C-21）。
 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class StockReducePlannerReadResponse {

    @Builder.Default
    private StockReducePlannerReadStatus status = StockReducePlannerReadStatus.DEGRADED;

    private BigDecimal grandTotalAmount;

    private BigDecimal produceTotal;

    private BigDecimal wasteTotal;

    private BigDecimal lossTotal;

    private BigDecimal returnTotal;

    private String totalsBasis;

    @Builder.Default
    private Map<String, Object> summary = new LinkedHashMap<>();

    @Builder.Default
    private List<Map<String, Object>> focusRows = new ArrayList<>();

    @Builder.Default
    private List<Map<String, Object>> secondaryRows = new ArrayList<>();

    private String errorCode;

    private String errorMessage;
}
