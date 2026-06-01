package com.nongxinle.ai.graph.business;

import com.nongxinle.ai.core.AiRunState;
import com.nongxinle.ai.dto.business.DishSalesAnswerPlan;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class DishSalesAnswerPlanCardSupportTest {

    @Test
    void buildSingleDishCardPayload_projectsMetricsAndDerivesUnitPrice() {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("dishName", "烩菜");
        row.put("soldPortionsTotal", "120");
        row.put("listPriceRevenue", "4200");
        row.put("foodId", "1001");
        row.put("rank", 3);

        DishSalesAnswerPlan plan =
                DishSalesAnswerPlan.builder()
                        .planType(DishSalesAnswerPlan.TYPE_DISH_SALES_SINGLE_DISH)
                        .metricType(DishSalesAnswerPlan.METRIC_SINGLE_DISH)
                        .timeLabel("本月")
                        .scopeLabel("本店")
                        .rankingRows(List.of(row))
                        .build();

        Map<String, Object> card = DishSalesAnswerPlanCardSupport.buildCardPayload(plan);

        assertNotNull(card);
        assertEquals(DishSalesAnswerPlan.CARD_TYPE_DISH_SALES, card.get("cardType"));
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) card.get("data");
        assertNotNull(data);
        assertEquals("烩菜", data.get("dishName"));
        assertEquals("120", data.get("soldPortionsTotal"));
        assertEquals("4200", data.get("salesAmount"));
        assertEquals("35", data.get("salesUnitPrice"));
        assertEquals("本月", data.get("timeLabel"));
        assertEquals("本店", data.get("scopeLabel"));
    }

    @Test
    void buildCardPayload_projectsRankingCardForCountRankingHigh() {
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
                        .summary("当前范围内销量最高的菜品是 烩菜，销量 120 份，销售额 4200。")
                        .rankingRows(List.of(row))
                        .build();

        AiRunState state =
                AiRunState.builder()
                        .statStartDate("2026-04-01")
                        .statEndDate("2026-04-30")
                        .build();

        Map<String, Object> card = DishSalesAnswerPlanCardSupport.buildCardPayload(plan, state);

        assertNotNull(card);
        assertEquals(DishSalesAnswerPlan.CARD_TYPE_DISH_SALES_RANKING, card.get("cardType"));
        assertEquals("上个月·菜品销量排行", card.get("title"));
        assertEquals("按销量排序", card.get("subtitle"));
        @SuppressWarnings("unchecked")
        Map<String, Object> payload = (Map<String, Object>) card.get("payload");
        assertNotNull(payload);
        assertEquals("2026-04-01", payload.get("startDate"));
        assertEquals("2026-04-30", payload.get("endDate"));
        assertEquals("上个月", payload.get("timeLabel"));
        assertEquals(DishSalesAnswerPlan.RANKING_TYPE_COUNT_HIGH, payload.get("rankingType"));
        assertEquals("销量", payload.get("metricLabel"));
        assertEquals("汀兰餐厅", payload.get("scopeLabel"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) payload.get("rows");
        assertEquals(1, rows.size());
        assertEquals("1", rows.get(0).get("rank"));
        assertEquals("烩菜", rows.get(0).get("dishName"));
        assertEquals("4200", rows.get(0).get("salesAmount"));
    }

    @Test
    void buildRankingCardPayload_amountHighUsesAmountRankingMeta() {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("rank", 1);
        row.put("dishName", "招牌鱼");
        row.put("soldPortionsTotal", "80");
        row.put("listPriceRevenue", "9600");

        DishSalesAnswerPlan plan =
                DishSalesAnswerPlan.builder()
                        .planType(DishSalesAnswerPlan.TYPE_DISH_SALES_AMOUNT_RANKING_HIGH)
                        .metricType(DishSalesAnswerPlan.METRIC_AMOUNT_HIGH)
                        .timeLabel("本月")
                        .rankingRows(List.of(row))
                        .build();

        Map<String, Object> card = DishSalesAnswerPlanCardSupport.buildRankingCardPayload(plan, null);

        assertNotNull(card);
        assertEquals(DishSalesAnswerPlan.CARD_TYPE_DISH_SALES_RANKING, card.get("cardType"));
        assertEquals("按销售额排序", card.get("subtitle"));
        @SuppressWarnings("unchecked")
        Map<String, Object> payload = (Map<String, Object>) card.get("payload");
        assertEquals(DishSalesAnswerPlan.RANKING_TYPE_AMOUNT_HIGH, payload.get("rankingType"));
        assertEquals("销售额", payload.get("metricLabel"));
    }

    @Test
    void buildCardPayload_returnsNullWhenRankingRowsEmpty() {
        DishSalesAnswerPlan plan =
                DishSalesAnswerPlan.builder()
                        .planType(DishSalesAnswerPlan.TYPE_DISH_SALES_COUNT_RANKING_HIGH)
                        .rankingRows(List.of())
                        .build();

        assertNull(DishSalesAnswerPlanCardSupport.buildCardPayload(plan));
    }

    @Test
    void buildCardPayload_returnsNullWhenSingleDishRankingRowsEmpty() {
        DishSalesAnswerPlan plan =
                DishSalesAnswerPlan.builder()
                        .planType(DishSalesAnswerPlan.TYPE_DISH_SALES_SINGLE_DISH)
                        .rankingRows(List.of())
                        .build();

        assertNull(DishSalesAnswerPlanCardSupport.buildCardPayload(plan));
    }

    @Test
    void buildCardPayload_projectsEmptyRankingCardWhenNoSalesEvidence() {
        DishSalesAnswerPlan plan =
                DishSalesRankingSalesEvidenceSupport.buildNoDataRankingPlan(
                        DishSalesAnswerPlan.TYPE_DISH_SALES_COUNT_RANKING_HIGH,
                        DishSalesAnswerPlan.METRIC_COUNT_HIGH,
                        "本店",
                        "上个月",
                        Map.of(),
                        List.of());

        Map<String, Object> card = DishSalesAnswerPlanCardSupport.buildCardPayload(plan);

        assertNotNull(card);
        assertEquals(DishSalesAnswerPlan.CARD_TYPE_DISH_SALES_RANKING, card.get("cardType"));
        @SuppressWarnings("unchecked")
        Map<String, Object> payload = (Map<String, Object>) card.get("payload");
        assertNotNull(payload);
        assertEquals("EMPTY", payload.get("status"));
        assertEquals(List.of(), payload.get("rows"));
        assertEquals(
                DishSalesRankingSalesEvidenceSupport.EMPTY_RANKING_MESSAGE, payload.get("message"));
    }

    @Test
    void buildRankingCardPayload_returnsEmptyCardWhenRowsAllZero() {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("rank", 1);
        row.put("dishName", "零销量菜");
        row.put("soldPortionsTotal", "0");
        row.put("listPriceRevenue", "0");

        DishSalesAnswerPlan plan =
                DishSalesAnswerPlan.builder()
                        .planType(DishSalesAnswerPlan.TYPE_DISH_SALES_COUNT_RANKING_HIGH)
                        .metricType(DishSalesAnswerPlan.METRIC_COUNT_HIGH)
                        .timeLabel("上个月")
                        .rankingRows(List.of(row))
                        .build();

        Map<String, Object> card = DishSalesAnswerPlanCardSupport.buildRankingCardPayload(plan, null);

        assertNotNull(card);
        @SuppressWarnings("unchecked")
        Map<String, Object> payload = (Map<String, Object>) card.get("payload");
        assertEquals("EMPTY", payload.get("status"));
        assertEquals(List.of(), payload.get("rows"));
    }
}
