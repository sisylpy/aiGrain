package com.nongxinle.ai.planner;

import com.nongxinle.ai.conversation.AiQuerySemanticLexicon;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Harness-only 假桥：验证 {@link DishProfitPlannerReadRequest} → {@link DishProfitPlannerReadResponse} 闭环。
 * <strong>非</strong>真实 {@link com.nongxinle.ai.graph.business.DishProfitQueryToolExecutor} / DB。
 * <p>{@code salesAmount} 明示为标价收入汇总语义（与生产 {@code listPriceRevenueTotal} 对齐），不将份数排行伪装为销售额。</p>
 */
public final class FakeDishProfitPlannerReadBridge implements DishProfitPlannerReadBridge {

    public static final String HARNESS_HONESTY_FAKE_READ_BRIDGE_OK = "FAKE_READ_BRIDGE_OK";

    private FakeDishProfitPlannerReadBridge() {
    }

    public static FakeDishProfitPlannerReadBridge instance() {
        return Holder.INSTANCE;
    }

    @Override
    public DishProfitPlannerReadResponse readDishProfit(DishProfitPlannerReadRequest request) {
        String timeLabel =
                request != null && request.getTimeLabel() != null && !request.getTimeLabel().isBlank()
                        ? request.getTimeLabel().trim()
                        : "harness_fake_dish_profit_time";
        String structured =
                request != null && request.getStructuredIntentDetail() != null
                                && !request.getStructuredIntentDetail().isBlank()
                        ? request.getStructuredIntentDetail().trim()
                        : AiQuerySemanticLexicon.STRUCTURED_DISH_PROFIT_OVERVIEW;
        String dishHint =
                request != null && request.getMentionedDishName() != null && !request.getMentionedDishName().isBlank()
                        ? request.getMentionedDishName().trim()
                        : null;
        String metricType =
                request != null && request.getDishProfitMetricType() != null
                                && !request.getDishProfitMetricType().isBlank()
                        ? request.getDishProfitMetricType().trim()
                        : "OVERVIEW";

        String listPriceRevenueTotal = "8800.00";
        String actualCostTotal = "5200.00";
        String grossProfitAmt = "3600.00";
        String grossProfitRateStr = "40.91%";

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("timeLabel", timeLabel);
        summary.put("structuredIntentDetail", structured);
        summary.put("dishProfitMetricType", metricType);
        summary.put("listPriceRevenueTotal", listPriceRevenueTotal);
        summary.put("totalActualCostType1", actualCostTotal);
        summary.put("portfolioGrossProfitAmount", grossProfitAmt);
        summary.put(
                "salesAmountSemantics",
                "list_price_revenue_aggregate_matches_production_Tool_field_listPriceRevenueTotal_not_sold_units_only");
        if (dishHint != null) {
            summary.put("mentionedDishNameEcho", dishHint);
        }

        Map<String, Object> row = new LinkedHashMap<>();
        row.put("dishName", "Harness Fake Dish A");
        row.put("listPriceRevenue", listPriceRevenueTotal);
        row.put("soldPortionsTotal", "120");
        row.put("theoryCostAmount", "5000.00");
        row.put("actualCostAmount", actualCostTotal);
        row.put("blendedGrossMarginRateOnListPrice", "40.91");

        return DishProfitPlannerReadResponse.builder()
                .status(DishProfitPlannerReadStatus.OK)
                .planType("HARNESS_FAKE_DISH_PROFIT_OVERVIEW")
                .grossProfitAmount(grossProfitAmt)
                .grossProfitRate(grossProfitRateStr)
                .salesAmount(listPriceRevenueTotal)
                .costAmount(actualCostTotal)
                .dishRows(List.of(row))
                .focusRows(List.of(row))
                .secondaryRows(List.of())
                .summary(summary)
                .errorCode(null)
                .errorMessage(null)
                .build();
    }

    private static final class Holder {
        private static final FakeDishProfitPlannerReadBridge INSTANCE = new FakeDishProfitPlannerReadBridge();
    }
}
