package com.nongxinle.ai.inventory;

import com.nongxinle.ai.context.AiResolvedQueryContext;
import com.nongxinle.ai.context.AiResolvedTimeWindow;
import com.nongxinle.ai.core.AiRunState;
import com.nongxinle.ai.dto.business.WarehouseAnswerPlan;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InventoryPresentationTimeSupportTest {

    @Test
    void warehouseRankingUsesSnapshotLabelNotPeriodRange() {
        AiRunState state = new AiRunState();
        state.setStatStartDate("2026-06-01");
        state.setStatEndDate("2026-06-01");
        AiResolvedQueryContext rq = AiResolvedQueryContext.builder()
                .timeWindowLabel("6月1日至6月1日")
                .timeWindow(AiResolvedTimeWindow.builder()
                        .startDate(LocalDate.parse("2026-06-01"))
                        .endDate(LocalDate.parse("2026-06-01"))
                        .build())
                .build();

        InventoryPlanTimeFields fields = InventoryPresentationTimeSupport.buildForWarehousePlan(
                WarehouseAnswerPlan.TYPE_WAREHOUSE_GOODS_AMOUNT_RANKING_HIGH, state, rq);

        assertEquals(InventoryQueryTimeKind.CURRENT_SNAPSHOT, fields.getInventoryQueryTimeKind());
        assertEquals("当前库存（截至 2026-06-01）", fields.getStockSnapshotLabel());
        assertEquals("当前库存（截至 2026-06-01）", fields.getTimeLabel());
        assertNull(fields.getPeriodFlowLabel());
        assertTrue(fields.getStockSnapshotLabel().contains("当前库存"));
        assertTrue(!fields.getStockSnapshotLabel().contains("至"));
    }

    @Test
    void warehouseOverviewHybridSeparatesSnapshotAndPeriodBaseline() {
        AiRunState state = new AiRunState();
        state.setStatEndDate("2026-06-01");
        AiResolvedQueryContext rq = AiResolvedQueryContext.builder()
                .timeWindowLabel("6月1日至6月1日")
                .build();

        InventoryPlanTimeFields fields = InventoryPresentationTimeSupport.buildForWarehousePlan(
                WarehouseAnswerPlan.TYPE_WAREHOUSE_STOCK_OVERVIEW, state, rq);

        assertEquals(InventoryQueryTimeKind.HYBRID_SNAPSHOT_WITH_PERIOD_BASELINE, fields.getInventoryQueryTimeKind());
        assertEquals("当前库存（截至 2026-06-01）", fields.getStockSnapshotLabel());
        assertEquals("6月1日至6月1日", fields.getPeriodFlowLabel());
    }

    @Test
    void dishIngredientCoverSeparatesSnapshotAndSalesBaseline() {
        AiRunState state = new AiRunState();
        state.setStatEndDate("2026-06-01");
        AiResolvedQueryContext rq = AiResolvedQueryContext.builder()
                .effectiveTimeWindowSource("DEFAULT_MONTH_TO_DATE")
                .timeWindowLabel("6月1日至6月1日")
                .timeWindow(AiResolvedTimeWindow.builder()
                        .startDate(LocalDate.parse("2026-06-01"))
                        .endDate(LocalDate.parse("2026-06-01"))
                        .build())
                .build();

        InventoryPlanTimeFields fields = InventoryPresentationTimeSupport.buildForDishIngredientCover(state, rq);

        assertEquals(InventoryQueryTimeKind.HYBRID_SNAPSHOT_WITH_PERIOD_BASELINE, fields.getInventoryQueryTimeKind());
        assertEquals("当前库存（截至当前）", fields.cardSubtitle());
        assertTrue(fields.getPeriodFlowLabel().contains("最近7天"));
        assertTrue(fields.getPeriodFlowLabel().contains("5月26日"));
    }
}
