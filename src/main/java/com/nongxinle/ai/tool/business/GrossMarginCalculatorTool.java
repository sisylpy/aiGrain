package com.nongxinle.ai.tool.business;

import com.nongxinle.ai.tool.AiTool;
import com.nongxinle.ai.tool.ToolRequest;
import com.nongxinle.ai.tool.ToolResult;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 纯推导：读取 inputs 中的各 Tool 输出 envelope（v1）。
 */
@Component
public class GrossMarginCalculatorTool implements AiTool {

    @Override
    public String name() {
        return AiBusinessToolIds.GROSS_MARGIN_CALCULATOR;
    }

    @Override
    public ToolResult execute(ToolRequest request) {
        Map<String, Object> args = request.getArgs() == null ? Map.of() : request.getArgs();
        @SuppressWarnings("unchecked")
        Map<String, Object> inputs = (Map<String, Object>) args.get(AiBusinessToolIds.ARG_INPUT_SNAPSHOT);
        inputs = inputs == null ? Map.of() : inputs;

        Map<String, Object> revenueEnv = unwrap(inputs.get(AiBusinessToolIds.REVENUE_QUERY));
        Map<String, Object> overviewEnv = unwrap(inputs.get(AiBusinessToolIds.BUSINESS_OVERVIEW_QUERY));
        Map<String, Object> dishEnv = unwrap(inputs.get(AiBusinessToolIds.DISH_SALES_QUERY));
        Map<String, Object> stockEnv = unwrap(inputs.get(AiBusinessToolIds.STOCK_REDUCE_QUERY));

        BigDecimal revenueFromQuery = bd(dataSection(revenueEnv).get("totalRevenue"));
        BigDecimal revenue = revenueFromQuery;
        if (revenue.signum() <= 0) {
            revenue = bd(dataSection(overviewEnv).get("totalRevenue"));
        }
        BigDecimal listRev = bd(dataSection(dishEnv).get("listPriceRevenueTotal"));
        BigDecimal production = bd(dataSection(stockEnv).get("productionTotal"));
        BigDecimal produceOnly = bd(dataSection(stockEnv).get("produceTotal"));
        BigDecimal wasteLoss = bd(dataSection(stockEnv).get("wasteTotal")).add(bd(dataSection(stockEnv).get("lossTotal")));

        BigDecimal denom = revenue.compareTo(BigDecimal.ZERO) > 0 ? revenue : listRev;

        boolean outboundCostMissing =
                production.signum() == 0 && produceOnly.signum() == 0 && wasteLoss.signum() == 0;

        BigDecimal cogForDisplay = production.compareTo(BigDecimal.ZERO) > 0 ? production : produceOnly.add(wasteLoss);

        String revenueSourceTag;
        if (revenue.compareTo(BigDecimal.ZERO) <= 0) {
            revenueSourceTag = listRev.compareTo(BigDecimal.ZERO) > 0 ? "dish_list_price_revenue" : "unknown";
        } else if (revenueFromQuery.signum() > 0) {
            revenueSourceTag = "daily_revenue_stats";
        } else {
            revenueSourceTag = "daily_revenue_overview_dashboard";
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

        Map<String, Object> metaEnv = envelopeWithArgs(revenueEnv, overviewEnv, dishEnv);
        Long dept = toLong(metaEnv.get(AiBusinessToolIds.ARG_DEPARTMENT_FATHER_ID));
        Long dis = toLong(metaEnv.get(AiBusinessToolIds.ARG_DIS_ID));
        String start = str(metaEnv.get(AiBusinessToolIds.ARG_START_DATE));
        String stop = str(metaEnv.get(AiBusinessToolIds.ARG_STOP_DATE));

        return ToolResult.builder()
                .success(true)
                .message("derived")
                .data(AiBusinessToolResponses.envelope(name(), true, false, emptyToNull(start), emptyToNull(stop), dept,
                        dis, data, null))
                .build();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> unwrap(Object v) {
        if (!(v instanceof Map)) {
            return Map.of();
        }
        return (Map<String, Object>) v;
    }

    /**
     * 经营概览路径可能缺少 {@link AiBusinessToolIds#REVENUE_QUERY}，则从 overview / dish envelope 回填日期与部门 meta。
     */
    private static Map<String, Object> envelopeWithArgs(Map<String, Object> revenueEnv,
            Map<String, Object> overviewEnv, Map<String, Object> dishEnv) {
        boolean revenueStatsPositive =
                bd(dataSection(revenueEnv).get("totalRevenue")).signum() > 0;
        if (revenueStatsPositive && revenueEnv != null && revenueEnv.containsKey(AiBusinessToolIds.ARG_START_DATE)) {
            return revenueEnv;
        }
        if (overviewEnv != null && overviewEnv.containsKey(AiBusinessToolIds.ARG_START_DATE)) {
            return overviewEnv;
        }
        return dishEnv == null ? Map.of() : dishEnv;
    }

    private static Map<String, Object> dataSection(Map<String, Object> envelope) {
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

    private static Long toLong(Object v) {
        if (v == null) {
            return null;
        }
        if (v instanceof Number n) {
            return n.longValue();
        }
        try {
            return Long.parseLong(v.toString().trim());
        } catch (Exception e) {
            return null;
        }
    }

    private static String str(Object v) {
        return v == null ? "" : v.toString().trim();
    }

    private static String emptyToNull(String s) {
        return s == null || s.isEmpty() ? null : s;
    }
}
