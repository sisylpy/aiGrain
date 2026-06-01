package com.nongxinle.ai.graph.business;

import com.nongxinle.ai.core.AiRunState;
import com.nongxinle.service.GbDepFoodBusinessInsightService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BusinessOverviewDishSalesReasonFactBuilderTest {

    @Test
    void build_includesPeriodActiveAndBaselineOnlyDishes() {
        GbDepFoodBusinessInsightService insightService = mock(GbDepFoodBusinessInsightService.class);
        when(insightService.buildInsight(eq(1), eq(10), eq("2026-05-31"), eq("2026-05-31"), isNull()))
                .thenReturn(
                        insightWithDishes(
                                "2026-05-31",
                                "2026-05-31",
                                List.of(dishRow(101, "宫保鸡丁", "20", "600")),
                                "600"));
        when(insightService.buildInsight(eq(1), eq(10), eq("2026-05-01"), eq("2026-05-30"), isNull()))
                .thenReturn(
                        insightWithDishes(
                                "2026-05-01",
                                "2026-05-30",
                                List.of(
                                        dishRow(101, "宫保鸡丁", "300", "9000"),
                                        dishRow(102, "鱼香肉丝", "150", "4500")),
                                "13500"));
        when(insightService.buildInsight(eq(1), eq(10), eq("2026-05-30"), eq("2026-05-30"), isNull()))
                .thenReturn(insightWithDishes("2026-05-30", "2026-05-30", List.of(), "0"));

        AiRunState state = new AiRunState();
        state.setDistributerId(1L);
        state.setDepartmentId(10L);
        BusinessStatusCardBuildRequest req =
                BusinessStatusCardBuildRequest.builder()
                        .startDate("2026-05-31")
                        .endDate("2026-05-31")
                        .periodDayCount(1L)
                        .timeExpression("今天")
                        .compareStartDate("2026-05-30")
                        .compareEndDate("2026-05-30")
                        .compareLabel("昨天")
                        .build();

        Map<String, Object> factPack =
                BusinessOverviewDishSalesReasonFactBuilder.build(state, req, insightService, null);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) factPack.get("dishCompareCandidates");
        assertEquals(2, rows.size());
        assertTrue(rows.stream().anyMatch(r -> "宫保鸡丁".equals(r.get("dishName"))));
        assertTrue(rows.stream().anyMatch(r -> "鱼香肉丝".equals(r.get("dishName"))));

        Map<String, Object> baselineOnly =
                rows.stream()
                        .filter(r -> "鱼香肉丝".equals(r.get("dishName")))
                        .findFirst()
                        .orElseThrow();
        assertEquals(true, baselineOnly.get("baselineOnly"));
        assertEquals("0", baselineOnly.get("periodQty"));
        assertEquals("150", baselineOnly.get("baselineTotalQty"));

        @SuppressWarnings("unchecked")
        Map<String, Object> diag = (Map<String, Object>) factPack.get("factPackDiagnostics");
        assertNotNull(diag);
        assertEquals(2, diag.get("dishCompareRowCount"));
        assertEquals(1, diag.get("periodActiveDishCount"));
        assertEquals(1, diag.get("baselineOnlyDishCount"));
        assertEquals(true, diag.get("includesBaselineOnlyDishes"));
        assertEquals("2026-05-01", diag.get("baselineWindowStartDate"));
        assertEquals("2026-05-30", diag.get("baselineWindowEndDate"));
    }

    @Test
    void assembleFactPackRows_capPrioritizesPeriodActiveAndBaselineOnly() {
        List<BusinessOverviewDishSalesReasonFactBuilder.DishCompareRow> rows = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            rows.add(row("本期菜" + i, BigDecimal.valueOf(i + 1), BigDecimal.ZERO, BigDecimal.valueOf(i + 1)));
        }
        rows.add(row("基线零销量", BigDecimal.ZERO, BigDecimal.valueOf(50), BigDecimal.valueOf(50)));

        BusinessOverviewDishSalesReasonFactBuilder.FactPackAssembly assembly =
                BusinessOverviewDishSalesReasonFactBuilder.assembleFactPackRows(rows);

        assertTrue(assembly.rowCapApplied());
        assertEquals(BusinessOverviewDishSalesReasonFactBuilder.FACT_PACK_DISH_ROW_CAP, assembly.rows().size());
        assertTrue(
                assembly.rows().stream()
                        .anyMatch(r -> "基线零销量".equals(r.get("dishName"))));
    }

    private static BusinessOverviewDishSalesReasonFactBuilder.DishCompareRow row(
            String name, BigDecimal periodQty, BigDecimal baselineTotalQty, BigDecimal baselineDailyAvgQty) {
        BusinessOverviewDishSalesReasonFactBuilder.DishCompareRow r =
                new BusinessOverviewDishSalesReasonFactBuilder.DishCompareRow();
        r.dishName = name;
        r.periodQty = periodQty;
        r.periodSalesAmount = periodQty.multiply(BigDecimal.TEN);
        r.baselineTotalQty = baselineTotalQty;
        r.baselineTotalAmount = baselineTotalQty.multiply(BigDecimal.TEN);
        r.baselineDailyAvgQty = baselineDailyAvgQty;
        r.baselineDailyAvgAmount = baselineDailyAvgQty.multiply(BigDecimal.TEN);
        r.expectedPeriodQty = baselineDailyAvgQty;
        r.expectedPeriodAmount = baselineDailyAvgQty.multiply(BigDecimal.TEN);
        r.qtyDiff = periodQty.subtract(r.expectedPeriodQty);
        r.amountDiff = r.periodSalesAmount.subtract(r.expectedPeriodAmount);
        r.usualSeller = baselineDailyAvgQty.compareTo(BigDecimal.valueOf(5)) >= 0;
        r.changeDirection =
                BusinessOverviewDishSalesReasonFactBuilder.resolveChangeDirection(r.qtyDiff);
        r.candidateTag =
                BusinessOverviewDishSalesReasonFactBuilder.resolveCandidateTag(
                        r.usualSeller, r.periodQty, r.qtyDiff);
        return r;
    }

    private static Map<String, Object> insightWithDishes(
            String start, String end, List<Map<String, Object>> dishes, String totalRevenue) {
        Map<String, Object> summary = Map.of("totalListPriceRevenue", totalRevenue);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("startDate", start);
        out.put("stopDate", end);
        out.put("dishes", dishes);
        out.put("businessInsightSummary", summary);
        return out;
    }

    private static Map<String, Object> dishRow(int foodId, String name, String qty, String amount) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("foodId", foodId);
        row.put("foodName", name);
        row.put("soldPortionsTotal", qty);
        row.put("listPriceRevenue", amount);
        return row;
    }

    @Test
    void resolveCandidateTag_usualSellerZeroPeriod() {
        assertEquals(
                BusinessOverviewDishSalesReasonFactBuilder.TAG_ZERO_THIS_PERIOD,
                BusinessOverviewDishSalesReasonFactBuilder.resolveCandidateTag(
                        true, BigDecimal.ZERO, BigDecimal.valueOf(-5)));
    }

    @Test
    void resolveCandidateTag_usualSellerUnderperform() {
        assertEquals(
                BusinessOverviewDishSalesReasonFactBuilder.TAG_USUAL_UNDERPERFORM,
                BusinessOverviewDishSalesReasonFactBuilder.resolveCandidateTag(
                        true, BigDecimal.TEN, BigDecimal.valueOf(-3)));
    }

    @Test
    void resolveCandidateTag_surgeWhenQtyUp() {
        assertEquals(
                BusinessOverviewDishSalesReasonFactBuilder.TAG_SURGE,
                BusinessOverviewDishSalesReasonFactBuilder.resolveCandidateTag(
                        false, BigDecimal.TEN, BigDecimal.valueOf(3)));
    }

    @Test
    void resolveRevenueDirection_higherWhenAboveThreshold() {
        assertEquals(
                BusinessOverviewDishSalesReasonFactBuilder.DIR_HIGHER,
                BusinessOverviewDishSalesReasonFactBuilder.resolveRevenueDirection(
                        BigDecimal.valueOf(1200),
                        BigDecimal.valueOf(900),
                        30,
                        0.15));
    }

    @Test
    void resolveRevenueDirection_similarWithinThreshold() {
        assertEquals(
                BusinessOverviewDishSalesReasonFactBuilder.DIR_SIMILAR,
                BusinessOverviewDishSalesReasonFactBuilder.resolveRevenueDirection(
                        BigDecimal.valueOf(1030),
                        BigDecimal.valueOf(900),
                        30,
                        0.03));
    }

    @Test
    void resolveRevenueDirection_unknownWhenBaselineTooShort() {
        assertEquals(
                BusinessOverviewDishSalesReasonFactBuilder.DIR_UNKNOWN,
                BusinessOverviewDishSalesReasonFactBuilder.resolveRevenueDirection(
                        BigDecimal.valueOf(1000),
                        BigDecimal.valueOf(900),
                        5,
                        0.2));
    }

    @Test
    void shouldIncludeInFactPack_includesBaselineOnlyEvenWithoutPeriodSales() {
        BusinessOverviewDishSalesReasonFactBuilder.DishCompareRow baselineOnly = row(
                "鱼香肉丝", BigDecimal.ZERO, BigDecimal.valueOf(30), BigDecimal.valueOf(1));
        assertTrue(BusinessOverviewDishSalesReasonFactBuilder.shouldIncludeInFactPack(baselineOnly));
    }
}
