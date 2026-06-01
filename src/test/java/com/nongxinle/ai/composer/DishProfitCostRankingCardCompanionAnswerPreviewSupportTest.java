package com.nongxinle.ai.composer;

import com.nongxinle.ai.dto.business.DishProfitAnswerPlan;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DishProfitCostRankingCardCompanionAnswerPreviewSupportTest {

    @Test
    void shouldUseShortPreview_whenActualCostRankingHasRows() {
        DishProfitAnswerPlan plan =
                DishProfitAnswerPlan.builder()
                        .planType(DishProfitAnswerPlan.TYPE_DISH_HIGHEST_ACTUAL_COST)
                        .focusRows(List.of(Map.of("dishName", "招牌鱼")))
                        .build();

        assertTrue(DishProfitCostRankingCardCompanionAnswerPreviewSupport.shouldUseShortPreview(plan));
    }

    @Test
    void shouldNotUseShortPreview_forSingleDishPlanType() {
        DishProfitAnswerPlan plan =
                DishProfitAnswerPlan.builder()
                        .planType(DishProfitAnswerPlan.TYPE_DISH_PROFIT_RATE)
                        .focusRows(List.of(Map.of("dishName", "招牌鱼")))
                        .build();

        assertFalse(DishProfitCostRankingCardCompanionAnswerPreviewSupport.shouldUseShortPreview(plan));
    }

    @Test
    void composeCardCompanionHint_includesTimeLabel() {
        DishProfitAnswerPlan plan =
                DishProfitAnswerPlan.builder()
                        .planType(DishProfitAnswerPlan.TYPE_DISH_HIGHEST_ACTUAL_COST)
                        .timeLabel("上个月")
                        .build();

        assertEquals(
                "已整理上个月菜品实际成本排行，详见下方卡片。",
                DishProfitCostRankingCardCompanionAnswerPreviewSupport.composeCardCompanionHint(plan));
    }
}
