package com.nongxinle.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 单条菜品销量写入（{@code /upsertDishSalesLine}、{@code /deleteDishSalesLine}）。
 * <p>与日提交 {@link GbDepFoodDailySalesSubmitRequest} 不同：不删当日其它行，仅 upsert 或删除
 * {@code (depId, foodId, recordDate, type)} 唯一键对应的一条。</p>
 */
@Data
public class GbDepFoodDishSalesLineRequest {

    /** 记录日 yyyy-MM-dd */
    private String recordDate;

    /** 父部门/餐厅 ID */
    private Integer depFatherId;

    /** 子部门 ID；与 {@link #depId} 二选一，行内优先 {@code depId} */
    @JsonAlias("subDepid")
    private Integer subDepId;

    /** 子部门 ID（销量归属） */
    private Integer depId;

    /** 批发商 ID */
    private Integer distributerId;

    /** 批发商菜品 ID */
    private Integer foodId;

    /**
     * 消费类型：{@code 1} 正常销售 … {@code 5} 菜品型员工餐（见 {@link com.nongxinle.utils.GbConstants.FoodSalesType}）。
     * 同一菜品同日按 type 分别存一行，互不覆盖。
     */
    private Integer type;

    /** 本次份数；{@code upsertDishSalesLine} 必填且 ≥0；填 0 时删除该 type 行（有则删，无则 no-op） */
    private BigDecimal quantity;

    /**
     * 数量模式：{@code ADD}（默认，累加）或 {@code SET}（覆盖为 {@code quantity}）。
     * 仅 {@code upsertDishSalesLine} 使用。
     */
    private String quantityMode;

    /** 实际单价（元/份）；仅 type=1 等经营型可选，默认按部门菜标价 */
    private BigDecimal actualUnitPrice;
}
