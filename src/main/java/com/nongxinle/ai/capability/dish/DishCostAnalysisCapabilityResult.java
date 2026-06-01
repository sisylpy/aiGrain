package com.nongxinle.ai.capability.dish;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * AI 菜品成本分析 Capability 统一输出。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DishCostAnalysisCapabilityResult {

    public static final String CARD_TYPE_DISH_COST_ANALYSIS = "DISH_COST_ANALYSIS_CARD";

    private DishCostAnalysisCapabilityStatus status;
    /** 细分原因：{@code dish_not_found}、{@code no_data}、{@code missing_dish_selector} 等。 */
    private String reasonCode;
    private String message;

    private Integer dishId;
    private String dishName;
    private String salesPortions;
    private String salesAmount;
    private String salesUnitPrice;
    private String theoryCostPerPortion;
    private String actualCostPerPortion;
    private String actualCostAmount;
    private String diffCostPerPortion;

    @Builder.Default
    private List<Map<String, Object>> ingredientRows = new ArrayList<>();

    /** 最短板配料汇总（来自 report salesDishRows[].bottle）。 */
    private Map<String, Object> bottle;

    @Builder.Default
    private List<Map<String, Object>> candidates = new ArrayList<>();

    /** 可选：scopeSalesSubtotals、区间参数等摘要。 */
    private Map<String, Object> rawReportSummary;

    /** 前端卡片预留结构：{@code cardType} + {@code data}。 */
    private Map<String, Object> cardPayload;

    public static Map<String, Object> buildCardPayload(
            String dishName,
            String salesPortions,
            String salesAmount,
            String salesUnitPrice,
            String theoryCostPerPortion,
            String actualCostPerPortion,
            String diffCostPerPortion,
            List<Map<String, Object>> ingredientRows) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("dishName", dishName);
        data.put("salesPortions", salesPortions);
        data.put("salesAmount", salesAmount);
        data.put("salesUnitPrice", salesUnitPrice);
        data.put("theoryCostPerPortion", theoryCostPerPortion);
        data.put("actualCostPerPortion", actualCostPerPortion);
        data.put("diffCostPerPortion", diffCostPerPortion);
        data.put("ingredientRows", ingredientRows == null ? List.of() : ingredientRows);
        Map<String, Object> card = new LinkedHashMap<>();
        card.put("cardType", CARD_TYPE_DISH_COST_ANALYSIS);
        card.put("data", data);
        return card;
    }
}
