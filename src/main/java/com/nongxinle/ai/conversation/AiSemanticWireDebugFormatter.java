package com.nongxinle.ai.conversation;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.springframework.util.StringUtils;

import java.util.Locale;

/**
 * Harness / debug：registered wire → 可读枚举标签（不参与主链语义归一）。
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class AiSemanticWireDebugFormatter {

    public static String toStructuredIntentDetailDebugCode(String structuredIntentDetailWire) {
        if (structuredIntentDetailWire == null || structuredIntentDetailWire.isBlank()) {
            return null;
        }
        String w = AiSemanticWireConstants.normalizeWireCase(structuredIntentDetailWire.trim());
        if (w == null) {
            return null;
        }
        if (AiSemanticWireConstants.STRUCTURED_SUPPLIER_AMOUNT_RANKING.equals(w)) {
            return "SUPPLIER_AMOUNT_RANKING";
        }
        if (AiSemanticWireConstants.STRUCTURED_PURCHASE_OVERVIEW_SUMMARY.equals(w)) {
            return "PURCHASE_OVERVIEW_SUMMARY";
        }
        if (AiSemanticWireConstants.STRUCTURED_PURCHASE_SOURCE_SUMMARY.equals(w)) {
            return "PURCHASE_SOURCE_SUMMARY";
        }
        if (AiSemanticWireConstants.STRUCTURED_PURCHASE_SOURCE_AMOUNT_QUERY.equals(w)) {
            return "PURCHASE_SOURCE_AMOUNT_QUERY";
        }
        if (AiSemanticWireConstants.STRUCTURED_PURCHASE_SOURCE_GOODS_QUERY.equals(w)) {
            return "PURCHASE_SOURCE_GOODS_QUERY";
        }
        if (AiSemanticWireConstants.STRUCTURED_PURCHASE_GOODS_AMOUNT_RANKING.equals(w)) {
            return "PURCHASE_GOODS_AMOUNT_RANKING";
        }
        if (AiSemanticWireConstants.STRUCTURED_PURCHASE_GOODS_COUNT_RANKING.equals(w)) {
            return "PURCHASE_GOODS_COUNT_RANKING";
        }
        if (AiSemanticWireConstants.STRUCTURED_PURCHASE_STORE_AMOUNT_RANKING.equals(w)) {
            return "PURCHASE_STORE_AMOUNT_RANKING";
        }
        if (AiSemanticWireConstants.STRUCTURED_PURCHASE_GOODS_ANOMALY.equals(w)) {
            return "PURCHASE_GOODS_ANOMALY";
        }
        if (AiSemanticWireConstants.STRUCTURED_PURCHASE_PRICE_ANOMALY.equals(w)) {
            return "PURCHASE_PRICE_ANOMALY";
        }
        if (AiSemanticWireConstants.STRUCTURED_PURCHASE_FREQUENCY_ANOMALY.equals(w)) {
            return "PURCHASE_FREQUENCY_ANOMALY";
        }
        if (AiSemanticWireConstants.STRUCTURED_PURCHASE_QUANTITY_ANOMALY.equals(w)) {
            return "PURCHASE_QUANTITY_ANOMALY";
        }
        if (AiSemanticWireConstants.STRUCTURED_PURCHASE_GOODS_AMOUNT_SPIKE.equals(w)) {
            return "PURCHASE_GOODS_AMOUNT_SPIKE";
        }
        if (AiSemanticWireConstants.STRUCTURED_PURCHASE_STOCK_REDUCE_MISMATCH.equals(w)) {
            return "PURCHASE_STOCK_REDUCE_MISMATCH";
        }
        if (AiSemanticWireConstants.STRUCTURED_PURCHASE_SLOW_MOVING_RISK.equals(w)) {
            return "PURCHASE_SLOW_MOVING_RISK";
        }
        if (AiSemanticWireConstants.STRUCTURED_PURCHASE_INVENTORY_OVERSTOCK_RISK.equals(w)) {
            return "PURCHASE_INVENTORY_OVERSTOCK_RISK";
        }
        if (AiSemanticWireConstants.STRUCTURED_PURCHASE_FRESHNESS_RISK.equals(w)) {
            return "PURCHASE_FRESHNESS_RISK";
        }
        if (AiSemanticWireConstants.STRUCTURED_STOCK_REDUCE_OVERVIEW_SUMMARY.equals(w)) {
            return "STOCK_REDUCE_OVERVIEW";
        }
        if (AiSemanticWireConstants.STRUCTURED_PRODUCE_CONSUME.equals(w)) {
            return "PRODUCE_CONSUME";
        }
        if (AiSemanticWireConstants.STRUCTURED_PRODUCE_OUTPUT.equals(w)) {
            return "PRODUCE_OUTPUT";
        }
        if (AiSemanticWireConstants.STRUCTURED_WASTE.equals(w)) {
            return "WASTE";
        }
        if (AiSemanticWireConstants.STRUCTURED_LOSS.equals(w)) {
            return "LOSS";
        }
        if (AiSemanticWireConstants.STRUCTURED_RETURN.equals(w)) {
            return "RETURN";
        }
        if (AiSemanticWireConstants.STRUCTURED_GOODS_OUTBOUND_RANKING.equals(w)) {
            return "GOODS_OUTBOUND_RANKING";
        }
        if (AiSemanticWireConstants.STRUCTURED_GOODS_OUTBOUND_COUNT_RANKING.equals(w)) {
            return "GOODS_OUTBOUND_COUNT_RANKING";
        }
        if (AiSemanticWireConstants.STRUCTURED_STORE_OUTBOUND_AMOUNT_RANKING.equals(w)) {
            return "STORE_OUTBOUND_AMOUNT_RANKING";
        }
        if (AiSemanticWireConstants.STRUCTURED_REVENUE_OVERVIEW_SUMMARY.equals(w)) {
            return "REVENUE_OVERVIEW_SUMMARY";
        }
        if (AiSemanticWireConstants.STRUCTURED_REVENUE_PERIOD_COMPARE.equals(w)) {
            return "REVENUE_PERIOD_COMPARE";
        }
        if (AiSemanticWireConstants.STRUCTURED_REVENUE_TREND.equals(w)) {
            return "REVENUE_TREND";
        }
        if (AiSemanticWireConstants.STRUCTURED_REVENUE_DINE_IN_OVERVIEW.equals(w)) {
            return "REVENUE_DINE_IN_OVERVIEW";
        }
        if (AiSemanticWireConstants.STRUCTURED_REVENUE_TAKEOUT_OVERVIEW.equals(w)) {
            return "REVENUE_TAKEOUT_OVERVIEW";
        }
        if (AiSemanticWireConstants.STRUCTURED_REVENUE_PLATFORM_RANKING.equals(w)) {
            return "REVENUE_PLATFORM_RANKING";
        }
        if (AiSemanticWireConstants.STRUCTURED_REVENUE_ORDER_COUNT_OVERVIEW.equals(w)) {
            return "REVENUE_ORDER_COUNT_OVERVIEW";
        }
        if (AiSemanticWireConstants.STRUCTURED_REVENUE_CUSTOMER_COUNT_OVERVIEW.equals(w)) {
            return "REVENUE_CUSTOMER_COUNT_OVERVIEW";
        }
        if (AiSemanticWireConstants.STRUCTURED_REVENUE_AVERAGE_ORDER_VALUE.equals(w)) {
            return "REVENUE_AVERAGE_ORDER_VALUE";
        }
        if (AiSemanticWireConstants.STRUCTURED_REVENUE_DAILY_AMOUNT_RANKING.equals(w)) {
            return "REVENUE_DAILY_AMOUNT_RANKING";
        }
        if (AiSemanticWireConstants.STRUCTURED_REVENUE_STORE_AMOUNT_RANKING.equals(w)) {
            return "REVENUE_STORE_AMOUNT_RANKING";
        }
        if (AiSemanticWireConstants.STRUCTURED_REVENUE_CHANNEL_BREAKDOWN.equals(w)) {
            return "REVENUE_CHANNEL_BREAKDOWN";
        }
        if (AiSemanticWireConstants.STRUCTURED_BUSINESS_DIAGNOSIS_SUMMARY.equals(w)) {
            return "BUSINESS_DIAGNOSIS_SUMMARY";
        }
        if (AiSemanticWireConstants.STRUCTURED_BUSINESS_OVERVIEW_SUMMARY.equals(w)) {
            return "BUSINESS_OVERVIEW_SUMMARY";
        }
        if (AiSemanticWireConstants.STRUCTURED_BUSINESS_OVERVIEW_STATUS.equals(w)) {
            return "BUSINESS_OVERVIEW_STATUS";
        }
        if (AiSemanticWireConstants.STRUCTURED_BUSINESS_STORE_STATUS_COMPARE.equals(w)) {
            return "BUSINESS_STORE_STATUS_COMPARE";
        }
        if (AiSemanticWireConstants.STRUCTURED_STORE_PRIORITY_RANKING.equals(w)) {
            return "STORE_PRIORITY_RANKING";
        }
        if (AiSemanticWireConstants.STRUCTURED_STORE_RISK_REASON_EXPLANATION.equals(w)) {
            return "STORE_RISK_REASON_EXPLANATION";
        }
        if (AiSemanticWireConstants.STRUCTURED_STORE_DOMAIN_ATTRIBUTION_PURCHASE.equals(w)) {
            return "STORE_DOMAIN_ATTRIBUTION_PURCHASE";
        }
        if (AiSemanticWireConstants.STRUCTURED_STORE_DOMAIN_ATTRIBUTION_STOCK_REDUCE.equals(w)) {
            return "STORE_DOMAIN_ATTRIBUTION_STOCK_REDUCE";
        }
        if (AiSemanticWireConstants.STRUCTURED_STORE_DOMAIN_ATTRIBUTION_DISH_PROFIT.equals(w)) {
            return "STORE_DOMAIN_ATTRIBUTION_DISH_PROFIT";
        }
        if (AiSemanticWireConstants.STRUCTURED_DIAGNOSIS_ACTION_SUGGESTION.equals(w)) {
            return "DIAGNOSIS_ACTION_SUGGESTION";
        }
        if (AiSemanticWireConstants.STRUCTURED_DISH_PROFIT_OVERVIEW.equals(w)) {
            return "DISH_PROFIT_OVERVIEW";
        }
        if (AiSemanticWireConstants.STRUCTURED_DISH_THEORETICAL_COST.equals(w)) {
            return "DISH_THEORETICAL_COST";
        }
        if (AiSemanticWireConstants.STRUCTURED_DISH_ACTUAL_OUTBOUND_COST.equals(w)) {
            return "DISH_ACTUAL_OUTBOUND_COST";
        }
        if (AiSemanticWireConstants.STRUCTURED_DISH_COST_GAP.equals(w)) {
            return "DISH_COST_GAP";
        }
        if (AiSemanticWireConstants.STRUCTURED_DISH_GROSS_MARGIN_QUERY.equals(w)) {
            return "DISH_GROSS_MARGIN_QUERY";
        }
        if (AiSemanticWireConstants.STRUCTURED_DISH_PROFIT_RANKING_LOW_MARGIN.equals(w)) {
            return "DISH_LOW_PROFIT_RANKING";
        }
        if (AiSemanticWireConstants.STRUCTURED_DISH_PROFIT_RANKING_HIGH_MARGIN.equals(w)) {
            return "DISH_HIGH_MARGIN_RANKING";
        }
        if (AiSemanticWireConstants.STRUCTURED_DISH_PROFIT_RANKING_HIGH_PROFIT_AMOUNT.equals(w)) {
            return "DISH_HIGH_PROFIT_AMOUNT_RANKING";
        }
        if (AiSemanticWireConstants.STRUCTURED_DISH_PROFIT_RANKING_LOW_PROFIT_AMOUNT.equals(w)) {
            return "DISH_LOW_PROFIT_AMOUNT_RANKING";
        }
        if (AiSemanticWireConstants.STRUCTURED_DISH_ACTUAL_COST_RANKING_HIGH.equals(w)) {
            return "DISH_ACTUAL_COST_RANKING";
        }
        if (AiSemanticWireConstants.STRUCTURED_DISH_ACTUAL_COST_RANKING_LOW.equals(w)) {
            return "DISH_ACTUAL_COST_RANKING_LOW";
        }
        if (AiSemanticWireConstants.STRUCTURED_DISH_THEORETICAL_COST_RANKING_HIGH.equals(w)) {
            return "DISH_THEORETICAL_COST_RANKING_HIGH";
        }
        if (AiSemanticWireConstants.STRUCTURED_DISH_THEORETICAL_COST_RANKING_LOW.equals(w)) {
            return "DISH_THEORETICAL_COST_RANKING_LOW";
        }
        if (AiSemanticWireConstants.STRUCTURED_DISH_LOW_PROFIT_REASON.equals(w)) {
            return "DISH_LOW_PROFIT_REASON";
        }
        if (AiSemanticWireConstants.STRUCTURED_DISH_GAP_RANKING_MAX.equals(w)) {
            return "DISH_GAP_RANKING_MAX";
        }
        if (AiSemanticWireConstants.STRUCTURED_DISH_SALES_COUNT_RANKING_HIGH.equals(w)) {
            return "DISH_SALES_COUNT_RANKING_HIGH";
        }
        if (AiSemanticWireConstants.STRUCTURED_DISH_SALES_AMOUNT_RANKING_HIGH.equals(w)) {
            return "DISH_SALES_AMOUNT_RANKING_HIGH";
        }
        if (AiSemanticWireConstants.STRUCTURED_DISH_SALES_COUNT_RANKING_LOW.equals(w)) {
            return "DISH_SALES_COUNT_RANKING_LOW";
        }
        if (AiSemanticWireConstants.STRUCTURED_DISH_SALES_SINGLE_DISH.equals(w)
                || AiSemanticWireConstants.STRUCTURED_DISH_SALES_STORE_SINGLE_DISH.equals(w)) {
            return "DISH_SALES_SINGLE_DISH";
        }
        if (AiSemanticWireConstants.STRUCTURED_DISH_SALES_STORE_RANKING.equals(w)) {
            return "DISH_SALES_STORE_RANKING";
        }
        if (AiSemanticWireConstants.STRUCTURED_DISH_SALES_TREND.equals(w)) {
            return "DISH_SALES_TREND";
        }
        if (AiSemanticWireConstants.STRUCTURED_DISH_SALES_OVERVIEW.equals(w)) {
            return "DISH_SALES_OVERVIEW";
        }
        if (AiSemanticWireConstants.STRUCTURED_DISH_INGREDIENT_COST_BREAKDOWN.equals(w)) {
            return "DISH_INGREDIENT_COST_BREAKDOWN";
        }
        if (AiSemanticWireConstants.STRUCTURED_WAREHOUSE_STOCK_OVERSTOCK_RISK.equals(w)) {
            return "WAREHOUSE_STOCK_OVERSTOCK_RISK";
        }
        if (AiSemanticWireConstants.STRUCTURED_WAREHOUSE_STOCK_LOW_RISK.equals(w)) {
            return "WAREHOUSE_STOCK_LOW_RISK";
        }
        if (AiSemanticWireConstants.STRUCTURED_WAREHOUSE_STOCK_REPLENISHMENT_NEEDED.equals(w)) {
            return "WAREHOUSE_STOCK_REPLENISHMENT_NEEDED";
        }
        if (AiSemanticWireConstants.STRUCTURED_STORE_STOCK_AMOUNT_RANKING.equals(w)) {
            return "STORE_STOCK_AMOUNT_RANKING";
        }
        if (AiSemanticWireConstants.STRUCTURED_STORE_STOCK_ITEM_COUNT_RANKING.equals(w)) {
            return "STORE_STOCK_ITEM_COUNT_RANKING";
        }
        if (AiSemanticWireConstants.STRUCTURED_WAREHOUSE_STOCK_AMOUNT_RANKING.equals(w)) {
            return "WAREHOUSE_STOCK_AMOUNT_RANKING";
        }
        if (AiSemanticWireConstants.STRUCTURED_WAREHOUSE_STOCK_ITEM_COUNT_RANKING.equals(w)) {
            return "WAREHOUSE_STOCK_ITEM_COUNT_RANKING";
        }
        return wireToScreamingSnake(w);
    }

    public static String wireToScreamingSnake(String wire) {
        if (!StringUtils.hasText(wire)) {
            return null;
        }
        String[] parts = wire.split("_");
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (p.isEmpty()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append('_');
            }
            sb.append(p.toUpperCase(Locale.ROOT));
        }
        return sb.isEmpty() ? null : sb.toString();
    }
}
