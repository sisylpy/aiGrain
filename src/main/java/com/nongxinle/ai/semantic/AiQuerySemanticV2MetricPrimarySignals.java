package com.nongxinle.ai.semantic;

import org.springframework.util.StringUtils;

import java.util.Locale;

/**
 * v2 {@code metric.primaryMetric} 粗分（仅 LLM 结构化字段，不读用户原文）：经营综合 vs 明确营业额 vs 采购。
 */
public final class AiQuerySemanticV2MetricPrimarySignals {

    private AiQuerySemanticV2MetricPrimarySignals() {
    }

    /** 经营/生意/综合经营等综合口径（应走 {@code BUSINESS_OVERVIEW}）。 */
    public static boolean isBusinessHolisticPrimary(String pmRaw) {
        if (!StringUtils.hasText(pmRaw)) {
            return false;
        }
        String t = pmRaw.trim();
        if (t.contains("经营情况")
                || t.contains("经营状况")
                || t.contains("综合经营")
                || t.contains("整体经营")) {
            return true;
        }
        if (t.contains("经营") || t.contains("生意")) {
            return true;
        }
        String u = t.toUpperCase(Locale.ROOT).replace('-', '_');
        if (u.contains("BUSINESS_OVERVIEW") || u.contains("OPERATIONS_OVERVIEW")) {
            return true;
        }
        if ("BUSINESS".equals(u)
                || "OPERATIONS".equals(u)
                || "OPERATION".equals(u)
                || "HOLISTIC".equals(u)) {
            return true;
        }
        if (u.contains("BUSINESS_STATUS") || u.contains("OPERATION_STATUS") || "BUSINESS_OVERVIEW_PRIMARY".equals(u)) {
            return true;
        }
        return false;
    }

    /**
     * 明确营业额/营收类（应走 {@code REVENUE_OVERVIEW}）；窄于旧版「凡 revenue 即营业额」。
     */
    public static boolean isRevenueExplicitPrimary(String pmRaw) {
        if (!StringUtils.hasText(pmRaw)) {
            return false;
        }
        String t = pmRaw.trim();
        if (t.contains("营业额") || t.contains("营收")) {
            return true;
        }
        if (t.contains("销售额") || t.contains("销售收入")) {
            return true;
        }
        if (t.contains("收入") && !isBusinessHolisticPrimary(pmRaw)) {
            return true;
        }
        if (t.contains("堂食") || t.contains("外卖")) {
            return true;
        }
        if (t.contains("客单价") || t.contains("订单数") || t.contains("订单量")) {
            return true;
        }
        if (t.contains("哪个营业额") || t.contains("营业额高")) {
            return true;
        }
        String u = t.toUpperCase(Locale.ROOT).replace('-', '_');
        if ("REVENUE".equals(u) || "SALES".equals(u) || "TURNOVER".equals(u)) {
            return true;
        }
        if (u.startsWith("REVENUE_") || u.startsWith("SALES_")) {
            return true;
        }
        return false;
    }
}
