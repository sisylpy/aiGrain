package com.nongxinle.ai.graph.business;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class BusinessOverviewDishSalesReasonOutputGuardTest {

    @Test
    void parseAndSanitize_replacesNumbersFromFactPack() {
        Map<String, Object> candidate = new LinkedHashMap<>();
        candidate.put("dishName", "核桃芽菜西芹");
        candidate.put("candidateTag", "SURGE");
        candidate.put("periodQty", "16");
        candidate.put("baselineDailyAvgQty", "0.8");
        candidate.put("expectedPeriodQty", "0.8");
        candidate.put("qtyDiff", "15.2");
        candidate.put("periodSalesAmount", "400");
        candidate.put("amountDiff", "380");

        Map<String, Object> factPack = new LinkedHashMap<>();
        factPack.put("dishCompareCandidates", List.of(candidate));

        String raw =
                """
                {
                  "summary": "本月至今营业额高于平时，主要是部分菜品销量明显超过近30天常态。",
                  "items": [
                    {
                      "dishName": "核桃芽菜西芹",
                      "periodQty": 999,
                      "baselineDailyAvgQty": 1,
                      "expectedPeriodQty": 1,
                      "qtyDiff": 998,
                      "periodSalesAmount": 9999,
                      "amountDiff": 9999,
                      "reason": "本期销量明显高于平时日均"
                    }
                  ]
                }
                """;

        BusinessOverviewDishSalesReasonOutputGuard.ComposeResult result =
                BusinessOverviewDishSalesReasonOutputGuard.parseAndSanitize(raw, factPack);

        assertNotNull(result);
        assertEquals("本月至今营业额高于平时，主要是部分菜品销量明显超过近30天常态。", result.summary());
        assertEquals(1, result.items().size());
        Map<String, Object> item = result.items().get(0);
        assertEquals("核桃芽菜西芹", item.get("dishName"));
        assertEquals(16.0, item.get("periodQty"));
        assertEquals(0.8, item.get("baselineDailyAvgQty"));
        assertEquals(0.8, item.get("expectedPeriodQty"));
        assertEquals(15.2, item.get("qtyDiff"));
        assertEquals(400.0, item.get("periodSalesAmount"));
        assertEquals(380.0, item.get("amountDiff"));
        assertEquals(0.8, item.get("compareAvgQty"));
        assertEquals("SURGE", item.get("candidateTag"));
        assertEquals("本期销量明显高于平时日均", item.get("reason"));
    }

    @Test
    void parseAndSanitize_fallsBackToPeriodDishSales() {
        Map<String, Object> factPack =
                Map.of(
                        "periodDishSales",
                        List.of(
                                Map.of(
                                        "dishName",
                                        "宫保鸡丁",
                                        "periodQty",
                                        "10",
                                        "baselineDailyAvgQty",
                                        "5",
                                        "expectedPeriodQty",
                                        "5",
                                        "qtyDiff",
                                        "5")));

        String raw =
                """
                {"summary":"本月至今菜品销量整体接近平时。","items":[{"dishName":"宫保鸡丁","periodQty":1,"reason":"x"}]}
                """;

        BusinessOverviewDishSalesReasonOutputGuard.ComposeResult result =
                BusinessOverviewDishSalesReasonOutputGuard.parseAndSanitize(raw, factPack);

        assertNotNull(result);
        assertEquals(1, result.items().size());
        assertEquals(10.0, result.items().get(0).get("periodQty"));
    }

    @Test
    void parseAndSanitize_dropsUnknownDishNames() {
        Map<String, Object> factPack =
                Map.of("dishCompareCandidates", List.of(Map.of("dishName", "宫保鸡丁", "periodQty", "10")));

        String raw =
                """
                {"summary":"本月至今菜品销量整体接近平时。","items":[{"dishName":"不存在","periodQty":1,"reason":"x"}]}
                """;

        BusinessOverviewDishSalesReasonOutputGuard.ComposeResult result =
                BusinessOverviewDishSalesReasonOutputGuard.parseAndSanitize(raw, factPack);

        assertNotNull(result);
        assertEquals(0, result.items().size());
    }

    @Test
    void parseAndSanitize_invalidJson_returnsNull() {
        assertNull(
                BusinessOverviewDishSalesReasonOutputGuard.parseAndSanitize(
                        "not json", Map.of("dishCompareCandidates", List.of())));
    }
}
