package com.nongxinle.dto;

import lombok.Data;

/**
 * 查询某日菜品销售 + 日营收表单（{@code /getDailyFoodSalesAndRevenue}），使用 JSON Body。
 */
@Data
public class GbDepFoodDailySalesQueryRequest {

    private Integer depFatherId;
    private Integer distributerId;
    /** 可选，yyyy-MM-dd；空则为中国时区当天 */
    private String recordDate;
}
