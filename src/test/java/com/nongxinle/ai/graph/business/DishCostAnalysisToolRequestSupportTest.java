package com.nongxinle.ai.graph.business;

import com.nongxinle.ai.context.AiResolvedQueryContext;
import com.nongxinle.ai.context.AiResolvedTimeWindow;
import com.nongxinle.ai.core.AiRunState;
import com.nongxinle.ai.semantic.contract.SemanticContractCompletionEngine;
import com.nongxinle.ai.semantic.SemanticTimeContractCheck;
import com.nongxinle.ai.semantic.matrix.DishCostAnalysisSemanticCapabilityMatrix;
import com.nongxinle.ai.semantic.AiQuerySemanticParseResult;
import com.nongxinle.ai.tool.business.AiBusinessToolIds;
import com.nongxinle.service.GbAiDailyRevenueService;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

class DishCostAnalysisToolRequestSupportTest {

    private final DishCostAnalysisToolRequestSupport support =
            new DishCostAnalysisToolRequestSupport(mock(GbAiDailyRevenueService.class));

    @Test
    void coverContract_overridesToolDatesWithSalesBaselineNotInheritedTimeWindow() {
        AiRunState state = new AiRunState();
        state.setStatStartDate("2026-04-01");
        state.setStatEndDate("2026-04-30");
        AiResolvedQueryContext rq =
                AiResolvedQueryContext.builder()
                        .effectiveTimeWindowSource(SemanticTimeContractCheck.SOURCE_INHERITED_PREVIOUS)
                        .querySemanticParse(coverContractParse())
                        .timeWindow(
                                AiResolvedTimeWindow.builder()
                                        .startDate(LocalDate.of(2026, 4, 1))
                                        .endDate(LocalDate.of(2026, 4, 30))
                                        .build())
                        .build();
        state.setResolvedQueryContext(rq);

        Map<String, Object> args =
                support.buildDishCostAnalysisToolArgs(100L, 100L, 2L, "2026-04-01", "2026-04-30", state);

        LocalDate today = LocalDate.now();
        assertEquals(today.minusDays(6).toString(), args.get(AiBusinessToolIds.ARG_START_DATE));
        assertEquals(today.toString(), args.get(AiBusinessToolIds.ARG_STOP_DATE));
        assertEquals(today.toString(), args.get(AiBusinessToolIds.ARG_END_DATE));
    }

    @Test
    void nonCoverContract_keepsResolvedTimeWindowDates() {
        AiRunState state = new AiRunState();
        Map<String, Object> trace = new LinkedHashMap<>();
        trace.put(SemanticContractCompletionEngine.TRACE_CONTRACT_ENTRY_VALIDATED, true);
        AiQuerySemanticParseResult parse =
                AiQuerySemanticParseResult.builder()
                        .parseMissing(false)
                        .contractCompletionTrace(trace)
                        .semanticSlots(
                                AiQuerySemanticParseResult.SemanticSlotsPart.builder()
                                        .selectedContractId(
                                                DishCostAnalysisSemanticCapabilityMatrix.CONTRACT_DISH_COST_SINGLE)
                                        .build())
                        .build();
        AiResolvedQueryContext rq =
                AiResolvedQueryContext.builder()
                        .querySemanticParse(parse)
                        .timeWindow(
                                AiResolvedTimeWindow.builder()
                                        .startDate(LocalDate.of(2026, 4, 1))
                                        .endDate(LocalDate.of(2026, 4, 30))
                                        .build())
                        .build();
        state.setResolvedQueryContext(rq);

        Map<String, Object> args =
                support.buildDishCostAnalysisToolArgs(100L, 100L, 2L, "2026-04-01", "2026-04-30", state);

        assertEquals("2026-04-01", args.get(AiBusinessToolIds.ARG_START_DATE));
        assertEquals("2026-04-30", args.get(AiBusinessToolIds.ARG_STOP_DATE));
    }

    private static AiQuerySemanticParseResult coverContractParse() {
        Map<String, Object> trace = new LinkedHashMap<>();
        trace.put(SemanticContractCompletionEngine.TRACE_CONTRACT_ENTRY_VALIDATED, true);
        return AiQuerySemanticParseResult.builder()
                .parseMissing(false)
                .contractCompletionTrace(trace)
                .semanticSlots(
                        AiQuerySemanticParseResult.SemanticSlotsPart.builder()
                                .selectedContractId(
                                        DishCostAnalysisSemanticCapabilityMatrix.CONTRACT_DISH_INGREDIENT_COVER_DAYS)
                                .build())
                .build();
    }
}
