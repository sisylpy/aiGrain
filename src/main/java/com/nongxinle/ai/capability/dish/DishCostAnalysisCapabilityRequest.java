package com.nongxinle.ai.capability.dish;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * AI 已解析的确定性入参；Adapter 不做用户原文语义推断。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DishCostAnalysisCapabilityRequest {

    private String startDate;
    /** 区间结束日；与 {@link #endDate} 二选一，优先 stopDate。 */
    private String stopDate;
    /** {@link #stopDate} 别名。 */
    private String endDate;
    private Integer disId;
    private Integer depFatherId;
    /** 单子部门 scope；与 {@link #subDepId} 等价，优先 searchDepId。 */
    private String searchDepId;
    private Integer subDepId;
    @Builder.Default
    private String sortBy = "sales";
    @Builder.Default
    private String sortOrder = "desc";
    /** 已在 contract / semantic 层解析出的菜名，用于 salesDishRows 确定性过滤。 */
    private String dishName;
    /** P1 预留：直接按 dishId 命中 salesDishRows。 */
    private Integer foodId;
}
