package com.nongxinle.ai.graph.business;

import com.nongxinle.ai.conversation.AiQuerySemanticLexicon;
import com.nongxinle.ai.dto.business.PurchaseAnswerPlan;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PurchaseAnswerPlanBuilderTest {

    @Test
    void resolvePlanType_purchaseParallelStoresWire() {
        assertEquals(PurchaseAnswerPlan.TYPE_PURCHASE_STORE_AMOUNT_RANKING,
                PurchaseAnswerPlanBuilder.resolvePlanType(
                        AiQuerySemanticLexicon.STRUCTURED_PURCHASE_STORE_AMOUNT_RANKING,
                        AiQuerySemanticLexicon.SOURCE_ALL));
    }

    @Test
    void resolvePlanType_supplierRanking() {
        assertEquals(PurchaseAnswerPlan.TYPE_PURCHASE_SUPPLIER_AMOUNT_RANKING,
                PurchaseAnswerPlanBuilder.resolvePlanType(
                        AiQuerySemanticLexicon.STRUCTURED_SUPPLIER_AMOUNT_RANKING,
                        AiQuerySemanticLexicon.SOURCE_ALL));
    }

    @Test
    void resolvePlanType_selfAmountQuery() {
        assertEquals(PurchaseAnswerPlan.TYPE_PURCHASE_SELF_OVERVIEW,
                PurchaseAnswerPlanBuilder.resolvePlanType(
                        AiQuerySemanticLexicon.STRUCTURED_PURCHASE_SOURCE_AMOUNT_QUERY,
                        AiQuerySemanticLexicon.SOURCE_SELF_PURCHASE));
    }

    @Test
    void resolvePlanType_goodsAmountVsCount_wiresOnly() {
        assertEquals(PurchaseAnswerPlan.TYPE_PURCHASE_GOODS_AMOUNT_RANKING,
                PurchaseAnswerPlanBuilder.resolvePlanType(
                        AiQuerySemanticLexicon.STRUCTURED_PURCHASE_GOODS_AMOUNT_RANKING,
                        AiQuerySemanticLexicon.SOURCE_ALL));
        assertEquals(PurchaseAnswerPlan.TYPE_PURCHASE_GOODS_COUNT_RANKING,
                PurchaseAnswerPlanBuilder.resolvePlanType(
                        AiQuerySemanticLexicon.STRUCTURED_PURCHASE_GOODS_COUNT_RANKING,
                        AiQuerySemanticLexicon.SOURCE_ALL));
    }

    @Test
    void resolvePlanType_overviewSummaryWire_staysOverviewWithoutRankingWire() {
        assertEquals(PurchaseAnswerPlan.TYPE_PURCHASE_OVERVIEW,
                PurchaseAnswerPlanBuilder.resolvePlanType(
                        AiQuerySemanticLexicon.STRUCTURED_PURCHASE_OVERVIEW_SUMMARY,
                        AiQuerySemanticLexicon.SOURCE_ALL));
    }

    @Test
    void resolvePlanType_sourceGoodsQuery_ambiguous_isOverviewNotRanking() {
        assertEquals(PurchaseAnswerPlan.TYPE_PURCHASE_OVERVIEW,
                PurchaseAnswerPlanBuilder.resolvePlanType(
                        AiQuerySemanticLexicon.STRUCTURED_PURCHASE_SOURCE_GOODS_QUERY,
                        AiQuerySemanticLexicon.SOURCE_ALL));
    }

    @Test
    void structured_stock_reduce_goods_outbound_wire_is_catalogued() {
        assertTrue(AiQuerySemanticLexicon.isStructuredStockReduceDetail(
                AiQuerySemanticLexicon.STRUCTURED_GOODS_OUTBOUND_RANKING));
        assertTrue(AiQuerySemanticLexicon.isNonOverviewStockReduceStructuredDetail(
                AiQuerySemanticLexicon.STRUCTURED_GOODS_OUTBOUND_RANKING));
        assertFalse(AiQuerySemanticLexicon.isNonOverviewStockReduceStructuredDetail(
                AiQuerySemanticLexicon.STRUCTURED_STOCK_REDUCE_OVERVIEW_SUMMARY));
    }
}
