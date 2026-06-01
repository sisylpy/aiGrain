package com.nongxinle.ai.composer.menu;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.nongxinle.ai.core.AiRunState;
import com.nongxinle.ai.dto.business.MenuOperationAnswerPlan;
import com.nongxinle.ai.dto.business.MenuOperationAnswerPlan.MenuOptimizationPlan;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MenuOperationExpertNarrativeInputBuilderTest {

    @Test
    void buildUserMessage_containsMenuFactPackAndExcludesOptimizationPlan() {
        MenuOperationAnswerPlan plan =
                MenuOperationAnswerPlan.builder()
                        .planType(MenuOperationAnswerPlan.TYPE_MENU_ACTION_RECOMMENDATION)
                        .scopeLabel("集团")
                        .timeLabel("2026年4月")
                        .menuOptimizationPlan(
                                MenuOptimizationPlan.builder()
                                        .optimizationSummary("不应进入 LLM 输入")
                                        .capabilityLimits(
                                                new LinkedHashMap<>(Map.of("latestPurchasePrice", "NOT_IN_P1")))
                                        .build())
                        .build();

        AiRunState state = MenuExpertNarrativeFactPackTestSupport.stateWithYogurtBowlRow();
        state.setNormalizedUserInput("菜单怎么优化？");

        JSONObject envelope = JSON.parseObject(MenuOperationExpertNarrativeInputBuilder.buildUserMessage(state, plan));

        assertEquals("menu_action_recommendation_presentation", envelope.getString("task"));
        assertEquals("菜单怎么优化？", envelope.getString("userQuestion"));
        assertEquals("集团 · 2026年4月", envelope.getString("scopeTimeSummary"));
        assertTrue(envelope.containsKey("menuFactPack"));
        assertTrue(envelope.getJSONObject("menuFactPack").containsKey("dishRows"));
        assertTrue(envelope.getJSONObject("menuFactPack").containsKey("totalDishCount"));
        assertTrue(envelope.getString("capabilityBoundaryZh").contains("最新采购价"));
        assertFalse(envelope.containsKey("menuOptimizationPlan"));
        assertFalse(envelope.containsKey("evidenceRows"));
        assertFalse(envelope.toJSONString().contains("priorityGroups"));
        assertFalse(envelope.toJSONString().contains("optimizationSummary"));
        assertFalse(envelope.toJSONString().contains("toolResults"));
        assertFalse(envelope.toJSONString().contains("NOT_IN_P1"));
        assertFalse(envelope.toJSONString().contains("capabilityLimits"));
        assertFalse(envelope.containsKey("cardPayload"));
    }
}
