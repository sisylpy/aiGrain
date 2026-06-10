package com.nongxinle.ai.inventory;

import com.nongxinle.ai.composer.AnswerBoundaryNoteComposer;
import com.nongxinle.ai.context.AiResolvedTimeWindowDisplaySupport;
import com.nongxinle.ai.semantic.AiQuerySemanticParseResult;
import com.nongxinle.ai.semantic.contract.SemanticContractCompletionEngine;
import com.nongxinle.ai.semantic.matrix.DishCostAnalysisSemanticCapabilityMatrix;
import java.util.LinkedHashMap;
import java.util.Map;
import com.nongxinle.ai.context.AiResolvedQueryContext;
import com.nongxinle.ai.context.AiResolvedTimeWindow;
import com.nongxinle.ai.core.AiRunState;
import com.nongxinle.ai.dto.business.WarehouseAnswerPlan;
import com.nongxinle.ai.semantic.SemanticTimeContractCheck;
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
        // snapshot 口径为「截至某日」，不应嵌入 timeWindowLabel 的期间区间「A至B」
        assertTrue(!fields.getStockSnapshotLabel().contains("6月1日至6月1日"));
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
                .querySemanticV2InputPreview(Map.of("today", "2026-06-01"))
                .timeWindow(AiResolvedTimeWindow.builder()
                        .startDate(LocalDate.parse("2026-06-01"))
                        .endDate(LocalDate.parse("2026-06-01"))
                        .build())
                .build();

        InventoryPlanTimeFields fields = InventoryPresentationTimeSupport.buildForDishIngredientCover(state, rq);

        assertEquals(InventoryQueryTimeKind.HYBRID_SNAPSHOT_WITH_PERIOD_BASELINE, fields.getInventoryQueryTimeKind());
        assertEquals("2026-06-01", fields.getAsOfDate());
        assertEquals("当前库存（截至当前）", fields.cardSubtitle());
        assertTrue(fields.getPeriodFlowLabel().contains("最近7天"));
        assertTrue(fields.getPeriodFlowLabel().contains("5月26日"));
    }

    @Test
    void goodsSupportedDishCoverUsesCurrentSnapshotNotInheritedStatEnd() {
        AiRunState state = new AiRunState();
        state.setStatEndDate("2026-04-30");
        AiResolvedQueryContext rq = AiResolvedQueryContext.builder()
                .effectiveTimeWindowSource(SemanticTimeContractCheck.SOURCE_INHERITED_PREVIOUS)
                .querySemanticV2InputPreview(Map.of("today", "2026-06-02"))
                .timeWindow(AiResolvedTimeWindow.builder()
                        .startDate(LocalDate.parse("2026-04-01"))
                        .endDate(LocalDate.parse("2026-04-30"))
                        .build())
                .build();

        InventoryPlanTimeFields fields = InventoryPresentationTimeSupport.buildForGoodsSupportedDishCover(state, rq);

        assertEquals("当前库存（截至当前）", fields.getStockSnapshotLabel());
        assertEquals("2026-06-02", fields.getAsOfDate());
        assertTrue(fields.getPeriodFlowLabel().contains("最近7天"));
        assertTrue(fields.getPeriodFlowLabel().contains("5月27日"));
    }

    @Test
    void inventoryCoverBoundaryNote_suppressesInheritedTimeCarryover() {
        Map<String, Object> trace = new LinkedHashMap<>();
        trace.put(SemanticContractCompletionEngine.TRACE_CONTRACT_ENTRY_VALIDATED, true);
        AiQuerySemanticParseResult parse =
                AiQuerySemanticParseResult.builder()
                        .parseMissing(false)
                        .contractCompletionTrace(trace)
                        .semanticSlots(
                                AiQuerySemanticParseResult.SemanticSlotsPart.builder()
                                        .selectedContractId(
                                                DishCostAnalysisSemanticCapabilityMatrix
                                                        .CONTRACT_DISH_INGREDIENT_COVER_DAYS)
                                        .build())
                        .build();
        AiResolvedQueryContext ctx =
                AiResolvedQueryContext.builder()
                        .effectiveTimeWindowSource(SemanticTimeContractCheck.SOURCE_INHERITED_PREVIOUS)
                        .querySemanticParse(parse)
                        .timeWindow(
                                AiResolvedTimeWindow.builder()
                                        .startDate(LocalDate.parse("2026-04-01"))
                                        .endDate(LocalDate.parse("2026-04-30"))
                                        .displayText("4月1日至4月30日")
                                        .build())
                        .build();
        String raw =
                AiResolvedTimeWindowDisplaySupport.buildAnswerBoundaryNote(
                        SemanticTimeContractCheck.SOURCE_INHERITED_PREVIOUS,
                        ctx.getTimeWindow(),
                        null);
        String refined = AnswerBoundaryNoteComposer.refineUserFacingBoundaryNote(ctx, raw);
        assertNull(refined);
    }

    @Test
    void warehouseInventorySupervisionUsesCurrentSnapshotNotInheritedPeriod() {
        AiRunState state = new AiRunState();
        state.setStatStartDate("2026-04-01");
        state.setStatEndDate("2026-04-30");
        AiResolvedQueryContext rq = AiResolvedQueryContext.builder()
                .effectiveTimeWindowSource(SemanticTimeContractCheck.SOURCE_INHERITED_PREVIOUS)
                .timeWindowLabel("4月1日至4月30日")
                .querySemanticV2InputPreview(Map.of("today", "2026-06-02"))
                .timeWindow(AiResolvedTimeWindow.builder()
                        .startDate(LocalDate.parse("2026-04-01"))
                        .endDate(LocalDate.parse("2026-04-30"))
                        .build())
                .build();

        InventoryPlanTimeFields fields = InventoryPresentationTimeSupport.buildForWarehousePlan(
                WarehouseAnswerPlan.TYPE_WAREHOUSE_INVENTORY_SUPERVISION, state, rq);

        assertEquals(InventoryQueryTimeKind.CURRENT_SNAPSHOT, fields.getInventoryQueryTimeKind());
        assertEquals("2026-06-02", fields.getAsOfDate());
        assertEquals("当前库存（截至 2026-06-02）", fields.getStockSnapshotLabel());
        assertEquals(fields.getStockSnapshotLabel(), fields.getTimeLabel());
        assertNull(fields.getPeriodFlowLabel());
        assertTrue(fields.getInternalBaselineLabel().contains("最近7天"));
        assertTrue(fields.getInternalBaselineLabel().contains("5月27日"));
        assertTrue(!fields.getStockSnapshotLabel().contains("4月"));
    }

    @Test
    void warehouseNearExpiryUsesFrozenClockSnapshotDate() {
        AiRunState state = new AiRunState();
        AiResolvedQueryContext rq = AiResolvedQueryContext.builder()
                .querySemanticV2InputPreview(Map.of("today", "2026-06-02"))
                .build();

        InventoryPlanTimeFields fields =
                InventoryPresentationTimeSupport.buildForNearExpiryRiskPlan(state, rq);

        assertEquals("2026-06-02", fields.getAsOfDate());
        assertEquals("当前库存（截至 2026-06-02）", fields.getStockSnapshotLabel());
    }
}
