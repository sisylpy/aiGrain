package com.nongxinle.ai.planner;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 分门店营收一行（C-8）；真实实现由 Tool 填充。
 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class RevenuePlannerStoreRevenueRow {

    private Long departmentId;
    private String storeLabel;
    private BigDecimal amount;
}
