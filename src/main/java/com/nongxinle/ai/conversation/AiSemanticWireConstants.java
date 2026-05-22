package com.nongxinle.ai.conversation;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.springframework.util.StringUtils;

import java.util.Locale;
import java.util.Set;

/**
 * Registered canonical structuredIntentDetail wire 常量（与 ACTIVE {@link com.nongxinle.ai.semantic.contract.SemanticCapabilityContract} 对齐）。
 * <p>P4-C：主链 SSOT；不含 alias / compat 映射。</p>
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class AiSemanticWireConstants {

    public static final String STRUCTURED_PURCHASE_OVERVIEW_SUMMARY = "purchase_overview_summary";
    public static final String STRUCTURED_PURCHASE_SOURCE_SUMMARY = "purchase_source_summary";
    public static final String STRUCTURED_PURCHASE_SOURCE_AMOUNT_QUERY = "purchase_source_amount_query";
    public static final String STRUCTURED_PURCHASE_SOURCE_GOODS_QUERY = "purchase_source_goods_query";
    public static final String STRUCTURED_PURCHASE_GOODS_AMOUNT_RANKING = "purchase_goods_amount_ranking";
    public static final String STRUCTURED_PURCHASE_GOODS_COUNT_RANKING = "purchase_goods_count_ranking";
    public static final String STRUCTURED_PURCHASE_GOODS_ANOMALY = "purchase_goods_anomaly";
    public static final String STRUCTURED_PURCHASE_PRICE_ANOMALY = "purchase_price_anomaly";
    public static final String STRUCTURED_PURCHASE_FREQUENCY_ANOMALY = "purchase_frequency_anomaly";
    public static final String STRUCTURED_PURCHASE_QUANTITY_ANOMALY = "purchase_quantity_anomaly";
    public static final String STRUCTURED_PURCHASE_GOODS_AMOUNT_SPIKE = "purchase_goods_amount_spike";
    public static final String STRUCTURED_PURCHASE_STOCK_REDUCE_MISMATCH = "purchase_stock_reduce_mismatch";
    public static final String STRUCTURED_PURCHASE_SLOW_MOVING_RISK = "purchase_slow_moving_risk";
    public static final String STRUCTURED_PURCHASE_INVENTORY_OVERSTOCK_RISK = "purchase_inventory_overstock_risk";
    public static final String STRUCTURED_PURCHASE_FRESHNESS_RISK = "purchase_freshness_risk";
    public static final String STRUCTURED_PURCHASE_STORE_AMOUNT_RANKING = "purchase_store_amount_ranking";
    public static final String STRUCTURED_SUPPLIER_AMOUNT_RANKING = "supplier_amount_ranking";
    public static final String STRUCTURED_SUPPLIER_RANKING = STRUCTURED_SUPPLIER_AMOUNT_RANKING;

    public static final String SOURCE_SELF_PURCHASE = "SELF_PURCHASE";
    public static final String SOURCE_SUPPLIER_PURCHASE = "SUPPLIER_PURCHASE";
    public static final String SOURCE_ALL = "ALL";

    public static final String DETAIL_WANTED_SOURCE_BREAKDOWN = "SOURCE_BREAKDOWN";
    public static final String DETAIL_WANTED_SUPPLIER_BREAKDOWN = "SUPPLIER_BREAKDOWN";
    public static final String DETAIL_WANTED_SUPPLIER_UNIT_PRICE = "SUPPLIER_UNIT_PRICE";

    public static final String STRUCTURED_STOCK_REDUCE_OVERVIEW_SUMMARY = "stock_reduce_overview";
    public static final String STRUCTURED_PRODUCE_CONSUME = "produce_consume";
    public static final String STRUCTURED_PRODUCE_OUTPUT = "produce_output";
    public static final String STRUCTURED_WASTE = "waste";
    public static final String STRUCTURED_LOSS = "loss";
    public static final String STRUCTURED_RETURN = "return";
    public static final String STRUCTURED_GOODS_OUTBOUND_RANKING = "goods_outbound_ranking";
    public static final String STRUCTURED_GOODS_OUTBOUND_COUNT_RANKING = "goods_outbound_count_ranking";
    public static final String STRUCTURED_STORE_OUTBOUND_AMOUNT_RANKING = "store_outbound_amount_ranking";

    public static final String STRUCTURED_DISH_PROFIT_OVERVIEW = "dish_profit_overview";
    public static final String STRUCTURED_DISH_THEORETICAL_COST = "dish_theoretical_cost";
    public static final String STRUCTURED_DISH_ACTUAL_OUTBOUND_COST = "dish_actual_outbound_cost";
    public static final String STRUCTURED_DISH_COST_GAP = "dish_cost_gap";
    public static final String STRUCTURED_DISH_GROSS_MARGIN_QUERY = "dish_gross_margin_query";
    public static final String STRUCTURED_DISH_PROFIT_RANKING_LOW_MARGIN = "dish_profit_ranking_low_margin";
    public static final String STRUCTURED_DISH_PROFIT_RANKING_HIGH_MARGIN = "dish_profit_ranking_high_margin";
    public static final String STRUCTURED_DISH_LOW_PROFIT_REASON = "dish_low_profit_reason";
    public static final String STRUCTURED_DISH_ACTUAL_COST_RANKING_HIGH = "dish_actual_cost_ranking_high";
    public static final String STRUCTURED_DISH_ACTUAL_COST_RANKING_LOW = "dish_actual_cost_ranking_low";
    public static final String STRUCTURED_DISH_THEORETICAL_COST_RANKING_HIGH = "dish_theoretical_cost_ranking_high";
    public static final String STRUCTURED_DISH_THEORETICAL_COST_RANKING_LOW = "dish_theoretical_cost_ranking_low";
    public static final String STRUCTURED_DISH_GAP_RANKING_MAX = "dish_gap_ranking_max";
    /** @deprecated 历史字面量；不在 REGISTERED 集合；须用 {@link #STRUCTURED_DISH_SALES_COUNT_RANKING_HIGH}。 */
    @Deprecated
    public static final String STRUCTURED_DISH_SALES_RANKING = "dish_sales_ranking";
    public static final String STRUCTURED_DISH_SALES_COUNT_RANKING_HIGH = "dish_sales_count_ranking_high";
    public static final String STRUCTURED_DISH_SALES_AMOUNT_RANKING_HIGH = "dish_sales_amount_ranking_high";
    public static final String STRUCTURED_DISH_SALES_COUNT_RANKING_LOW = "dish_sales_count_ranking_low";
    public static final String STRUCTURED_DISH_SALES_OVERVIEW = "dish_sales_overview";
    public static final String STRUCTURED_DISH_SALES_RANKING_HIGH = "dish_sales_ranking_high";
    public static final String STRUCTURED_DISH_SALES_RANKING_LOW = "dish_sales_ranking_low";
    public static final String STRUCTURED_DISH_SALES_SINGLE_DISH = "dish_sales_single_dish";
    public static final String STRUCTURED_DISH_SALES_STORE_RANKING = "dish_sales_store_ranking";
    public static final String STRUCTURED_DISH_SALES_STORE_SINGLE_DISH = "dish_sales_store_single_dish";
    public static final String STRUCTURED_DISH_SALES_TREND = "dish_sales_trend";
    public static final String STRUCTURED_DISH_INGREDIENT_COST_BREAKDOWN = "dish_ingredient_cost_breakdown";

    public static final String STRUCTURED_REVENUE_OVERVIEW_SUMMARY = "revenue_overview_summary";
    /** @deprecated 历史别名；不在 REGISTERED 集合；须用 {@link #STRUCTURED_REVENUE_OVERVIEW_SUMMARY}。 */
    @Deprecated
    public static final String STRUCTURED_REVENUE_OVERVIEW = "revenue_overview";
    public static final String STRUCTURED_REVENUE_SINGLE_STORE_OVERVIEW = "revenue_single_store_overview";
    public static final String STRUCTURED_REVENUE_STORE_COMPARE = "revenue_store_compare";
    public static final String STRUCTURED_REVENUE_PERIOD_COMPARE = "revenue_period_compare";
    public static final String STRUCTURED_REVENUE_DAILY_RANKING = "revenue_daily_ranking";
    public static final String STRUCTURED_REVENUE_TREND = "revenue_trend";
    public static final String STRUCTURED_REVENUE_DINE_IN_OVERVIEW = "revenue_dine_in_overview";
    public static final String STRUCTURED_REVENUE_TAKEOUT_OVERVIEW = "revenue_takeout_overview";
    public static final String STRUCTURED_REVENUE_PLATFORM_RANKING = "revenue_platform_ranking";
    public static final String STRUCTURED_REVENUE_ORDER_COUNT_OVERVIEW = "revenue_order_count_overview";
    public static final String STRUCTURED_REVENUE_CUSTOMER_COUNT_OVERVIEW = "revenue_customer_count_overview";
    public static final String STRUCTURED_REVENUE_AVERAGE_ORDER_VALUE = "revenue_average_order_value";
    public static final String STRUCTURED_REVENUE_DAILY_AMOUNT_RANKING = "revenue_daily_amount_ranking";
    public static final String STRUCTURED_REVENUE_STORE_AMOUNT_RANKING = "revenue_store_amount_ranking";
    public static final String STRUCTURED_REVENUE_CHANNEL_BREAKDOWN = "revenue_channel_breakdown";

    public static final String STRUCTURED_BUSINESS_DIAGNOSIS_SUMMARY = "business_diagnosis_summary";
    public static final String STRUCTURED_BUSINESS_COST_PRESSURE_DIAGNOSIS = "business_cost_pressure_diagnosis";
    public static final String STRUCTURED_BUSINESS_STORE_COMPARE_DIAGNOSIS = "business_store_status_compare_diagnosis";
    public static final String STRUCTURED_BUSINESS_OVERVIEW_SUMMARY = "business_overview_summary";
    public static final String STRUCTURED_BUSINESS_OVERVIEW_STATUS = "business_overview_status";
    public static final String STRUCTURED_BUSINESS_STORE_STATUS_COMPARE = "business_store_status_compare";

    public static final String STRUCTURED_STORE_PRIORITY_RANKING = "store_priority_ranking";
    /** @deprecated 历史别名；不在 REGISTERED 集合。 */
    @Deprecated
    public static final String STRUCTURED_STORE_RISK_RANKING = "store_risk_ranking";

    /** D-13.2：承接 STORE 锚点的门店风险原因解释（BD-C / BD-D）。 */
    public static final String STRUCTURED_STORE_RISK_REASON_EXPLANATION = "store_risk_reason_explanation";

    public static final String STRUCTURED_STORE_DOMAIN_ATTRIBUTION_PURCHASE = "store_domain_attribution_purchase";
    public static final String STRUCTURED_STORE_DOMAIN_ATTRIBUTION_STOCK_REDUCE = "store_domain_attribution_stock_reduce";
    public static final String STRUCTURED_STORE_DOMAIN_ATTRIBUTION_DISH_PROFIT = "store_domain_attribution_dish_profit";

    /** BD-K：诊断内改进行动建议（宣读 DiagnosisPlan.actionSuggestions）。 */
    public static final String STRUCTURED_DIAGNOSIS_ACTION_SUGGESTION = "diagnosis_action_suggestion";

    public static final String STRUCTURED_WAREHOUSE_STOCK_OVERVIEW = "warehouse_stock_overview";
    public static final String STRUCTURED_WAREHOUSE_STOCK_LOW_RISK = "warehouse_stock_low_risk";
    public static final String STRUCTURED_WAREHOUSE_STOCK_REPLENISHMENT_NEEDED = "warehouse_stock_replenishment_needed";
    public static final String STRUCTURED_WAREHOUSE_STOCK_OVERSTOCK_RISK = "warehouse_stock_overstock_risk";
    public static final String STRUCTURED_STORE_STOCK_AMOUNT_RANKING = "store_stock_amount_ranking";
    public static final String STRUCTURED_STORE_STOCK_ITEM_COUNT_RANKING = "store_stock_item_count_ranking";
    public static final String STRUCTURED_WAREHOUSE_STOCK_AMOUNT_RANKING = "warehouse_stock_amount_ranking";
    public static final String STRUCTURED_WAREHOUSE_STOCK_ITEM_COUNT_RANKING = "warehouse_stock_item_count_ranking";
    public static final String STRUCTURED_GOODS_STOCK_AMOUNT_RANKING_LOW = "goods_stock_amount_ranking_low";
    public static final String STRUCTURED_WAREHOUSE_NEAR_EXPIRY = "warehouse_near_expiry";

    private static final Set<String> PURCHASE_OVERVIEW_DOMAIN_CANONICAL_WIRES =
            Set.of(
                    STRUCTURED_PURCHASE_OVERVIEW_SUMMARY,
                    STRUCTURED_PURCHASE_SOURCE_SUMMARY,
                    STRUCTURED_PURCHASE_SOURCE_AMOUNT_QUERY,
                    STRUCTURED_PURCHASE_SOURCE_GOODS_QUERY,
                    STRUCTURED_PURCHASE_GOODS_AMOUNT_RANKING,
                    STRUCTURED_PURCHASE_GOODS_COUNT_RANKING,
                    STRUCTURED_PURCHASE_GOODS_ANOMALY,
                    STRUCTURED_PURCHASE_PRICE_ANOMALY,
                    STRUCTURED_PURCHASE_FREQUENCY_ANOMALY,
                    STRUCTURED_PURCHASE_QUANTITY_ANOMALY,
                    STRUCTURED_PURCHASE_GOODS_AMOUNT_SPIKE,
                    STRUCTURED_PURCHASE_STOCK_REDUCE_MISMATCH,
                    STRUCTURED_PURCHASE_SLOW_MOVING_RISK,
                    STRUCTURED_PURCHASE_INVENTORY_OVERSTOCK_RISK,
                    STRUCTURED_PURCHASE_FRESHNESS_RISK,
                    STRUCTURED_PURCHASE_STORE_AMOUNT_RANKING,
                    STRUCTURED_SUPPLIER_AMOUNT_RANKING);

    private static final Set<String> PURCHASE_ANOMALY_DETECTION_WIRES =
            Set.of(
                    STRUCTURED_PURCHASE_PRICE_ANOMALY,
                    STRUCTURED_PURCHASE_FREQUENCY_ANOMALY,
                    STRUCTURED_PURCHASE_QUANTITY_ANOMALY,
                    STRUCTURED_PURCHASE_GOODS_AMOUNT_SPIKE);

    /** ACTIVE SemanticCapabilityContract 登记的 canonical wire（不含历史 alias 字面量）。 */
    private static final Set<String> REGISTERED_CANONICAL_WIRES =
            Set.of(
                    STRUCTURED_PURCHASE_OVERVIEW_SUMMARY,
                    STRUCTURED_PURCHASE_SOURCE_SUMMARY,
                    STRUCTURED_PURCHASE_SOURCE_AMOUNT_QUERY,
                    STRUCTURED_PURCHASE_SOURCE_GOODS_QUERY,
                    STRUCTURED_PURCHASE_GOODS_AMOUNT_RANKING,
                    STRUCTURED_PURCHASE_GOODS_COUNT_RANKING,
                    STRUCTURED_PURCHASE_GOODS_ANOMALY,
                    STRUCTURED_PURCHASE_PRICE_ANOMALY,
                    STRUCTURED_PURCHASE_FREQUENCY_ANOMALY,
                    STRUCTURED_PURCHASE_QUANTITY_ANOMALY,
                    STRUCTURED_PURCHASE_GOODS_AMOUNT_SPIKE,
                    STRUCTURED_PURCHASE_STOCK_REDUCE_MISMATCH,
                    STRUCTURED_PURCHASE_SLOW_MOVING_RISK,
                    STRUCTURED_PURCHASE_INVENTORY_OVERSTOCK_RISK,
                    STRUCTURED_PURCHASE_FRESHNESS_RISK,
                    STRUCTURED_PURCHASE_STORE_AMOUNT_RANKING,
                    STRUCTURED_SUPPLIER_AMOUNT_RANKING,
                    STRUCTURED_STOCK_REDUCE_OVERVIEW_SUMMARY,
                    STRUCTURED_PRODUCE_CONSUME,
                    STRUCTURED_PRODUCE_OUTPUT,
                    STRUCTURED_WASTE,
                    STRUCTURED_LOSS,
                    STRUCTURED_RETURN,
                    STRUCTURED_GOODS_OUTBOUND_RANKING,
                    STRUCTURED_GOODS_OUTBOUND_COUNT_RANKING,
                    STRUCTURED_STORE_OUTBOUND_AMOUNT_RANKING,
                    STRUCTURED_DISH_PROFIT_OVERVIEW,
                    STRUCTURED_DISH_THEORETICAL_COST,
                    STRUCTURED_DISH_ACTUAL_OUTBOUND_COST,
                    STRUCTURED_DISH_COST_GAP,
                    STRUCTURED_DISH_GROSS_MARGIN_QUERY,
                    STRUCTURED_DISH_PROFIT_RANKING_LOW_MARGIN,
                    STRUCTURED_DISH_PROFIT_RANKING_HIGH_MARGIN,
                    STRUCTURED_DISH_LOW_PROFIT_REASON,
                    STRUCTURED_DISH_ACTUAL_COST_RANKING_HIGH,
                    STRUCTURED_DISH_ACTUAL_COST_RANKING_LOW,
                    STRUCTURED_DISH_THEORETICAL_COST_RANKING_HIGH,
                    STRUCTURED_DISH_THEORETICAL_COST_RANKING_LOW,
                    STRUCTURED_DISH_GAP_RANKING_MAX,
                    STRUCTURED_DISH_SALES_COUNT_RANKING_HIGH,
                    STRUCTURED_DISH_SALES_AMOUNT_RANKING_HIGH,
                    STRUCTURED_DISH_SALES_COUNT_RANKING_LOW,
                    STRUCTURED_DISH_SALES_OVERVIEW,
                    STRUCTURED_DISH_SALES_RANKING_HIGH,
                    STRUCTURED_DISH_SALES_RANKING_LOW,
                    STRUCTURED_DISH_SALES_SINGLE_DISH,
                    STRUCTURED_DISH_SALES_STORE_RANKING,
                    STRUCTURED_DISH_SALES_STORE_SINGLE_DISH,
                    STRUCTURED_DISH_SALES_TREND,
                    STRUCTURED_DISH_INGREDIENT_COST_BREAKDOWN,
                    STRUCTURED_REVENUE_OVERVIEW_SUMMARY,
                    STRUCTURED_REVENUE_SINGLE_STORE_OVERVIEW,
                    STRUCTURED_REVENUE_STORE_COMPARE,
                    STRUCTURED_REVENUE_PERIOD_COMPARE,
                    STRUCTURED_REVENUE_DAILY_RANKING,
                    STRUCTURED_REVENUE_TREND,
                    STRUCTURED_REVENUE_DINE_IN_OVERVIEW,
                    STRUCTURED_REVENUE_TAKEOUT_OVERVIEW,
                    STRUCTURED_REVENUE_PLATFORM_RANKING,
                    STRUCTURED_REVENUE_ORDER_COUNT_OVERVIEW,
                    STRUCTURED_REVENUE_CUSTOMER_COUNT_OVERVIEW,
                    STRUCTURED_REVENUE_AVERAGE_ORDER_VALUE,
                    STRUCTURED_REVENUE_DAILY_AMOUNT_RANKING,
                    STRUCTURED_REVENUE_STORE_AMOUNT_RANKING,
                    STRUCTURED_REVENUE_CHANNEL_BREAKDOWN,
                    STRUCTURED_BUSINESS_DIAGNOSIS_SUMMARY,
                    STRUCTURED_BUSINESS_COST_PRESSURE_DIAGNOSIS,
                    STRUCTURED_BUSINESS_STORE_COMPARE_DIAGNOSIS,
                    STRUCTURED_BUSINESS_OVERVIEW_SUMMARY,
                    STRUCTURED_BUSINESS_OVERVIEW_STATUS,
                    STRUCTURED_BUSINESS_STORE_STATUS_COMPARE,
                    STRUCTURED_STORE_PRIORITY_RANKING,
                    STRUCTURED_STORE_RISK_REASON_EXPLANATION,
                    STRUCTURED_STORE_DOMAIN_ATTRIBUTION_PURCHASE,
                    STRUCTURED_STORE_DOMAIN_ATTRIBUTION_STOCK_REDUCE,
                    STRUCTURED_STORE_DOMAIN_ATTRIBUTION_DISH_PROFIT,
                    STRUCTURED_DIAGNOSIS_ACTION_SUGGESTION,
                    STRUCTURED_WAREHOUSE_STOCK_OVERVIEW,
                    STRUCTURED_WAREHOUSE_STOCK_LOW_RISK,
                    STRUCTURED_WAREHOUSE_STOCK_REPLENISHMENT_NEEDED,
                    STRUCTURED_WAREHOUSE_STOCK_OVERSTOCK_RISK,
                    STRUCTURED_STORE_STOCK_AMOUNT_RANKING,
                    STRUCTURED_STORE_STOCK_ITEM_COUNT_RANKING,
                    STRUCTURED_WAREHOUSE_STOCK_AMOUNT_RANKING,
                    STRUCTURED_WAREHOUSE_STOCK_ITEM_COUNT_RANKING,
                    STRUCTURED_GOODS_STOCK_AMOUNT_RANKING_LOW,
                    STRUCTURED_WAREHOUSE_NEAR_EXPIRY);

    public static boolean isRegisteredCanonicalWire(String wire) {
        return StringUtils.hasText(wire) && REGISTERED_CANONICAL_WIRES.contains(wire.trim());
    }

    public static Set<String> registeredCanonicalWires() {
        return REGISTERED_CANONICAL_WIRES;
    }

    public static boolean isPurchaseOverviewDomainCanonicalWire(String wire) {
        return StringUtils.hasText(wire) && PURCHASE_OVERVIEW_DOMAIN_CANONICAL_WIRES.contains(wire.trim());
    }

    public static boolean isPurchaseAnomalyDetectionWire(String wire) {
        return StringUtils.hasText(wire) && PURCHASE_ANOMALY_DETECTION_WIRES.contains(wire.trim());
    }

    /** 仅大小写 / 分隔符归一；不做 alias → registered wire 映射。 */
    public static String normalizeWireCase(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        String t = raw.trim().replace('-', '_');
        if (!t.contains("_")) {
            return t.toLowerCase(Locale.ROOT);
        }
        return t.toLowerCase(Locale.ROOT);
    }
}
