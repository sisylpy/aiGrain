package com.nongxinle.ai.graph.business;

import com.nongxinle.ai.core.AiRunState;
import com.nongxinle.ai.dto.business.DishProfitAnswerPlan;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class DishProfitAnswerPlanCardSupportTest {

    @Test
    void buildRankingCardPayload_projectsRowsFromFocusAndSecondary() {
        Map<String, Object> top = new LinkedHashMap<>();
        top.put("dishName", "招牌鱼");
        top.put("salesQuantity", "120");
        top.put("listPriceRevenue", "9600");
        top.put("theoryCostAmount", "2100");
        top.put("actualCostAmount", "3200");
        top.put("blendedGrossMarginRateOnListPrice", "0.66");
        top.put("dishId", "1001");

        Map<String, Object> second = new LinkedHashMap<>();
        second.put("dishName", "烩菜");
        second.put("salesQuantity", "80");
        second.put("listPriceRevenue", "4200");
        second.put("actualCostAmount", "2800");

        DishProfitAnswerPlan plan =
                DishProfitAnswerPlan.builder()
                        .planType(DishProfitAnswerPlan.TYPE_DISH_HIGHEST_ACTUAL_COST)
                        .timeLabel("上个月")
                        .scopeLabel("本店")
                        .sortKey("totalActualCostAmount123")
                        .sortDirection("DESC")
                        .focusRows(List.of(top))
                        .secondaryRows(List.of(second))
                        .build();

        AiRunState state =
                AiRunState.builder()
                        .statStartDate("2026-04-01")
                        .statEndDate("2026-04-30")
                        .build();

        Map<String, Object> card = DishProfitAnswerPlanCardSupport.buildCardPayload(plan, state);

        assertNotNull(card);
        assertEquals(DishProfitAnswerPlan.CARD_TYPE_DISH_PROFIT_RANKING, card.get("cardType"));
        assertEquals("上个月·菜品实际成本排行", card.get("title"));
        assertEquals("按实际成本排序", card.get("subtitle"));
        @SuppressWarnings("unchecked")
        Map<String, Object> payload = (Map<String, Object>) card.get("payload");
        assertNotNull(payload);
        assertEquals("OK", payload.get("status"));
        assertEquals("2026-04-01", payload.get("startDate"));
        assertEquals("2026-04-30", payload.get("endDate"));
        assertEquals("上个月", payload.get("timeLabel"));
        assertEquals("本店", payload.get("scopeLabel"));
        assertEquals(DishProfitAnswerPlan.RANKING_TYPE_ACTUAL_COST_HIGH, payload.get("rankingType"));
        assertEquals("实际成本", payload.get("metricLabel"));
        assertEquals("totalActualCostAmount123", payload.get("sortKey"));
        assertEquals("DESC", payload.get("sortDirection"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) payload.get("rows");
        assertEquals(2, rows.size());
        assertEquals("1", rows.get(0).get("rank"));
        assertEquals("招牌鱼", rows.get(0).get("dishName"));
        assertEquals("3200", rows.get(0).get("actualCostAmount"));
        assertEquals("2", rows.get(1).get("rank"));
        assertEquals("烩菜", rows.get(1).get("dishName"));
    }

    @Test
    void buildCardPayload_projectsEmptyRankingCardForNoDataPlan() {
        LinkedHashMap<String, Object> debug = new LinkedHashMap<>();
        debug.put(
                DishProfitRankingSalesEvidenceSupport.DEBUG_REQUESTED_RANKING_PLAN_TYPE,
                DishProfitAnswerPlan.TYPE_DISH_HIGHEST_ACTUAL_COST);
        debug.put(
                DishProfitRankingSalesEvidenceSupport.DEBUG_NO_DATA_REASON,
                DishProfitRankingSalesEvidenceSupport.NO_DATA_REASON_NO_DISH_SALES_FOR_PERIOD);

        DishProfitAnswerPlan plan =
                DishProfitAnswerPlan.builder()
                        .planType(DishProfitAnswerPlan.TYPE_DISH_PROFIT_RANKING_NO_DATA)
                        .timeLabel("本月")
                        .scopeLabel("本店")
                        .debug(debug)
                        .build();

        Map<String, Object> card = DishProfitAnswerPlanCardSupport.buildCardPayload(plan);

        assertNotNull(card);
        assertEquals(DishProfitAnswerPlan.CARD_TYPE_DISH_PROFIT_RANKING, card.get("cardType"));
        assertEquals("本月·菜品实际成本排行", card.get("title"));
        @SuppressWarnings("unchecked")
        Map<String, Object> payload = (Map<String, Object>) card.get("payload");
        assertNotNull(payload);
        assertEquals("EMPTY", payload.get("status"));
        assertEquals(DishProfitRankingSalesEvidenceSupport.EMPTY_RANKING_MESSAGE, payload.get("message"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) payload.get("rows");
        assertEquals(0, rows.size());
    }

    @Test
    void buildCardPayload_projectsMarginRankingCard() {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("dishName", "酸奶碗");
        row.put("blendedGrossMarginRateOnListPrice", "0.72");

        DishProfitAnswerPlan plan =
                DishProfitAnswerPlan.builder()
                        .planType(DishProfitAnswerPlan.TYPE_DISH_HIGHEST_MARGIN)
                        .timeLabel("本月")
                        .focusRows(List.of(row))
                        .build();

        Map<String, Object> card = DishProfitAnswerPlanCardSupport.buildCardPayload(plan);

        assertNotNull(card);
        assertEquals("本月·菜品毛利率排行", card.get("title"));
        @SuppressWarnings("unchecked")
        Map<String, Object> payload = (Map<String, Object>) card.get("payload");
        assertEquals(DishProfitAnswerPlan.RANKING_TYPE_MARGIN_HIGH, payload.get("rankingType"));
        assertEquals("毛利率", payload.get("metricLabel"));
    }

    @Test
    void buildCardPayload_projectsProfitAmountRankingCard() {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("dishName", "招牌鱼");
        row.put("grossProfitAmount", "6400");

        DishProfitAnswerPlan plan =
                DishProfitAnswerPlan.builder()
                        .planType(DishProfitAnswerPlan.TYPE_DISH_HIGHEST_PROFIT_AMOUNT)
                        .timeLabel("本月")
                        .sortKey("grossProfitAmount")
                        .sortDirection("DESC")
                        .focusRows(List.of(row))
                        .build();

        Map<String, Object> card = DishProfitAnswerPlanCardSupport.buildCardPayload(plan);

        assertNotNull(card);
        assertEquals("本月·菜品利润额排行", card.get("title"));
        @SuppressWarnings("unchecked")
        Map<String, Object> payload = (Map<String, Object>) card.get("payload");
        assertEquals(DishProfitAnswerPlan.RANKING_TYPE_PROFIT_AMOUNT_HIGH, payload.get("rankingType"));
        assertEquals("利润额", payload.get("metricLabel"));
        assertEquals("grossProfitAmount", payload.get("sortKey"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) payload.get("rows");
        assertEquals("6400", rows.get(0).get("grossProfitAmount"));
    }

    @Test
    void buildCardPayload_returnsNullForNonRankingPlanType() {
        DishProfitAnswerPlan plan =
                DishProfitAnswerPlan.builder()
                        .planType(DishProfitAnswerPlan.TYPE_DISH_PROFIT_RATE)
                        .focusRows(List.of(Map.of("dishName", "烩菜")))
                        .build();

        assertNull(DishProfitAnswerPlanCardSupport.buildCardPayload(plan));
    }
}
