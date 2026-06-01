package com.nongxinle.ai.graph.business;

import com.nongxinle.ai.core.AiRunState;
import com.nongxinle.ai.dto.business.DailyRevenueAnswerPlan;
import com.nongxinle.ai.util.AiNumericPlainText;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** {@link DailyRevenueAnswerPlan} → {@link BusinessStatusCardTypes#REVENUE_REPORT_CARD}。 */
final class RevenueReportCardSupport {

    private static final String SOURCE = "dailyRevenueAnswerPlan";

    private RevenueReportCardSupport() {}

    static Map<String, Object> build(AiRunState state, BusinessStatusCardBuildRequest req) {
        DailyRevenueAnswerPlan plan = state != null ? state.getRevenueAnswerPlan() : null;
        Map<String, Object> payload = new LinkedHashMap<>();
        BusinessStatusCardShellSupport.putRangeFields(payload, req);

        if (plan == null || plan.getSummary() == null || plan.getSummary().isEmpty()) {
            payload.put("status", BusinessStatusCardShellSupport.STATUS_EMPTY);
            payload.put("emptyReason", "本轮未生成营业额 AnswerPlan");
            payload.put("checkSummary", "暂无营业额数据。");
            return BusinessStatusCardShellSupport.buildCard(
                    BusinessStatusCardTypes.REVENUE_REPORT_CARD,
                    BusinessStatusCardShellSupport.titled(req.getReportLabel(), "·营业额"),
                    "总营业额、渠道拆分与菜品销量原因",
                    BusinessStatusCardShellSupport.CHART_KPI,
                    payload,
                    SOURCE);
        }

        Map<String, Object> summary = plan.getSummary();
        payload.put("status", BusinessStatusCardShellSupport.STATUS_OK);
        putMoney(payload, "totalRevenue", summary.get("totalRevenue"));
        putMoney(payload, "dineInRevenue", firstPresent(summary, "totalDineInRevenue", "total_dine_in_revenue"));
        putMoney(payload, "takeoutRevenue", firstPresent(summary, "totalTakeoutRevenue", "total_takeout_revenue"));
        putMoney(payload, "platformFee", firstPresent(summary, "totalPlatformFee", "total_platform_fee"));
        putCount(payload, "orderCount", firstPresent(summary, "totalOrders", "total_orders"));
        putMoney(payload, "averageOrderValue", deriveAov(summary));

        payload.put("revenueReasonSummary", null);
        payload.put("checkSummary", buildCheckSummary(payload));
        payload.put("warnings", List.of());

        return BusinessStatusCardShellSupport.buildCard(
                BusinessStatusCardTypes.REVENUE_REPORT_CARD,
                BusinessStatusCardShellSupport.titled(req.getReportLabel(), "·营业额"),
                "总营业额、渠道拆分与菜品销量原因",
                BusinessStatusCardShellSupport.CHART_KPI,
                payload,
                SOURCE);
    }

    private static String buildCheckSummary(Map<String, Object> payload) {
        Object total = payload.get("totalRevenue");
        Object orders = payload.get("orderCount");
        if (total == null && orders == null) {
            return "暂无营业额汇总。";
        }
        StringBuilder sb = new StringBuilder("本期总营业额 ");
        sb.append(stringify(total));
        if (orders != null) {
            sb.append("，订单 ").append(stringify(orders)).append(" 单");
        }
        Object aov = payload.get("averageOrderValue");
        if (aov != null) {
            sb.append("，客单价 ").append(stringify(aov)).append(" 元");
        }
        sb.append("。");
        return sb.toString();
    }

    private static Object deriveAov(Map<String, Object> summary) {
        Object aov = summary.get("averageOrderValue");
        if (aov != null) {
            return formatNumber(aov);
        }
        Object total = summary.get("totalRevenue");
        Object orders = firstPresent(summary, "totalOrders", "total_orders");
        if (total == null || orders == null) {
            return null;
        }
        BigDecimal rev = toBigDecimal(total);
        BigDecimal ord = toBigDecimal(orders);
        if (rev == null || ord == null || ord.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }
        return formatNumber(rev.divide(ord, 2, java.math.RoundingMode.HALF_UP));
    }

    private static Object firstPresent(Map<String, Object> map, String... keys) {
        for (String key : keys) {
            if (map.containsKey(key) && map.get(key) != null) {
                return map.get(key);
            }
        }
        return null;
    }

    private static void putMoney(Map<String, Object> payload, String key, Object value) {
        if (value == null) {
            return;
        }
        payload.put(key, formatNumber(value));
    }

    private static void putCount(Map<String, Object> payload, String key, Object value) {
        if (value == null) {
            return;
        }
        payload.put(key, formatNumber(value));
    }

    private static String formatNumber(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof BigDecimal bd) {
            return AiNumericPlainText.plainNumber(bd);
        }
        if (value instanceof Number n) {
            return AiNumericPlainText.plainNumber(n);
        }
        String s = value.toString().trim();
        return s.isEmpty() ? null : s;
    }

    private static BigDecimal toBigDecimal(Object v) {
        if (v == null) {
            return null;
        }
        if (v instanceof BigDecimal bd) {
            return bd;
        }
        if (v instanceof Number n) {
            return BigDecimal.valueOf(n.doubleValue());
        }
        try {
            return new BigDecimal(v.toString().trim());
        } catch (Exception e) {
            return null;
        }
    }

    private static String stringify(Object v) {
        return v == null ? "暂无" : v.toString();
    }
}
