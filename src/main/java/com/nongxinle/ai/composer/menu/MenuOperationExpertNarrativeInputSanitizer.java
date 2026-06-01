package com.nongxinle.ai.composer.menu;

import com.alibaba.fastjson2.JSON;
import com.nongxinle.ai.dto.business.MenuOperationAnswerPlan.MenuOptimizationPlan;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 菜单专家 LLM 输入净化：去掉 capabilityLimits / knownGap / 内部英文 code，保留老板可读字段。
 * 仅作用于 LLM user message；不修改 AnswerPlan 或 cards[] payload。
 */
public final class MenuOperationExpertNarrativeInputSanitizer {

    private static final String[] DISH_MACHINE_KEYS = {"dishId", "quadrantCode", "evidenceRefId"};
    private static final String[] GROUP_MACHINE_KEYS = {"groupCode"};
    private static final String[] PAYLOAD_MACHINE_KEYS = {"capabilityLimits", "knownGaps", "debug"};

    private MenuOperationExpertNarrativeInputSanitizer() {}

    public static Map<String, Object> sanitizeOptimizationPlanForLlm(MenuOptimizationPlan plan) {
        if (plan == null) {
            return Map.of();
        }
        Map<String, Object> copy = deepCopyMap(plan);
        if (copy.isEmpty()) {
            return Map.of();
        }
        copy.remove("capabilityLimits");
        copy.remove("knownGaps");
        sanitizePriorityGroups(copy.get("priorityGroups"));
        sanitizeDishBucket(copy.get("costReviewDishes"));
        sanitizeDishBucket(copy.get("protectDishes"));
        sanitizeDishBucket(copy.get("promotionDishes"));
        sanitizeDishBucket(copy.get("watchListDishes"));
        return copy;
    }

    public static Map<String, Object> sanitizeCardPayloadForLlm(Map<String, Object> cardPayload) {
        if (cardPayload == null || cardPayload.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> copy = new LinkedHashMap<>(cardPayload);
        for (String key : PAYLOAD_MACHINE_KEYS) {
            copy.remove(key);
        }
        sanitizePriorityGroups(copy.get("priorityGroups"));
        sanitizeDishBucket(copy.get("costReviewDishes"));
        sanitizeDishBucket(copy.get("protectDishes"));
        sanitizeDishBucket(copy.get("promotionDishes"));
        sanitizeDishBucket(copy.get("watchListDishes"));
        sanitizeEvidenceRows(copy.get("evidenceRows"));
        return copy;
    }

    private static Map<String, Object> deepCopyMap(Object source) {
        if (source == null) {
            return Map.of();
        }
        try {
            Object parsed = JSON.parse(JSON.toJSONString(source));
            if (parsed instanceof Map<?, ?> map) {
                return toStringObjectMap(map);
            }
        } catch (Exception ignore) {
            // fall through
        }
        return Map.of();
    }

    private static Map<String, Object> toStringObjectMap(Map<?, ?> source) {
        LinkedHashMap<String, Object> copy = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : source.entrySet()) {
            Object key = entry.getKey();
            if (key instanceof String stringKey) {
                copy.put(stringKey, entry.getValue());
            }
        }
        return copy;
    }

    private static void sanitizePriorityGroups(Object groupsObj) {
        if (!(groupsObj instanceof List<?> groups)) {
            return;
        }
        for (Object item : groups) {
            if (!(item instanceof Map<?, ?> group)) {
                continue;
            }
            removeKeys(group, GROUP_MACHINE_KEYS);
            sanitizeDishBucket(group.get("dishes"));
        }
    }

    private static void sanitizeDishBucket(Object dishesObj) {
        if (!(dishesObj instanceof List<?> dishes)) {
            return;
        }
        for (Object item : dishes) {
            if (item instanceof Map<?, ?> dish) {
                sanitizeDishMap(dish);
            }
        }
    }

    private static void sanitizeEvidenceRows(Object rowsObj) {
        if (!(rowsObj instanceof List<?> rows)) {
            return;
        }
        for (Object item : rows) {
            if (item instanceof Map<?, ?> row) {
                row.remove("evidenceRefId");
                row.remove("dishId");
            }
        }
    }

    private static void sanitizeDishMap(Map<?, ?> dish) {
        if (dish == null || dish.isEmpty()) {
            return;
        }
        removeKeys(dish, DISH_MACHINE_KEYS);
    }

    private static void removeKeys(Map<?, ?> target, String... keys) {
        if (target == null || keys == null) {
            return;
        }
        for (String key : keys) {
            target.remove(key);
        }
    }
}
