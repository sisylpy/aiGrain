package com.nongxinle.ai.graph.business;

import com.nongxinle.ai.dto.business.MenuOperationAnswerPlan;
import com.nongxinle.ai.dto.business.MenuOperationAnswerPlan.MenuOptimizationDishItem;
import com.nongxinle.ai.dto.business.MenuOperationAnswerPlan.MenuOptimizationPlan;
import com.nongxinle.ai.dto.business.MenuOperationAnswerPlan.MenuOptimizationPriorityGroup;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MenuOperationAnswerPlanCardSupportTest {

    @Test
    void buildRunCards_projectsMenuOptimizationPlanPayload() {
        MenuOptimizationPlan optimization =
                MenuOptimizationPlan.builder()
                        .optimizationSummary("本月菜单优化重点是先复核引流菜，同时稳定明星菜。")
                        .priorityGroups(
                                List.of(
                                        MenuOptimizationPriorityGroup.builder()
                                                .groupCode(MenuOperationAnswerPlan.OPT_GROUP_PRIORITY_HANDLE)
                                                .groupName("优先处理")
                                                .priority(1)
                                                .reason("相对引流档需复核成本")
                                                .suggestedAction("复核成本")
                                                .dishes(
                                                        List.of(
                                                                MenuOptimizationDishItem.builder()
                                                                        .dishId("101")
                                                                        .dishName("酸奶碗")
                                                                        .suggestedActionLabel("复核成本")
                                                                        .blendedGrossMarginRateOnListPrice("18.00%")
                                                                        .actualProfitAmount("-120.00")
                                                                        .build()))
                                                .build()))
                        .costReviewDishes(
                                List.of(
                                        MenuOptimizationDishItem.builder()
                                                .dishId("101")
                                                .dishName("酸奶碗")
                                                .suggestedActionLabel("复核成本")
                                                .build()))
                        .protectDishes(
                                List.of(
                                        MenuOptimizationDishItem.builder()
                                                .dishId("102")
                                                .dishName("香煎青鱼")
                                                .suggestedActionLabel("继续主推")
                                                .build()))
                        .nextSteps(List.of("先复核「酸奶碗」的成本结构、标准用量与损耗（复核成本）"))
                        .build();

        MenuOperationAnswerPlan plan =
                MenuOperationAnswerPlan.builder()
                        .planType(MenuOperationAnswerPlan.TYPE_MENU_ACTION_RECOMMENDATION)
                        .menuOptimizationPlan(optimization)
                        .evidenceRows(
                                List.of(
                                        Map.of(
                                                "evidenceId",
                                                "ev-1",
                                                "displayLabel",
                                                "综合毛利率",
                                                "value",
                                                "32.50%")))
                        .displayCards(
                                List.of(
                                        MenuOperationAnswerPlan.MenuOperationDisplayCard.builder()
                                                .cardType(
                                                        MenuOperationAnswerPlan
                                                                .CARD_TYPE_MENU_ACTION_RECOMMENDATION)
                                                .dataRef(
                                                        MenuOperationAnswerPlan
                                                                .DATA_REF_MENU_OPTIMIZATION_PLAN)
                                                .build()))
                        .build();

        List<Map<String, Object>> cards = MenuOperationAnswerPlanCardSupport.buildRunCards(plan);
        assertEquals(1, cards.size());
        assertEquals(
                MenuOperationAnswerPlan.CARD_TYPE_MENU_ACTION_RECOMMENDATION,
                cards.get(0).get("cardType"));
        assertEquals("菜单优化方案", cards.get(0).get("title"));
        assertEquals("PLAN", cards.get(0).get("chartType"));
        @SuppressWarnings("unchecked")
        Map<String, Object> source = (Map<String, Object>) cards.get(0).get("source");
        assertNotNull(source);
        assertEquals("menuOperationAnswerPlan", source.get("answerPlan"));
        assertEquals("menuOptimizationPlan", source.get("dataRef"));

        @SuppressWarnings("unchecked")
        Map<String, Object> payload = (Map<String, Object>) cards.get(0).get("payload");
        assertNotNull(payload);
        assertEquals("ACTIVE", payload.get("status"));
        assertTrue(payload.get("optimizationSummary").toString().contains("引流菜"));
        assertEquals(1, ((List<?>) payload.get("priorityGroups")).size());
        assertEquals(1, ((List<?>) payload.get("costReviewDishes")).size());
        assertEquals(1, ((List<?>) payload.get("protectDishes")).size());
        assertEquals(1, ((List<?>) payload.get("nextSteps")).size());
        assertEquals(1, ((List<?>) payload.get("evidenceRows")).size());
    }
}
