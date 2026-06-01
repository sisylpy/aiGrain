package com.nongxinle.ai.platform;

import com.nongxinle.ai.capability.dish.DishSalesAnalysisCapabilityResult;
import com.nongxinle.ai.composer.menu.MenuOperationPortfolioExpressionSupport;
import com.nongxinle.ai.core.AiRunState;
import com.nongxinle.ai.dto.business.DishProfitAnswerPlan;
import com.nongxinle.ai.graph.business.DishProfitRankingSalesEvidenceSupport;
import com.nongxinle.ai.dto.business.DishSalesAnswerPlan;
import com.nongxinle.ai.dto.business.MenuOperationAnswerPlan;
import com.nongxinle.ai.dto.business.MenuOperationRecommendedAction;
import com.nongxinle.ai.history.dto.AiConversationMessageDTO;
import com.nongxinle.ai.tool.business.AiBusinessToolIds;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiCardPayloadWireSupportTest {

    @Test
    void refreshAllCardPayloads_projectsDishSalesRankingCardFromAnswerPlan() {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("rank", 1);
        row.put("dishName", "烩菜");
        row.put("soldPortionsTotal", "120");
        row.put("listPriceRevenue", "4200");
        row.put("foodId", "1001");

        DishSalesAnswerPlan plan =
                DishSalesAnswerPlan.builder()
                        .planType(DishSalesAnswerPlan.TYPE_DISH_SALES_COUNT_RANKING_HIGH)
                        .metricType(DishSalesAnswerPlan.METRIC_COUNT_HIGH)
                        .timeLabel("上个月")
                        .scopeLabel("汀兰餐厅")
                        .rankingRows(List.of(row))
                        .build();

        AiRunState state =
                AiRunState.builder()
                        .dishSalesAnswerPlan(plan)
                        .statStartDate("2026-04-01")
                        .statEndDate("2026-04-30")
                        .build();

        AiCardPayloadWireSupport.refreshAllCardPayloads(state);

        assertTrue(state.isCardPayloadPresent());
        assertTrue(state.isCardsPresent());
        assertEquals(DishSalesAnswerPlan.CARD_TYPE_DISH_SALES_RANKING, state.getCards().get(0).get("cardType"));
        assertEquals("上个月·菜品销量排行", state.getCards().get(0).get("title"));
        @SuppressWarnings("unchecked")
        Map<String, Object> payload = (Map<String, Object>) state.getCards().get(0).get("payload");
        assertNotNull(payload);
        assertEquals(DishSalesAnswerPlan.RANKING_TYPE_COUNT_HIGH, payload.get("rankingType"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) payload.get("rows");
        assertEquals("烩菜", rows.get(0).get("dishName"));
    }

    @Test
    void refreshAllCardPayloads_projectsDishProfitCostRankingCardFromAnswerPlan() {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("dishName", "招牌鱼");
        row.put("salesQuantity", "120");
        row.put("listPriceRevenue", "9600");
        row.put("actualCostAmount", "3200");
        row.put("theoryCostAmount", "2100");
        row.put("blendedGrossMarginRateOnListPrice", "0.66");

        DishProfitAnswerPlan plan =
                DishProfitAnswerPlan.builder()
                        .planType(DishProfitAnswerPlan.TYPE_DISH_HIGHEST_ACTUAL_COST)
                        .timeLabel("上个月")
                        .scopeLabel("本店")
                        .sortKey("totalActualCostAmount123")
                        .sortDirection("DESC")
                        .focusRows(List.of(row))
                        .build();

        AiRunState state =
                AiRunState.builder()
                        .dishProfitAnswerPlan(plan)
                        .statStartDate("2026-04-01")
                        .statEndDate("2026-04-30")
                        .build();

        AiCardPayloadWireSupport.refreshAllCardPayloads(state);

        assertTrue(state.isCardPayloadPresent());
        assertTrue(state.isCardsPresent());
        assertEquals(
                DishProfitAnswerPlan.CARD_TYPE_DISH_PROFIT_RANKING,
                state.getCards().get(0).get("cardType"));
        assertEquals("上个月·菜品实际成本排行", state.getCards().get(0).get("title"));
        @SuppressWarnings("unchecked")
        Map<String, Object> payload = (Map<String, Object>) state.getCards().get(0).get("payload");
        assertNotNull(payload);
        assertEquals("OK", payload.get("status"));
        assertEquals(DishProfitAnswerPlan.RANKING_TYPE_ACTUAL_COST_HIGH, payload.get("rankingType"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) payload.get("rows");
        assertEquals("招牌鱼", rows.get(0).get("dishName"));
    }

    @Test
    void refreshAllCardPayloads_projectsEmptyDishProfitRankingCardFromNoDataPlan() {
        LinkedHashMap<String, Object> debug = new LinkedHashMap<>();
        debug.put(
                DishProfitRankingSalesEvidenceSupport.DEBUG_REQUESTED_RANKING_PLAN_TYPE,
                DishProfitAnswerPlan.TYPE_DISH_HIGHEST_ACTUAL_COST);

        DishProfitAnswerPlan plan =
                DishProfitAnswerPlan.builder()
                        .planType(DishProfitAnswerPlan.TYPE_DISH_PROFIT_RANKING_NO_DATA)
                        .timeLabel("本月")
                        .scopeLabel("本店")
                        .debug(debug)
                        .build();

        AiRunState state =
                AiRunState.builder()
                        .dishProfitAnswerPlan(plan)
                        .statStartDate("2026-05-01")
                        .statEndDate("2026-05-31")
                        .build();

        AiCardPayloadWireSupport.refreshAllCardPayloads(state);

        assertTrue(state.isCardPayloadPresent());
        assertTrue(state.isCardsPresent());
        assertEquals(
                DishProfitAnswerPlan.CARD_TYPE_DISH_PROFIT_RANKING,
                state.getCards().get(0).get("cardType"));
        @SuppressWarnings("unchecked")
        Map<String, Object> payload = (Map<String, Object>) state.getCards().get(0).get("payload");
        assertNotNull(payload);
        assertEquals("EMPTY", payload.get("status"));
        assertEquals(DishProfitRankingSalesEvidenceSupport.EMPTY_RANKING_MESSAGE, payload.get("message"));
    }

    @Test
    void refreshAllCardPayloads_projectsDishSalesCardFromAnswerPlanWhenToolHasNoCard() {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("dishName", "烩菜");
        row.put("soldPortionsTotal", "120");
        row.put("listPriceRevenue", "4200");
        row.put("foodId", "1001");
        row.put("ranking", 1);

        DishSalesAnswerPlan plan =
                DishSalesAnswerPlan.builder()
                        .planType(DishSalesAnswerPlan.TYPE_DISH_SALES_SINGLE_DISH)
                        .metricType(DishSalesAnswerPlan.METRIC_SINGLE_DISH)
                        .timeLabel("本月")
                        .scopeLabel("本店")
                        .rankingRows(List.of(row))
                        .build();

        AiRunState state = AiRunState.builder()
                .dishSalesAnswerPlan(plan)
                .build();

        AiCardPayloadWireSupport.refreshAllCardPayloads(state);

        assertTrue(state.isCardPayloadPresent());
        assertTrue(state.isCardsPresent());
        assertEquals(DishSalesAnswerPlan.CARD_TYPE_DISH_SALES, state.getCards().get(0).get("cardType"));
        assertEquals("菜品销售", state.getCards().get(0).get("title"));
        @SuppressWarnings("unchecked")
        Map<String, Object> payload = (Map<String, Object>) state.getCards().get(0).get("payload");
        assertNotNull(payload);
        assertEquals("烩菜", payload.get("dishName"));
        assertEquals("35", payload.get("salesUnitPrice"));
        @SuppressWarnings("unchecked")
        Map<String, Object> compatData = (Map<String, Object>) state.getCardPayload().get("data");
        assertEquals("烩菜", compatData.get("dishName"));
    }

    @Test
    void refreshAllCardPayloads_buildsCardFromSalesToolEnvelopeWhenNestedCardMissing() {
        Map<String, Object> toolData = new LinkedHashMap<>();
        toolData.put("status", "SUCCESS");
        toolData.put("dishName", "烩菜");
        toolData.put("salesPortions", "120");
        toolData.put("salesAmount", "4200");
        Map<String, Object> toolEnvelope = new LinkedHashMap<>();
        toolEnvelope.put("data", toolData);

        AiRunState state = AiRunState.builder().build();
        state.getToolResults().put(AiBusinessToolIds.DISH_SALES_ANALYSIS_CARD, toolEnvelope);

        AiCardPayloadWireSupport.refreshAllCardPayloads(state);

        assertTrue(state.isCardPayloadPresent());
        @SuppressWarnings("unchecked")
        Map<String, Object> payload =
                (Map<String, Object>) state.getCards().get(0).get("payload");
        assertEquals("烩菜", payload.get("dishName"));
    }

    @Test
    void refreshAllCardPayloads_keepsValidToolCardWithoutAnswerPlanOverwrite() {
        Map<String, Object> cardData = new LinkedHashMap<>();
        cardData.put("dishName", "烩菜");
        cardData.put("soldPortionsTotal", "99");
        Map<String, Object> card = new LinkedHashMap<>();
        card.put("cardType", DishSalesAnalysisCapabilityResult.CARD_TYPE);
        card.put("data", cardData);

        Map<String, Object> toolData = new LinkedHashMap<>();
        toolData.put("status", "SUCCESS");
        toolData.put("dishName", "烩菜");
        toolData.put("cardPayload", card);
        Map<String, Object> toolEnvelope = new LinkedHashMap<>();
        toolEnvelope.put("data", toolData);

        AiRunState state = AiRunState.builder().build();
        state.getToolResults().put(AiBusinessToolIds.DISH_SALES_ANALYSIS_CARD, toolEnvelope);

        AiCardPayloadWireSupport.refreshAllCardPayloads(state);

        @SuppressWarnings("unchecked")
        Map<String, Object> payload =
                (Map<String, Object>) state.getCards().get(0).get("payload");
        assertEquals("99", payload.get("soldPortionsTotal"));
    }

    @Test
    void enrichHarnessSummaryWithCardFields_mirrorsStateCards() {
        Map<String, Object> card = new LinkedHashMap<>();
        card.put("cardType", DishSalesAnswerPlan.CARD_TYPE_DISH_SALES);
        card.put("title", "菜品销售");
        card.put("payload", Map.of("dishName", "烩菜"));
        Map<String, Object> compat = new LinkedHashMap<>();
        compat.put("cardType", DishSalesAnswerPlan.CARD_TYPE_DISH_SALES);
        compat.put("data", Map.of("dishName", "烩菜"));
        AiRunState state = AiRunState.builder().cardPayload(compat).cards(List.of(card)).build();

        LinkedHashMap<String, Object> summary = new LinkedHashMap<>();
        AiCardPayloadWireSupport.enrichHarnessSummaryWithCardFields(summary, state);

        assertTrue((Boolean) summary.get("cardPayloadPresent"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> cards = (List<Map<String, Object>>) summary.get("cards");
        assertEquals(DishSalesAnswerPlan.CARD_TYPE_DISH_SALES, cards.get(0).get("cardType"));
    }

    @Test
    void refreshAllCardPayloads_projectsMenuPortfolioQuadrantCardFromMenuOperationAnswerPlan() {
        MenuOperationAnswerPlan.MenuPortfolioCategory star =
                MenuOperationAnswerPlan.MenuPortfolioCategory.builder()
                        .categoryCode(MenuOperationAnswerPlan.CATEGORY_STAR)
                        .categoryName("明星")
                        .count(1)
                        .build();
        MenuOperationAnswerPlan.MenuPortfolioClassification portfolio =
                MenuOperationAnswerPlan.MenuPortfolioClassification.builder()
                        .totalDishCount(4)
                        .salesHighThreshold("243")
                        .profitHighThreshold("9440.76")
                        .thresholdMethod("median")
                        .salesMetricName("soldPortionsTotal")
                        .profitMetricName("actualProfitAmount")
                        .categories(List.of(star, star, star, star))
                        .build();
        MenuOperationAnswerPlan plan =
                MenuOperationAnswerPlan.builder()
                        .planType(MenuOperationAnswerPlan.TYPE_MENU_OPERATION_OVERVIEW)
                        .menuPortfolioClassification(portfolio)
                        .displayCards(
                                List.of(
                                        MenuOperationAnswerPlan.MenuOperationDisplayCard.builder()
                                                .cardType(MenuOperationAnswerPlan.CARD_TYPE_MENU_PORTFOLIO_QUADRANT)
                                                .title("菜单结构四象限")
                                                .subtitle("本轮菜单内相对分层（销量与实际利润中位数阈值，非绝对行业标准）")
                                                .chartType(MenuOperationAnswerPlan.CHART_TYPE_PIE)
                                                .dataRef(MenuOperationAnswerPlan.DATA_REF_MENU_PORTFOLIO_CLASSIFICATION)
                                                .build()))
                        .build();

        AiRunState state = AiRunState.builder().menuOperationAnswerPlan(plan).build();

        AiCardPayloadWireSupport.refreshAllCardPayloads(state);

        assertTrue(state.isCardsPresent());
        assertEquals(1, state.getCards().size());
        Map<String, Object> unified = state.getCards().get(0);
        assertEquals(MenuOperationAnswerPlan.CARD_TYPE_MENU_PORTFOLIO_QUADRANT, unified.get("cardType"));
        assertEquals("菜单结构四象限", unified.get("title"));
        assertEquals(
                MenuOperationPortfolioExpressionSupport.portfolioCardSubtitle(),
                unified.get("subtitle"));
        assertEquals("PIE", unified.get("chartType"));
        @SuppressWarnings("unchecked")
        Map<String, Object> payload = (Map<String, Object>) unified.get("payload");
        assertNotNull(payload);
        assertTrue(!payload.containsKey("salesHighThreshold"));
        assertTrue(!payload.containsKey("profitHighThreshold"));
        assertTrue(!payload.containsKey("thresholdMethod"));
        assertTrue(!payload.containsKey("salesMetricName"));
        assertTrue(!payload.containsKey("profitMetricName"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> categories = (List<Map<String, Object>>) payload.get("categories");
        assertEquals(4, categories.size());
        @SuppressWarnings("unchecked")
        Map<String, Object> source = (Map<String, Object>) unified.get("source");
        assertEquals("menuOperationAnswerPlan", source.get("answerPlan"));
        assertEquals("menuPortfolioClassification", source.get("dataRef"));
    }

    @Test
    void refreshAllCardPayloads_projectsHighSalesLowMarginCardFromMenuOperationAnswerPlan() {
        Map<String, Object> riskRow = new LinkedHashMap<>();
        riskRow.put("foodId", "1001");
        riskRow.put("dishName", "测试菜");
        riskRow.put("soldPortionsTotal", "120");
        riskRow.put("listPriceRevenue", "3600");
        riskRow.put("actualCostTotalAmount123", "3000");
        riskRow.put("actualProfitAmount", "600");
        riskRow.put("blendedGrossMarginRateOnListPrice", "16.67%");
        riskRow.put("riskReason", "销量靠前但综合毛利率偏低，利润效率待提升，建议降本复核");

        MenuOperationAnswerPlan plan =
                MenuOperationAnswerPlan.builder()
                        .planType(MenuOperationAnswerPlan.TYPE_MENU_DISH_HIGH_SALES_LOW_PROFIT)
                        .riskDishes(List.of(riskRow))
                        .recommendedActions(
                                List.of(
                                        MenuOperationRecommendedAction.builder()
                                                .actionCode(MenuOperationRecommendedAction.REDUCE_COST)
                                                .targetDishIds(List.of("1001"))
                                                .evidenceRefIds(List.of("ev-1"))
                                                .build()))
                        .displayCards(
                                List.of(
                                        MenuOperationAnswerPlan.MenuOperationDisplayCard.builder()
                                                .cardType(MenuOperationAnswerPlan.CARD_TYPE_MENU_HIGH_SALES_LOW_MARGIN)
                                                .title("畅销低利菜")
                                                .chartType(MenuOperationAnswerPlan.CHART_TYPE_TABLE)
                                                .dataRef(MenuOperationAnswerPlan.DATA_REF_RISK_DISHES)
                                                .build()))
                        .build();

        AiRunState state = AiRunState.builder().menuOperationAnswerPlan(plan).build();
        AiCardPayloadWireSupport.refreshAllCardPayloads(state);

        assertEquals(2, state.getCards().size());
        Map<String, Object> unified = state.getCards().get(0);
        assertEquals(MenuOperationAnswerPlan.CARD_TYPE_MENU_HIGH_SALES_LOW_MARGIN, unified.get("cardType"));
        assertEquals("TABLE", unified.get("chartType"));
        @SuppressWarnings("unchecked")
        Map<String, Object> payload = (Map<String, Object>) unified.get("payload");
        assertEquals(1, payload.get("totalRiskDishCount"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> dishes = (List<Map<String, Object>>) payload.get("dishes");
        assertEquals("测试菜", dishes.get(0).get("dishName"));
        assertEquals("压降成本", dishes.get(0).get("recommendedAction"));
        assertEquals("ev-1", dishes.get(0).get("evidenceRefId"));

        Map<String, Object> actionCard = state.getCards().get(1);
        assertEquals(MenuOperationAnswerPlan.CARD_TYPE_MENU_ACTION_RECOMMENDATION, actionCard.get("cardType"));
        assertEquals("LIST", actionCard.get("chartType"));
        @SuppressWarnings("unchecked")
        Map<String, Object> actionPayload = (Map<String, Object>) actionCard.get("payload");
        assertEquals("ACTIVE", actionPayload.get("status"));
        assertEquals(1, actionPayload.get("totalActionCount"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> actions = (List<Map<String, Object>>) actionPayload.get("actions");
        assertEquals("测试菜", actions.get(0).get("dishName"));
        assertEquals("压降成本", actions.get(0).get("actionName"));
        assertEquals("COST_REVIEW", actions.get(0).get("actionType"));
        assertEquals("ev-1", actions.get(0).get("evidenceRefId"));
    }

    @Test
    void refreshAllCardPayloads_highSalesLowMarginCardEmptyState() {
        MenuOperationAnswerPlan plan =
                MenuOperationAnswerPlan.builder()
                        .planType(MenuOperationAnswerPlan.TYPE_MENU_DISH_HIGH_SALES_LOW_PROFIT)
                        .riskDishes(List.of())
                        .build();

        AiRunState state = AiRunState.builder().menuOperationAnswerPlan(plan).build();
        AiCardPayloadWireSupport.refreshAllCardPayloads(state);

        assertEquals(1, state.getCards().size());
        @SuppressWarnings("unchecked")
        Map<String, Object> payload = (Map<String, Object>) state.getCards().get(0).get("payload");
        assertEquals(0, payload.get("totalRiskDishCount"));
        assertEquals("EMPTY", payload.get("status"));
        assertEquals("本期未发现明显畅销低利菜。", payload.get("summary"));
    }

    @Test
    void refreshAllCardPayloads_overviewPrimaryWithSecondaryActionCard() {
        MenuOperationAnswerPlan.MenuPortfolioClassification portfolio =
                MenuOperationAnswerPlan.MenuPortfolioClassification.builder()
                        .totalDishCount(1)
                        .categories(List.of())
                        .build();
        MenuOperationAnswerPlan plan =
                MenuOperationAnswerPlan.builder()
                        .planType(MenuOperationAnswerPlan.TYPE_MENU_OPERATION_OVERVIEW)
                        .menuPortfolioClassification(portfolio)
                        .recommendedActions(
                                List.of(
                                        MenuOperationRecommendedAction.builder()
                                                .actionCode(MenuOperationRecommendedAction.KEEP_AND_PROMOTE)
                                                .priority(1)
                                                .targetDishIds(List.of("2001"))
                                                .evidenceRefIds(List.of("ev-2"))
                                                .build()))
                        .focusDishes(
                                List.of(
                                        Map.of(
                                                "foodId",
                                                "2001",
                                                "dishName",
                                                "明星菜",
                                                "soldPortionsTotal",
                                                "80",
                                                "listPriceRevenue",
                                                "2400",
                                                "actualProfitAmount",
                                                "900",
                                                "blendedGrossMarginRateOnListPrice",
                                                "37.50%")))
                        .build();

        AiRunState state = AiRunState.builder().menuOperationAnswerPlan(plan).build();
        AiCardPayloadWireSupport.refreshAllCardPayloads(state);

        assertEquals(2, state.getCards().size());
        assertEquals(
                MenuOperationAnswerPlan.CARD_TYPE_MENU_PORTFOLIO_QUADRANT,
                state.getCards().get(0).get("cardType"));
        assertEquals(
                MenuOperationAnswerPlan.CARD_TYPE_MENU_ACTION_RECOMMENDATION,
                state.getCards().get(1).get("cardType"));
    }

    @Test
    void refreshAllCardPayloads_actionPrimaryWhenDedicatedPlanType() {
        MenuOperationAnswerPlan plan =
                MenuOperationAnswerPlan.builder()
                        .planType(MenuOperationAnswerPlan.TYPE_MENU_ACTION_RECOMMENDATION)
                        .recommendedActions(List.of())
                        .build();

        AiRunState state = AiRunState.builder().menuOperationAnswerPlan(plan).build();
        AiCardPayloadWireSupport.refreshAllCardPayloads(state);

        assertEquals(1, state.getCards().size());
        assertEquals(
                MenuOperationAnswerPlan.CARD_TYPE_MENU_ACTION_RECOMMENDATION,
                state.getCards().get(0).get("cardType"));
        @SuppressWarnings("unchecked")
        Map<String, Object> payload = (Map<String, Object>) state.getCards().get(0).get("payload");
        assertEquals("EMPTY", payload.get("status"));
    }

    @Test
    void refreshAllCardPayloads_menuOptimizationCardMetadataSurvivesSecondRefresh() {
        MenuOperationAnswerPlan.MenuOptimizationPlan optimization =
                MenuOperationAnswerPlan.MenuOptimizationPlan.builder()
                        .optimizationSummary("本月菜单优化重点是先复核引流菜。")
                        .build();
        MenuOperationAnswerPlan plan =
                MenuOperationAnswerPlan.builder()
                        .planType(MenuOperationAnswerPlan.TYPE_MENU_ACTION_RECOMMENDATION)
                        .menuOptimizationPlan(optimization)
                        .displayCards(
                                List.of(
                                        MenuOperationAnswerPlan.MenuOperationDisplayCard.builder()
                                                .cardType(
                                                        MenuOperationAnswerPlan
                                                                .CARD_TYPE_MENU_ACTION_RECOMMENDATION)
                                                .title("菜单优化方案")
                                                .chartType(MenuOperationAnswerPlan.CHART_TYPE_PLAN)
                                                .dataRef(
                                                        MenuOperationAnswerPlan
                                                                .DATA_REF_MENU_OPTIMIZATION_PLAN)
                                                .build()))
                        .build();

        AiRunState state = AiRunState.builder().menuOperationAnswerPlan(plan).build();
        AiCardPayloadWireSupport.refreshAllCardPayloads(state);
        AiCardPayloadWireSupport.refreshAllCardPayloads(state);

        Map<String, Object> card = state.getCards().get(0);
        assertEquals("菜单优化方案", card.get("title"));
        assertEquals("PLAN", card.get("chartType"));
        @SuppressWarnings("unchecked")
        Map<String, Object> source = (Map<String, Object>) card.get("source");
        assertEquals("menuOperationAnswerPlan", source.get("answerPlan"));
        assertEquals("menuOptimizationPlan", source.get("dataRef"));
        @SuppressWarnings("unchecked")
        Map<String, Object> payload = (Map<String, Object>) card.get("payload");
        assertTrue(payload.get("optimizationSummary").toString().contains("引流菜"));
    }

    @Test
    void cardsJsonPersistence_roundTripAndDeprecatedCompat() {
        Map<String, Object> card = new LinkedHashMap<>();
        card.put("cardType", MenuOperationAnswerPlan.CARD_TYPE_MENU_PORTFOLIO_QUADRANT);
        card.put("title", "菜单结构四象限");
        card.put("payload", Map.of("categories", List.of(Map.of("categoryCode", "STAR"))));
        List<Map<String, Object>> cards = List.of(card);

        String json = AiCardPayloadWireSupport.serializeCardsForPersistence(cards);
        assertNotNull(json);

        List<Map<String, Object>> parsed = AiCardPayloadWireSupport.parseCardsFromPersistence(json);
        assertNotNull(parsed);
        assertEquals(MenuOperationAnswerPlan.CARD_TYPE_MENU_PORTFOLIO_QUADRANT, parsed.get(0).get("cardType"));

        AiConversationMessageDTO dto = new AiConversationMessageDTO();
        AiCardPayloadWireSupport.hydrateMessageCardsFromPersistence(dto, json);
        assertEquals(1, dto.getCards().size());
        assertEquals(
                MenuOperationAnswerPlan.CARD_TYPE_MENU_PORTFOLIO_QUADRANT,
                dto.getCardPayload().get("cardType"));
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) dto.getCardPayload().get("data");
        assertNotNull(data.get("categories"));
    }
}
