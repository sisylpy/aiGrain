package com.nongxinle.ai.harness.replay;

import com.nongxinle.ai.dto.business.PurchaseAnswerPlan;
import com.nongxinle.ai.dto.business.StockReduceAnswerPlan;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AiHarnessReplayContextProbesTest {

    @Test
    void resolvePurchasePlanType_storeRankingMatchesBuilder() {
        assertEquals(
                PurchaseAnswerPlan.TYPE_PURCHASE_STORE_AMOUNT_RANKING,
                AiHarnessReplayContextProbes.resolvePurchasePlanType("purchase_store_amount_ranking", null));
    }

    @Test
    void resolveStockPlanType_storeOutboundRankingMatchesBuilder() {
        assertEquals(
                StockReduceAnswerPlan.TYPE_STOCK_REDUCE_STORE_AMOUNT_RANKING,
                AiHarnessReplayContextProbes.resolveStockPlanType("store_outbound_amount_ranking"));
    }

    @Test
    void resolveStockReduceType_overviewIsAll() {
        assertEquals(
                StockReduceAnswerPlan.REDUCE_TYPE_ALL,
                AiHarnessReplayContextProbes.resolveStockReduceType(StockReduceAnswerPlan.TYPE_STOCK_REDUCE_OVERVIEW));
    }
}
