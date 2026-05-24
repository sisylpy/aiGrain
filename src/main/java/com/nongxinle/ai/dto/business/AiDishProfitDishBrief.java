package com.nongxinle.ai.dto.business;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/** 单个菜品毛利摘要（{@code dishProfitOverview} 列表项）。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiDishProfitDishBrief {

    /** 菜谱 ID；与工具行 {@code foodId} 对齐，用于去重。 */
    private String foodId;

    private String dishName;

    /** 计价销量（份），与旧版 {@code soldPortionsTotal} 一致。 */
    private String salesQty;

    /** 标价收入（元）。 */
    private String salesAmount;

    /** 理论成本（区间汇总，元）。 */
    private String theoreticalCost;

    /** 实际成本（对外展示：优先 type1+2+3，否则 type1 生产出库）。 */
    private String actualCost;

    /** type1 生产出库成本（legacy {@code actualCostAmount}，元）。 */
    private String productionActualCost;

    /** 完整实际成本 type1+2+3（{@code actualCostTotalAmount123}，元）。 */
    private String totalActualCost123;

    /** 标价收入 − 对外展示实际成本。 */
    private String grossProfitAmount;

    /** {@code grossMarginRateOnListPrice} 旧版格式化字符串或「暂无」。 */
    private String grossProfitRate;

    @Builder.Default
    private List<String> mainCostItems = new ArrayList<>();

    /** 异常说明（偏低、实耗偏高、无售价等）。 */
    private String riskReason;
}
