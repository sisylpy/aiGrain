package com.nongxinle.ai.graph.business;

import com.nongxinle.ai.conversation.AiQuerySemanticLexicon;
import com.nongxinle.ai.context.AiResolvedQueryContext;
import com.nongxinle.ai.context.AiResolvedQueryIntent;
import com.nongxinle.ai.core.AiRunState;
import com.nongxinle.ai.dto.business.MenuOperationAnswerPlan;
import com.nongxinle.ai.semantic.AiQuerySemanticParseResult;
import com.nongxinle.ai.semantic.contract.SemanticContractCompletionEngine;
import com.nongxinle.ai.tool.business.AiBusinessToolIds;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class MenuOperationAnswerPlanBuilderPortfolioGateTest {

    @Test
    void overview_skipsQuadrantWhenNoSalesEvidence() {
        AiRunState state = menuOperationStateWithToolData(zeroSalesToolData(4));

        MenuOperationAnswerPlanBuilder.attachIfApplicable(state);

        MenuOperationAnswerPlan plan = state.getMenuOperationAnswerPlan();
        assertThat(plan).isNotNull();
        assertThat(plan.getMenuPortfolioClassification()).isNull();
        assertThat(plan.getDebug().get("menuPortfolioSalesEvidenceAvailable")).isEqualTo(false);
        assertThat(plan.getDebug().get("menuPortfolioNoDataReason"))
                .isEqualTo(MenuPortfolioSalesEvidenceSupport.NO_DATA_REASON_NO_DISH_SALES_FOR_PERIOD);
        assertThat(plan.getKnownGaps()).contains(MenuPortfolioSalesEvidenceSupport.KNOWN_GAP_NO_SALES);
        assertThat(plan.getSummaryFacts().get("menuPortfolioNoDataReason"))
                .isEqualTo(MenuPortfolioSalesEvidenceSupport.NO_DATA_REASON_NO_DISH_SALES_FOR_PERIOD);
        assertThat(plan.getSummaryFacts().get("soldDishCount")).isEqualTo(0);

        List<Map<String, Object>> cards = MenuOperationAnswerPlanCardSupport.buildRunCards(plan);
        assertThat(cards).hasSize(1);
        assertThat(cards.get(0).get("cardType"))
                .isEqualTo(MenuOperationAnswerPlan.CARD_TYPE_MENU_PORTFOLIO_QUADRANT);
        @SuppressWarnings("unchecked")
        Map<String, Object> payload = (Map<String, Object>) cards.get(0).get("payload");
        assertThat(payload.get("status")).isEqualTo("EMPTY");
        assertThat(payload.get("categories")).isEqualTo(List.of());
    }

    @Test
    void overview_buildsQuadrantWhenSalesPresent() {
        AiRunState state =
                menuOperationStateWithToolData(
                        toolDataWithSales(
                                List.of(
                                        dishRow("1", "明星A", "100", "3000", "2000"),
                                        dishRow("2", "引流B", "80", "1600", "1400"),
                                        dishRow("3", "潜力C", "20", "600", "400"),
                                        dishRow("4", "观察D", "5", "100", "90"))));

        MenuOperationAnswerPlanBuilder.attachIfApplicable(state);

        MenuOperationAnswerPlan plan = state.getMenuOperationAnswerPlan();
        assertThat(plan).isNotNull();
        assertThat(plan.getDebug().get("menuPortfolioSalesEvidenceAvailable")).isEqualTo(true);
        assertThat(plan.getMenuPortfolioClassification()).isNotNull();
        assertThat(plan.getMenuPortfolioClassification().getTotalDishCount()).isEqualTo(4);
        assertThat(plan.getMenuPortfolioClassification().getCategories()).isNotEmpty();

        List<Map<String, Object>> cards = MenuOperationAnswerPlanCardSupport.buildRunCards(plan);
        assertThat(cards).hasSize(1);
        @SuppressWarnings("unchecked")
        Map<String, Object> payload = (Map<String, Object>) cards.get(0).get("payload");
        assertThat(payload.get("status")).isNull();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> categories = (List<Map<String, Object>>) payload.get("categories");
        assertThat(categories).isNotEmpty();
    }

    private static AiRunState menuOperationStateWithToolData(Map<String, Object> toolData) {
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("success", true);
        envelope.put("data", toolData);

        AiQuerySemanticParseResult sem =
                AiQuerySemanticParseResult.builder()
                        .semanticSlots(
                                AiQuerySemanticParseResult.SemanticSlotsPart.builder()
                                        .selectedContractId("menu_operation.overview")
                                        .structuredIntentDetailWire(
                                                AiQuerySemanticLexicon.STRUCTURED_MENU_OPERATION_OVERVIEW)
                                        .answerPlanType(MenuOperationAnswerPlan.TYPE_MENU_OPERATION_OVERVIEW)
                                        .build())
                        .contractCompletionTrace(
                                Map.of(
                                        SemanticContractCompletionEngine.TRACE_CONTRACT_ENTRY_VALIDATED,
                                        true))
                        .build();

        AiResolvedQueryContext rq =
                AiResolvedQueryContext.builder()
                        .querySemanticParse(sem)
                        .effectivePathCode(AiResolvedQueryIntent.PATH_MENU_OPERATION)
                        .build();

        Map<String, Object> toolResults = new LinkedHashMap<>();
        toolResults.put(AiBusinessToolIds.DISH_PROFIT_ANALYSIS, envelope);

        return AiRunState.builder()
                .runId(1L)
                .menuOperationPath(true)
                .statStartDate("2026-06-01")
                .statEndDate("2026-06-01")
                .resolvedQueryContext(rq)
                .toolResults(toolResults)
                .build();
    }

    private static Map<String, Object> zeroSalesToolData(int dishCount) {
        List<Map<String, Object>> rows = new java.util.ArrayList<>();
        for (int i = 1; i <= dishCount; i++) {
            rows.add(dishRow(String.valueOf(i), "菜" + i, "0", "0", "0"));
        }
        return toolDataWithSales(rows);
    }

    private static Map<String, Object> toolDataWithSales(List<Map<String, Object>> dishRows) {
        java.math.BigDecimal totalRev = java.math.BigDecimal.ZERO;
        for (Map<String, Object> row : dishRows) {
            totalRev =
                    totalRev.add(
                            MenuPortfolioSalesEvidenceSupport.parseDecimal(row.get("listPriceRevenue")));
        }
        Map<String, Object> insight = new LinkedHashMap<>();
        insight.put("totalListPriceRevenue", totalRev.toPlainString());
        insight.put("comprehensiveGrossMarginRateOnListPrice", "35");

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("dishRows", dishRows);
        data.put("businessInsightSummary", insight);
        return data;
    }

    private static Map<String, Object> dishRow(
            String foodId, String name, String sold, String revenue, String cost123) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("foodId", foodId);
        row.put("dishName", name);
        row.put("soldPortionsTotal", sold);
        row.put("listPriceRevenue", revenue);
        row.put("actualCostTotalAmount123", cost123);
        row.put("blendedGrossMarginRateOnListPrice", "0");
        return row;
    }
}
