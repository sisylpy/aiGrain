package com.nongxinle.ai.semantic.intake.grounding;

import com.nongxinle.ai.conversation.AiConversationTurnMemory;
import com.nongxinle.ai.dto.business.GoodsSupportedDishCoverAnswerPlan;
import com.nongxinle.ai.graph.business.DishIngredientCoverSalesBaseline;
import com.nongxinle.ai.semantic.AiQuerySemanticParseResult;
import com.nongxinle.ai.semantic.SemanticTimeContractCheck;
import com.nongxinle.ai.semantic.intake.SemanticIntakeResult;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CoverDaysSalesBaselineTimeSupportTest {

    private static final LocalDate ANCHOR = LocalDate.of(2026, 6, 5);

    @Test
    void reconcile_defaultsSalesBaselineWhenOnlyMislabeledTimeExplicit() {
        AiQuerySemanticParseResult sem =
                whHCoverParse(
                        AiQuerySemanticParseResult.TimePart.builder()
                                .timeType("CURRENT_SNAPSHOT")
                                .startDate(ANCHOR.toString())
                                .endDate(ANCHOR.toString())
                                .timeSource(SemanticTimeContractCheck.SOURCE_CURRENT_MESSAGE_EXPLICIT)
                                .reason("current_message_explicit_inventory_snapshot")
                                .build(),
                        AiQuerySemanticParseResult.StockSnapshotPart.builder()
                                .asOfDate(ANCHOR.toString())
                                .build(),
                        null);
        SemanticIntakeResult intake =
                SemanticIntakeResult.builder().reason("goods_supported_dish_cover").build();

        AiQuerySemanticParseResult reconciled =
                CoverDaysSalesBaselineTimeSupport.reconcileBeforeTimeContract(
                        sem, intake, null, ANCHOR);

        assertFalse(
                CoverDaysSalesBaselineTimeSupport.isExplicitSalesBaselineAction(
                        reconciled.getSalesBaselineWindow()));
        assertEquals(
                CoverDaysSalesBaselineTimeSupport.SBW_ACTION_DEFAULT,
                reconciled.getSalesBaselineWindow().getAction());
        assertEquals(
                DishIngredientCoverSalesBaseline.SOURCE_DEFAULT_LAST_7_DAYS,
                reconciled.getSalesBaselineWindow().getSource());
        assertEquals(ANCHOR.minusDays(6).toString(), reconciled.getSalesBaselineWindow().getStartDate());
        assertEquals(ANCHOR.toString(), reconciled.getSalesBaselineWindow().getEndDate());
        assertEquals(ANCHOR.toString(), reconciled.getStockSnapshot().getAsOfDate());
        assertEquals(
                SemanticTimeContractCheck.SOURCE_DEFAULT_MONTH_TO_DATE,
                reconciled.getTime().getTimeSource());
    }

    @Test
    void reconcile_defaultsOnGoodsAnchorStockFollowUpWithoutStructuredExplicitBaseline() {
        AiConversationTurnMemory previousTurn =
                AiConversationTurnMemory.builder()
                        .lastSemanticSlots(
                                AiQuerySemanticParseResult.SemanticSlotsPart.builder()
                                        .selectedContractId(GoodsSupportedDishCoverAnswerPlan.CONTRACT_ID)
                                        .structuredIntentDetailWire("goods_supported_dish_cover")
                                        .mentionedGoodsName("大米")
                                        .build())
                        .lastStartDate(ANCHOR.minusDays(6).toString())
                        .lastEndDate(ANCHOR.toString())
                        .build();
        AiQuerySemanticParseResult sem =
                whHCoverParse(
                                AiQuerySemanticParseResult.TimePart.builder()
                                        .startDate(ANCHOR.toString())
                                        .endDate(ANCHOR.toString())
                                        .timeSource(
                                                SemanticTimeContractCheck.SOURCE_CURRENT_MESSAGE_EXPLICIT)
                                        .build(),
                                AiQuerySemanticParseResult.StockSnapshotPart.builder()
                                        .asOfDate(ANCHOR.toString())
                                        .build(),
                                null)
                        .toBuilder()
                        .followUp(true)
                        .timeAction("NEW")
                        .build();
        SemanticIntakeResult intake =
                SemanticIntakeResult.builder()
                        .reason("goods_anchor_stock_follow_up;goods_supported_dish_cover")
                        .isFollowUp(true)
                        .usedPreviousContext(true)
                        .build();

        AiQuerySemanticParseResult reconciled =
                CoverDaysSalesBaselineTimeSupport.reconcileBeforeTimeContract(
                        sem, intake, previousTurn, ANCHOR);

        assertEquals(
                CoverDaysSalesBaselineTimeSupport.SBW_ACTION_DEFAULT,
                reconciled.getSalesBaselineWindow().getAction());
        assertEquals(
                DishIngredientCoverSalesBaseline.SOURCE_DEFAULT_LAST_7_DAYS,
                reconciled.getSalesBaselineWindow().getSource());
    }

    @Test
    void reconcile_preservesStructuredExplicitSalesBaseline() {
        AiQuerySemanticParseResult sem =
                whHCoverParse(
                        null,
                        null,
                        AiQuerySemanticParseResult.SalesBaselineWindowPart.builder()
                                .action(CoverDaysSalesBaselineTimeSupport.SBW_ACTION_EXPLICIT)
                                .source(DishIngredientCoverSalesBaseline.SOURCE_USER_EXPLICIT_TIME_WINDOW)
                                .startDate("2026-05-01")
                                .endDate("2026-05-31")
                                .timeType("LAST_MONTH")
                                .build());
        SemanticIntakeResult intake =
                SemanticIntakeResult.builder().reason("goods_supported_dish_cover").build();

        AiQuerySemanticParseResult reconciled =
                CoverDaysSalesBaselineTimeSupport.reconcileBeforeTimeContract(
                        sem, intake, null, LocalDate.of(2026, 6, 8));

        assertTrue(
                CoverDaysSalesBaselineTimeSupport.isExplicitSalesBaselineAction(
                        reconciled.getSalesBaselineWindow()));
        assertEquals("2026-05-01", reconciled.getSalesBaselineWindow().getStartDate());
        assertEquals("2026-05-31", reconciled.getSalesBaselineWindow().getEndDate());
        assertEquals(
                DishIngredientCoverSalesBaseline.SOURCE_USER_EXPLICIT_TIME_WINDOW,
                reconciled.getSalesBaselineWindow().getSource());
    }

    @Test
    void reconcile_intakeSalesBaselineMarkerDoesNotCreateExplicitWithoutStructuredWindow() {
        AiQuerySemanticParseResult sem =
                whHCoverParse(
                        AiQuerySemanticParseResult.TimePart.builder()
                                .timeType("CUSTOM")
                                .startDate(ANCHOR.minusDays(29).toString())
                                .endDate(ANCHOR.toString())
                                .timeSource(SemanticTimeContractCheck.SOURCE_CURRENT_MESSAGE_EXPLICIT)
                                .reason("cover_days_sales_baseline_last_30_days")
                                .build(),
                        null,
                        null);
        SemanticIntakeResult intake =
                SemanticIntakeResult.builder()
                        .reason("goods_supported_dish_cover;cover_days_sales_baseline")
                        .build();

        AiQuerySemanticParseResult reconciled =
                CoverDaysSalesBaselineTimeSupport.reconcileBeforeTimeContract(
                        sem, intake, null, ANCHOR);

        assertFalse(
                CoverDaysSalesBaselineTimeSupport.isExplicitSalesBaselineAction(
                        reconciled.getSalesBaselineWindow()));
        assertEquals(
                CoverDaysSalesBaselineTimeSupport.SBW_ACTION_DEFAULT,
                reconciled.getSalesBaselineWindow().getAction());
        assertEquals(ANCHOR.minusDays(6).toString(), reconciled.getSalesBaselineWindow().getStartDate());
    }

    @Test
    void resolveDualTimePlan_projectsDefaultBaselineForBareStockQuery() {
        AiQuerySemanticParseResult sem =
                CoverDaysSalesBaselineTimeSupport.reconcileBeforeTimeContract(
                        whHCoverParse(
                                null,
                                AiQuerySemanticParseResult.StockSnapshotPart.builder()
                                        .asOfDate(ANCHOR.toString())
                                        .build(),
                                null),
                        SemanticIntakeResult.builder()
                                .reason("goods_anchor_inventory_bundle")
                                .build(),
                        null,
                        ANCHOR);

        CoverDaysSalesBaselineTimeSupport.DualTimePlan plan =
                CoverDaysSalesBaselineTimeSupport.resolveDualTimePlan(sem, ANCHOR);

        assertEquals(ANCHOR.toString(), plan.stockAsOfDate());
        assertEquals(
                DishIngredientCoverSalesBaseline.SOURCE_DEFAULT_LAST_7_DAYS,
                plan.baseline().getBaselineSource());
        assertEquals(7, plan.baseline().getBaselineDays());
    }

    private static AiQuerySemanticParseResult whHCoverParse(
            AiQuerySemanticParseResult.TimePart timePart,
            AiQuerySemanticParseResult.StockSnapshotPart stockSnapshot,
            AiQuerySemanticParseResult.SalesBaselineWindowPart salesBaselineWindow) {
        return AiQuerySemanticParseResult.builder()
                .semanticSlots(
                        AiQuerySemanticParseResult.SemanticSlotsPart.builder()
                                .selectedContractId(GoodsSupportedDishCoverAnswerPlan.CONTRACT_ID)
                                .structuredIntentDetailWire("goods_supported_dish_cover")
                                .mentionedGoodsName("大米")
                                .build())
                .time(timePart)
                .stockSnapshot(stockSnapshot)
                .salesBaselineWindow(salesBaselineWindow)
                .timeAction("NEW")
                .build();
    }
}
