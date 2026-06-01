package com.nongxinle.ai.composer;

import com.nongxinle.ai.dto.business.DishProfitAnswerPlan;
import com.nongxinle.ai.graph.business.DishProfitRankingSalesEvidenceSupport;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DishProfitRankingCardCompanionAnswerPreviewSupportTest {

    @Test
    void shouldUseShortPreview_trueForRankingWithRows() {
        DishProfitAnswerPlan plan =
                DishProfitAnswerPlan.builder()
                        .planType(DishProfitAnswerPlan.TYPE_DISH_HIGHEST_ACTUAL_COST)
                        .focusRows(List.of(Map.of("dishName", "招牌鱼")))
                        .build();

        assertTrue(DishProfitRankingCardCompanionAnswerPreviewSupport.shouldUseShortPreview(plan));
    }

    @Test
    void shouldUseShortPreview_falseForNoDataRanking() {
        LinkedHashMap<String, Object> debug = new LinkedHashMap<>();
        debug.put(
                DishProfitRankingSalesEvidenceSupport.DEBUG_NO_DATA_REASON,
                DishProfitRankingSalesEvidenceSupport.NO_DATA_REASON_NO_DISH_SALES_FOR_PERIOD);

        DishProfitAnswerPlan plan =
                DishProfitAnswerPlan.builder()
                        .planType(DishProfitAnswerPlan.TYPE_DISH_PROFIT_RANKING_NO_DATA)
                        .debug(debug)
                        .build();

        assertFalse(DishProfitRankingCardCompanionAnswerPreviewSupport.shouldUseShortPreview(plan));
    }

    @Test
    void shouldUseShortPreview_falseForSingleDishPlan() {
        DishProfitAnswerPlan plan =
                DishProfitAnswerPlan.builder()
                        .planType(DishProfitAnswerPlan.TYPE_DISH_PROFIT_RATE)
                        .focusRows(List.of(Map.of("dishName", "烩菜")))
                        .build();

        assertFalse(DishProfitRankingCardCompanionAnswerPreviewSupport.shouldUseShortPreview(plan));
    }
}
