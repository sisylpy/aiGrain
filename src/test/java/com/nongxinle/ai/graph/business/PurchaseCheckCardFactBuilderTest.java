package com.nongxinle.ai.graph.business;

import com.nongxinle.service.GbDistributerPurchaseGoodsService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PurchaseCheckCardFactBuilderTest {

    @Mock
    private GbDistributerPurchaseGoodsService purchaseGoodsService;

    @Test
    void subtitleForMode_singleDay_vsPeriodAvg() {
        assertTrue(PurchaseCheckCardFactBuilder.subtitleForMode(true).contains("上一笔"));
        assertTrue(PurchaseCheckCardFactBuilder.subtitleForMode(false).contains("平均"));
    }

    @Test
    void emptyReasonForMode_reflectsCompareMode() {
        assertTrue(
                PurchaseCheckCardFactBuilder.emptyReasonForMode(
                                PurchaseCheckCardFactBuilder.MODE_PERIOD_AVG_VS_COMPARE)
                        .contains("对比期"));
        assertTrue(
                PurchaseCheckCardFactBuilder.emptyReasonForMode(
                                PurchaseCheckCardFactBuilder.MODE_SINGLE_DAY_VS_PREVIOUS)
                        .contains("上一笔"));
    }

    @Test
    void priceCompareDisplay_singleDay_usesPreviousPurchaseNotCalendarCompare() {
        BusinessStatusCardBuildRequest req =
                BusinessStatusCardBuildRequest.builder()
                        .startDate("2026-05-31")
                        .endDate("2026-05-31")
                        .compareLabel("昨天")
                        .build();

        assertEquals(
                "上一笔采购价",
                PurchaseCheckCardFactBuilder.priceCompareLabel(
                        PurchaseCheckCardFactBuilder.MODE_SINGLE_DAY_VS_PREVIOUS, req));
        assertEquals(
                "本次入库价相对上一笔采购价变化",
                PurchaseCheckCardFactBuilder.priceCompareDescription(
                        PurchaseCheckCardFactBuilder.MODE_SINGLE_DAY_VS_PREVIOUS, req));
    }

    @Test
    void priceCompareDisplay_multiDay_usesUnifiedCompareLabel() {
        BusinessStatusCardBuildRequest req =
                BusinessStatusCardBuildRequest.builder()
                        .startDate("2026-05-01")
                        .endDate("2026-05-31")
                        .compareLabel("上月同期")
                        .build();

        assertEquals(
                "上月同期",
                PurchaseCheckCardFactBuilder.priceCompareLabel(
                        PurchaseCheckCardFactBuilder.MODE_PERIOD_AVG_VS_COMPARE, req));
        assertTrue(
                PurchaseCheckCardFactBuilder.priceCompareDescription(
                                PurchaseCheckCardFactBuilder.MODE_PERIOD_AVG_VS_COMPARE, req)
                        .contains("上月同期"));
    }

    @Test
    void build_missingDeps_returnsEmpty() {
        BusinessStatusCardBuildRequest req =
                BusinessStatusCardBuildRequest.builder()
                        .startDate("2026-05-01")
                        .endDate("2026-05-31")
                        .compareStartDate("2026-04-01")
                        .compareEndDate("2026-04-30")
                        .build();

        PurchaseCheckCardFactBuilder.FactResult result =
                PurchaseCheckCardFactBuilder.build(new com.nongxinle.ai.core.AiRunState(), req, null, null, null);

        assertEquals(PurchaseCheckCardFactBuilder.MODE_PERIOD_AVG_VS_COMPARE, result.priceCompareMode());
        assertTrue(result.unitPriceChangedItems().isEmpty());
        assertEquals(0.0, result.purchaseSummary().get("totalPurchaseAmount"));
    }

    @Test
    void buildPurchaseSummary_usesSupplierBuySplitForSelfAndSupplier() {
        Map<String, Object> base = new HashMap<>();
        base.put("disId", 1);
        base.put("startDate", "2026-06-01");
        base.put("stopDate", "2026-06-03");
        base.put("dayuStatus", 2);

        when(purchaseGoodsService.queryGbPurchaseGoodsBuySubtotalSum(base)).thenReturn(348.0);
        when(purchaseGoodsService.queryGbPurchaseGoodsCount(any())).thenReturn(1);
        when(purchaseGoodsService.queryPurchaseGoodsSubTotal(any()))
                .thenAnswer(inv -> {
                    Map<String, Object> q = inv.getArgument(0);
                    if (Integer.valueOf(-1).equals(q.get("supplierBuy"))) {
                        return 240.0;
                    }
                    if (Integer.valueOf(1).equals(q.get("supplierBuy"))) {
                        return 108.0;
                    }
                    return 0.0;
                });

        Map<String, Object> summary = PurchaseCheckCardFactBuilder.buildPurchaseSummary(base, purchaseGoodsService);

        assertEquals(348.0, summary.get("totalPurchaseAmount"));
        assertEquals(240.0, summary.get("selfPurchaseAmount"));
        assertEquals(108.0, summary.get("supplierPurchaseAmount"));

        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(purchaseGoodsService, times(2)).queryGbPurchaseGoodsCount(captor.capture());
        Map<String, Object> selfQuery =
                captor.getAllValues().stream()
                        .filter(q -> Integer.valueOf(-1).equals(q.get("supplierBuy")))
                        .findFirst()
                        .orElseThrow();
        Map<String, Object> supplierQuery =
                captor.getAllValues().stream()
                        .filter(q -> Integer.valueOf(1).equals(q.get("supplierBuy")))
                        .findFirst()
                        .orElseThrow();
        assertEquals(-1, selfQuery.get("supplierBuy"));
        assertEquals(1, supplierQuery.get("supplierBuy"));
        assertEquals(2, supplierQuery.get("batchDayuStatus"));
    }

    @Test
    void buildPurchaseSummary_whenBaseFocusedSelf_doesNotQuerySupplierSplit() {
        Map<String, Object> base = new HashMap<>();
        base.put("disId", 1);
        base.put("startDate", "2026-06-01");
        base.put("stopDate", "2026-06-03");
        base.put("dayuStatus", 2);
        base.put("supplierBuy", -1);

        when(purchaseGoodsService.queryGbPurchaseGoodsBuySubtotalSum(base)).thenReturn(240.0);

        Map<String, Object> summary = PurchaseCheckCardFactBuilder.buildPurchaseSummary(base, purchaseGoodsService);

        assertEquals(240.0, summary.get("totalPurchaseAmount"));
        assertEquals(240.0, summary.get("selfPurchaseAmount"));
        assertEquals(0.0, summary.get("supplierPurchaseAmount"));
        verify(purchaseGoodsService, times(0)).queryGbPurchaseGoodsCount(any());
    }

    @Test
    void buildPurchaseSummary_zeroWhenServiceMissing() {
        Map<String, Object> summary =
                PurchaseCheckCardFactBuilder.buildPurchaseSummary(Map.of("disId", 1), null);
        assertEquals(0.0, summary.get("totalPurchaseAmount"));
        assertEquals(0.0, summary.get("selfPurchaseAmount"));
        assertEquals(0.0, summary.get("supplierPurchaseAmount"));
    }
}
