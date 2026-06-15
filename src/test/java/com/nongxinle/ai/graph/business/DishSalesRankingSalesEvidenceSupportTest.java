package com.nongxinle.ai.graph.business;

import com.nongxinle.ai.dto.business.DishSalesAnswerPlan;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DishSalesRankingSalesEvidenceSupportTest {

    @Test
    void hasCountRankingEvidence_falseWhenAllZero() {
        List<Map<String, Object>> rows =
                List.of(
                        new LinkedHashMap<>(
                                Map.of("dishName", "测试菜", "soldPortionsTotal", 0)),
                        new LinkedHashMap<>(
                                Map.of("dishName", "另一菜", "soldPortionsTotal", "0")));
        assertThat(DishSalesRankingSalesEvidenceSupport.hasCountRankingEvidence(rows)).isFalse();
        assertThat(
                        DishSalesRankingSalesEvidenceSupport.hasRankingEvidenceForMetric(
                                DishSalesAnswerPlan.METRIC_COUNT_HIGH, rows))
                .isFalse();
    }

    @Test
    void hasCountRankingEvidence_trueWhenAnyPositive() {
        List<Map<String, Object>> rows =
                List.of(
                        new LinkedHashMap<>(Map.of("dishName", "A", "soldPortionsTotal", 0)),
                        new LinkedHashMap<>(Map.of("dishName", "B", "soldPortionsTotal", 3)));
        assertThat(DishSalesRankingSalesEvidenceSupport.hasCountRankingEvidence(rows)).isTrue();
    }

    @Test
    void hasAmountRankingEvidence_falseWhenAllZero() {
        List<Map<String, Object>> rows =
                List.of(
                        new LinkedHashMap<>(
                                Map.of(
                                        "dishName",
                                        "测试菜",
                                        "actualRevenue",
                                        0,
                                        "salesAmount",
                                        "0")));
        assertThat(DishSalesRankingSalesEvidenceSupport.hasAmountRankingEvidence(rows)).isFalse();
        assertThat(
                        DishSalesRankingSalesEvidenceSupport.hasRankingEvidenceForMetric(
                                DishSalesAnswerPlan.METRIC_AMOUNT_HIGH, rows))
                .isFalse();
    }

    @Test
    void buildNoDataRankingPlan_emitsNoDataTypeWithoutRows() {
        DishSalesAnswerPlan plan =
                DishSalesRankingSalesEvidenceSupport.buildNoDataRankingPlan(
                        DishSalesAnswerPlan.TYPE_DISH_SALES_COUNT_RANKING_HIGH,
                        DishSalesAnswerPlan.METRIC_COUNT_HIGH,
                        "本店",
                        "上个月",
                        Map.of("wire", "dish_sales_count_ranking_high"),
                        List.of());
        assertThat(plan.getPlanType()).isEqualTo(DishSalesAnswerPlan.TYPE_DISH_SALES_RANKING_NO_DATA);
        assertThat(plan.getRankingRows()).isEmpty();
        assertThat(plan.getResultAnchors()).isEmpty();
        assertThat(plan.getSummary()).isNull();
        assertThat(plan.getDebug())
                .containsEntry(
                        DishSalesRankingSalesEvidenceSupport.DEBUG_NO_DATA_REASON,
                        DishSalesRankingSalesEvidenceSupport.NO_DATA_REASON_NO_DISH_SALES_FOR_PERIOD)
                .containsEntry(
                        DishSalesRankingSalesEvidenceSupport.DEBUG_REQUESTED_RANKING_PLAN_TYPE,
                        DishSalesAnswerPlan.TYPE_DISH_SALES_COUNT_RANKING_HIGH);
        assertThat(DishSalesRankingSalesEvidenceSupport.isNoDataRankingPlan(plan)).isTrue();
    }
}
