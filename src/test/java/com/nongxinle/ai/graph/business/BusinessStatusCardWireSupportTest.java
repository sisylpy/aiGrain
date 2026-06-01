package com.nongxinle.ai.graph.business;

import com.nongxinle.ai.core.AiRunState;
import com.nongxinle.ai.dto.business.DailyRevenueAnswerPlan;
import com.nongxinle.ai.dto.business.PurchaseAnswerPlan;
import com.nongxinle.ai.dto.business.StockReduceAnswerPlan;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BusinessStatusCardWireSupportTest {

    @Test
    void buildCards_fullQuartet_emitsFourCardTypes() {
        AiRunState state = new AiRunState();
        state.setStatStartDate("2026-05-01");
        state.setStatEndDate("2026-05-31");
        state.setRevenueAnswerPlan(
                DailyRevenueAnswerPlan.builder()
                        .planType(DailyRevenueAnswerPlan.TYPE_REVENUE_OVERVIEW)
                        .timeLabel("本月")
                        .summary(new LinkedHashMap<>(Map.of("totalRevenue", 1000d, "total_orders", 10d)))
                        .build());
        state.setPurchaseAnswerPlan(
                PurchaseAnswerPlan.builder()
                        .planType(PurchaseAnswerPlan.TYPE_PURCHASE_OVERVIEW)
                        .summary(new LinkedHashMap<>(Map.of("totalPurchaseAmount", 200d)))
                        .build());
        state.setStockReduceAnswerPlan(
                StockReduceAnswerPlan.builder()
                        .planType(StockReduceAnswerPlan.TYPE_STOCK_REDUCE_OVERVIEW)
                        .summary(new LinkedHashMap<>(Map.of("grandTotalFourTypes", 50d)))
                        .build());

        List<Map<String, Object>> cards =
                BusinessStatusCardWireSupport.buildCards(
                        state,
                        BusinessStatusCardProjection.FULL_QUARTET,
                        BusinessStatusCardBuildDeps.builder().build());

        assertEquals(4, cards.size());
        assertEquals(BusinessStatusCardTypes.REVENUE_REPORT_CARD, cards.get(0).get("cardType"));
        assertEquals(BusinessStatusCardTypes.PURCHASE_CHECK_CARD, cards.get(1).get("cardType"));
        assertEquals(BusinessStatusCardTypes.STOCK_RECONCILE_CARD, cards.get(2).get("cardType"));
        assertEquals(BusinessStatusCardTypes.REORDER_REMINDER_CARD, cards.get(3).get("cardType"));
        assertTrue(cards.get(0).containsKey("payload"));
        @SuppressWarnings("unchecked")
        Map<String, Object> purchasePayload = (Map<String, Object>) cards.get(1).get("payload");
        assertTrue(purchasePayload.containsKey("unitPriceChangedItems"));
        assertTrue(purchasePayload.containsKey("totalPurchaseAmount"));
        assertTrue(purchasePayload.containsKey("selfPurchaseAmount"));
        assertTrue(purchasePayload.containsKey("supplierPurchaseAmount"));
        assertFalse(purchasePayload.containsKey("priceAnomalyItems"));
        assertEquals("2026-05-01", purchasePayload.get("startDate"));
        assertEquals("2026-05-31", purchasePayload.get("endDate"));
        assertTrue(purchasePayload.containsKey("compareStartDate"));
        assertTrue(purchasePayload.containsKey("compareEndDate"));
        assertTrue(purchasePayload.containsKey("compareLabel"));
    }

    @Test
    void resolveProjection_purchasePeriodGoodsDetail_skipsPurchaseCheckCard() {
        AiRunState state = new AiRunState();
        state.setPurchaseOverviewPath(true);
        state.setPurchaseAnswerPlan(
                PurchaseAnswerPlan.builder()
                        .planType(PurchaseAnswerPlan.TYPE_PURCHASE_PERIOD_GOODS_DETAIL)
                        .timeLabel("昨天")
                        .build());

        assertEquals(BusinessStatusCardProjection.NONE, BusinessStatusCardWireSupport.resolveProjection(state));
        assertTrue(
                BusinessStatusCardWireSupport.buildCards(
                                state,
                                BusinessStatusCardWireSupport.resolveProjection(state),
                                BusinessStatusCardBuildDeps.builder().build())
                        .isEmpty());
    }

    @Test
    void isPurchasePeriodGoodsDetailMainline_contractIdOnly() {
        AiRunState state = new AiRunState();
        state.setPurchaseOverviewPath(true);
        com.nongxinle.ai.context.AiResolvedQueryContext rq =
                com.nongxinle.ai.context.AiResolvedQueryContext.builder()
                        .semanticContractValidation(
                                com.nongxinle.ai.semantic.contract.SemanticContractValidationDebug.builder()
                                        .matchedContractId("purchase.period_goods_list")
                                        .build())
                        .build();
        state.setResolvedQueryContext(rq);
        assertTrue(BusinessStatusCardWireSupport.isPurchasePeriodGoodsDetailMainline(state));
        assertEquals(BusinessStatusCardProjection.NONE, BusinessStatusCardWireSupport.resolveProjection(state));
    }

    @Test
    void resolveProjection_purchaseOverview_stillEmitsPurchaseCheckCard() {
        AiRunState state = new AiRunState();
        state.setPurchaseOverviewPath(true);
        state.setPurchaseAnswerPlan(
                PurchaseAnswerPlan.builder()
                        .planType(PurchaseAnswerPlan.TYPE_PURCHASE_OVERVIEW)
                        .summary(new LinkedHashMap<>(Map.of("totalPurchaseAmount", 200d)))
                        .build());

        assertEquals(BusinessStatusCardProjection.PURCHASE_ONLY, BusinessStatusCardWireSupport.resolveProjection(state));
        List<Map<String, Object>> cards =
                BusinessStatusCardWireSupport.buildCards(
                        state,
                        BusinessStatusCardWireSupport.resolveProjection(state),
                        BusinessStatusCardBuildDeps.builder().build());
        assertEquals(1, cards.size());
        assertEquals(BusinessStatusCardTypes.PURCHASE_CHECK_CARD, cards.get(0).get("cardType"));
    }

    @Test
    void resolveProjection_dishProfitRanking_doesNotProjectStockReconcileCard() {
        AiRunState state = new AiRunState();
        state.setDishProfitPath(true);

        assertEquals(BusinessStatusCardProjection.NONE, BusinessStatusCardWireSupport.resolveProjection(state));
        assertTrue(
                BusinessStatusCardWireSupport.buildCards(
                                state,
                                BusinessStatusCardWireSupport.resolveProjection(state),
                                BusinessStatusCardBuildDeps.builder().build())
                        .isEmpty());
    }
}
