package com.nongxinle.ai.graph.business;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 菜品销量原因 Agent：解析 LLM JSON，数字回填 fact pack。 */
final class BusinessOverviewDishSalesReasonOutputGuard {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final int MAX_SUMMARY_LEN = 280;
    private static final int MAX_ITEMS = 5;
    private static final int MAX_REASON_LEN = 120;

    private BusinessOverviewDishSalesReasonOutputGuard() {}

    record ComposeResult(String summary, List<Map<String, Object>> items) {}

    static ComposeResult parseAndSanitize(String raw, Map<String, Object> factPack) {
        if (!StringUtils.hasText(raw) || factPack == null) {
            return null;
        }
        JsonNode root;
        try {
            root = JSON.readTree(stripMarkdownFence(raw.trim()));
        } catch (Exception e) {
            return null;
        }
        if (root == null || !root.isObject()) {
            return null;
        }
        String summary = textOrNull(root.get("summary"));
        if (!StringUtils.hasText(summary)) {
            return null;
        }
        summary = summary.trim();
        if (summary.length() > MAX_SUMMARY_LEN) {
            summary = summary.substring(0, MAX_SUMMARY_LEN);
        }

        Map<String, Map<String, Object>> factByDish = indexDishCandidates(factPack);
        List<Map<String, Object>> items = new ArrayList<>();
        JsonNode itemsNode = root.get("items");
        if (itemsNode != null && itemsNode.isArray()) {
            for (JsonNode node : itemsNode) {
                if (items.size() >= MAX_ITEMS) {
                    break;
                }
                if (node == null || !node.isObject()) {
                    continue;
                }
                String dishName = textOrNull(node.get("dishName"));
                if (!StringUtils.hasText(dishName)) {
                    continue;
                }
                Map<String, Object> factRow = factByDish.get(dishName.trim());
                if (factRow == null) {
                    continue;
                }
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("dishName", dishName.trim());
                putNumberFromFact(item, "periodQty", factRow.get("periodQty"));
                putNumberFromFact(item, "baselineDailyAvgQty", factRow.get("baselineDailyAvgQty"));
                putNumberFromFact(item, "expectedPeriodQty", factRow.get("expectedPeriodQty"));
                putNumberFromFact(item, "qtyDiff", factRow.get("qtyDiff"));
                putNumberFromFact(item, "periodSalesAmount", factRow.get("periodSalesAmount"));
                putNumberFromFact(item, "baselineTotalQty", factRow.get("baselineTotalQty"));
                putNumberFromFact(item, "amountDiff", factRow.get("amountDiff"));
                putNumberFromFact(item, "compareAvgQty", factRow.get("baselineDailyAvgQty"));
                Object tag = factRow.get("candidateTag");
                if (tag != null) {
                    item.put("candidateTag", tag.toString());
                }
                String reason = textOrNull(node.get("reason"));
                if (StringUtils.hasText(reason)) {
                    reason = reason.trim();
                    if (reason.length() > MAX_REASON_LEN) {
                        reason = reason.substring(0, MAX_REASON_LEN);
                    }
                    item.put("reason", reason);
                }
                items.add(item);
            }
        }
        return new ComposeResult(summary, List.copyOf(items));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Map<String, Object>> indexDishCandidates(Map<String, Object> factPack) {
        Map<String, Map<String, Object>> byName = new LinkedHashMap<>();
        Object raw = factPack.get("dishCompareCandidates");
        if (raw == null) {
            raw = factPack.get("periodDishSales");
        }
        if (raw == null) {
            raw = factPack.get("todayDishSales");
        }
        if (!(raw instanceof List<?> list)) {
            return byName;
        }
        for (Object o : list) {
            if (!(o instanceof Map<?, ?> rowRaw)) {
                continue;
            }
            Map<String, Object> row = (Map<String, Object>) rowRaw;
            Object nameObj = row.get("dishName");
            if (nameObj == null) {
                continue;
            }
            String name = nameObj.toString().trim();
            if (StringUtils.hasText(name)) {
                byName.put(name, row);
            }
        }
        return byName;
    }

    private static void putNumberFromFact(Map<String, Object> item, String key, Object factValue) {
        Double n = toDouble(factValue);
        if (n != null) {
            item.put(key, n);
        }
    }

    private static Double toDouble(Object v) {
        if (v == null) {
            return null;
        }
        if (v instanceof Number n) {
            return roundDisplay(n.doubleValue());
        }
        try {
            return roundDisplay(Double.parseDouble(v.toString().trim()));
        } catch (Exception e) {
            return null;
        }
    }

    private static double roundDisplay(double v) {
        return BigDecimal.valueOf(v).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }

    private static String textOrNull(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return null;
        }
        String s = node.asText().trim();
        return s.isEmpty() ? null : s;
    }

    private static String stripMarkdownFence(String trimmed) {
        if (!trimmed.startsWith("```")) {
            return trimmed;
        }
        int firstNl = trimmed.indexOf('\n');
        int fence = trimmed.lastIndexOf("```");
        if (firstNl > 0 && fence > firstNl) {
            return trimmed.substring(firstNl + 1, fence).trim();
        }
        return trimmed;
    }
}
