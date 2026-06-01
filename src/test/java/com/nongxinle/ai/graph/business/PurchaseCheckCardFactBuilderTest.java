package com.nongxinle.ai.graph.business;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PurchaseCheckCardFactBuilderTest {

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
    void buildPurchaseSummary_zeroWhenServiceMissing() {
        Map<String, Object> summary =
                PurchaseCheckCardFactBuilder.buildPurchaseSummary(Map.of("disId", 1), null);
        assertEquals(0.0, summary.get("totalPurchaseAmount"));
        assertEquals(0.0, summary.get("selfPurchaseAmount"));
        assertEquals(0.0, summary.get("supplierPurchaseAmount"));
    }
}
