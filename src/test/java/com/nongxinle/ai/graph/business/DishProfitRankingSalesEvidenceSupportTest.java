package com.nongxinle.ai.graph.business;

import com.nongxinle.ai.dto.business.DishProfitAnswerPlan;
import com.nongxinle.ai.semantic.matrix.DishProfitSemanticCapabilityMatrix;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DishProfitRankingSalesEvidenceSupportTest {

    @Test
    void hasRankingSalesEvidence_falseWhenNoSoldDishes() {
        List<Map<String, Object>> rows =
                List.of(
                        new LinkedHashMap<>(
                                Map.of(
                                        "dishName",
                                        "测试菜",
                                        "soldPortionsTotal",
                                        0,
                                        "actualRevenue",
                                        0)));
        Map<String, Object> toolData = Map.of("salesDishCount", 0);
        assertThat(DishProfitRankingSalesEvidenceSupport.hasRankingSalesEvidence(rows, toolData))
                .isFalse();
    }

    @Test
    void hasRankingSalesEvidence_trueWhenToolReportsSoldDishes() {
        assertThat(
                        DishProfitRankingSalesEvidenceSupport.hasRankingSalesEvidence(
                                List.of(), Map.of("salesDishCount", 2)))
                .isTrue();
    }

    @Test
    void buildNoDataRankingPlan_emitsEmptyRankingType() {
        DishProfitAnswerPlan plan =
                DishProfitRankingSalesEvidenceSupport.buildNoDataRankingPlan(
                        DishProfitAnswerPlan.TYPE_DISH_HIGHEST_MARGIN,
                        "集团",
                        "2026-06-01",
                        Map.of("wire", "dish_profit_ranking_high_margin"));
        assertThat(plan.getPlanType()).isEqualTo(DishProfitAnswerPlan.TYPE_DISH_PROFIT_RANKING_NO_DATA);
        assertThat(plan.getFocusRows()).isEmpty();
        assertThat(plan.getDebug())
                .containsEntry(
                        DishProfitRankingSalesEvidenceSupport.DEBUG_NO_DATA_REASON,
                        DishProfitRankingSalesEvidenceSupport.NO_DATA_REASON_NO_DISH_SALES_FOR_PERIOD);
        assertThat(DishProfitRankingSalesEvidenceSupport.isNoDataRankingPlan(plan)).isTrue();
    }

    @Test
    void matrixRecognizesRankingWire() {
        assertThat(
                        DishProfitSemanticCapabilityMatrix.isRankingStructuredWire(
                                "dish_profit_ranking_high_margin"))
                .isTrue();
        assertThat(
                        DishProfitSemanticCapabilityMatrix.isRankingStructuredWire(
                                "dish_profit_overview"))
                .isFalse();
    }
}
