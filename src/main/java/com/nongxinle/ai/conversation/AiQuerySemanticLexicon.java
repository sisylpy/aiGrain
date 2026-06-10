package com.nongxinle.ai.conversation;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;

/**
 * P4-C：结构化子意图薄桥接层 — re-export registered wire 常量、wire case 归一、域判定 predicate、格式化工具。
 * <p>主链 wire SSOT 见 {@link AiSemanticWireConstants}；debug 标签见 {@link AiSemanticWireDebugFormatter}。</p>
 * <p>不再承担 alias / compat / rankingType → wire 的 silent 归一；合同外 wire 由 {@link com.nongxinle.ai.semantic.contract.SemanticContractValidator} 报 UNSUPPORTED_WIRE。</p>
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class AiQuerySemanticLexicon {

    // --- re-export registered wire / slot constants (backward compatible imports) ---

    public static final String STRUCTURED_PURCHASE_OVERVIEW_SUMMARY =
            AiSemanticWireConstants.STRUCTURED_PURCHASE_OVERVIEW_SUMMARY;
    public static final String STRUCTURED_PURCHASE_SOURCE_SUMMARY =
            AiSemanticWireConstants.STRUCTURED_PURCHASE_SOURCE_SUMMARY;
    public static final String STRUCTURED_PURCHASE_SOURCE_AMOUNT_QUERY =
            AiSemanticWireConstants.STRUCTURED_PURCHASE_SOURCE_AMOUNT_QUERY;
    public static final String STRUCTURED_PURCHASE_SOURCE_GOODS_QUERY =
            AiSemanticWireConstants.STRUCTURED_PURCHASE_SOURCE_GOODS_QUERY;
    public static final String STRUCTURED_PURCHASE_PERIOD_GOODS_LIST =
            AiSemanticWireConstants.STRUCTURED_PURCHASE_PERIOD_GOODS_LIST;
    public static final String STRUCTURED_PURCHASE_GOODS_AMOUNT_RANKING =
            AiSemanticWireConstants.STRUCTURED_PURCHASE_GOODS_AMOUNT_RANKING;
    public static final String STRUCTURED_PURCHASE_GOODS_COUNT_RANKING =
            AiSemanticWireConstants.STRUCTURED_PURCHASE_GOODS_COUNT_RANKING;
    public static final String STRUCTURED_PURCHASE_GOODS_QUANTITY_RANKING =
            AiSemanticWireConstants.STRUCTURED_PURCHASE_GOODS_QUANTITY_RANKING;
    public static final String STRUCTURED_PURCHASE_GOODS_ANOMALY =
            AiSemanticWireConstants.STRUCTURED_PURCHASE_GOODS_ANOMALY;
    public static final String STRUCTURED_PURCHASE_PRICE_ANOMALY =
            AiSemanticWireConstants.STRUCTURED_PURCHASE_PRICE_ANOMALY;
    public static final String STRUCTURED_PURCHASE_FREQUENCY_ANOMALY =
            AiSemanticWireConstants.STRUCTURED_PURCHASE_FREQUENCY_ANOMALY;
    public static final String STRUCTURED_PURCHASE_QUANTITY_ANOMALY =
            AiSemanticWireConstants.STRUCTURED_PURCHASE_QUANTITY_ANOMALY;
    public static final String STRUCTURED_PURCHASE_GOODS_AMOUNT_SPIKE =
            AiSemanticWireConstants.STRUCTURED_PURCHASE_GOODS_AMOUNT_SPIKE;
    public static final String STRUCTURED_PURCHASE_GOODS_BUSINESS_ANALYSIS =
            AiSemanticWireConstants.STRUCTURED_PURCHASE_GOODS_BUSINESS_ANALYSIS;
    public static final String STRUCTURED_PURCHASE_STOCK_REDUCE_MISMATCH =
            AiSemanticWireConstants.STRUCTURED_PURCHASE_STOCK_REDUCE_MISMATCH;
    public static final String STRUCTURED_PURCHASE_SLOW_MOVING_RISK =
            AiSemanticWireConstants.STRUCTURED_PURCHASE_SLOW_MOVING_RISK;
    public static final String STRUCTURED_PURCHASE_INVENTORY_OVERSTOCK_RISK =
            AiSemanticWireConstants.STRUCTURED_PURCHASE_INVENTORY_OVERSTOCK_RISK;
    public static final String STRUCTURED_PURCHASE_FRESHNESS_RISK =
            AiSemanticWireConstants.STRUCTURED_PURCHASE_FRESHNESS_RISK;
    public static final String STRUCTURED_PURCHASE_STORE_AMOUNT_RANKING =
            AiSemanticWireConstants.STRUCTURED_PURCHASE_STORE_AMOUNT_RANKING;
    public static final String STRUCTURED_PURCHASE_STORE_COMPARE =
            AiSemanticWireConstants.STRUCTURED_PURCHASE_STORE_COMPARE;
    public static final String STRUCTURED_PURCHASE_STORE_PAIR_AMOUNT_COMPARE =
            AiSemanticWireConstants.STRUCTURED_PURCHASE_STORE_PAIR_AMOUNT_COMPARE;
    public static final String STRUCTURED_SUPPLIER_AMOUNT_RANKING =
            AiSemanticWireConstants.STRUCTURED_SUPPLIER_AMOUNT_RANKING;
    public static final String STRUCTURED_SUPPLIER_RANKING = AiSemanticWireConstants.STRUCTURED_SUPPLIER_RANKING;

    public static final String SOURCE_SELF_PURCHASE = AiSemanticWireConstants.SOURCE_SELF_PURCHASE;
    public static final String SOURCE_SUPPLIER_PURCHASE = AiSemanticWireConstants.SOURCE_SUPPLIER_PURCHASE;
    public static final String SOURCE_ALL = AiSemanticWireConstants.SOURCE_ALL;

    public static final String DETAIL_WANTED_SOURCE_BREAKDOWN =
            AiSemanticWireConstants.DETAIL_WANTED_SOURCE_BREAKDOWN;
    public static final String DETAIL_WANTED_SUPPLIER_BREAKDOWN =
            AiSemanticWireConstants.DETAIL_WANTED_SUPPLIER_BREAKDOWN;
    public static final String DETAIL_WANTED_SUPPLIER_UNIT_PRICE =
            AiSemanticWireConstants.DETAIL_WANTED_SUPPLIER_UNIT_PRICE;

    public static final String STRUCTURED_STOCK_REDUCE_OVERVIEW_SUMMARY =
            AiSemanticWireConstants.STRUCTURED_STOCK_REDUCE_OVERVIEW_SUMMARY;
    public static final String STRUCTURED_PRODUCE_CONSUME = AiSemanticWireConstants.STRUCTURED_PRODUCE_CONSUME;
    public static final String STRUCTURED_PRODUCE_OUTPUT = AiSemanticWireConstants.STRUCTURED_PRODUCE_OUTPUT;
    public static final String STRUCTURED_WASTE = AiSemanticWireConstants.STRUCTURED_WASTE;
    public static final String STRUCTURED_LOSS = AiSemanticWireConstants.STRUCTURED_LOSS;
    public static final String STRUCTURED_RETURN = AiSemanticWireConstants.STRUCTURED_RETURN;
    public static final String STRUCTURED_GOODS_OUTBOUND_RANKING =
            AiSemanticWireConstants.STRUCTURED_GOODS_OUTBOUND_RANKING;
    public static final String STRUCTURED_GOODS_OUTBOUND_COUNT_RANKING =
            AiSemanticWireConstants.STRUCTURED_GOODS_OUTBOUND_COUNT_RANKING;
    public static final String STRUCTURED_STORE_OUTBOUND_AMOUNT_RANKING =
            AiSemanticWireConstants.STRUCTURED_STORE_OUTBOUND_AMOUNT_RANKING;

    public static final String STRUCTURED_DISH_PROFIT_OVERVIEW =
            AiSemanticWireConstants.STRUCTURED_DISH_PROFIT_OVERVIEW;
    public static final String STRUCTURED_DISH_THEORETICAL_COST =
            AiSemanticWireConstants.STRUCTURED_DISH_THEORETICAL_COST;
    public static final String STRUCTURED_DISH_ACTUAL_OUTBOUND_COST =
            AiSemanticWireConstants.STRUCTURED_DISH_ACTUAL_OUTBOUND_COST;
    public static final String STRUCTURED_DISH_COST_GAP = AiSemanticWireConstants.STRUCTURED_DISH_COST_GAP;
    public static final String STRUCTURED_DISH_GROSS_MARGIN_QUERY =
            AiSemanticWireConstants.STRUCTURED_DISH_GROSS_MARGIN_QUERY;
    public static final String STRUCTURED_DISH_PROFIT_RANKING_LOW_MARGIN =
            AiSemanticWireConstants.STRUCTURED_DISH_PROFIT_RANKING_LOW_MARGIN;
    public static final String STRUCTURED_DISH_PROFIT_RANKING_HIGH_MARGIN =
            AiSemanticWireConstants.STRUCTURED_DISH_PROFIT_RANKING_HIGH_MARGIN;
    public static final String STRUCTURED_DISH_PROFIT_RANKING_HIGH_PROFIT_AMOUNT =
            AiSemanticWireConstants.STRUCTURED_DISH_PROFIT_RANKING_HIGH_PROFIT_AMOUNT;
    public static final String STRUCTURED_DISH_PROFIT_RANKING_LOW_PROFIT_AMOUNT =
            AiSemanticWireConstants.STRUCTURED_DISH_PROFIT_RANKING_LOW_PROFIT_AMOUNT;
    public static final String STRUCTURED_DISH_LOW_PROFIT_REASON =
            AiSemanticWireConstants.STRUCTURED_DISH_LOW_PROFIT_REASON;
    public static final String STRUCTURED_DISH_ACTUAL_COST_RANKING_HIGH =
            AiSemanticWireConstants.STRUCTURED_DISH_ACTUAL_COST_RANKING_HIGH;
    public static final String STRUCTURED_DISH_ACTUAL_COST_RANKING_LOW =
            AiSemanticWireConstants.STRUCTURED_DISH_ACTUAL_COST_RANKING_LOW;
    public static final String STRUCTURED_DISH_THEORETICAL_COST_RANKING_HIGH =
            AiSemanticWireConstants.STRUCTURED_DISH_THEORETICAL_COST_RANKING_HIGH;
    public static final String STRUCTURED_DISH_THEORETICAL_COST_RANKING_LOW =
            AiSemanticWireConstants.STRUCTURED_DISH_THEORETICAL_COST_RANKING_LOW;
    public static final String STRUCTURED_DISH_GAP_RANKING_MAX =
            AiSemanticWireConstants.STRUCTURED_DISH_GAP_RANKING_MAX;
    public static final String STRUCTURED_DISH_SALES_COUNT_RANKING_HIGH =
            AiSemanticWireConstants.STRUCTURED_DISH_SALES_COUNT_RANKING_HIGH;
    public static final String STRUCTURED_DISH_SALES_AMOUNT_RANKING_HIGH =
            AiSemanticWireConstants.STRUCTURED_DISH_SALES_AMOUNT_RANKING_HIGH;
    public static final String STRUCTURED_DISH_SALES_COUNT_RANKING_LOW =
            AiSemanticWireConstants.STRUCTURED_DISH_SALES_COUNT_RANKING_LOW;
    public static final String STRUCTURED_DISH_SALES_OVERVIEW =
            AiSemanticWireConstants.STRUCTURED_DISH_SALES_OVERVIEW;
    public static final String STRUCTURED_DISH_SALES_RANKING_HIGH =
            AiSemanticWireConstants.STRUCTURED_DISH_SALES_RANKING_HIGH;
    public static final String STRUCTURED_DISH_SALES_RANKING_LOW =
            AiSemanticWireConstants.STRUCTURED_DISH_SALES_RANKING_LOW;
    public static final String STRUCTURED_DISH_SALES_SINGLE_DISH =
            AiSemanticWireConstants.STRUCTURED_DISH_SALES_SINGLE_DISH;
    public static final String STRUCTURED_DISH_SALES_STORE_RANKING =
            AiSemanticWireConstants.STRUCTURED_DISH_SALES_STORE_RANKING;
    public static final String STRUCTURED_DISH_SALES_STORE_SINGLE_DISH =
            AiSemanticWireConstants.STRUCTURED_DISH_SALES_STORE_SINGLE_DISH;
    public static final String STRUCTURED_DISH_SALES_TREND = AiSemanticWireConstants.STRUCTURED_DISH_SALES_TREND;
    public static final String STRUCTURED_DISH_INGREDIENT_COST_BREAKDOWN =
            AiSemanticWireConstants.STRUCTURED_DISH_INGREDIENT_COST_BREAKDOWN;
    public static final String STRUCTURED_DISH_COST_ANALYSIS =
            AiSemanticWireConstants.STRUCTURED_DISH_COST_ANALYSIS;
    public static final String STRUCTURED_DISH_PROFIT_PRESCRIPTION =
            AiSemanticWireConstants.STRUCTURED_DISH_PROFIT_PRESCRIPTION;
    public static final String STRUCTURED_DISH_INGREDIENT_COVER_DAYS =
            AiSemanticWireConstants.STRUCTURED_DISH_INGREDIENT_COVER_DAYS;
    public static final String STRUCTURED_MENU_OPERATION_OVERVIEW =
            AiSemanticWireConstants.STRUCTURED_MENU_OPERATION_OVERVIEW;
    public static final String STRUCTURED_MENU_DISH_HIGH_SALES_LOW_PROFIT =
            AiSemanticWireConstants.STRUCTURED_MENU_DISH_HIGH_SALES_LOW_PROFIT;
    public static final String STRUCTURED_MENU_ACTION_RECOMMENDATION =
            AiSemanticWireConstants.STRUCTURED_MENU_ACTION_RECOMMENDATION;

    public static final String STRUCTURED_REVENUE_OVERVIEW_SUMMARY =
            AiSemanticWireConstants.STRUCTURED_REVENUE_OVERVIEW_SUMMARY;
    @Deprecated
    public static final String STRUCTURED_REVENUE_OVERVIEW = AiSemanticWireConstants.STRUCTURED_REVENUE_OVERVIEW;
    public static final String STRUCTURED_REVENUE_SINGLE_STORE_OVERVIEW =
            AiSemanticWireConstants.STRUCTURED_REVENUE_SINGLE_STORE_OVERVIEW;
    public static final String STRUCTURED_REVENUE_STORE_COMPARE =
            AiSemanticWireConstants.STRUCTURED_REVENUE_STORE_COMPARE;
    public static final String STRUCTURED_REVENUE_PERIOD_COMPARE =
            AiSemanticWireConstants.STRUCTURED_REVENUE_PERIOD_COMPARE;
    public static final String STRUCTURED_REVENUE_DAILY_RANKING =
            AiSemanticWireConstants.STRUCTURED_REVENUE_DAILY_RANKING;
    public static final String STRUCTURED_REVENUE_TREND = AiSemanticWireConstants.STRUCTURED_REVENUE_TREND;
    public static final String STRUCTURED_REVENUE_DINE_IN_OVERVIEW =
            AiSemanticWireConstants.STRUCTURED_REVENUE_DINE_IN_OVERVIEW;
    public static final String STRUCTURED_REVENUE_TAKEOUT_OVERVIEW =
            AiSemanticWireConstants.STRUCTURED_REVENUE_TAKEOUT_OVERVIEW;
    public static final String STRUCTURED_REVENUE_PLATFORM_RANKING =
            AiSemanticWireConstants.STRUCTURED_REVENUE_PLATFORM_RANKING;
    public static final String STRUCTURED_REVENUE_ORDER_COUNT_OVERVIEW =
            AiSemanticWireConstants.STRUCTURED_REVENUE_ORDER_COUNT_OVERVIEW;
    public static final String STRUCTURED_REVENUE_CUSTOMER_COUNT_OVERVIEW =
            AiSemanticWireConstants.STRUCTURED_REVENUE_CUSTOMER_COUNT_OVERVIEW;
    public static final String STRUCTURED_REVENUE_AVERAGE_ORDER_VALUE =
            AiSemanticWireConstants.STRUCTURED_REVENUE_AVERAGE_ORDER_VALUE;
    public static final String STRUCTURED_REVENUE_DAILY_AMOUNT_RANKING =
            AiSemanticWireConstants.STRUCTURED_REVENUE_DAILY_AMOUNT_RANKING;
    public static final String STRUCTURED_REVENUE_STORE_AMOUNT_RANKING =
            AiSemanticWireConstants.STRUCTURED_REVENUE_STORE_AMOUNT_RANKING;
    public static final String STRUCTURED_REVENUE_CHANNEL_BREAKDOWN =
            AiSemanticWireConstants.STRUCTURED_REVENUE_CHANNEL_BREAKDOWN;

    public static final String STRUCTURED_BUSINESS_DIAGNOSIS_SUMMARY =
            AiSemanticWireConstants.STRUCTURED_BUSINESS_DIAGNOSIS_SUMMARY;
    public static final String STRUCTURED_BUSINESS_COST_PRESSURE_DIAGNOSIS =
            AiSemanticWireConstants.STRUCTURED_BUSINESS_COST_PRESSURE_DIAGNOSIS;
    public static final String STRUCTURED_BUSINESS_STORE_COMPARE_DIAGNOSIS =
            AiSemanticWireConstants.STRUCTURED_BUSINESS_STORE_COMPARE_DIAGNOSIS;
    public static final String STRUCTURED_BUSINESS_OVERVIEW_SUMMARY =
            AiSemanticWireConstants.STRUCTURED_BUSINESS_OVERVIEW_SUMMARY;
    public static final String STRUCTURED_BUSINESS_OVERVIEW_STATUS =
            AiSemanticWireConstants.STRUCTURED_BUSINESS_OVERVIEW_STATUS;
    public static final String STRUCTURED_BUSINESS_STORE_STATUS_COMPARE =
            AiSemanticWireConstants.STRUCTURED_BUSINESS_STORE_STATUS_COMPARE;

    public static final String STRUCTURED_STORE_PRIORITY_RANKING =
            AiSemanticWireConstants.STRUCTURED_STORE_PRIORITY_RANKING;

    public static final String STRUCTURED_STORE_RISK_REASON_EXPLANATION =
            AiSemanticWireConstants.STRUCTURED_STORE_RISK_REASON_EXPLANATION;

    public static final String STRUCTURED_STORE_DOMAIN_ATTRIBUTION_PURCHASE =
            AiSemanticWireConstants.STRUCTURED_STORE_DOMAIN_ATTRIBUTION_PURCHASE;
    public static final String STRUCTURED_STORE_DOMAIN_ATTRIBUTION_STOCK_REDUCE =
            AiSemanticWireConstants.STRUCTURED_STORE_DOMAIN_ATTRIBUTION_STOCK_REDUCE;
    public static final String STRUCTURED_STORE_DOMAIN_ATTRIBUTION_DISH_PROFIT =
            AiSemanticWireConstants.STRUCTURED_STORE_DOMAIN_ATTRIBUTION_DISH_PROFIT;

    public static final String STRUCTURED_DIAGNOSIS_ACTION_SUGGESTION =
            AiSemanticWireConstants.STRUCTURED_DIAGNOSIS_ACTION_SUGGESTION;

    public static final String STRUCTURED_WAREHOUSE_STOCK_OVERVIEW =
            AiSemanticWireConstants.STRUCTURED_WAREHOUSE_STOCK_OVERVIEW;
    public static final String STRUCTURED_WAREHOUSE_STOCK_LOW_RISK =
            AiSemanticWireConstants.STRUCTURED_WAREHOUSE_STOCK_LOW_RISK;
    public static final String STRUCTURED_WAREHOUSE_STOCK_REPLENISHMENT_NEEDED =
            AiSemanticWireConstants.STRUCTURED_WAREHOUSE_STOCK_REPLENISHMENT_NEEDED;
    public static final String STRUCTURED_WAREHOUSE_STOCK_OVERSTOCK_RISK =
            AiSemanticWireConstants.STRUCTURED_WAREHOUSE_STOCK_OVERSTOCK_RISK;
    public static final String STRUCTURED_STORE_STOCK_AMOUNT_RANKING =
            AiSemanticWireConstants.STRUCTURED_STORE_STOCK_AMOUNT_RANKING;
    public static final String STRUCTURED_STORE_STOCK_ITEM_COUNT_RANKING =
            AiSemanticWireConstants.STRUCTURED_STORE_STOCK_ITEM_COUNT_RANKING;
    public static final String STRUCTURED_WAREHOUSE_STOCK_AMOUNT_RANKING =
            AiSemanticWireConstants.STRUCTURED_WAREHOUSE_STOCK_AMOUNT_RANKING;
    public static final String STRUCTURED_WAREHOUSE_STOCK_ITEM_COUNT_RANKING =
            AiSemanticWireConstants.STRUCTURED_WAREHOUSE_STOCK_ITEM_COUNT_RANKING;
    public static final String STRUCTURED_GOODS_STOCK_AMOUNT_RANKING_LOW =
            AiSemanticWireConstants.STRUCTURED_GOODS_STOCK_AMOUNT_RANKING_LOW;
    public static final String STRUCTURED_GOODS_SUPPORTED_DISH_COVER =
            AiSemanticWireConstants.STRUCTURED_GOODS_SUPPORTED_DISH_COVER;
    public static final String STRUCTURED_GOODS_STOCK_BATCH_DETAIL =
            AiSemanticWireConstants.STRUCTURED_GOODS_STOCK_BATCH_DETAIL;
    public static final String STRUCTURED_GOODS_ANCHOR_INVENTORY_BUNDLE =
            AiSemanticWireConstants.STRUCTURED_GOODS_ANCHOR_INVENTORY_BUNDLE;
    public static final String STRUCTURED_WAREHOUSE_NEAR_EXPIRY =
            AiSemanticWireConstants.STRUCTURED_WAREHOUSE_NEAR_EXPIRY;
    public static final String STRUCTURED_WAREHOUSE_INVENTORY_SUPERVISION =
            AiSemanticWireConstants.STRUCTURED_WAREHOUSE_INVENTORY_SUPERVISION;

    public static boolean isPurchaseOverviewDomainCanonicalWire(String canonicalWire) {
        return AiSemanticWireConstants.isPurchaseOverviewDomainCanonicalWire(canonicalWire);
    }

    /** 时段采购商品清单 wire（{@code purchase_period_goods_list}）。 */
    public static boolean isPurchasePeriodGoodsListStructuredDetail(String structuredIntentDetail) {
        if (!StringUtils.hasText(structuredIntentDetail)) {
            return false;
        }
        return STRUCTURED_PURCHASE_PERIOD_GOODS_LIST.equals(
                canonicalStructuredIntentDetailWire(structuredIntentDetail.trim()));
    }

    public static boolean isPurchaseAnomalyDetectionWire(String canonicalWire) {
        return AiSemanticWireConstants.isPurchaseAnomalyDetectionWire(canonicalWire);
    }

    /**
     * 仅 case / 分隔符归一；registered wire identity return；非 registered 原样透传供 Validator 判定。
     */
    public static String canonicalStructuredIntentDetailWire(String raw) {
        return AiSemanticWireConstants.normalizeWireCase(raw);
    }

    public static boolean isRegisteredCanonicalWire(String wire) {
        return AiSemanticWireConstants.isRegisteredCanonicalWire(wire);
    }

    public static String canonicalDetailWanted(
            String detailWanted,
            String queryObject,
            String operation,
            String structuredIntentDetailWire) {
        if (!StringUtils.hasText(detailWanted)) {
            return null;
        }
        String dw = detailWanted.trim().toUpperCase(Locale.ROOT).replace('-', '_');
        return dw.isEmpty() ? null : dw;
    }

    public static String canonicalOperation(
            String operation,
            String detailWanted,
            String queryObject,
            String anchorPolicy,
            String structuredIntentDetailWire) {
        return com.nongxinle.ai.semantic.matrix.PurchaseSemanticCapabilityMatrix.canonicalOperation(
                operation, detailWanted, queryObject, anchorPolicy, structuredIntentDetailWire);
    }

    public static boolean isStorePriorityRankingStructuredDetail(String structuredIntentDetail) {
        String c = canonicalStructuredIntentDetailWire(structuredIntentDetail);
        return STRUCTURED_STORE_PRIORITY_RANKING.equals(c);
    }

    public static boolean isStoreRiskReasonExplanationStructuredDetail(String structuredIntentDetail) {
        String c = canonicalStructuredIntentDetailWire(structuredIntentDetail);
        return STRUCTURED_STORE_RISK_REASON_EXPLANATION.equals(c);
    }

    public static boolean isBusinessDiagnosisSummaryStructuredDetail(String structuredIntentDetail) {
        String c = canonicalStructuredIntentDetailWire(structuredIntentDetail);
        return STRUCTURED_BUSINESS_DIAGNOSIS_SUMMARY.equals(c);
    }

    public static boolean isStructuredBusinessDiagnosisDetail(String structuredIntentDetail) {
        String c = canonicalStructuredIntentDetailWire(structuredIntentDetail);
        if (!StringUtils.hasText(c)) {
            return false;
        }
        return isBusinessDiagnosisSummaryStructuredDetail(c)
                || isStorePriorityRankingStructuredDetail(c)
                || isStoreRiskReasonExplanationStructuredDetail(c)
                || STRUCTURED_BUSINESS_STORE_COMPARE_DIAGNOSIS.equals(c)
                || isStoreDomainAttributionPurchaseStructuredDetail(c)
                || isStoreDomainAttributionStockReduceStructuredDetail(c)
                || isStoreDomainAttributionDishProfitStructuredDetail(c)
                || isDiagnosisActionSuggestionStructuredDetail(c)
                || STRUCTURED_PURCHASE_STOCK_REDUCE_MISMATCH.equals(c)
                || STRUCTURED_PURCHASE_SLOW_MOVING_RISK.equals(c)
                || STRUCTURED_PURCHASE_INVENTORY_OVERSTOCK_RISK.equals(c)
                || STRUCTURED_PURCHASE_FRESHNESS_RISK.equals(c);
    }

    public static boolean isStoreDomainAttributionPurchaseStructuredDetail(String structuredIntentDetail) {
        String c = canonicalStructuredIntentDetailWire(structuredIntentDetail);
        return STRUCTURED_STORE_DOMAIN_ATTRIBUTION_PURCHASE.equals(c);
    }

    public static boolean isStoreDomainAttributionStockReduceStructuredDetail(String structuredIntentDetail) {
        String c = canonicalStructuredIntentDetailWire(structuredIntentDetail);
        return STRUCTURED_STORE_DOMAIN_ATTRIBUTION_STOCK_REDUCE.equals(c);
    }

    public static boolean isStoreDomainAttributionDishProfitStructuredDetail(String structuredIntentDetail) {
        String c = canonicalStructuredIntentDetailWire(structuredIntentDetail);
        return STRUCTURED_STORE_DOMAIN_ATTRIBUTION_DISH_PROFIT.equals(c);
    }

    public static boolean isDiagnosisActionSuggestionStructuredDetail(String structuredIntentDetail) {
        String c = canonicalStructuredIntentDetailWire(structuredIntentDetail);
        return STRUCTURED_DIAGNOSIS_ACTION_SUGGESTION.equals(c);
    }

    public static boolean isStructuredBusinessOverviewFourDomainOrchestrationSurface(String structuredIntentDetail) {
        String c = canonicalStructuredIntentDetailWire(structuredIntentDetail);
        if (!StringUtils.hasText(c)) {
            return false;
        }
        return STRUCTURED_BUSINESS_OVERVIEW_SUMMARY.equals(c)
                || STRUCTURED_BUSINESS_OVERVIEW_STATUS.equals(c)
                || STRUCTURED_BUSINESS_STORE_STATUS_COMPARE.equals(c);
    }

    public static boolean isDishLowProfitReasonStructuredWire(String structuredIntentDetail) {
        String c = canonicalStructuredIntentDetailWire(structuredIntentDetail);
        return STRUCTURED_DISH_LOW_PROFIT_REASON.equals(c);
    }

    public static boolean isStructuredRevenueDetail(String structuredIntentDetail) {
        if (structuredIntentDetail == null || structuredIntentDetail.isBlank()) {
            return false;
        }
        String t = canonicalStructuredIntentDetailWire(structuredIntentDetail.trim());
        return STRUCTURED_REVENUE_OVERVIEW_SUMMARY.equals(t)
                || STRUCTURED_REVENUE_SINGLE_STORE_OVERVIEW.equals(t)
                || STRUCTURED_REVENUE_STORE_COMPARE.equals(t)
                || STRUCTURED_REVENUE_PERIOD_COMPARE.equals(t)
                || STRUCTURED_REVENUE_TREND.equals(t)
                || STRUCTURED_REVENUE_DINE_IN_OVERVIEW.equals(t)
                || STRUCTURED_REVENUE_TAKEOUT_OVERVIEW.equals(t)
                || STRUCTURED_REVENUE_PLATFORM_RANKING.equals(t)
                || STRUCTURED_REVENUE_ORDER_COUNT_OVERVIEW.equals(t)
                || STRUCTURED_REVENUE_CUSTOMER_COUNT_OVERVIEW.equals(t)
                || STRUCTURED_REVENUE_AVERAGE_ORDER_VALUE.equals(t)
                || STRUCTURED_REVENUE_DAILY_AMOUNT_RANKING.equals(t)
                || STRUCTURED_REVENUE_STORE_AMOUNT_RANKING.equals(t)
                || STRUCTURED_REVENUE_CHANNEL_BREAKDOWN.equals(t);
    }

    public static boolean isStructuredStockReduceDetail(String structuredIntentDetail) {
        if (structuredIntentDetail == null || structuredIntentDetail.isBlank()) {
            return false;
        }
        String t = canonicalStructuredIntentDetailWire(structuredIntentDetail.trim());
        return STRUCTURED_STOCK_REDUCE_OVERVIEW_SUMMARY.equals(t)
                || STRUCTURED_PRODUCE_CONSUME.equals(t)
                || STRUCTURED_PRODUCE_OUTPUT.equals(t)
                || STRUCTURED_WASTE.equals(t)
                || STRUCTURED_LOSS.equals(t)
                || STRUCTURED_RETURN.equals(t)
                || STRUCTURED_GOODS_OUTBOUND_RANKING.equals(t)
                || STRUCTURED_GOODS_OUTBOUND_COUNT_RANKING.equals(t)
                || STRUCTURED_STORE_OUTBOUND_AMOUNT_RANKING.equals(t);
    }

    public static boolean isNonOverviewStockReduceStructuredDetail(String structuredIntentDetail) {
        if (structuredIntentDetail == null || structuredIntentDetail.isBlank()) {
            return false;
        }
        String t = canonicalStructuredIntentDetailWire(structuredIntentDetail.trim());
        if (STRUCTURED_STOCK_REDUCE_OVERVIEW_SUMMARY.equals(t)) {
            return false;
        }
        return isStructuredStockReduceDetail(t);
    }

    public static boolean isStockReduceOutboundRankingWire(String structuredIntentDetail) {
        if (structuredIntentDetail == null || structuredIntentDetail.isBlank()) {
            return false;
        }
        String t = canonicalStructuredIntentDetailWire(structuredIntentDetail.trim());
        return STRUCTURED_GOODS_OUTBOUND_RANKING.equals(t)
                || STRUCTURED_GOODS_OUTBOUND_COUNT_RANKING.equals(t)
                || STRUCTURED_STORE_OUTBOUND_AMOUNT_RANKING.equals(t);
    }

    public static boolean isNonOverviewDishProfitStructuredDetail(String structuredIntentDetail) {
        if (structuredIntentDetail == null || structuredIntentDetail.isBlank()) {
            return false;
        }
        String t = canonicalStructuredIntentDetailWire(structuredIntentDetail.trim());
        if (STRUCTURED_DISH_PROFIT_OVERVIEW.equals(t)) {
            return false;
        }
        return STRUCTURED_DISH_THEORETICAL_COST.equals(t) || STRUCTURED_DISH_ACTUAL_OUTBOUND_COST.equals(t)
                || STRUCTURED_DISH_COST_GAP.equals(t) || STRUCTURED_DISH_GROSS_MARGIN_QUERY.equals(t)
                || STRUCTURED_DISH_PROFIT_RANKING_LOW_MARGIN.equals(t)
                || STRUCTURED_DISH_PROFIT_RANKING_HIGH_MARGIN.equals(t)
                || STRUCTURED_DISH_PROFIT_RANKING_HIGH_PROFIT_AMOUNT.equals(t)
                || STRUCTURED_DISH_PROFIT_RANKING_LOW_PROFIT_AMOUNT.equals(t)
                || STRUCTURED_DISH_ACTUAL_COST_RANKING_HIGH.equals(t) || STRUCTURED_DISH_ACTUAL_COST_RANKING_LOW.equals(t)
                || STRUCTURED_DISH_THEORETICAL_COST_RANKING_HIGH.equals(t)
                || STRUCTURED_DISH_THEORETICAL_COST_RANKING_LOW.equals(t)
                || STRUCTURED_DISH_LOW_PROFIT_REASON.equals(t)
                || STRUCTURED_DISH_GAP_RANKING_MAX.equals(t)
                || STRUCTURED_DISH_INGREDIENT_COST_BREAKDOWN.equals(t);
    }

    public static boolean isStructuredWarehouseStockDetail(String structuredIntentDetail) {
        if (structuredIntentDetail == null || structuredIntentDetail.isBlank()) {
            return false;
        }
        String t = canonicalStructuredIntentDetailWire(structuredIntentDetail.trim());
        return STRUCTURED_WAREHOUSE_STOCK_OVERVIEW.equals(t)
                || STRUCTURED_WAREHOUSE_STOCK_LOW_RISK.equals(t)
                || STRUCTURED_WAREHOUSE_STOCK_REPLENISHMENT_NEEDED.equals(t)
                || STRUCTURED_WAREHOUSE_STOCK_OVERSTOCK_RISK.equals(t)
                || STRUCTURED_WAREHOUSE_NEAR_EXPIRY.equals(t)
                || STRUCTURED_WAREHOUSE_INVENTORY_SUPERVISION.equals(t)
                || STRUCTURED_GOODS_STOCK_AMOUNT_RANKING_LOW.equals(t)
                || STRUCTURED_GOODS_SUPPORTED_DISH_COVER.equals(t)
                || STRUCTURED_GOODS_STOCK_BATCH_DETAIL.equals(t)
                || STRUCTURED_GOODS_ANCHOR_INVENTORY_BUNDLE.equals(t)
                || isStructuredWarehouseStockRankingDetail(t);
    }

    public static boolean isStructuredWarehouseStockRankingDetail(String structuredIntentDetail) {
        if (structuredIntentDetail == null || structuredIntentDetail.isBlank()) {
            return false;
        }
        String t = canonicalStructuredIntentDetailWire(structuredIntentDetail.trim());
        return STRUCTURED_STORE_STOCK_AMOUNT_RANKING.equals(t)
                || STRUCTURED_STORE_STOCK_ITEM_COUNT_RANKING.equals(t)
                || STRUCTURED_WAREHOUSE_STOCK_AMOUNT_RANKING.equals(t)
                || STRUCTURED_WAREHOUSE_STOCK_ITEM_COUNT_RANKING.equals(t)
                || STRUCTURED_GOODS_STOCK_AMOUNT_RANKING_LOW.equals(t);
    }

    public static boolean isStructuredDishSalesDetail(String structuredIntentDetail) {
        if (structuredIntentDetail == null || structuredIntentDetail.isBlank()) {
            return false;
        }
        String t = canonicalStructuredIntentDetailWire(structuredIntentDetail.trim());
        return STRUCTURED_DISH_SALES_COUNT_RANKING_HIGH.equals(t)
                || STRUCTURED_DISH_SALES_AMOUNT_RANKING_HIGH.equals(t)
                || STRUCTURED_DISH_SALES_COUNT_RANKING_LOW.equals(t)
                || STRUCTURED_DISH_SALES_OVERVIEW.equals(t)
                || STRUCTURED_DISH_SALES_RANKING_HIGH.equals(t)
                || STRUCTURED_DISH_SALES_RANKING_LOW.equals(t)
                || STRUCTURED_DISH_SALES_SINGLE_DISH.equals(t)
                || STRUCTURED_DISH_SALES_STORE_RANKING.equals(t)
                || STRUCTURED_DISH_SALES_STORE_SINGLE_DISH.equals(t)
                || STRUCTURED_DISH_SALES_TREND.equals(t);
    }

    /** 菜品销量单菜（含门店单菜）structured wire。 */
    public static boolean isDishSalesSingleDishStructuredDetail(String structuredIntentDetail) {
        if (structuredIntentDetail == null || structuredIntentDetail.isBlank()) {
            return false;
        }
        String t = canonicalStructuredIntentDetailWire(structuredIntentDetail.trim());
        return STRUCTURED_DISH_SALES_SINGLE_DISH.equals(t)
                || STRUCTURED_DISH_SALES_STORE_SINGLE_DISH.equals(t);
    }

    /** 菜品销量排行/概览（非单菜）structured wire。 */
    public static boolean isDishSalesRankingStructuredDetail(String structuredIntentDetail) {
        return isStructuredDishSalesDetail(structuredIntentDetail)
                && !isDishSalesSingleDishStructuredDetail(structuredIntentDetail);
    }

    public static boolean isStructuredDishCostAnalysisDetail(String structuredIntentDetail) {
        if (structuredIntentDetail == null || structuredIntentDetail.isBlank()) {
            return false;
        }
        return STRUCTURED_DISH_COST_ANALYSIS.equals(
                canonicalStructuredIntentDetailWire(structuredIntentDetail.trim()));
    }

    public static boolean isStructuredDishProfitPrescriptionDetail(String structuredIntentDetail) {
        if (structuredIntentDetail == null || structuredIntentDetail.isBlank()) {
            return false;
        }
        return STRUCTURED_DISH_PROFIT_PRESCRIPTION.equals(
                canonicalStructuredIntentDetailWire(structuredIntentDetail.trim()));
    }

    public static boolean isStructuredDishIngredientCoverDaysDetail(String structuredIntentDetail) {
        if (structuredIntentDetail == null || structuredIntentDetail.isBlank()) {
            return false;
        }
        return STRUCTURED_DISH_INGREDIENT_COVER_DAYS.equals(
                canonicalStructuredIntentDetailWire(structuredIntentDetail.trim()));
    }

    /** {@code dish_cost_analysis_path} 下 contract-locked wire（成本卡、处方卡或配料可支撑天数）。 */
    public static boolean isDishCostPathStructuredDetail(String structuredIntentDetail) {
        return isStructuredDishCostAnalysisDetail(structuredIntentDetail)
                || isStructuredDishProfitPrescriptionDetail(structuredIntentDetail)
                || isStructuredDishIngredientCoverDaysDetail(structuredIntentDetail);
    }

    public static boolean isStructuredMenuOperationDetail(String structuredIntentDetail) {
        if (structuredIntentDetail == null || structuredIntentDetail.isBlank()) {
            return false;
        }
        String t = canonicalStructuredIntentDetailWire(structuredIntentDetail.trim());
        return STRUCTURED_MENU_OPERATION_OVERVIEW.equals(t)
                || STRUCTURED_MENU_DISH_HIGH_SALES_LOW_PROFIT.equals(t)
                || STRUCTURED_MENU_ACTION_RECOMMENDATION.equals(t);
    }

    public static boolean isDishProfitRankingStructuredDetail(String structuredIntentDetail) {
        String t = canonicalStructuredIntentDetailWire(structuredIntentDetail);
        return STRUCTURED_DISH_PROFIT_RANKING_LOW_MARGIN.equals(t)
                || STRUCTURED_DISH_PROFIT_RANKING_HIGH_MARGIN.equals(t)
                || STRUCTURED_DISH_PROFIT_RANKING_HIGH_PROFIT_AMOUNT.equals(t)
                || STRUCTURED_DISH_PROFIT_RANKING_LOW_PROFIT_AMOUNT.equals(t)
                || STRUCTURED_DISH_ACTUAL_COST_RANKING_HIGH.equals(t)
                || STRUCTURED_DISH_ACTUAL_COST_RANKING_LOW.equals(t)
                || STRUCTURED_DISH_THEORETICAL_COST_RANKING_HIGH.equals(t)
                || STRUCTURED_DISH_THEORETICAL_COST_RANKING_LOW.equals(t)
                || STRUCTURED_DISH_GAP_RANKING_MAX.equals(t);
    }

    public static boolean isSingleDishMetricOrReasonStructuredDetail(String structuredIntentDetail) {
        if (structuredIntentDetail == null || structuredIntentDetail.isBlank()) {
            return false;
        }
        String t = canonicalStructuredIntentDetailWire(structuredIntentDetail.trim());
        if (isDishProfitRankingStructuredDetail(t)) {
            return false;
        }
        if (isStructuredDishSalesDetail(t)) {
            return false;
        }
        return STRUCTURED_DISH_THEORETICAL_COST.equals(t) || STRUCTURED_DISH_ACTUAL_OUTBOUND_COST.equals(t)
                || STRUCTURED_DISH_GROSS_MARGIN_QUERY.equals(t) || STRUCTURED_DISH_COST_GAP.equals(t)
                || STRUCTURED_DISH_LOW_PROFIT_REASON.equals(t);
    }

    public static boolean isSupplierAmountRankingDetail(String structuredIntentDetail) {
        if (structuredIntentDetail == null || structuredIntentDetail.isBlank()) {
            return false;
        }
        String t = canonicalStructuredIntentDetailWire(structuredIntentDetail.trim());
        return STRUCTURED_SUPPLIER_AMOUNT_RANKING.equals(t);
    }

    public static String finalizeMentionedDishNameForDishProfit(String name) {
        if (!StringUtils.hasText(name)) {
            return null;
        }
        String trimmed = name.stripLeading().stripTrailing().replaceFirst("^\uFEFF+", "");
        String collapsed = trimmed.replaceAll("[\\s\\u3000]+", "").trim();
        return collapsed.isBlank() ? null : collapsed;
    }

    public static String formatGrossMarginRateForNaturalLanguage(String grossProfitRate) {
        if (!StringUtils.hasText(grossProfitRate)) {
            return "—";
        }
        String s = grossProfitRate.trim();
        if (s.endsWith("%")) {
            s = s.substring(0, s.length() - 1).trim();
        }
        if (!StringUtils.hasText(s)) {
            return "—";
        }
        final BigDecimal bd;
        try {
            bd = new BigDecimal(s);
        } catch (NumberFormatException ex) {
            return "—";
        }
        BigDecimal out = bd.stripTrailingZeros();
        int scale = Math.max(out.scale(), 0);
        if (scale > 2) {
            out = out.setScale(2, RoundingMode.HALF_UP).stripTrailingZeros();
        }
        return out.toPlainString() + "%";
    }

    public static String toStructuredIntentDetailDebugCode(String structuredIntentDetailWire) {
        return AiSemanticWireDebugFormatter.toStructuredIntentDetailDebugCode(structuredIntentDetailWire);
    }

    public static String wireToScreamingSnake(String wire) {
        return AiSemanticWireDebugFormatter.wireToScreamingSnake(wire);
    }

    public static String dishProfitMetricTypeFromStructuredWire(String wire) {
        if (wire == null || wire.isBlank()) {
            return null;
        }
        String t = canonicalStructuredIntentDetailWire(wire.trim());
        return switch (t) {
            case STRUCTURED_DISH_PROFIT_OVERVIEW -> "OVERVIEW";
            case STRUCTURED_DISH_THEORETICAL_COST -> "THEORETICAL_COST";
            case STRUCTURED_DISH_ACTUAL_OUTBOUND_COST -> "ACTUAL_OUTBOUND_COST";
            case STRUCTURED_DISH_COST_GAP -> "COST_GAP";
            case STRUCTURED_DISH_GROSS_MARGIN_QUERY -> "GROSS_MARGIN";
            case STRUCTURED_DISH_PROFIT_RANKING_LOW_MARGIN -> "RANKING_LOW_MARGIN";
            case STRUCTURED_DISH_PROFIT_RANKING_HIGH_MARGIN -> "RANKING_HIGH_MARGIN";
            case STRUCTURED_DISH_PROFIT_RANKING_HIGH_PROFIT_AMOUNT -> "RANKING_HIGH_PROFIT_AMOUNT";
            case STRUCTURED_DISH_PROFIT_RANKING_LOW_PROFIT_AMOUNT -> "RANKING_LOW_PROFIT_AMOUNT";
            case STRUCTURED_DISH_ACTUAL_COST_RANKING_HIGH -> "RANKING_HIGH_ACTUAL_COST";
            case STRUCTURED_DISH_GAP_RANKING_MAX -> "RANKING_MAX_GAP";
            case STRUCTURED_DISH_SALES_COUNT_RANKING_HIGH -> "RANKING_SALES";
            case STRUCTURED_DISH_SALES_AMOUNT_RANKING_HIGH -> "RANKING_SALES_AMOUNT_HIGH";
            case STRUCTURED_DISH_SALES_COUNT_RANKING_LOW -> "RANKING_SALES_COUNT_LOW";
            case STRUCTURED_DISH_SALES_SINGLE_DISH, STRUCTURED_DISH_SALES_STORE_SINGLE_DISH -> "DETAIL_SALES_SINGLE_DISH";
            case STRUCTURED_DISH_SALES_STORE_RANKING -> "RANKING_SALES_STORE";
            case STRUCTURED_DISH_SALES_TREND -> "TREND_SALES";
            case STRUCTURED_DISH_SALES_OVERVIEW -> "OVERVIEW_SALES";
            case STRUCTURED_DISH_INGREDIENT_COST_BREAKDOWN -> "INGREDIENT_BREAKDOWN";
            case STRUCTURED_DISH_LOW_PROFIT_REASON -> "LOW_PROFIT_REASON";
            default -> wireToScreamingSnake(t);
        };
    }
}
