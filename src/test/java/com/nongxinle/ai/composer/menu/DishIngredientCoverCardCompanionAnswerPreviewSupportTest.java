package com.nongxinle.ai.composer.menu;

import com.nongxinle.ai.dto.business.DishIngredientCoverAnswerPlan;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DishIngredientCoverCardCompanionAnswerPreviewSupportTest {

    @Test
    void withCoverDays_usesSupportDaysWording() {
        DishIngredientCoverAnswerPlan plan =
                DishIngredientCoverAnswerPlan.builder()
                        .planType(DishIngredientCoverAnswerPlan.TYPE)
                        .dishName("宫保鸡丁")
                        .dishCoverDays("5")
                        .bottleneckIngredientName("花生")
                        .knownGaps(List.of("shelf_life_batch_not_in_cover_days_p1"))
                        .build();

        String hint = DishIngredientCoverCardCompanionAnswerPreviewSupport.composeCardCompanionHint(plan);

        assertTrue(hint.contains("大约还能支撑 5 天"));
        assertTrue(hint.contains("花生"));
        assertFalse(hint.contains("暂无法推算"));
    }

    @Test
    void noSalesGap_withIngredientRows_explainsLimitAndPointsToCard() {
        DishIngredientCoverAnswerPlan plan =
                DishIngredientCoverAnswerPlan.builder()
                        .planType(DishIngredientCoverAnswerPlan.TYPE)
                        .dishName("测试菜")
                        .knownGaps(
                                List.of(
                                        "shelf_life_batch_not_in_cover_days_p1",
                                        DishIngredientCoverCardCompanionAnswerPreviewSupport.GAP_NO_SALES_IN_WINDOW))
                        .ingredientRows(List.of(Map.of("ingredientName", "生鸡")))
                        .summary(Map.of("salesBaselineDays", 7))
                        .build();

        String hint = DishIngredientCoverCardCompanionAnswerPreviewSupport.composeCardCompanionHint(plan);

        assertTrue(hint.contains("最近 7 天没有该菜销量"));
        assertTrue(hint.contains("暂不能按销售节奏估算可用天数"));
        assertTrue(hint.contains("下方卡片仍可查看各配料当前库存"));
        assertFalse(hint.contains("大约还能支撑"));
        assertFalse(hint.contains("暂无法推算"));
    }

    @Test
    void noRecipeGap_usesRecipeMissingWording() {
        DishIngredientCoverAnswerPlan plan =
                DishIngredientCoverAnswerPlan.builder()
                        .planType(DishIngredientCoverAnswerPlan.TYPE)
                        .dishName("测试菜")
                        .knownGaps(List.of(DishIngredientCoverCardCompanionAnswerPreviewSupport.GAP_NO_RECIPE))
                        .build();

        String hint = DishIngredientCoverCardCompanionAnswerPreviewSupport.composeCardCompanionHint(plan);

        assertTrue(hint.contains("暂无配方数据"));
        assertFalse(hint.contains("暂无法推算"));
    }
}
