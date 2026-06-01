package com.nongxinle.ai.composer.menu;

import com.nongxinle.ai.core.AiRunState;
import com.nongxinle.ai.dto.business.MenuOperationAnswerPlan;
import com.nongxinle.ai.dto.business.MenuOperationAnswerPlan.MenuOptimizationDishItem;
import com.nongxinle.ai.dto.business.MenuOperationAnswerPlan.MenuOptimizationPlan;
import com.nongxinle.ai.dto.business.MenuOperationAnswerPlan.MenuOptimizationPriorityGroup;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MenuExpertPresentationPlanGuardTest {

    @Test
    void acceptsValidPresentationPlan() {
        MenuOperationAnswerPlan plan = planWithDish();
        AiRunState state = MenuExpertNarrativeFactPackTestSupport.stateWithYogurtBowlRow();
        MenuExpertPresentationPlan presentation = validPresentation();
        assertNull(MenuExpertPresentationPlanGuard.validate(presentation, state, plan));
    }

    @Test
    void rejectsUnknownDish() {
        MenuOperationAnswerPlan plan = planWithDish();
        AiRunState state = MenuExpertNarrativeFactPackTestSupport.stateWithYogurtBowlRow();
        MenuExpertPresentationPlan presentation = validPresentation();
        presentation.getFocusSections().get(0).getDishes().get(0).setDishName("未知菜");
        assertEquals("unknown_dish:未知菜", MenuExpertPresentationPlanGuard.validate(presentation, state, plan));
    }

    @Test
    void acceptsRephrasedNextStep() {
        MenuOperationAnswerPlan plan = planWithDish();
        AiRunState state = MenuExpertNarrativeFactPackTestSupport.stateWithYogurtBowlRow();
        MenuExpertPresentationPlan presentation = validPresentation();
        presentation.setNextSteps(List.of("本周优先核对「酸奶碗」成本、用量与损耗，再决定主推力度"));
        assertNull(MenuExpertPresentationPlanGuard.validate(presentation, state, plan));
    }

    @Test
    void rejectsUnknownDishInNextStep() {
        MenuOperationAnswerPlan plan = planWithDish();
        AiRunState state = MenuExpertNarrativeFactPackTestSupport.stateWithYogurtBowlRow();
        MenuExpertPresentationPlan presentation = validPresentation();
        presentation.setNextSteps(List.of("先去优化「宫保鸡丁」的配方"));
        assertEquals("next_step_unknown_dish:宫保鸡丁", MenuExpertPresentationPlanGuard.validate(presentation, state, plan));
    }

    @Test
    void dishMetricsOptionalWhenOmitted() {
        MenuOperationAnswerPlan plan = planWithDish();
        AiRunState state = MenuExpertNarrativeFactPackTestSupport.stateWithYogurtBowlRow();
        MenuExpertPresentationPlan presentation = validPresentation();
        presentation.getFocusSections().get(0).getDishes().get(0).setBlendedGrossMarginRateOnListPrice(null);
        presentation.getFocusSections().get(0).getDishes().get(0).setActualProfitAmount(null);
        assertNull(MenuExpertPresentationPlanGuard.validate(presentation, state, plan));
    }

    @Test
    void buildCardPayload_usesPresentationFields() {
        MenuOperationAnswerPlan plan = planWithDish();
        MenuExpertPresentationPlan presentation = validPresentation();
        Map<String, Object> card =
                MenuExpertPresentationPlanCardSupport.buildActionRecommendationCard(plan, presentation);
        assertEquals("MENU_ACTION_RECOMMENDATION_CARD", card.get("cardType"));
        @SuppressWarnings("unchecked")
        Map<String, Object> payload = (Map<String, Object>) card.get("payload");
        assertNotNull(payload);
        assertEquals("llm_expert_presentation", payload.get("presentationSource"));
        assertTrue(payload.containsKey("mainSummary"));
        assertTrue(payload.containsKey("focusSections"));
        assertTrue(payload.containsKey("detailData"));
        assertEquals("harness_only", payload.get("detailDataScope"));
        assertEquals(false, payload.get("detailDataVisible"));
        assertTrue(((Map<?, ?>) payload.get("detailData")).containsKey("priorityGroups"));
        assertFalse(payload.containsKey("priorityGroups"));
    }

    private static MenuExpertPresentationPlan validPresentation() {
        return MenuExpertPresentationPlan.builder()
                .mainSummary("这次菜单优化的重点，是先处理畅销但毛利偏低的引流菜，同时稳住已有利润菜。")
                .keyFindings(List.of("酸奶碗销量高但毛利率偏低"))
                .focusSections(
                        List.of(
                                MenuExpertPresentationPlan.MenuExpertPresentationFocusSection.builder()
                                        .sectionTitle("优先处理")
                                        .sectionSummary("先复核酸奶碗")
                                        .dishes(
                                                List.of(
                                                        MenuExpertPresentationPlan.MenuExpertPresentationDish.builder()
                                                                .dishName("酸奶碗")
                                                                .blendedGrossMarginRateOnListPrice("18%")
                                                                .actualProfitAmount("1200")
                                                                .suggestedAction("复核成本")
                                                                .reason("畅销但毛利偏低")
                                                                .build()))
                                        .build()))
                .nextSteps(List.of("先复核「酸奶碗」的成本结构、标准用量与损耗"))
                .capabilityBoundaryZh("当前版本暂不提供最新采购价。")
                .build();
    }

    private static MenuOperationAnswerPlan planWithDish() {
        return MenuOperationAnswerPlan.builder()
                .planType(MenuOperationAnswerPlan.TYPE_MENU_ACTION_RECOMMENDATION)
                .menuOptimizationPlan(
                        MenuOptimizationPlan.builder()
                                .optimizationSummary("先复核引流菜。")
                                .nextSteps(List.of("先复核「酸奶碗」的成本结构、标准用量与损耗"))
                                .priorityGroups(
                                        List.of(
                                                MenuOptimizationPriorityGroup.builder()
                                                        .groupName("优先处理")
                                                        .dishes(
                                                                List.of(
                                                                        MenuOptimizationDishItem.builder()
                                                                                .dishName("酸奶碗")
                                                                                .blendedGrossMarginRateOnListPrice("18%")
                                                                                .actualProfitAmount("1200")
                                                                                .build()))
                                                        .build()))
                                .capabilityLimits(Map.of("latestPurchasePrice", "NOT_IN_P1"))
                                .build())
                .build();
    }
}
