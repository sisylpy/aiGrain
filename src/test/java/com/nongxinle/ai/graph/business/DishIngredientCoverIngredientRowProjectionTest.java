package com.nongxinle.ai.graph.business;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DishIngredientCoverIngredientRowProjectionTest {

    @Test
    void projectsBusinessFieldsFromInventoryRestWeight() {
        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("gbDgGoodsName", "生鸡");
        raw.put("recipeUnitPerDish", "0.5");
        raw.put("inventoryRestWeightQty", "10");
        raw.put("outboundAllocatedQty", "999");
        raw.put("actualProduceUsage", "888");

        Map<String, Object> projected =
                DishIngredientCoverIngredientRowProjection.project(
                        raw, new BigDecimal("2"), 30, "生鸡");

        assertEquals("生鸡", projected.get("ingredientName"));
        assertEquals("0.5", projected.get("recipeUnitPerDish"));
        assertEquals("10", projected.get("currentStockQty"));
        assertNull(projected.get("currentStockQtyEmptyReason"));
        assertEquals("1", projected.get("dailyExpectedUsageQty"));
        assertEquals("5", projected.get("coverDays"));
        assertTrue((Boolean) projected.get("isBottleneck"));
        assertFalse(projected.containsKey("gbDgGoodsName"));
        assertFalse(projected.containsKey("outboundAllocatedQty"));
    }

    @Test
    void nullMetricsGetEmptyReasonWhenNoSales() {
        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("gbDgGoodsName", "花椒");
        raw.put("recipeUnitPerDish", "0.1");
        raw.put("inventoryRestWeightQty", "3");

        Map<String, Object> projected =
                DishIngredientCoverIngredientRowProjection.project(raw, BigDecimal.ZERO, 30, null);

        assertEquals("花椒", projected.get("ingredientName"));
        assertEquals("3", projected.get("currentStockQty"));
        assertNull(projected.get("dailyExpectedUsageQty"));
        assertEquals(
                DishIngredientCoverIngredientRowProjection.REASON_NO_SALES_DAILY_USAGE,
                projected.get("dailyExpectedUsageQtyEmptyReason"));
        assertNull(projected.get("coverDays"));
        assertEquals(
                DishIngredientCoverIngredientRowProjection.REASON_NO_SALES_COVER_DAYS,
                projected.get("coverDaysEmptyReason"));
        assertEquals("30", projected.get("supportedPortionsFromStock"));
    }

    @Test
    void missingInventoryRestWeightMarksEmptyReason() {
        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("goodsName", "生鸡");
        raw.put("recipeUnitPerDish", "0.5");

        Map<String, Object> projected =
                DishIngredientCoverIngredientRowProjection.project(
                        raw, new BigDecimal("1"), 30, null);

        assertNull(projected.get("currentStockQty"));
        assertEquals(
                DishIngredientCoverIngredientRowProjection.REASON_INVENTORY_REST_WEIGHT_MISSING,
                projected.get("currentStockQtyEmptyReason"));
    }

    @Test
    void cardSupportStripsRawCostKeysIfPresent() {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("ingredientName", "生鸡");
        row.put("recipeUnitPerDish", "0.5");
        row.put("currentStockQty", "5");
        row.put("currentStockQtyEmptyReason", null);
        row.put("dailyExpectedUsageQty", null);
        row.put(
                "dailyExpectedUsageQtyEmptyReason",
                DishIngredientCoverIngredientRowProjection.REASON_NO_SALES_DAILY_USAGE);
        row.put("coverDays", null);
        row.put(
                "coverDaysEmptyReason",
                DishIngredientCoverIngredientRowProjection.REASON_NO_SALES_COVER_DAYS);
        row.put("isBottleneck", false);
        row.put("gbDgGoodsName", "should-not-leak");

        List<Map<String, Object>> cardRows = DishIngredientCoverAnswerPlanCardSupport.projectRowsForCard(List.of(row));
        assertEquals(1, cardRows.size());
        assertFalse(cardRows.get(0).containsKey("gbDgGoodsName"));
        assertEquals("0.5", cardRows.get(0).get("recipeUnitPerDish"));
        assertEquals("生鸡", cardRows.get(0).get("ingredientName"));
    }
}
