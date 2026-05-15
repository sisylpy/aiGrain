package com.nongxinle.ai.planner;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 菜品毛利只读桥接出参（C-26）。金额类可读字段为 Harness / 结构化占位；真实链路以 {@code dish_profit_analysis} Tool 为准。
 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class DishProfitPlannerReadResponse {

    @Builder.Default
    private DishProfitPlannerReadStatus status = DishProfitPlannerReadStatus.DEGRADED;

    /** 与 {@link com.nongxinle.ai.dto.business.DishProfitAnswerPlan#getPlanType()} 语义对齐时可填；Fake 可为 Harness 占位。 */
    private String planType;

    /** 综合毛利额（元，plain 字符串占位）。 */
    private String grossProfitAmount;

    /** 综合毛利率可读串（如 {@code 12.34%}）。 */
    private String grossProfitRate;

    /**
     * 标价收入汇总（元），与生产 {@code listPriceRevenue} / {@code listPriceRevenueTotal} 同源语义；
     * <strong>不得</strong>用「销量/份数」冒充销售收入。
     */
    private String salesAmount;

    /** 实际成本汇总占位（元）。 */
    private String costAmount;

    @Builder.Default
    private List<Map<String, Object>> dishRows = new ArrayList<>();

    @Builder.Default
    private List<Map<String, Object>> focusRows = new ArrayList<>();

    @Builder.Default
    private List<Map<String, Object>> secondaryRows = new ArrayList<>();

    @Builder.Default
    private Map<String, Object> summary = new LinkedHashMap<>();

    private String errorCode;

    private String errorMessage;
}
