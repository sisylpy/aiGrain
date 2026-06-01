package com.nongxinle.ai.composer;

import com.nongxinle.ai.dto.business.DishSalesAnswerPlan;
import com.nongxinle.ai.graph.business.DishSalesRankingSalesEvidenceSupport;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DishSalesRankingCardCompanionAnswerPreviewSupportTest {

    @Test
    void shouldUseShortPreview_onlyForRankingPlansWithRows() {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("dishName", "烩菜");
        DishSalesAnswerPlan ranking =
                DishSalesAnswerPlan.builder()
                        .planType(DishSalesAnswerPlan.TYPE_DISH_SALES_COUNT_RANKING_HIGH)
                        .timeLabel("上个月")
                        .rankingRows(List.of(row))
                        .build();
        DishSalesAnswerPlan single =
                DishSalesAnswerPlan.builder()
                        .planType(DishSalesAnswerPlan.TYPE_DISH_SALES_SINGLE_DISH)
                        .rankingRows(List.of(row))
                        .build();

        assertTrue(DishSalesRankingCardCompanionAnswerPreviewSupport.shouldUseShortPreview(ranking));
        assertFalse(DishSalesRankingCardCompanionAnswerPreviewSupport.shouldUseShortPreview(single));
    }

    @Test
    void composeCardCompanionHint_usesTimeLabel() {
        DishSalesAnswerPlan plan =
                DishSalesAnswerPlan.builder()
                        .planType(DishSalesAnswerPlan.TYPE_DISH_SALES_COUNT_RANKING_HIGH)
                        .timeLabel("上个月")
                        .build();

        assertEquals(
                "已整理上个月菜品销量排行，详见下方卡片。",
                DishSalesRankingCardCompanionAnswerPreviewSupport.composeCardCompanionHint(plan));
    }

    @Test
    void shouldUseShortPreview_falseForNoDataRankingPlan() {
        DishSalesAnswerPlan noData =
                DishSalesRankingSalesEvidenceSupport.buildNoDataRankingPlan(
                        DishSalesAnswerPlan.TYPE_DISH_SALES_COUNT_RANKING_HIGH,
                        DishSalesAnswerPlan.METRIC_COUNT_HIGH,
                        "本店",
                        "上个月",
                        Map.of(),
                        List.of());

        assertFalse(DishSalesRankingCardCompanionAnswerPreviewSupport.shouldUseShortPreview(noData));
    }

    @Test
    void shouldUseShortPreview_falseWhenRankingRowsAllZero() {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("dishName", "零销量菜");
        row.put("soldPortionsTotal", "0");
        DishSalesAnswerPlan plan =
                DishSalesAnswerPlan.builder()
                        .planType(DishSalesAnswerPlan.TYPE_DISH_SALES_COUNT_RANKING_HIGH)
                        .metricType(DishSalesAnswerPlan.METRIC_COUNT_HIGH)
                        .rankingRows(List.of(row))
                        .build();

        assertFalse(DishSalesRankingCardCompanionAnswerPreviewSupport.shouldUseShortPreview(plan));
    }
}
