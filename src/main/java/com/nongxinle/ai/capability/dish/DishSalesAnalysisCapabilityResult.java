package com.nongxinle.ai.capability.dish;

import com.nongxinle.ai.dto.business.DishSalesAnswerPlan;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** AI 单菜销售 Capability 统一输出（数据源自 {@code GbDepFoodBusinessInsightService#buildInsight}）。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DishSalesAnalysisCapabilityResult {

    public static final String CARD_TYPE = DishSalesAnswerPlan.CARD_TYPE_DISH_SALES;

    private DishCostAnalysisCapabilityStatus status;
    private String reasonCode;
    private String message;

    private Integer dishId;
    private String dishName;
    private String salesPortions;
    private String salesAmount;
    private String salesUnitPrice;
    private Integer ranking;

    @Builder.Default
    private List<Map<String, Object>> candidates = new ArrayList<>();

    @Builder.Default
    private List<Map<String, Object>> rawSalesRows = new ArrayList<>();

    private Map<String, Object> rawReportSummary;
    private Map<String, Object> cardPayload;

    public static Map<String, Object> buildCardPayload(
            String dishName,
            Integer foodId,
            String salesPortions,
            String salesAmount,
            String salesUnitPrice,
            Integer ranking) {
        return buildCardPayload(
                dishName, foodId, salesPortions, salesAmount, salesUnitPrice, ranking, null, null);
    }

    public static Map<String, Object> buildCardPayload(
            String dishName,
            Integer foodId,
            String salesPortions,
            String salesAmount,
            String salesUnitPrice,
            Integer ranking,
            String timeLabel,
            String scopeLabel) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("dishName", dishName);
        if (foodId != null) {
            data.put("foodId", String.valueOf(foodId));
            data.put("dishId", String.valueOf(foodId));
        }
        data.put("soldPortionsTotal", salesPortions);
        data.put("salesPortions", salesPortions);
        data.put("salesAmount", salesAmount);
        data.put("actualRevenue", salesAmount);
        data.put("salesUnitPrice", salesUnitPrice);
        if (ranking != null) {
            data.put("ranking", ranking);
        }
        if (timeLabel != null && !timeLabel.isBlank()) {
            data.put("timeLabel", timeLabel.trim());
        }
        if (scopeLabel != null && !scopeLabel.isBlank()) {
            data.put("scopeLabel", scopeLabel.trim());
        }
        data.put("source", "depGeFoodBusiness");
        Map<String, Object> card = new LinkedHashMap<>();
        card.put("cardType", CARD_TYPE);
        card.put("data", data);
        return card;
    }
}
