package com.nongxinle.ai.conversation;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;

/**
 * 结构化子意图协议：wire 常量、canonical/debug 映射、格式化工具。
 * <p>不包含自然语言 contains/正则语义判断。</p>
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class AiQuerySemanticLexicon {

    public static final String STRUCTURED_PURCHASE_OVERVIEW_SUMMARY = "purchase_overview_summary";
    public static final String STRUCTURED_PURCHASE_SOURCE_SUMMARY = "purchase_source_summary";
    public static final String STRUCTURED_PURCHASE_SOURCE_AMOUNT_QUERY = "purchase_source_amount_query";
    public static final String STRUCTURED_PURCHASE_SOURCE_GOODS_QUERY = "purchase_source_goods_query";
    /** 商品采购金额排行（metric.rankingType / structuredIntentDetail；勿用语义 contains 推断）。 */
    public static final String STRUCTURED_PURCHASE_GOODS_AMOUNT_RANKING = "purchase_goods_amount_ranking";
    /** 商品采购次数排行 */
    public static final String STRUCTURED_PURCHASE_GOODS_COUNT_RANKING = "purchase_goods_count_ranking";
    /** 采购异常总览（未细分单价/次数/数量等）：仅解析层 structured / metric wire，禁止话术 contains。 */
    public static final String STRUCTURED_PURCHASE_GOODS_ANOMALY = "purchase_goods_anomaly";
    /** 采购单价异常。 */
    public static final String STRUCTURED_PURCHASE_PRICE_ANOMALY = "purchase_price_anomaly";
    /** 采购次数异常。 */
    public static final String STRUCTURED_PURCHASE_FREQUENCY_ANOMALY = "purchase_frequency_anomaly";
    /** 采购数量异常。 */
    public static final String STRUCTURED_PURCHASE_QUANTITY_ANOMALY = "purchase_quantity_anomaly";
    /** 商品采购金额突变/冲高（环比或异常检测）：同上。 */
    public static final String STRUCTURED_PURCHASE_GOODS_AMOUNT_SPIKE = "purchase_goods_amount_spike";
    /** 采购多、出库/耗用过少（双域风险 wire）。 */
    public static final String STRUCTURED_PURCHASE_STOCK_REDUCE_MISMATCH = "purchase_stock_reduce_mismatch";
    /** 采购后长期无出库/未核销/慢动销（双域风险 wire）。 */
    public static final String STRUCTURED_PURCHASE_SLOW_MOVING_RISK = "purchase_slow_moving_risk";
    /** 积压/库存压力过大（双域风险 wire）。 */
    public static final String STRUCTURED_PURCHASE_INVENTORY_OVERSTOCK_RISK = "purchase_inventory_overstock_risk";
    /** 效期/新鲜度风险（双域风险 wire）。 */
    public static final String STRUCTURED_PURCHASE_FRESHNESS_RISK = "purchase_freshness_risk";
    public static final String STRUCTURED_PURCHASE_STORE_AMOUNT_RANKING = "purchase_store_amount_ranking";
    public static final String STRUCTURED_SUPPLIER_AMOUNT_RANKING = "supplier_amount_ranking";
    public static final String STRUCTURED_SUPPLIER_RANKING = STRUCTURED_SUPPLIER_AMOUNT_RANKING;

    public static final String SOURCE_SELF_PURCHASE = "SELF_PURCHASE";
    public static final String SOURCE_SUPPLIER_PURCHASE = "SUPPLIER_PURCHASE";
    public static final String SOURCE_ALL = "ALL";

    public static final String STRUCTURED_STOCK_REDUCE_OVERVIEW_SUMMARY = "stock_reduce_overview";
    public static final String STRUCTURED_PRODUCE_CONSUME = "produce_consume";
    /** 出品耗用（type1）；与生产耗用区分由解析层下发，禁止 Builder 读原文猜测。 */
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
    /** 综合毛利率由高到低排行（与 {@code dish_gross_profit_rate_ranking_high} 对齐）。 */
    public static final String STRUCTURED_DISH_PROFIT_RANKING_HIGH_MARGIN = "dish_profit_ranking_high_margin";
    public static final String STRUCTURED_DISH_LOW_PROFIT_REASON = "dish_low_profit_reason";
    public static final String STRUCTURED_DISH_ACTUAL_COST_RANKING_HIGH = "dish_actual_cost_ranking_high";
    /** 实际耗用/成本由低到高排行。 */
    public static final String STRUCTURED_DISH_ACTUAL_COST_RANKING_LOW = "dish_actual_cost_ranking_low";
    public static final String STRUCTURED_DISH_THEORETICAL_COST_RANKING_HIGH = "dish_theoretical_cost_ranking_high";
    public static final String STRUCTURED_DISH_THEORETICAL_COST_RANKING_LOW = "dish_theoretical_cost_ranking_low";
    public static final String STRUCTURED_DISH_GAP_RANKING_MAX = "dish_gap_ranking_max";
    /** @deprecated 历史字面量；{@link #canonicalStructuredIntentDetailWire} 归一到 {@link #STRUCTURED_DISH_SALES_COUNT_RANKING_HIGH}。 */
    @Deprecated
    public static final String STRUCTURED_DISH_SALES_RANKING = "dish_sales_ranking";
    /** 菜品销量（份数）由高到低排行。 */
    public static final String STRUCTURED_DISH_SALES_COUNT_RANKING_HIGH = "dish_sales_count_ranking_high";
    /** 菜品销售额由高到低排行。 */
    public static final String STRUCTURED_DISH_SALES_AMOUNT_RANKING_HIGH = "dish_sales_amount_ranking_high";
    /** 菜品销量（份数）由低到高排行。 */
    public static final String STRUCTURED_DISH_SALES_COUNT_RANKING_LOW = "dish_sales_count_ranking_low";
    public static final String STRUCTURED_DISH_INGREDIENT_COST_BREAKDOWN = "dish_ingredient_cost_breakdown";

    public static final String STRUCTURED_REVENUE_OVERVIEW_SUMMARY = "revenue_overview_summary";
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
    /**
     * 成本偏高 / 结构性成本压力的证据型诊断（走 BUSINESS_DIAGNOSIS），由解析层 metric 与服务端 merge 对齐，不靠用户原文正则。
     */
    public static final String STRUCTURED_BUSINESS_COST_PRESSURE_DIAGNOSIS = "business_cost_pressure_diagnosis";
    /**
     * 多店综合对比且要求因果/原因解释的诊断口径（同上）。
     */
    public static final String STRUCTURED_BUSINESS_STORE_COMPARE_DIAGNOSIS = "business_store_status_compare_diagnosis";
    public static final String STRUCTURED_BUSINESS_OVERVIEW_SUMMARY = "business_overview_summary";
    public static final String STRUCTURED_BUSINESS_OVERVIEW_STATUS = "business_overview_status";
    /** 多店综合经营对比（非纯营业额排行）；工具链仍走经营概览默认 tools。 */
    public static final String STRUCTURED_BUSINESS_STORE_STATUS_COMPARE = "business_store_status_compare";

    public static final String STRUCTURED_STORE_PRIORITY_RANKING = "store_priority_ranking";
    public static final String STRUCTURED_STORE_RISK_RANKING = "store_risk_ranking";

    /**
     * 库存偏低 / 不足风险提示（账面启发式 {@code lowStockItems}；非真实安全库存线）。
     */
    public static final String STRUCTURED_WAREHOUSE_STOCK_LOW_RISK = "warehouse_stock_low_risk";
    /**
     * 「需要补货」语义锚点（诚实降级：不得推断精确订货量）。
     */
    public static final String STRUCTURED_WAREHOUSE_STOCK_REPLENISHMENT_NEEDED = "warehouse_stock_replenishment_needed";
    /**
     * 纯库存侧偏高 / 账面「太多、压力大」等（启发式 {@code overStockItems}；非真实滞销或 MRP）。
     */
    public static final String STRUCTURED_WAREHOUSE_STOCK_OVERSTOCK_RISK = "warehouse_stock_overstock_risk";
    /** 门店维度库存剩余金额排行或对比（D-6 Phase 4B；与经营对比 wire 区分）。 */
    public static final String STRUCTURED_STORE_STOCK_AMOUNT_RANKING = "store_stock_amount_ranking";
    /** 门店维度库存 SKU/商品种类数排行（D-6 Phase 4B）。 */
    public static final String STRUCTURED_STORE_STOCK_ITEM_COUNT_RANKING = "store_stock_item_count_ranking";
    /** 库房维度库存金额排行（D-6 Phase 4B 注册；数据模型未稳时答复须诚实降级）。 */
    public static final String STRUCTURED_WAREHOUSE_STOCK_AMOUNT_RANKING = "warehouse_stock_amount_ranking";
    /** 库房维度库存商品种类数排行（同上）。 */
    public static final String STRUCTURED_WAREHOUSE_STOCK_ITEM_COUNT_RANKING = "warehouse_stock_item_count_ranking";

    private static String toLowerSnakeWire(String raw) {
        String t = raw.trim().replace('-', '_');
        if (!t.contains("_")) {
            return t.toLowerCase(Locale.ROOT);
        }
        String upperish = t.toUpperCase(Locale.ROOT);
        boolean looksScreaming = t.equals(upperish);
        return looksScreaming ? t.toLowerCase(Locale.ROOT) : t.toLowerCase(Locale.ROOT);
    }

    /**
     * Wire / LLM枚举片段 → canonical wire（低风险别名归一并小写）。
     */
    public static String canonicalStructuredIntentDetailWire(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        String snake = toLowerSnakeWire(raw);
        return switch (snake) {
            // Merge / v2 可能将 metric.stockReduceType 原样写入 structuredIntentDetail（TYPE1…），归一到与 Builder 一致的 snake wire。
            case "type1" -> STRUCTURED_PRODUCE_CONSUME;
            case "type2" -> STRUCTURED_WASTE;
            case "type3" -> STRUCTURED_LOSS;
            case "type4" -> STRUCTURED_RETURN;
            case "supplier_ranking" -> STRUCTURED_SUPPLIER_AMOUNT_RANKING;
            case "goods_outbound_amount_ranking" -> STRUCTURED_GOODS_OUTBOUND_RANKING;
            case "stock_reduce_store_amount_ranking" -> STRUCTURED_STORE_OUTBOUND_AMOUNT_RANKING;
            case "business_store_status_ranking" -> STRUCTURED_BUSINESS_STORE_STATUS_COMPARE;
            case "cost_pressure_diagnosis", "business_cost_pressure_diagnosis", "cost_pressure" ->
                    STRUCTURED_BUSINESS_COST_PRESSURE_DIAGNOSIS;
            case "business_store_compare_diagnosis",
                    "business_status_compare_diagnosis",
                    "business_store_status_compare_diagnosis" -> STRUCTURED_BUSINESS_STORE_COMPARE_DIAGNOSIS;
            case STRUCTURED_STORE_RISK_RANKING -> STRUCTURED_STORE_PRIORITY_RANKING;
            case "dish_actual_cost_ranking", "dish_actual_cost_amount_ranking" -> STRUCTURED_DISH_ACTUAL_COST_RANKING_HIGH;
            case "dish_gross_profit_rate_ranking_low" -> STRUCTURED_DISH_PROFIT_RANKING_LOW_MARGIN;
            case "dish_gross_profit_rate_ranking_high" -> STRUCTURED_DISH_PROFIT_RANKING_HIGH_MARGIN;
            case "dish_actual_cost_ranking_low" -> STRUCTURED_DISH_ACTUAL_COST_RANKING_LOW;
            case "dish_theoretical_cost_ranking_high" -> STRUCTURED_DISH_THEORETICAL_COST_RANKING_HIGH;
            case "dish_theoretical_cost_ranking_low" -> STRUCTURED_DISH_THEORETICAL_COST_RANKING_LOW;
            case STRUCTURED_DISH_GAP_RANKING_MAX,
                    "dish_cost_gap_ranking_max",
                    "dish_theoretical_actual_gap_ranking_max",
                    "dish_ingredient_cost_gap_ranking_max" -> STRUCTURED_DISH_GAP_RANKING_MAX;
            case "purchase_amount_ranking_high",
                    "purchase_goods_amount_ranking_high",
                    "goods_purchase_amount_ranking_high",
                    "purchase_goods_purchase_amount_ranking_high",
                    "highest_goods_purchase_amount_ranking" ->
                    STRUCTURED_PURCHASE_GOODS_AMOUNT_RANKING;
            case "purchase_unit_price_abnormal",
                    "purchase_abnormal_price",
                    "purchase_price_abnormal",
                    "purchase_abnormal_unit_price" -> STRUCTURED_PURCHASE_PRICE_ANOMALY;
            case "purchase_frequency_abnormal",
                    "purchase_count_anomaly",
                    "purchase_times_anomaly",
                    "purchase_count_abnormal",
                    "purchase_times_abnormal" -> STRUCTURED_PURCHASE_FREQUENCY_ANOMALY;
            case "purchase_anomaly_quantity",
                    "purchase_quantity_abnormal",
                    "purchase_abnormal_quantity" -> STRUCTURED_PURCHASE_QUANTITY_ANOMALY;
            case "purchase_goods_anomaly",
                    "purchase_anomaly_goods",
                    "goods_purchase_anomaly",
                    "purchase_goods_amount_anomaly" -> STRUCTURED_PURCHASE_GOODS_ANOMALY;
            case "purchase_goods_amount_spike",
                    "purchase_amount_spike",
                    "purchase_amount_abnormal_increase",
                    "purchase_amount_rise_abnormal",
                    "goods_purchase_amount_spike",
                    "goods_amount_spike" -> STRUCTURED_PURCHASE_GOODS_AMOUNT_SPIKE;
            case "purchase_reduce_mismatch",
                    "purchase_outbound_mismatch",
                    "purchase_consumption_mismatch",
                    "purchase_stock_reduce_gap",
                    "purchase_usage_mismatch" -> STRUCTURED_PURCHASE_STOCK_REDUCE_MISMATCH;
            case "purchase_no_stock_reduce",
                    "purchase_no_outbound",
                    "purchase_not_used_after_purchase",
                    "purchase_no_verify_after_purchase",
                    "purchase_long_time_no_reduce" -> STRUCTURED_PURCHASE_SLOW_MOVING_RISK;
            case STRUCTURED_WAREHOUSE_STOCK_OVERSTOCK_RISK,
                    "warehouse_overstock_risk",
                    "stock_overstock_risk",
                    "inventory_overstock_risk",
                    "warehouse_stock_high_risk",
                    "high_stock",
                    "high_inventory",
                    "stock_too_much",
                    "inventory_too_much",
                    "stock_pressure",
                    "inventory_pressure",
                    "stock_amount_high",
                    "inventory_amount_high",
                    "over_stock_items" -> STRUCTURED_WAREHOUSE_STOCK_OVERSTOCK_RISK;
            case "purchase_overstock_risk",
                    "purchase_stock_overstock",
                    "purchase_inventory_pressure" -> STRUCTURED_PURCHASE_INVENTORY_OVERSTOCK_RISK;
            case "purchase_expiry_risk",
                    "purchase_expiration_risk",
                    "freshness_risk",
                    "purchase_not_used_expiring",
                    "purchase_freshness_warning" -> STRUCTURED_PURCHASE_FRESHNESS_RISK;
            case STRUCTURED_DISH_SALES_RANKING,
                    "dish_sales_ranking_high",
                    "dish_sales_count_ranking",
                    "dish_sold_count_ranking_high",
                    "dish_sold_portions_ranking_high" -> STRUCTURED_DISH_SALES_COUNT_RANKING_HIGH;
            case "dish_sales_amount_ranking",
                    "dish_revenue_ranking_high",
                    "dish_sales_revenue_ranking_high",
                    "dish_list_price_revenue_ranking_high" -> STRUCTURED_DISH_SALES_AMOUNT_RANKING_HIGH;
            case "dish_sales_count_ranking_low",
                    "dish_sales_ranking_low",
                    "dish_sold_count_ranking_low",
                    "dish_sold_portions_ranking_low" -> STRUCTURED_DISH_SALES_COUNT_RANKING_LOW;
            case "stock_below_safety",
                    "below_safety_stock",
                    "low_stock",
                    "low_inventory",
                    "warehouse_low_stock",
                    "out_of_stock_risk",
                    "soon_out_of_stock" -> STRUCTURED_WAREHOUSE_STOCK_LOW_RISK;
            case "replenishment_needed",
                    "need_replenishment",
                    "restock_needed",
                    "stock_replenishment_needed",
                    "warehouse_replenishment_needed" -> STRUCTURED_WAREHOUSE_STOCK_REPLENISHMENT_NEEDED;
            case STRUCTURED_STORE_STOCK_AMOUNT_RANKING,
                    "store_inventory_amount_ranking",
                    "store_inventory_ranking",
                    "store_stock_value_ranking",
                    "store_inventory_value_ranking",
                    "store_stock_amount_compare",
                    "store_inventory_amount_compare" -> STRUCTURED_STORE_STOCK_AMOUNT_RANKING;
            case STRUCTURED_STORE_STOCK_ITEM_COUNT_RANKING,
                    "store_inventory_item_count_ranking",
                    "store_stock_goods_count_ranking",
                    "store_inventory_goods_count_ranking",
                    "store_sku_count_ranking",
                    "store_inventory_sku_count_ranking" -> STRUCTURED_STORE_STOCK_ITEM_COUNT_RANKING;
            case STRUCTURED_WAREHOUSE_STOCK_AMOUNT_RANKING,
                    "warehouse_inventory_amount_ranking",
                    "warehouse_stock_value_ranking",
                    "warehouse_inventory_value_ranking" -> STRUCTURED_WAREHOUSE_STOCK_AMOUNT_RANKING;
            case STRUCTURED_WAREHOUSE_STOCK_ITEM_COUNT_RANKING,
                    "warehouse_inventory_item_count_ranking",
                    "warehouse_stock_goods_count_ranking",
                    "warehouse_inventory_goods_count_ranking",
                    "warehouse_sku_count_ranking" -> STRUCTURED_WAREHOUSE_STOCK_ITEM_COUNT_RANKING;
            default -> snake;
        };
    }

    /**
     * 已为商品侧采购结构化子口径或采购+出库双域商品风险 wire（不含门店横向对比）；
     * 供 merge 层抑制多店误写 store ranking。
     */
    public static boolean isStructuredPurchaseGoodsFocusedDetail(String structuredIntentDetail) {
        String c = canonicalStructuredIntentDetailWire(structuredIntentDetail);
        if (!StringUtils.hasText(c)) {
            return false;
        }
        return STRUCTURED_PURCHASE_GOODS_AMOUNT_RANKING.equals(c)
                || STRUCTURED_PURCHASE_GOODS_COUNT_RANKING.equals(c)
                || STRUCTURED_PURCHASE_GOODS_ANOMALY.equals(c)
                || STRUCTURED_PURCHASE_PRICE_ANOMALY.equals(c)
                || STRUCTURED_PURCHASE_FREQUENCY_ANOMALY.equals(c)
                || STRUCTURED_PURCHASE_QUANTITY_ANOMALY.equals(c)
                || STRUCTURED_PURCHASE_GOODS_AMOUNT_SPIKE.equals(c)
                || STRUCTURED_PURCHASE_STOCK_REDUCE_MISMATCH.equals(c)
                || STRUCTURED_PURCHASE_SLOW_MOVING_RISK.equals(c)
                || STRUCTURED_PURCHASE_INVENTORY_OVERSTOCK_RISK.equals(c)
                || STRUCTURED_PURCHASE_FRESHNESS_RISK.equals(c)
                || STRUCTURED_PURCHASE_SOURCE_GOODS_QUERY.equals(c);
    }

    public static boolean isStorePriorityRankingStructuredDetail(String structuredIntentDetail) {
        String c = canonicalStructuredIntentDetailWire(structuredIntentDetail);
        return STRUCTURED_STORE_PRIORITY_RANKING.equals(c);
    }

    /**
     * {@code business_overview_path} 上四域 MultiAgent 编排应对齐的结构化子意图（经营综合汇总/多店经营综合对比）；
     * 与 {@link com.nongxinle.ai.graph.business.DiagnosisPlanBuilder DiagnosisPlanBuilder} 挂载 DiagnosisPlan 的经营概览表面一致。
     * <p>不单靠用户话术；仅以 canonical wire 判定。</p>
     */
    public static boolean isStructuredBusinessOverviewFourDomainOrchestrationSurface(String structuredIntentDetail) {
        String c = canonicalStructuredIntentDetailWire(structuredIntentDetail);
        if (!StringUtils.hasText(c)) {
            return false;
        }
        return STRUCTURED_BUSINESS_OVERVIEW_SUMMARY.equals(c)
                || STRUCTURED_BUSINESS_OVERVIEW_STATUS.equals(c)
                || STRUCTURED_BUSINESS_STORE_STATUS_COMPARE.equals(c);
    }

    /** 点菜毛利追问：低毛利原因结构化子意图。 */
    public static boolean isDishLowProfitReasonStructuredWire(String structuredIntentDetail) {
        String c = canonicalStructuredIntentDetailWire(structuredIntentDetail);
        return STRUCTURED_DISH_LOW_PROFIT_REASON.equals(c);
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

    public static boolean isStructuredDishProfitDetail(String structuredIntentDetail) {
        if (structuredIntentDetail == null || structuredIntentDetail.isBlank()) {
            return false;
        }
        String t = canonicalStructuredIntentDetailWire(structuredIntentDetail.trim());
        return STRUCTURED_DISH_PROFIT_OVERVIEW.equals(t) || isNonOverviewDishProfitStructuredDetail(t);
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
                || STRUCTURED_DISH_ACTUAL_COST_RANKING_HIGH.equals(t) || STRUCTURED_DISH_ACTUAL_COST_RANKING_LOW.equals(t)
                || STRUCTURED_DISH_THEORETICAL_COST_RANKING_HIGH.equals(t)
                || STRUCTURED_DISH_THEORETICAL_COST_RANKING_LOW.equals(t)
                || STRUCTURED_DISH_LOW_PROFIT_REASON.equals(t)
                || STRUCTURED_DISH_GAP_RANKING_MAX.equals(t)
                || STRUCTURED_DISH_INGREDIENT_COST_BREAKDOWN.equals(t);
    }

    /** 门店/库房库存排行类 structured wire（Phase 4B；不靠用户原文推断）。 */
    public static boolean isStructuredWarehouseStockRankingDetail(String structuredIntentDetail) {
        if (structuredIntentDetail == null || structuredIntentDetail.isBlank()) {
            return false;
        }
        String t = canonicalStructuredIntentDetailWire(structuredIntentDetail.trim());
        return STRUCTURED_STORE_STOCK_AMOUNT_RANKING.equals(t)
                || STRUCTURED_STORE_STOCK_ITEM_COUNT_RANKING.equals(t)
                || STRUCTURED_WAREHOUSE_STOCK_AMOUNT_RANKING.equals(t)
                || STRUCTURED_WAREHOUSE_STOCK_ITEM_COUNT_RANKING.equals(t);
    }

    /** 菜品销量/销售额 structured wire（canonical）。 */
    public static boolean isStructuredDishSalesDetail(String structuredIntentDetail) {
        if (structuredIntentDetail == null || structuredIntentDetail.isBlank()) {
            return false;
        }
        String t = canonicalStructuredIntentDetailWire(structuredIntentDetail.trim());
        return STRUCTURED_DISH_SALES_COUNT_RANKING_HIGH.equals(t)
                || STRUCTURED_DISH_SALES_AMOUNT_RANKING_HIGH.equals(t)
                || STRUCTURED_DISH_SALES_COUNT_RANKING_LOW.equals(t);
    }

    public static boolean isDishProfitRankingStructuredDetail(String structuredIntentDetail) {
        String t = canonicalStructuredIntentDetailWire(structuredIntentDetail);
        return STRUCTURED_DISH_PROFIT_RANKING_LOW_MARGIN.equals(t)
                || STRUCTURED_DISH_PROFIT_RANKING_HIGH_MARGIN.equals(t)
                || STRUCTURED_DISH_ACTUAL_COST_RANKING_HIGH.equals(t)
                || STRUCTURED_DISH_ACTUAL_COST_RANKING_LOW.equals(t)
                || STRUCTURED_DISH_THEORETICAL_COST_RANKING_HIGH.equals(t)
                || STRUCTURED_DISH_THEORETICAL_COST_RANKING_LOW.equals(t)
                || STRUCTURED_DISH_GAP_RANKING_MAX.equals(t);
    }

    /** 单菜明细/追问：排行榜类除外。 */
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

    /** 供货商金额/笔数排行 wire（兼容历史别名）。 */
    public static boolean isSupplierAmountRankingDetail(String structuredIntentDetail) {
        if (structuredIntentDetail == null || structuredIntentDetail.isBlank()) {
            return false;
        }
        String t = canonicalStructuredIntentDetailWire(structuredIntentDetail.trim());
        return STRUCTURED_SUPPLIER_AMOUNT_RANKING.equals(t);
    }

    /**
     * LLM/json 下发的点名菜名归一（已由解析层抽到；此处仅去空白/BOM）。
     */
    public static String finalizeMentionedDishNameForDishProfit(String name) {
        if (!StringUtils.hasText(name)) {
            return null;
        }
        String trimmed = name.stripLeading().stripTrailing().replaceFirst("^\uFEFF+", "");
        String collapsed = trimmed.replaceAll("[\\s\\u3000]+", "").trim();
        return collapsed.isBlank() ? null : collapsed;
    }

    /** 自然语言话术：格式化毛利率百分比（已为百分数口径的值）。 */
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

    /**
     * Harness/Debug：wire → 可读枚举标签（UNKNOWN wire 则用 {@link #wireToScreamingSnake}）。
     */
    public static String toStructuredIntentDetailDebugCode(String structuredIntentDetailWire) {
        if (structuredIntentDetailWire == null || structuredIntentDetailWire.isBlank()) {
            return null;
        }
        String w = canonicalStructuredIntentDetailWire(structuredIntentDetailWire.trim());
        if (w == null) {
            return null;
        }
        if (STRUCTURED_SUPPLIER_AMOUNT_RANKING.equals(w)) {
            return "SUPPLIER_AMOUNT_RANKING";
        }
        if (STRUCTURED_PURCHASE_OVERVIEW_SUMMARY.equals(w)) {
            return "PURCHASE_OVERVIEW_SUMMARY";
        }
        if (STRUCTURED_PURCHASE_SOURCE_SUMMARY.equals(w)) {
            return "PURCHASE_SOURCE_SUMMARY";
        }
        if (STRUCTURED_PURCHASE_SOURCE_AMOUNT_QUERY.equals(w)) {
            return "PURCHASE_SOURCE_AMOUNT_QUERY";
        }
        if (STRUCTURED_PURCHASE_SOURCE_GOODS_QUERY.equals(w)) {
            return "PURCHASE_SOURCE_GOODS_QUERY";
        }
        if (STRUCTURED_PURCHASE_GOODS_AMOUNT_RANKING.equals(w)) {
            return "PURCHASE_GOODS_AMOUNT_RANKING";
        }
        if (STRUCTURED_PURCHASE_GOODS_COUNT_RANKING.equals(w)) {
            return "PURCHASE_GOODS_COUNT_RANKING";
        }
        if (STRUCTURED_PURCHASE_STORE_AMOUNT_RANKING.equals(w)) {
            return "PURCHASE_STORE_AMOUNT_RANKING";
        }
        if (STRUCTURED_PURCHASE_GOODS_ANOMALY.equals(w)) {
            return "PURCHASE_GOODS_ANOMALY";
        }
        if (STRUCTURED_PURCHASE_PRICE_ANOMALY.equals(w)) {
            return "PURCHASE_PRICE_ANOMALY";
        }
        if (STRUCTURED_PURCHASE_FREQUENCY_ANOMALY.equals(w)) {
            return "PURCHASE_FREQUENCY_ANOMALY";
        }
        if (STRUCTURED_PURCHASE_QUANTITY_ANOMALY.equals(w)) {
            return "PURCHASE_QUANTITY_ANOMALY";
        }
        if (STRUCTURED_PURCHASE_GOODS_AMOUNT_SPIKE.equals(w)) {
            return "PURCHASE_GOODS_AMOUNT_SPIKE";
        }
        if (STRUCTURED_PURCHASE_STOCK_REDUCE_MISMATCH.equals(w)) {
            return "PURCHASE_STOCK_REDUCE_MISMATCH";
        }
        if (STRUCTURED_PURCHASE_SLOW_MOVING_RISK.equals(w)) {
            return "PURCHASE_SLOW_MOVING_RISK";
        }
        if (STRUCTURED_PURCHASE_INVENTORY_OVERSTOCK_RISK.equals(w)) {
            return "PURCHASE_INVENTORY_OVERSTOCK_RISK";
        }
        if (STRUCTURED_PURCHASE_FRESHNESS_RISK.equals(w)) {
            return "PURCHASE_FRESHNESS_RISK";
        }
        if (STRUCTURED_STOCK_REDUCE_OVERVIEW_SUMMARY.equals(w)) {
            return "STOCK_REDUCE_OVERVIEW";
        }
        if (STRUCTURED_PRODUCE_CONSUME.equals(w)) {
            return "PRODUCE_CONSUME";
        }
        if (STRUCTURED_PRODUCE_OUTPUT.equals(w)) {
            return "PRODUCE_OUTPUT";
        }
        if (STRUCTURED_WASTE.equals(w)) {
            return "WASTE";
        }
        if (STRUCTURED_LOSS.equals(w)) {
            return "LOSS";
        }
        if (STRUCTURED_RETURN.equals(w)) {
            return "RETURN";
        }
        if (STRUCTURED_GOODS_OUTBOUND_RANKING.equals(w)) {
            return "GOODS_OUTBOUND_RANKING";
        }
        if (STRUCTURED_GOODS_OUTBOUND_COUNT_RANKING.equals(w)) {
            return "GOODS_OUTBOUND_COUNT_RANKING";
        }
        if (STRUCTURED_STORE_OUTBOUND_AMOUNT_RANKING.equals(w)) {
            return "STORE_OUTBOUND_AMOUNT_RANKING";
        }
        if (STRUCTURED_REVENUE_OVERVIEW_SUMMARY.equals(w)) {
            return "REVENUE_OVERVIEW_SUMMARY";
        }
        if (STRUCTURED_REVENUE_DINE_IN_OVERVIEW.equals(w)) {
            return "REVENUE_DINE_IN_OVERVIEW";
        }
        if (STRUCTURED_REVENUE_TAKEOUT_OVERVIEW.equals(w)) {
            return "REVENUE_TAKEOUT_OVERVIEW";
        }
        if (STRUCTURED_REVENUE_PLATFORM_RANKING.equals(w)) {
            return "REVENUE_PLATFORM_RANKING";
        }
        if (STRUCTURED_REVENUE_ORDER_COUNT_OVERVIEW.equals(w)) {
            return "REVENUE_ORDER_COUNT_OVERVIEW";
        }
        if (STRUCTURED_REVENUE_CUSTOMER_COUNT_OVERVIEW.equals(w)) {
            return "REVENUE_CUSTOMER_COUNT_OVERVIEW";
        }
        if (STRUCTURED_REVENUE_AVERAGE_ORDER_VALUE.equals(w)) {
            return "REVENUE_AVERAGE_ORDER_VALUE";
        }
        if (STRUCTURED_REVENUE_DAILY_AMOUNT_RANKING.equals(w)) {
            return "REVENUE_DAILY_AMOUNT_RANKING";
        }
        if (STRUCTURED_REVENUE_STORE_AMOUNT_RANKING.equals(w)) {
            return "REVENUE_STORE_AMOUNT_RANKING";
        }
        if (STRUCTURED_REVENUE_CHANNEL_BREAKDOWN.equals(w)) {
            return "REVENUE_CHANNEL_BREAKDOWN";
        }
        if (STRUCTURED_BUSINESS_DIAGNOSIS_SUMMARY.equals(w)) {
            return "BUSINESS_DIAGNOSIS_SUMMARY";
        }
        if (STRUCTURED_BUSINESS_OVERVIEW_SUMMARY.equals(w)) {
            return "BUSINESS_OVERVIEW_SUMMARY";
        }
        if (STRUCTURED_BUSINESS_OVERVIEW_STATUS.equals(w)) {
            return "BUSINESS_OVERVIEW_STATUS";
        }
        if (STRUCTURED_BUSINESS_STORE_STATUS_COMPARE.equals(w)) {
            return "BUSINESS_STORE_STATUS_COMPARE";
        }
        if (STRUCTURED_STORE_PRIORITY_RANKING.equals(w)) {
            return "STORE_PRIORITY_RANKING";
        }
        if (STRUCTURED_DISH_PROFIT_OVERVIEW.equals(w)) {
            return "DISH_PROFIT_OVERVIEW";
        }
        if (STRUCTURED_DISH_THEORETICAL_COST.equals(w)) {
            return "DISH_THEORETICAL_COST";
        }
        if (STRUCTURED_DISH_ACTUAL_OUTBOUND_COST.equals(w)) {
            return "DISH_ACTUAL_OUTBOUND_COST";
        }
        if (STRUCTURED_DISH_COST_GAP.equals(w)) {
            return "DISH_COST_GAP";
        }
        if (STRUCTURED_DISH_GROSS_MARGIN_QUERY.equals(w)) {
            return "DISH_GROSS_MARGIN_QUERY";
        }
        if (STRUCTURED_DISH_PROFIT_RANKING_LOW_MARGIN.equals(w)) {
            return "DISH_LOW_PROFIT_RANKING";
        }
        if (STRUCTURED_DISH_PROFIT_RANKING_HIGH_MARGIN.equals(w)) {
            return "DISH_HIGH_PROFIT_RANKING";
        }
        if (STRUCTURED_DISH_ACTUAL_COST_RANKING_HIGH.equals(w)) {
            return "DISH_ACTUAL_COST_RANKING";
        }
        if (STRUCTURED_DISH_ACTUAL_COST_RANKING_LOW.equals(w)) {
            return "DISH_ACTUAL_COST_RANKING_LOW";
        }
        if (STRUCTURED_DISH_THEORETICAL_COST_RANKING_HIGH.equals(w)) {
            return "DISH_THEORETICAL_COST_RANKING_HIGH";
        }
        if (STRUCTURED_DISH_THEORETICAL_COST_RANKING_LOW.equals(w)) {
            return "DISH_THEORETICAL_COST_RANKING_LOW";
        }
        if (STRUCTURED_DISH_LOW_PROFIT_REASON.equals(w)) {
            return "DISH_LOW_PROFIT_REASON";
        }
        if (STRUCTURED_DISH_GAP_RANKING_MAX.equals(w)) {
            return "DISH_GAP_RANKING_MAX";
        }
        if (STRUCTURED_DISH_SALES_COUNT_RANKING_HIGH.equals(w)) {
            return "DISH_SALES_COUNT_RANKING_HIGH";
        }
        if (STRUCTURED_DISH_SALES_AMOUNT_RANKING_HIGH.equals(w)) {
            return "DISH_SALES_AMOUNT_RANKING_HIGH";
        }
        if (STRUCTURED_DISH_SALES_COUNT_RANKING_LOW.equals(w)) {
            return "DISH_SALES_COUNT_RANKING_LOW";
        }
        if (STRUCTURED_DISH_INGREDIENT_COST_BREAKDOWN.equals(w)) {
            return "DISH_INGREDIENT_COST_BREAKDOWN";
        }
        if (STRUCTURED_WAREHOUSE_STOCK_OVERSTOCK_RISK.equals(w)) {
            return "WAREHOUSE_STOCK_OVERSTOCK_RISK";
        }
        if (STRUCTURED_WAREHOUSE_STOCK_LOW_RISK.equals(w)) {
            return "WAREHOUSE_STOCK_LOW_RISK";
        }
        if (STRUCTURED_WAREHOUSE_STOCK_REPLENISHMENT_NEEDED.equals(w)) {
            return "WAREHOUSE_STOCK_REPLENISHMENT_NEEDED";
        }
        if (STRUCTURED_STORE_STOCK_AMOUNT_RANKING.equals(w)) {
            return "STORE_STOCK_AMOUNT_RANKING";
        }
        if (STRUCTURED_STORE_STOCK_ITEM_COUNT_RANKING.equals(w)) {
            return "STORE_STOCK_ITEM_COUNT_RANKING";
        }
        if (STRUCTURED_WAREHOUSE_STOCK_AMOUNT_RANKING.equals(w)) {
            return "WAREHOUSE_STOCK_AMOUNT_RANKING";
        }
        if (STRUCTURED_WAREHOUSE_STOCK_ITEM_COUNT_RANKING.equals(w)) {
            return "WAREHOUSE_STOCK_ITEM_COUNT_RANKING";
        }
        return wireToScreamingSnake(w);
    }

    public static String wireToScreamingSnake(String wire) {
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
            case STRUCTURED_DISH_ACTUAL_COST_RANKING_HIGH -> "RANKING_HIGH_ACTUAL_COST";
            case STRUCTURED_DISH_GAP_RANKING_MAX -> "RANKING_MAX_GAP";
            case STRUCTURED_DISH_SALES_COUNT_RANKING_HIGH -> "RANKING_SALES";
            case STRUCTURED_DISH_SALES_AMOUNT_RANKING_HIGH -> "RANKING_SALES_AMOUNT_HIGH";
            case STRUCTURED_DISH_SALES_COUNT_RANKING_LOW -> "RANKING_SALES_COUNT_LOW";
            case STRUCTURED_DISH_INGREDIENT_COST_BREAKDOWN -> "INGREDIENT_BREAKDOWN";
            case STRUCTURED_DISH_LOW_PROFIT_REASON -> "LOW_PROFIT_REASON";
            default -> wireToScreamingSnake(t);
        };
    }
}
