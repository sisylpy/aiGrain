package com.nongxinle.service.impl;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GbDishCostAnalysisIngredientSortTest {

    @Test
    void normalizeIngredientSortBy_acceptsNewAliases() {
        assertThat(GbDishCostAnalysisServiceImpl.normalizeIngredientSortBy("diffRate")).isEqualTo("diffrate");
        assertThat(GbDishCostAnalysisServiceImpl.normalizeIngredientSortBy("diffRatePerPortion")).isEqualTo("diffrate");
        assertThat(GbDishCostAnalysisServiceImpl.normalizeIngredientSortBy("成本偏差率")).isEqualTo("diffrate");
        assertThat(GbDishCostAnalysisServiceImpl.normalizeIngredientSortBy("ingredientCount")).isEqualTo("ingredientcount");
        assertThat(GbDishCostAnalysisServiceImpl.normalizeIngredientSortBy("配料数量")).isEqualTo("ingredientcount");
    }

    @Test
    void normalizeIngredientSortBy_rejectsUnknown() {
        assertThatThrownBy(() -> GbDishCostAnalysisServiceImpl.normalizeIngredientSortBy("unknown"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("sortBy");
    }

    @Test
    void sortIngredientAnalysisDishRows_byDiffRateDesc() {
        List<Map<String, Object>> rows = new ArrayList<>();
        rows.add(dishRow(1, "10.00", "20.00", 2));
        rows.add(dishRow(2, "50.00", "10.00", 1));
        rows.add(dishRow(3, "10.00", "10.00", 3));

        GbDishCostAnalysisServiceImpl.sortIngredientAnalysisDishRows(rows, "diffrate", false);

        assertThat(rows).extracting(r -> r.get("dishId")).containsExactly(1, 2, 3);
    }

    @Test
    void sortIngredientAnalysisDishRows_byIngredientCountAsc() {
        List<Map<String, Object>> rows = new ArrayList<>();
        rows.add(dishRow(1, "10.00", "10.00", 5));
        rows.add(dishRow(2, "10.00", "10.00", 1));
        rows.add(dishRow(3, "10.00", "10.00", 3));

        GbDishCostAnalysisServiceImpl.sortIngredientAnalysisDishRows(rows, "ingredientcount", true);

        assertThat(rows).extracting(r -> r.get("dishId")).containsExactly(2, 3, 1);
    }

    @Test
    void sortIngredientAnalysisDishRows_byDiffAbsDesc() {
        List<Map<String, Object>> rows = new ArrayList<>();
        rows.add(dishRowWithDiff(1, "1.00"));
        rows.add(dishRowWithDiff(2, "-5.00"));
        rows.add(dishRowWithDiff(3, "3.00"));

        GbDishCostAnalysisServiceImpl.sortIngredientAnalysisDishRows(rows, "diff", false);

        assertThat(rows).extracting(r -> r.get("dishId")).containsExactly(2, 3, 1);
    }

    @Test
    void buildIngredientAnalysisScopeSalesSubtotals_aggregatesCostMetrics() {
        List<Map<String, Object>> rows = new ArrayList<>();
        Map<String, Object> a = new LinkedHashMap<>();
        a.put("actualCostAmount", "300.00");
        a.put("theoryCostPerPortion", "10.00");
        a.put("salesPortions", "10");
        rows.add(a);
        Map<String, Object> b = new LinkedHashMap<>();
        b.put("actualCostAmount", "150.00");
        b.put("theoryCostPerPortion", "5.00");
        b.put("salesPortions", "20");
        rows.add(b);

        Map<String, Object> scope = GbDishCostAnalysisServiceImpl.buildIngredientAnalysisScopeSalesSubtotals(rows);

        assertThat(scope.get("actualCostTotal")).isEqualTo("450.00");
        assertThat(scope.get("theoreticalCostTotal")).isEqualTo("200.00");
        assertThat(scope.get("costDeviationTotal")).isEqualTo("250.00");
        assertThat(scope.get("costDeviationRate")).isEqualTo("125.00");
    }

    @Test
    void buildIngredientAnalysisScopeSalesSubtotals_whenNoTheoryCost_rateIsNullUnlessAllZero() {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("actualCostAmount", "50.00");
        row.put("theoryCostPerPortion", "0.00");
        row.put("salesPortions", "5");

        Map<String, Object> scope = GbDishCostAnalysisServiceImpl.buildIngredientAnalysisScopeSalesSubtotals(
                List.of(row));

        assertThat(scope.get("costDeviationRate")).isNull();
    }

    private static Map<String, Object> dishRow(int dishId, String theoryPp, String actualPp, int ingredientCount) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("dishId", dishId);
        row.put("theoryCostPerPortion", theoryPp);
        row.put("actualCostPerPortion", actualPp);
        row.put("ingredientCount", ingredientCount);
        row.put("ingredientRows", new ArrayList<>(ingredientCount));
        if ("10.00".equals(theoryPp)) {
            row.put("diffRatePerPortion", "0.00");
        } else {
            row.put("diffRatePerPortion", diffRatePercent(theoryPp, actualPp));
        }
        row.put("salesAmount", "100.00");
        return row;
    }

    private static Map<String, Object> dishRowWithDiff(int dishId, String diffCostPerPortion) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("dishId", dishId);
        row.put("diffCostPerPortion", diffCostPerPortion);
        row.put("salesAmount", "100.00");
        return row;
    }

    private static String diffRatePercent(String theoryPp, String actualPp) {
        double th = Double.parseDouble(theoryPp);
        double ac = Double.parseDouble(actualPp);
        if (th <= 0) {
            return null;
        }
        return String.format("%.2f", (ac - th) / th * 100);
    }
}
