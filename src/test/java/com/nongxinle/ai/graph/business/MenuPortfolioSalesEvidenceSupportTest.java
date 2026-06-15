package com.nongxinle.ai.graph.business;

import com.nongxinle.ai.dto.business.MenuOperationAnswerPlan;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class MenuPortfolioSalesEvidenceSupportTest {

    @Test
    void assess_noSalesWhenAllDishRowsZero() {
        List<Map<String, Object>> rows =
                List.of(
                        dishRow("1", "菜A", "0", "0"),
                        dishRow("2", "菜B", "0", "0"),
                        dishRow("3", "菜C", "0", "0"),
                        dishRow("4", "菜D", "0", "0"));

        MenuPortfolioSalesEvidenceSupport.Assessment assessment =
                MenuPortfolioSalesEvidenceSupport.assess(rows, Map.of("totalActualRevenue", "0"));

        assertThat(assessment.salesEvidenceAvailable()).isFalse();
        assertThat(assessment.analyzedDishCount()).isEqualTo(4);
        assertThat(assessment.soldDishCount()).isZero();
        assertThat(assessment.totalSoldPortions()).isEqualByComparingTo("0");
        assertThat(assessment.totalSalesAmount()).isEqualByComparingTo("0");
    }

    @Test
    void assess_availableWhenAnyDishHasSoldPortions() {
        List<Map<String, Object>> rows =
                List.of(dishRow("1", "菜A", "0", "0"), dishRow("2", "菜B", "5", "120"));

        MenuPortfolioSalesEvidenceSupport.Assessment assessment =
                MenuPortfolioSalesEvidenceSupport.assess(rows, Map.of());

        assertThat(assessment.salesEvidenceAvailable()).isTrue();
        assertThat(assessment.soldDishCount()).isEqualTo(1);
    }

    @Test
    void assess_availableFromInsightTotalRevenue() {
        MenuPortfolioSalesEvidenceSupport.Assessment assessment =
                MenuPortfolioSalesEvidenceSupport.assess(
                        List.of(dishRow("1", "菜A", "0", "0")),
                        Map.of("totalActualRevenue", "500.00"));

        assertThat(assessment.salesEvidenceAvailable()).isTrue();
    }

    @Test
    void countSoldDishesFromRows_onlyCountsPositiveSales() {
        List<Map<String, Object>> rows =
                List.of(
                        dishRow("1", "菜A", "0", "0"),
                        dishRow("2", "菜B", "3", "90"),
                        dishRow("3", "菜C", "0", "10"));

        assertThat(MenuPortfolioSalesEvidenceSupport.countSoldDishesFromRows(rows)).isEqualTo(2);
    }

    @Test
    void isNoSalesPortfolioPeriod_readsSummaryFacts() {
        MenuOperationAnswerPlan plan =
                MenuOperationAnswerPlan.builder()
                        .planType(MenuOperationAnswerPlan.TYPE_MENU_OPERATION_OVERVIEW)
                        .summaryFacts(
                                Map.of(
                                        MenuPortfolioSalesEvidenceSupport.SUMMARY_FACT_NO_DATA_REASON,
                                        MenuPortfolioSalesEvidenceSupport.NO_DATA_REASON_NO_DISH_SALES_FOR_PERIOD))
                        .build();

        assertThat(MenuPortfolioSalesEvidenceSupport.isNoSalesPortfolioPeriod(plan)).isTrue();
    }

    private static Map<String, Object> dishRow(
            String foodId, String name, String soldPortions, String revenue) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("foodId", foodId);
        row.put("dishName", name);
        row.put("soldPortionsTotal", soldPortions);
        row.put("actualRevenue", revenue);
        return row;
    }
}
