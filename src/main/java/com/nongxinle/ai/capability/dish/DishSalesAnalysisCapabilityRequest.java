package com.nongxinle.ai.capability.dish;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * AI 单菜销售分析入参；菜名/foodId 来自 contract-locked anchor，Adapter 不读用户原文。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DishSalesAnalysisCapabilityRequest {

    private String startDate;
    private String stopDate;
    private String endDate;
    private Integer disId;
    private Integer depFatherId;
    private String searchDepId;
    private Integer subDepId;
    @Builder.Default
    private String sortBy = "sales";
    @Builder.Default
    private String sortOrder = "desc";
    private String dishName;
    private Integer foodId;
}
