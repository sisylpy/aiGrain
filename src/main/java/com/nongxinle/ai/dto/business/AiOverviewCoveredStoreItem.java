package com.nongxinle.ai.dto.business;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/** 经营概览：本轮查询覆盖到的门店及店内汇总指标（调试用，与异常清单分列）。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiOverviewCoveredStoreItem {

    private String storeName;
    /** 本子树内是否汇总到有意义的营业额与订单 */
    private Boolean hasRevenueData;
    private BigDecimal totalRevenue;
    private Integer days;
    private BigDecimal orderCount;
    private BigDecimal avgOrderCount;
    private BigDecimal avgPerCustomer;
}
