package com.nongxinle.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 每日菜品销售 + 日营业额指标提交（与 Excel 导入同一套数据落库逻辑）。
 * <p><strong>末次覆盖：</strong>调用 {@code submitDailyFoodSalesAndRevenue} / {@code updateDailyFoodSalesAndRevenue} 时，
 * 会先删掉本请求范围内、当日的 {@code gb_dep_food_sales} 与 {@code gb_dep_food_goods_sales}，
 * 再以本次 {@code lines} 为准重建；无有效行时即相当于清空该范围内当日菜品销量。</p>
 * <p>{@code depFatherId} 为父部门/餐厅 ID。子部门销量归属：根级 {@code subDepId}
 *（兼容旧字段名 {@code subDepid}）或每行 {@link Line#depId}；行内无 {@code depId} 时用根级 {@code subDepId}。
 * 须能在 {@code gb_dep_food} 中匹配 {@code gb_df_dep_id}+{@code gb_df_food_id}+{@code gb_df_dep_father_id=depFatherId}，且批发商菜品与 {@code distributerId} 一致。</p>
 */
@Data
public class GbDepFoodDailySalesSubmitRequest {

    /** 记录日 yyyy-MM-dd */
    private String recordDate;

    /** 父部门/餐厅 ID */
    private Integer depFatherId;

    /**
     * 当前页的子部门/门店 ID（可选）。与子部门菜品列表对齐：传入时仅用该子部门范围内的 {@code gb_dep_food} 校验；
     * 且 {@link Line#getDepId()} 为空时本条销量默认记入该子部门。
     */
    @JsonAlias("subDepid")
    private Integer subDepId;

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
        /**
         * 消费类型 1～5，见 {@link com.nongxinle.utils.GbConstants.FoodSalesType}。
         * 同一菜品同日可分别存在多类型行（按 type 唯一键）。
         */
        private Integer type;
        /** 实际单价（元/份）；折扣/会员场景可选，默认按标价 */
        private BigDecimal actualUnitPrice;
    }
}
