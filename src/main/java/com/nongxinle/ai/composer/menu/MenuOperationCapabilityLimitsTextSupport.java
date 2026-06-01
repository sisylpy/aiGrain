package com.nongxinle.ai.composer.menu;

import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * 将 AnswerPlan 内 {@code capabilityLimits} 机器字段转为老板可读中文说明；不暴露 NOT_IN_P1 等内部 code。
 */
public final class MenuOperationCapabilityLimitsTextSupport {

    private MenuOperationCapabilityLimitsTextSupport() {}

    public static String composeBoundaryNotice(Map<String, Object> limits) {
        LinkedHashSet<String> phrases = collectUnavailablePhrases(limits);
        if (phrases.isEmpty()) {
            return "";
        }
        return "当前版本暂不提供" + joinWithAnd(new ArrayList<>(phrases)) + "。";
    }

    public static LinkedHashSet<String> collectUnavailablePhrases(Map<String, Object> limits) {
        LinkedHashSet<String> phrases = new LinkedHashSet<>();
        if (limits == null || limits.isEmpty()) {
            return phrases;
        }
        if (capabilityNotInP1(limits, "latestPurchasePrice")) {
            phrases.add("最新采购价");
        }
        if (capabilityNotInP1(limits, "externalMarketBenchmark")) {
            phrases.add("外部市场比价");
        }
        if (capabilityNotInP1(limits, "multiPeriodTrend")) {
            phrases.add("连续多周期趋势");
        }
        if (capabilityNotInP1(limits, "crossStoreDishRank")) {
            phrases.add("跨门店排名");
        }
        if (capabilityNotInP1(limits, "comboOrderAnalysis")) {
            phrases.add("套餐点单组合分析");
        }
        return phrases;
    }

    private static boolean capabilityNotInP1(Map<String, Object> limits, String key) {
        if (limits == null || key == null) {
            return false;
        }
        Object value = limits.get(key);
        return value != null && "NOT_IN_P1".equals(value.toString().trim());
    }

    private static String joinWithAnd(List<String> parts) {
        if (parts == null || parts.isEmpty()) {
            return "";
        }
        if (parts.size() == 1) {
            return parts.get(0);
        }
        if (parts.size() == 2) {
            return parts.get(0) + "和" + parts.get(1);
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.size(); i++) {
            if (i > 0) {
                sb.append(i == parts.size() - 1 ? "和" : "、");
            }
            sb.append(parts.get(i));
        }
        return sb.toString();
    }

    /** 能力项在 P1 不可用时，叙述中若当作已有数据引用则视为违规。 */
    public static boolean mentionsUnavailableCapabilityAsFact(String text, Map<String, Object> limits) {
        if (!StringUtils.hasText(text) || limits == null || limits.isEmpty()) {
            return false;
        }
        String t = text.trim();
        if (capabilityNotInP1(limits, "latestPurchasePrice") && mentionsAsFact(t, "最新采购价")) {
            return true;
        }
        if (capabilityNotInP1(limits, "externalMarketBenchmark")
                && (mentionsAsFact(t, "外部市场") || mentionsAsFact(t, "市场比价"))) {
            return true;
        }
        if (capabilityNotInP1(limits, "multiPeriodTrend")
                && (mentionsAsFact(t, "连续") && t.contains("趋势"))) {
            return true;
        }
        if (capabilityNotInP1(limits, "crossStoreDishRank")
                && (mentionsAsFact(t, "跨店") || mentionsAsFact(t, "跨门店"))) {
            return true;
        }
        return capabilityNotInP1(limits, "comboOrderAnalysis")
                && mentionsAsFact(t, "套餐点单")
                && !t.contains("可考虑")
                && !t.contains("暂不提供");
    }

    private static boolean mentionsAsFact(String text, String keyword) {
        if (!StringUtils.hasText(text) || !StringUtils.hasText(keyword) || !text.contains(keyword)) {
            return false;
        }
        if (text.contains("暂不提供") || text.contains("暂无") || text.contains("没有提供") || text.contains("无法提供")) {
            return false;
        }
        return text.contains("根据" + keyword)
                || text.contains("依据" + keyword)
                || text.contains("从" + keyword)
                || (text.contains(keyword) && text.contains("显示"));
    }
}
