package com.nongxinle.ai.graph.business;

import com.nongxinle.ai.context.AiResolvedQueryContext;
import com.nongxinle.ai.context.AiResolvedTimeWindow;
import com.nongxinle.ai.core.AiRunState;
import com.nongxinle.ai.semantic.SemanticTimeContractCheck;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DishIngredientCoverSalesBaselineSupportTest {

    @Test
    void defaultBaseline_usesLast7DaysWhenTimeNotExplicit() {
        AiRunState state = new AiRunState();
        state.setStatEndDate("2026-06-01");
        AiResolvedQueryContext rq =
                AiResolvedQueryContext.builder()
                        .effectiveTimeWindowSource("DEFAULT_MONTH_TO_DATE")
                        .timeWindow(
                                AiResolvedTimeWindow.builder()
                                        .startDate(LocalDate.of(2026, 6, 1))
                                        .endDate(LocalDate.of(2026, 6, 1))
                                        .build())
                        .build();

        DishIngredientCoverSalesBaseline baseline = DishIngredientCoverSalesBaselineSupport.resolve(state, rq);

        assertEquals("2026-05-26", baseline.getStartDateIso());
        assertEquals("2026-06-01", baseline.getStopDateIso());
        assertEquals(7, baseline.getBaselineDays());
        assertEquals(
                DishIngredientCoverSalesBaseline.SOURCE_DEFAULT_LAST_7_DAYS, baseline.getBaselineSource());
        assertTrue(baseline.getDisplayLabel().contains("最近7天"));
    }

    @Test
    void explicitBaseline_usesResolvedTimeWindow() {
        AiRunState state = new AiRunState();
        state.setStatStartDate("2026-06-01");
        state.setStatEndDate("2026-06-15");
        AiResolvedQueryContext rq =
                AiResolvedQueryContext.builder()
                        .effectiveTimeWindowSource(SemanticTimeContractCheck.SOURCE_CURRENT_MESSAGE_EXPLICIT)
                        .timeWindowLabel("6月1日至6月15日")
                        .timeWindow(
                                AiResolvedTimeWindow.builder()
                                        .startDate(LocalDate.of(2026, 6, 1))
                                        .endDate(LocalDate.of(2026, 6, 15))
                                        .build())
                        .build();

        DishIngredientCoverSalesBaseline baseline = DishIngredientCoverSalesBaselineSupport.resolve(state, rq);

        assertEquals("2026-06-01", baseline.getStartDateIso());
        assertEquals("2026-06-15", baseline.getStopDateIso());
        assertEquals(15, baseline.getBaselineDays());
        assertEquals(
                DishIngredientCoverSalesBaseline.SOURCE_USER_EXPLICIT_TIME_WINDOW,
                baseline.getBaselineSource());
        assertTrue(baseline.getDisplayLabel().contains("6月1日至6月15日"));
    }

    @Test
    void inheritedPrevious_defaultBaselineAnchorsToTodayNotInheritedEnd() {
        AiRunState state = new AiRunState();
        state.setStatEndDate("2026-04-30");
        AiResolvedQueryContext rq =
                AiResolvedQueryContext.builder()
                        .effectiveTimeWindowSource(SemanticTimeContractCheck.SOURCE_INHERITED_PREVIOUS)
                        .timeWindow(
                                AiResolvedTimeWindow.builder()
                                        .startDate(LocalDate.of(2026, 4, 1))
                                        .endDate(LocalDate.of(2026, 4, 30))
                                        .build())
                        .build();

        DishIngredientCoverSalesBaseline baseline = DishIngredientCoverSalesBaselineSupport.resolve(state, rq);

        LocalDate today = LocalDate.now();
        assertEquals(today.minusDays(6).toString(), baseline.getStartDateIso());
        assertEquals(today.toString(), baseline.getStopDateIso());
        assertEquals(
                DishIngredientCoverSalesBaseline.SOURCE_DEFAULT_LAST_7_DAYS, baseline.getBaselineSource());
    }

    @Test
    void isUserExplicit_onlyCurrentMessageExplicit() {
        AiResolvedQueryContext inherited =
                AiResolvedQueryContext.builder()
                        .effectiveTimeWindowSource("INHERITED_PREVIOUS")
                        .build();
        AiResolvedQueryContext explicit =
                AiResolvedQueryContext.builder()
                        .effectiveTimeWindowSource(SemanticTimeContractCheck.SOURCE_CURRENT_MESSAGE_EXPLICIT)
                        .build();

        assertFalse(DishIngredientCoverSalesBaselineSupport.isUserExplicitSalesBaselineTime(inherited));
        assertTrue(DishIngredientCoverSalesBaselineSupport.isUserExplicitSalesBaselineTime(explicit));
    }
}
