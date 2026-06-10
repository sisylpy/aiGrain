package com.nongxinle.service.impl;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class OutboundIngredientReconcileSupportTest {

    @Test
    void compute_splitsVerifiedNonVerifiedAndEmployeeMeal() {
        Map<Integer, BigDecimal> outbound123 = Map.of(100, new BigDecimal("100"));
        Map<Integer, BigDecimal> employeeMeal = Map.of(100, new BigDecimal("20"));
        List<OutboundIngredientReconcileSupport.DishGoodCostLine> lines = List.of(
                new OutboundIngredientReconcileSupport.DishGoodCostLine(1, 100, new BigDecimal("10"),
                        new BigDecimal("60"), new BigDecimal("50")),
                new OutboundIngredientReconcileSupport.DishGoodCostLine(2, 100, BigDecimal.ZERO,
                        new BigDecimal("40"), new BigDecimal("30")));

        OutboundIngredientReconcileSupport.ScopeBreakdown scope = OutboundIngredientReconcileSupport.compute(
                lines, outbound123, employeeMeal);

        assertThat(scope.verifiedTotal).isEqualByComparingTo("60");
        assertThat(scope.nonVerifiedTotal).isEqualByComparingTo("40");
        assertThat(scope.employeeMealTotal).isEqualByComparingTo("20");
        assertThat(scope.nonVerifiedDishCount).isEqualTo(1);

        OutboundIngredientReconcileSupport.GoodsBreakdown bd =
                OutboundIngredientReconcileSupport.goodsBreakdown(scope, 100);
        assertThat(bd.verified).isEqualByComparingTo("60");
        assertThat(bd.nonVerified).isEqualByComparingTo("40");
        assertThat(bd.employeeMeal).isEqualByComparingTo("20");
    }

    @Test
    void buildSummaryCard_exposesThreeCardFields() {
        Map<Integer, OutboundIngredientReconcileSupport.GoodsBreakdown> byGoods = new LinkedHashMap<>();
        byGoods.put(1, new OutboundIngredientReconcileSupport.GoodsBreakdown(
                new BigDecimal("80"), new BigDecimal("20"), new BigDecimal("50"), new BigDecimal("40"),
                new BigDecimal("30")));
        OutboundIngredientReconcileSupport.ScopeBreakdown scope = new OutboundIngredientReconcileSupport.ScopeBreakdown(
                byGoods, new BigDecimal("80"), new BigDecimal("20"), new BigDecimal("50"), new BigDecimal("40"),
                new BigDecimal("30"), 2);

        Map<String, Object> summary = OutboundIngredientReconcileSupport.buildSummaryCard(scope, new BigDecimal("200"));

        assertThat(summary.get("totalOutboundAmount")).isEqualTo("100.00");
        assertThat(summary.get("verifiedTotalAmount")).isEqualTo("50.00");
        assertThat(summary.get("nonVerifiedTotalAmount")).isEqualTo("30.00");
        assertThat(summary.get("employeeMealTotalAmount")).isEqualTo("20.00");
        assertThat(summary.get("verifiedGrossMarginRate")).isEqualTo("75.00");
        assertThat(summary.get("verifiedTheoryDeviationAmount")).isEqualTo("10.00");
        assertThat(summary.get("verifiedTheoryDeviationRate")).isEqualTo("25.00");
        assertThat(summary.get("nonVerifiedDishCount")).isEqualTo(2);
    }

    @Test
    void normalizeVerificationStatus_defaultsAndAliases() {
        assertThat(OutboundIngredientReconcileSupport.normalizeVerificationStatus(null)).isEqualTo("all");
        assertThat(OutboundIngredientReconcileSupport.normalizeVerificationStatus("")).isEqualTo("all");
        assertThat(OutboundIngredientReconcileSupport.normalizeVerificationStatus("verified")).isEqualTo("verified");
        assertThat(OutboundIngredientReconcileSupport.normalizeVerificationStatus("已核销")).isEqualTo("verified");
        assertThat(OutboundIngredientReconcileSupport.normalizeVerificationStatus("unverified")).isEqualTo("unverified");
        assertThat(OutboundIngredientReconcileSupport.normalizeVerificationStatus("未核销")).isEqualTo("unverified");
    }

    @Test
    void matchesVerificationStatus_filtersByVerifiedAmount() {
        OutboundIngredientReconcileSupport.GoodsBreakdown mixed =
                new OutboundIngredientReconcileSupport.GoodsBreakdown(
                        new BigDecimal("100"), BigDecimal.ZERO, new BigDecimal("60"), new BigDecimal("50"),
                        new BigDecimal("40"));
        OutboundIngredientReconcileSupport.GoodsBreakdown verifiedOnly =
                new OutboundIngredientReconcileSupport.GoodsBreakdown(
                        new BigDecimal("50"), BigDecimal.ZERO, new BigDecimal("50"), new BigDecimal("40"),
                        BigDecimal.ZERO);
        OutboundIngredientReconcileSupport.GoodsBreakdown unverifiedOnly =
                new OutboundIngredientReconcileSupport.GoodsBreakdown(
                        new BigDecimal("30"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                        new BigDecimal("30"));

        assertThat(OutboundIngredientReconcileSupport.matchesVerificationStatus(mixed, "all")).isTrue();
        assertThat(OutboundIngredientReconcileSupport.matchesVerificationStatus(mixed, "verified")).isTrue();
        assertThat(OutboundIngredientReconcileSupport.matchesVerificationStatus(mixed, "unverified")).isTrue();
        assertThat(OutboundIngredientReconcileSupport.matchesVerificationStatus(verifiedOnly, "verified")).isTrue();
        assertThat(OutboundIngredientReconcileSupport.matchesVerificationStatus(verifiedOnly, "unverified")).isFalse();
        assertThat(OutboundIngredientReconcileSupport.matchesVerificationStatus(unverifiedOnly, "unverified")).isTrue();
        assertThat(OutboundIngredientReconcileSupport.matchesVerificationStatus(unverifiedOnly, "verified")).isFalse();
    }

    @Test
    void matchesVerificationStatus_ignoresSubCentDustMatchingDisplayRounding() {
        OutboundIngredientReconcileSupport.GoodsBreakdown displayFullyVerified =
                new OutboundIngredientReconcileSupport.GoodsBreakdown(
                        new BigDecimal("7.00"), BigDecimal.ZERO, new BigDecimal("6.996"), new BigDecimal("2.70"),
                        new BigDecimal("0.004"));

        assertThat(OutboundIngredientReconcileSupport.matchesVerificationStatus(displayFullyVerified, "unverified"))
                .isFalse();
        assertThat(OutboundIngredientReconcileSupport.matchesVerificationStatus(displayFullyVerified, "verified"))
                .isTrue();
    }

    @Test
    void mergeOutboundIngredientListGoodsIds_includesOutboundOnlyGoods() {
        LinkedHashSet<Integer> merged = OutboundIngredientReconcileSupport.mergeOutboundIngredientListGoodsIds(
                Set.of(1),
                Map.of(2, new BigDecimal("50"), 3, BigDecimal.ZERO),
                Map.of(4, new BigDecimal("10")));

        assertThat(merged).containsExactly(1, 2, 4);
    }

    @Test
    void compute_splitsVerifiedAfterSalesGraceWindow() {
        Map<Integer, Set<Integer>> foodIdsByGoods = Map.of(90, Set.of(1));
        Map<Integer, String> lastSalesByFood = Map.of(1, "2026-06-03");
        List<Map<String, Object>> reduceByDate = List.of(
                reduceDateRow(90, "2026-06-04", "1.80"),
                reduceDateRow(90, "2026-06-06", "2.96"));
        Map<Integer, BigDecimal> afterGrace = OutboundIngredientReconcileSupport
                .computeOutbound123AfterSalesGraceByGoods(reduceByDate, foodIdsByGoods, lastSalesByFood);
        assertThat(afterGrace.get(90)).isEqualByComparingTo("2.96");

        Map<Integer, BigDecimal> outbound123 = Map.of(90, new BigDecimal("4.76"));
        List<OutboundIngredientReconcileSupport.DishGoodCostLine> lines = List.of(
                new OutboundIngredientReconcileSupport.DishGoodCostLine(1, 90, new BigDecimal("3"),
                        new BigDecimal("4.76"), new BigDecimal("2.28")));

        OutboundIngredientReconcileSupport.ScopeBreakdown scope = OutboundIngredientReconcileSupport.compute(
                lines, outbound123, Map.of(), afterGrace);

        OutboundIngredientReconcileSupport.GoodsBreakdown bd =
                OutboundIngredientReconcileSupport.goodsBreakdown(scope, 90);
        assertThat(bd.verified).isEqualByComparingTo("1.80");
        assertThat(bd.nonVerified).isEqualByComparingTo("2.96");
    }

    private static Map<String, Object> reduceDateRow(int goodsId, String date, String subtotal) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("disGoodsId", goodsId);
        row.put("reduceDate", date);
        row.put("subtotalSum", new BigDecimal(subtotal));
        return row;
    }

    @Test
    void rowReconcileFields_exposesOutboundAndSplitAmounts() {
        OutboundIngredientReconcileSupport.GoodsBreakdown bd =
                new OutboundIngredientReconcileSupport.GoodsBreakdown(
                        new BigDecimal("100"), new BigDecimal("15"), new BigDecimal("60"), new BigDecimal("50"),
                        new BigDecimal("40"));

        Map<String, Object> row = OutboundIngredientReconcileSupport.rowReconcileFields(bd,
                new BigDecimal("10"), new BigDecimal("2"));

        assertThat(row.get("outboundTotalAmount")).isEqualTo("100.00");
        assertThat(row.get("verifiedAmount")).isEqualTo("60.00");
        assertThat(row.get("nonVerifiedAmount")).isEqualTo("40.00");
        assertThat(row.get("employeeMealAmount")).isEqualTo("15.00");
        assertThat(row.get("outboundTotalQty")).isEqualTo("10.00");
        assertThat(row.get("verifiedQty")).isEqualTo("6.00");
        assertThat(row.get("nonVerifiedQty")).isEqualTo("4.00");
        assertThat(row.get("employeeMealQty")).isEqualTo("2.00");
    }

    @Test
    void enrichOutbound123WhenSubtotalMissing_usesCatalogPriceForWeightOnlyOutbound() {
        Map<Integer, BigDecimal> outbound123 = Map.of(200, BigDecimal.ZERO);
        Map<Integer, BigDecimal> reduceW = Map.of(200, new BigDecimal("0.5"));
        Map<Integer, BigDecimal> reduceS = Map.of(200, BigDecimal.ZERO);
        Map<Integer, BigDecimal> catalog = Map.of(200, new BigDecimal("12"));

        Map<Integer, BigDecimal> enriched = OutboundIngredientReconcileSupport.enrichOutbound123WhenSubtotalMissing(
                outbound123, reduceW, Map.of(), Map.of(), reduceS, Map.of(), Map.of(), catalog);

        assertThat(enriched.get(200)).isEqualByComparingTo("6");
    }

    @Test
    void rowReconcileFields_splitsQtyWhenAmountMissingButWeightPresent() {
        OutboundIngredientReconcileSupport.GoodsBreakdown bd =
                new OutboundIngredientReconcileSupport.GoodsBreakdown(
                        BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);

        Map<String, Object> row = OutboundIngredientReconcileSupport.rowReconcileFields(bd, new BigDecimal("0.5"), null);

        assertThat(row.get("nonVerifiedQty")).isEqualTo("0.50");
        assertThat(row.get("verifiedQty")).isEqualTo("0.00");
        assertThat(row.get("outboundTotalQty")).isEqualTo("0.50");
    }

    @Test
    void compute_countsDishEmployeeMealConsumptionAsVerified() {
        Map<Integer, BigDecimal> outbound123 = Map.of(107, new BigDecimal("8"));
        List<OutboundIngredientReconcileSupport.DishGoodCostLine> lines = List.of(
                new OutboundIngredientReconcileSupport.DishGoodCostLine(41, 107, new BigDecimal("1"),
                        new BigDecimal("8"), new BigDecimal("4")));

        OutboundIngredientReconcileSupport.ScopeBreakdown scope = OutboundIngredientReconcileSupport.compute(
                lines, outbound123, Map.of());

        OutboundIngredientReconcileSupport.GoodsBreakdown bd =
                OutboundIngredientReconcileSupport.goodsBreakdown(scope, 107);
        assertThat(bd.verified).isEqualByComparingTo("8");
        assertThat(bd.nonVerified).isEqualByComparingTo("0");
        assertThat(scope.nonVerifiedDishCount).isZero();
    }

    @Test
    void compute_marksWeightOnlyOutboundAsNonVerifiedWhenNoSales() {
        Map<Integer, BigDecimal> outbound123 = Map.of(200, new BigDecimal("6"));
        List<OutboundIngredientReconcileSupport.DishGoodCostLine> lines = List.of(
                new OutboundIngredientReconcileSupport.DishGoodCostLine(50, 200, BigDecimal.ZERO,
                        BigDecimal.ZERO, BigDecimal.ZERO));

        OutboundIngredientReconcileSupport.ScopeBreakdown scope = OutboundIngredientReconcileSupport.compute(
                lines, outbound123, Map.of());

        OutboundIngredientReconcileSupport.GoodsBreakdown bd =
                OutboundIngredientReconcileSupport.goodsBreakdown(scope, 200);
        assertThat(bd.verified).isEqualByComparingTo("0");
        assertThat(bd.nonVerified).isEqualByComparingTo("6");
    }

    @Test
    void listGoodsIdsMatchingVerification_usesReconcileScopeAsSourceOfTruth() {
        Map<Integer, OutboundIngredientReconcileSupport.GoodsBreakdown> byGoods = new LinkedHashMap<>();
        byGoods.put(10, new OutboundIngredientReconcileSupport.GoodsBreakdown(
                new BigDecimal("80"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("80")));
        byGoods.put(20, new OutboundIngredientReconcileSupport.GoodsBreakdown(
                new BigDecimal("50"), BigDecimal.ZERO, new BigDecimal("50"), new BigDecimal("40"), BigDecimal.ZERO));
        OutboundIngredientReconcileSupport.ScopeBreakdown scope = new OutboundIngredientReconcileSupport.ScopeBreakdown(
                byGoods, new BigDecimal("130"), BigDecimal.ZERO, new BigDecimal("50"), new BigDecimal("40"),
                new BigDecimal("80"), 1);

        assertThat(OutboundIngredientReconcileSupport.listGoodsIdsMatchingVerification(scope, "unverified"))
                .containsExactly(10);
        assertThat(OutboundIngredientReconcileSupport.listGoodsIdsMatchingVerification(scope, "verified"))
                .containsExactly(20);
    }
}
