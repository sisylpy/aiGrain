package com.nongxinle.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 单菜单日五类销量批量保存（{@code /saveDishDailySalesBatch}）。
 * <p>按 {@code (departmentId, dishId, salesDate, type)} 分别 upsert；数量为 0 时删除该类型行。</p>
 */
@Data
public class GbDepFoodDishDailySalesBatchSaveRequest {

    private Integer dishId;

    private Integer foodId;

    /** 销量归属子部门 id */
    private Integer departmentId;

    /** 兼容 {@link #departmentId} */
    private Integer depId;

    @JsonAlias("subDepid")
    private Integer subDepId;

    private Integer depFatherId;

    private Integer disId;

    private Integer distributerId;

    /** 销售日 yyyy-MM-dd */
    private String salesDate;

    /** 兼容字段 */
    private String recordDate;

    private List<Item> items;

    @Data
    public static class Item {
        /** 1～5，见 {@link com.nongxinle.utils.GbConstants.FoodSalesType} */
        private Integer type;
        /** 份数；0 表示删除该类型当日记录 */
        private BigDecimal portions;
        /** 兼容字段 */
        private BigDecimal quantity;
        /** 实际单价；type4/type5 忽略并固定为 0 */
        private BigDecimal actualUnitPrice;
    }
}
