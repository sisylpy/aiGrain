package com.nongxinle.ai.dto.business;

import java.math.BigDecimal;
import java.util.List;

/**
 * 正文：本轮参与统计的门店完整列表（调试期不截断 TopN）。
 */
public final class AiGroupOverviewCoveredStoresBrief {

    private AiGroupOverviewCoveredStoresBrief() {
    }

    public static String format(List<AiOverviewCoveredStoreItem> items) {
        if (items == null || items.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("本次参与统计的门店：");
        int i = 1;
        for (AiOverviewCoveredStoreItem row : items) {
            sb.append('\n').append(i++).append(". ").append(nullToEmpty(row.getStoreName())).append("：");
            sb.append(describeStoreMetrics(row));
        }
        return sb.toString().trim();
    }

    private static String describeStoreMetrics(AiOverviewCoveredStoreItem row) {
        boolean has = Boolean.TRUE.equals(row.getHasRevenueData());
        String rev = plainMoney(row.getTotalRevenue());
        int days = row.getDays() == null ? 0 : Math.max(0, row.getDays());
        BigDecimal gross = row.getTotalRevenue() == null ? BigDecimal.ZERO : row.getTotalRevenue();
        BigDecimal avgDaily = days > 0
                ? gross.divide(BigDecimal.valueOf(days), 2, java.math.RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
        String avgDailyStr = plainMoney(avgDaily);
        String ord = plainNumber(row.getOrderCount());
        String avgOrd = plainNumber(row.getAvgOrderCount());
        String apc = plainNumber(row.getAvgPerCustomer());
        if (!has) {
            return String.format(
                    "营业额 %s 元，统计 %d 天，日均 %s 元，订单数 %s 单，日均订单 %s 单，客单价 %s 元。（统计周期内未见有效日营收汇总）",
                    rev, days, avgDailyStr, ord, avgOrd, apc);
        }
        return String.format(
                "营业额 %s 元，统计 %d 天，日均 %s 元，订单数 %s 单，日均订单 %s 单，客单价 %s 元。",
                rev, days, avgDailyStr, ord, avgOrd, apc);
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s.trim();
    }

    private static String plainMoney(BigDecimal v) {
        if (v == null) {
            return "0";
        }
        return v.stripTrailingZeros().toPlainString();
    }

    private static String plainNumber(BigDecimal v) {
        if (v == null) {
            return "0";
        }
        return v.stripTrailingZeros().toPlainString();
    }
}
