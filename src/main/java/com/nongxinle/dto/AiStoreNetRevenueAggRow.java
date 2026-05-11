package com.nongxinle.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 门店净营业额聚合行（AI 集团/排行用）。
 */
@Data
public class AiStoreNetRevenueAggRow {
    private Integer departmentId;
    private String departmentName;
    private BigDecimal netRevenue;
}
