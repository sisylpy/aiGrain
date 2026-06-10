package com.nongxinle.ai.graph.business;

import com.nongxinle.ai.conversation.AiQuerySemanticLexicon;
import com.nongxinle.ai.dto.business.PurchaseAnswerPlan;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PurchaseCheckCardSummaryProjectionTest {

    @Test
    void fromAnswerPlan_selfOverview_zerosSupplierAndAlignsTotal() {
        PurchaseAnswerPlan plan =
                PurchaseAnswerPlan.builder()
                        .planType(PurchaseAnswerPlan.TYPE_PURCHASE_SELF_OVERVIEW)
                        .purchaseSourceType(AiQuerySemanticLexicon.SOURCE_SELF_PURCHASE)
                        .summary(
                                new LinkedHashMap<>(
                                        Map.of(
                                                "totalAmount", 240.0,
                                                "selfPurchaseAmount", 240.0,
                                                "supplierPurchaseAmount", 0.0,
                                                "selfPurchaseLineCount", 2,
                                                "supplierPurchaseLineCount", 0)))
                        .build();

        Map<String, Object> projected = PurchaseCheckCardSummaryProjection.fromAnswerPlan(plan);

        assertEquals(240.0, projected.get("totalPurchaseAmount"));
        assertEquals(240.0, projected.get("selfPurchaseAmount"));
        assertEquals(0.0, projected.get("supplierPurchaseAmount"));
        assertEquals("自采", PurchaseCheckCardSummaryProjection.titleSuffix(plan));
    }

    @Test
    void fromAnswerPlan_supplierOverview_zerosSelfAndAlignsTotal() {
        PurchaseAnswerPlan plan =
                PurchaseAnswerPlan.builder()
                        .planType(PurchaseAnswerPlan.TYPE_PURCHASE_SUPPLIER_OVERVIEW)
                        .purchaseSourceType(AiQuerySemanticLexicon.SOURCE_SUPPLIER_PURCHASE)
                        .summary(
                                new LinkedHashMap<>(
                                        Map.of(
                                                "totalAmount", 108.0,
                                                "selfPurchaseAmount", 0.0,
                                                "supplierPurchaseAmount", 108.0)))
                        .build();

        Map<String, Object> projected = PurchaseCheckCardSummaryProjection.fromAnswerPlan(plan);

        assertEquals(108.0, projected.get("totalPurchaseAmount"));
        assertEquals(0.0, projected.get("selfPurchaseAmount"));
        assertEquals(108.0, projected.get("supplierPurchaseAmount"));
        assertEquals("供货商订货", PurchaseCheckCardSummaryProjection.titleSuffix(plan));
    }

    @Test
    void fromAnswerPlan_allOverview_keepsBothChannels() {
        PurchaseAnswerPlan plan =
                PurchaseAnswerPlan.builder()
                        .planType(PurchaseAnswerPlan.TYPE_PURCHASE_OVERVIEW)
                        .purchaseSourceType(AiQuerySemanticLexicon.SOURCE_ALL)
                        .summary(
                                new LinkedHashMap<>(
                                        Map.of(
                                                "totalAmount", 348.0,
                                                "selfPurchaseAmount", 240.0,
                                                "supplierPurchaseAmount", 108.0)))
                        .build();

        Map<String, Object> projected = PurchaseCheckCardSummaryProjection.fromAnswerPlan(plan);

        assertEquals(348.0, projected.get("totalPurchaseAmount"));
        assertEquals(240.0, projected.get("selfPurchaseAmount"));
        assertEquals(108.0, projected.get("supplierPurchaseAmount"));
        assertEquals("采购", PurchaseCheckCardSummaryProjection.titleSuffix(plan));
    }
}
