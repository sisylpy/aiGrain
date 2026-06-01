package com.nongxinle.ai.composer.menu;

import com.alibaba.fastjson2.JSON;
import com.nongxinle.ai.core.AiRunState;
import com.nongxinle.ai.dto.business.MenuOperationAnswerPlan;
import com.nongxinle.ai.dto.business.MenuOperationAnswerPlan.MenuOptimizationDishItem;
import com.nongxinle.ai.dto.business.MenuOperationAnswerPlan.MenuOptimizationPlan;
import com.nongxinle.ai.dto.business.MenuOperationAnswerPlan.MenuOptimizationPriorityGroup;
import com.nongxinle.ai.prompt.AiPromptIds;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MenuOperationExpertNarrativePromptPreviewSupportTest {

    @Test
    void buildInputPreview_containsFinalMessagesAndAuditFlags() {
        MenuOperationAnswerPlan plan = samplePlan();
        AiRunState state = AiRunState.builder().normalizedUserInput("菜单怎么优化？").build();
        Map<String, Object> envelope = MenuOperationExpertNarrativeInputBuilder.buildInputEnvelope(state, plan);
        String userMessage = MenuOperationExpertNarrativeInputBuilder.buildUserMessage(state, plan);

        Map<String, Object> preview =
                MenuOperationExpertNarrativePromptPreviewSupport.buildInputPreview(
                        AiPromptIds.COMPOSER_MENU_EXPERT_RUNTIME_V1,
                        "ai-prompts/semantic/menu-expert-runtime-prompt.md",
                        "你是菜单专家",
                        userMessage,
                        envelope);

        assertEquals(AiPromptIds.COMPOSER_MENU_EXPERT_RUNTIME_V1, preview.get("promptId"));
        assertNotNull(preview.get("finalMessages"));
        assertNotNull(preview.get("finalPrompt"));
        assertNotNull(preview.get("inputPayload"));
        assertFalse((Boolean) preview.get("auditContainsRawToolResults"));
        assertFalse((Boolean) preview.get("auditContainsNotInP1"));
        assertFalse((Boolean) preview.get("auditContainsKnownGapCode"));

        @SuppressWarnings("unchecked")
        Map<String, Object> payload = (Map<String, Object>) preview.get("inputPayload");
        assertTrue(payload.containsKey("menuOptimizationPlan"));
        assertTrue(payload.containsKey("evidenceRows"));
        assertTrue(payload.containsKey("capabilityBoundaryZh"));
        assertFalse(payload.toString().contains("capabilityLimits"));
    }

    @Test
    void buildOutputPreview_containsRawAndNormalized() {
        Map<String, Object> preview =
                MenuOperationExpertNarrativePromptPreviewSupport.buildOutputPreview("原始回答", "归一化回答");

        assertEquals("原始回答", preview.get("llmRawResponsePreview"));
        assertEquals("归一化回答", preview.get("llmNormalizedResponsePreview"));
        assertEquals(4, preview.get("outputLength"));
        assertEquals(5, preview.get("normalizedOutputLength"));
    }

    @Test
    void buildDecisionPreview_containsFinalAnswerSource() {
        Map<String, Object> preview =
                MenuOperationExpertNarrativePromptPreviewSupport.buildDecisionPreview(
                        true,
                        true,
                        false,
                        "accepted",
                        null,
                        MenuOperationExpertNarrativePromptPreviewSupport.FINAL_ANSWER_SOURCE_LLM);

        assertEquals(true, preview.get("llmUsed"));
        assertEquals(false, preview.get("fallbackUsed"));
        assertEquals("accepted", preview.get("outputGuardResult"));
        assertEquals("llm_expert_narrative", preview.get("finalAnswerSource"));
    }

    @Test
    void buildInputPreview_doesNotFlagHardRulesToolResultsMention() {
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("hardRules", List.of("不得输出 toolResults"));
        envelope.put("menuOptimizationPlan", Map.of("optimizationSummary", "先复核引流菜。"));
        envelope.put("cardPayload", Map.of("summary", "先复核引流菜。"));

        Map<String, Object> preview =
                MenuOperationExpertNarrativePromptPreviewSupport.buildInputPreview(
                        AiPromptIds.COMPOSER_MENU_EXPERT_RUNTIME_V1,
                        "ai-prompts/semantic/menu-expert-runtime-prompt.md",
                        "系统说明：不得直接引用 Tool 数据",
                        JSON.toJSONString(envelope),
                        envelope);

        assertFalse((Boolean) preview.get("auditContainsRawToolResults"));
    }

    @Test
    void buildInputPreview_flagsActualToolEnvelopeInPayloadData() {
        Map<String, Object> toolEnvelope =
                Map.of(
                        "schemaVersion",
                        "v1",
                        "tool",
                        "dish_profit_analysis",
                        "success",
                        true,
                        "data",
                        Map.of("rows", List.of()));
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("menuOptimizationPlan", Map.of("toolResults", Map.of("dish_profit_analysis", toolEnvelope)));
        envelope.put("cardPayload", Map.of());

        Map<String, Object> preview =
                MenuOperationExpertNarrativePromptPreviewSupport.buildInputPreview(
                        AiPromptIds.COMPOSER_MENU_EXPERT_RUNTIME_V1,
                        "ai-prompts/semantic/menu-expert-runtime-prompt.md",
                        "你是菜单专家",
                        JSON.toJSONString(envelope),
                        envelope);

        assertTrue((Boolean) preview.get("auditContainsRawToolResults"));
    }

    private static MenuOperationAnswerPlan samplePlan() {
        return MenuOperationAnswerPlan.builder()
                .planType(MenuOperationAnswerPlan.TYPE_MENU_ACTION_RECOMMENDATION)
                .scopeLabel("集团")
                .timeLabel("2026年4月")
                .menuOptimizationPlan(
                        MenuOptimizationPlan.builder()
                                .optimizationSummary("先复核引流菜。")
                                .priorityGroups(
                                        List.of(
                                                MenuOptimizationPriorityGroup.builder()
                                                        .groupCode("PRIORITY_HANDLE")
                                                        .groupName("优先处理")
                                                        .priority(1)
                                                        .dishes(
                                                                List.of(
                                                                        MenuOptimizationDishItem.builder()
                                                                                .dishName("酸奶碗")
                                                                                .build()))
                                                        .build()))
                                .nextSteps(List.of("先复核「酸奶碗」成本"))
                                .capabilityLimits(
                                        new LinkedHashMap<>(Map.of("latestPurchasePrice", "NOT_IN_P1")))
                                .build())
                .knownGaps(List.of("MENU_PRICING_ADVICE_NOT_IN_P1"))
                .evidenceRows(List.of(Map.of("dishName", "酸奶碗", "metric", "毛利率", "value", "18%")))
                .build();
    }
}
