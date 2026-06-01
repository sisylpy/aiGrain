package com.nongxinle.ai.composer.menu;

import com.nongxinle.ai.dto.business.MenuOperationAnswerPlan;
import com.nongxinle.ai.dto.business.MenuOperationAnswerPlan.MenuOptimizationDishItem;
import com.nongxinle.ai.dto.business.MenuOperationAnswerPlan.MenuOptimizationPlan;
import com.nongxinle.ai.dto.business.MenuOperationAnswerPlan.MenuOptimizationPriorityGroup;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MenuOperationExpertNarrativeInputSanitizerTest {

    @Test
    void sanitize_removesCapabilityLimitsAndMachineCodes() {
        MenuOptimizationPlan plan =
                MenuOptimizationPlan.builder()
                        .optimizationSummary("先复核引流菜。")
                        .capabilityLimits(new LinkedHashMap<>(Map.of("latestPurchasePrice", "NOT_IN_P1")))
                        .priorityGroups(
                                List.of(
                                        MenuOptimizationPriorityGroup.builder()
                                                .groupCode("PRIORITY_HANDLE")
                                                .groupName("优先处理")
                                                .priority(1)
                                                .dishes(
                                                        List.of(
                                                                MenuOptimizationDishItem.builder()
                                                                        .dishId("1001")
                                                                        .dishName("酸奶碗")
                                                                        .quadrantCode("TRAFFIC")
                                                                        .quadrantName("引流菜")
                                                                        .evidenceRefId("ev-1")
                                                                        .build()))
                                                .build()))
                        .build();

        Map<String, Object> sanitized =
                MenuOperationExpertNarrativeInputSanitizer.sanitizeOptimizationPlanForLlm(plan);
        String json = com.alibaba.fastjson2.JSON.toJSONString(sanitized);

        assertFalse(sanitized.containsKey("capabilityLimits"));
        assertFalse(json.contains("NOT_IN_P1"));
        assertFalse(json.contains("PRIORITY_HANDLE"));
        assertFalse(json.contains("TRAFFIC"));
        assertFalse(json.contains("evidenceRefId"));
        assertTrue(json.contains("酸奶碗"));
        assertTrue(json.contains("优先处理"));
    }

    @Test
    void sanitizeCardPayload_removesCapabilityLimits() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("optimizationSummary", "summary");
        payload.put("capabilityLimits", Map.of("latestPurchasePrice", "NOT_IN_P1"));
        payload.put("nextSteps", List.of("先复核成本"));

        Map<String, Object> sanitized =
                MenuOperationExpertNarrativeInputSanitizer.sanitizeCardPayloadForLlm(payload);
        String json = com.alibaba.fastjson2.JSON.toJSONString(sanitized);

        assertFalse(sanitized.containsKey("capabilityLimits"));
        assertFalse(json.contains("NOT_IN_P1"));
        assertTrue(json.contains("先复核成本"));
    }
}
