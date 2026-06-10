package com.nongxinle.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.Data;

/**
 * 单菜日期区间每日销量查询（{@code /getDishDailySalesRange}）。
 */
@Data
public class GbDepFoodDishDailySalesRangeQueryRequest {

    /** 菜品 id（与 {@link #foodId} 二选一） */
    private Integer dishId;

    /** 批发商菜品 id */
    private Integer foodId;

    private String startDate;

    /** 区间结束日；与 {@link #stopDate} 二选一 */
    private String endDate;

    private String stopDate;

    /** 批发商 id */
    private Integer disId;

    /** 兼容字段名 */
    private Integer distributerId;

    private Integer depFatherId;

    @JsonAlias("subDepid")
    private Integer subDepId;
}
