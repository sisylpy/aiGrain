package com.nongxinle.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 每日菜品销售 + 日营业额指标提交（与 Excel 导入同一套数据落库逻辑）。
 * <p>{@code depFatherId} 为父部门/餐厅 ID，与日营收 {@code gb_ai_daily_revenue_department_id}、菜品销售 {@code gb_dfs_dep_father_id} 一致。</p>
 */
@Data
public class GbDepFoodDailySalesSubmitRequest {

    /** 记录日 yyyy-MM-dd */
    private String recordDate;

    /** 父部门/餐厅 ID */
    private Integer depFatherId;

    /** 批发商/分配者 ID（与 gb_dfs_distributer_id 一致） */
    private Integer distributerId;

    /** 各子部门菜品销量；{@code quantity} 为份数，≤0 的忽略 */
    private List<Line> lines;

    /** 堂食订单数；null 表示不覆盖已有值 */
    private Integer dineInOrders;

    /** 堂食顾客数 */
    private Integer dineInCustomers;

    /** 外卖营业额（元） */
    private BigDecimal takeoutRevenue;

    /** 外卖订单数 */
    private Integer takeoutOrders;

    /** 平台抽成（元） */
    private BigDecimal platformFee;

    /** 备注 */
    private String notes;

    @Data
    public static class Line {
        /** 子部门 ID（门店） */
        private Integer depId;
        /** 批发商菜品 ID（gb_df_food_id） */
        private Integer foodId;
        /** 销售份数 */
        private BigDecimal quantity;
    }
}
