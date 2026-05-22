package com.nongxinle.ai.graph.business;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 成本诊断内部推导：门店粗估毛利率（纯内存，不查库）。
 * <p>
 * 仅由 {@link CostDiagnosisAgentNode} 调用；不作为独立 Tool 注册或写入 {@code toolResults}。
 * <p>
 * 数据来源（仅读各 Tool 的 {@code data} 段）：
 * <ul>
 *   <li>{@code revenue_query}</li>
 *   <li>{@code dish_profit_analysis}</li>
 *   <li>{@code stock_reduce_query}</li>
 * </ul>
 * 禁止读取：{@code purchase_overview}、{@code purchase_query}、{@code dish_sales_query}、
 * {@code business_overview_query}、{@code stock_query}（已删 Tool id，见 docs/AI_MAINLINE_INDEX.md）。
 */
public final class CostMarginDerivation {

    private CostMarginDerivation() {
    }

    /**
     * 从各 Tool 信封（v1，含 {@code data} 与可选日期/部门 meta）推导毛利 data 字段。
     */
    public static Map<String, Object> deriveFromEnvelopes(
            Map<String, Object> revenueEnvelope,
            Map<String, Object> dishProfitEnvelope,
            Map<String, Object> stockReduceEnvelope) {
        return derive(
                dataSection(revenueEnvelope),
                dataSection(dishProfitEnvelope),
                dataSection(stockReduceEnvelope));
    }

    /**
     * 推导 {@code AiCostDiagnosisResult} 消费的毛利字段（{@code grossMarginReliable}、
     * {@code estimatedGrossMarginPercent}、{@code estimatedGrossMarginPercentDisplay} 等）。
     */
    public static Map<String, Object> derive(
            Map<String, Object> revenueData,
            Map<String, Object> dishProfitData,
            Map<String, Object> stockReduceData) {
        Map<String, Object> rev = revenueData == null ? Map.of() : revenueData;
        Map<String, Object> dish = dishProfitData == null ? Map.of() : dishProfitData;
        Map<String, Object> stk = stockReduceData == null ? Map.of() : stockReduceData;

        BigDecimal revenueFromQuery = bd(rev.get("totalRevenue"));
        BigDecimal listRev = listPriceRevenueFromDishProfitData(dish);
        BigDecimal production = bd(stk.get("productionTotal"));
        BigDecimal produceOnly = bd(stk.get("produceTotal"));
        BigDecimal wasteLoss = bd(stk.get("wasteTotal")).add(bd(stk.get("lossTotal")));

        BigDecimal denom = revenueFromQuery.compareTo(BigDecimal.ZERO) > 0 ? revenueFromQuery : listRev;

        boolean outboundCostMissing =
                production.signum() == 0 && produceOnly.signum() == 0 && wasteLoss.signum() == 0;

        BigDecimal cogForDisplay = production.compareTo(BigDecimal.ZERO) > 0 ? production : produceOnly.add(wasteLoss);

        String revenueSourceTag;
        if (denom.compareTo(BigDecimal.ZERO) <= 0) {
            revenueSourceTag = "unknown";
        } else if (revenueFromQuery.signum() > 0) {
            revenueSourceTag = "daily_revenue_stats";
        } else {
            revenueSourceTag = "dish_profit_list_price_revenue";
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("basisRevenue", denom.compareTo(BigDecimal.ZERO) > 0 ? denom.stripTrailingZeros().toPlainString() : "0");
        data.put("revenueSource", revenueSourceTag);
        data.put("outboundProductionLikeCost", cogForDisplay.stripTrailingZeros().toPlainString());

        if (denom.compareTo(BigDecimal.ZERO) <= 0) {
            data.put("grossMarginReliable", false);
            data.put("estimatedGrossMarginPercent", "");
            data.put("estimatedGrossMarginPercentDisplay", "—");
            data.put("note", "缺少营收口径，毛利无法估算");
        } else if (outboundCostMissing) {
            data.put("grossMarginReliable", false);
            data.put("estimatedGrossMarginPercent", "");
            data.put("estimatedGrossMarginPercentDisplay", "毛利率暂不可准确计算");
            data.put("note", "核销/出库/生产消耗侧数据为 0 或缺失，毛利率估算口径不完整（勿将视同「毛利率 100%」）；请参考营业额与采购等指标。");
        } else {
            data.put("grossMarginReliable", true);
            BigDecimal cog = production.compareTo(BigDecimal.ZERO) > 0 ? production : produceOnly.add(wasteLoss);
            BigDecimal impliedMargin = denom.subtract(cog).divide(denom, 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP);
            data.put("estimatedGrossMarginPercent", impliedMargin.stripTrailingZeros().toPlainString());
            data.put("estimatedGrossMarginPercentDisplay", impliedMargin.stripTrailingZeros().toPlainString() + "%");
            data.put("note", "粗估：口径为 max(营业额,标价收入)-区间出库核销相关成本聚合");
        }
        return data;
    }

    /**
     * 首选 {@code businessInsightSummary.totalListPriceRevenue}（全量菜品汇总）；
     * 仅缺失时兼容 {@code listPriceRevenueTotal}（可能仅为 presented 子集，勿作首选）。
     */
    public static BigDecimal listPriceRevenueFromDishProfitData(Map<String, Object> dishProfitData) {
        if (dishProfitData == null || dishProfitData.isEmpty()) {
            return BigDecimal.ZERO;
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> bis = dishProfitData.get("businessInsightSummary") instanceof Map
                ? (Map<String, Object>) dishProfitData.get("businessInsightSummary")
                : null;
        if (bis != null && bis.get("totalListPriceRevenue") != null) {
            BigDecimal fromBis = bd(bis.get("totalListPriceRevenue"));
            if (fromBis.signum() > 0) {
                return fromBis;
            }
        }
        return bd(dishProfitData.get("listPriceRevenueTotal"));
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> dataSection(Map<String, Object> envelope) {
        if (envelope == null) {
            return Map.of();
        }
        Object d = envelope.get("data");
        if (!(d instanceof Map)) {
            return Map.of();
        }
        return (Map<String, Object>) d;
    }

    private static BigDecimal bd(Object v) {
        if (v == null) {
            return BigDecimal.ZERO;
        }
        if (v instanceof BigDecimal b) {
            return b;
        }
        try {
            return new BigDecimal(v.toString().trim());
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }
}
