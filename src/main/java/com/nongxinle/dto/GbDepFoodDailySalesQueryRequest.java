package com.nongxinle.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
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
    /** 可选；与按子部门拉菜品一致时传入（兼容 JSON 字段名 {@code subDepid}） */
    @JsonAlias("subDepid")
    private Integer subDepId;
}
