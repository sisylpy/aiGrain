package com.nongxinle.ai.graph.business;

import com.nongxinle.ai.composer.warehouse.WarehouseStockRankingCardCompanionAnswerPreviewSupport;
import com.nongxinle.ai.dto.business.WarehouseAnswerPlan;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WarehouseAnswerPlanCardSupportTest {

    @Test
    void buildRunCards_projectsGoodsAmountLowRankingCard() {
        Map<String, Object> row1 = new LinkedHashMap<>();
        row1.put("rank", 1);
        row1.put("goodsName", "西芹");
        row1.put("restAmountTotal", 12.5);
        row1.put("restWeightTotal", 3.2);
        Map<String, Object> row2 = new LinkedHashMap<>();
        row2.put("rank", 2);
        row2.put("goodsName", "三元原味酸奶");
        row2.put("restAmountTotal", 18.0);
        row2.put("restWeightTotal", 6.0);

        WarehouseAnswerPlan plan =
                WarehouseAnswerPlan.builder()
                        .planType(WarehouseAnswerPlan.TYPE_WAREHOUSE_GOODS_AMOUNT_RANKING_LOW)
                        .scopeLabel("集团范围")
                        .stockSnapshotLabel("当前库存（截至 2026-06-01）")
                        .focusRows(List.of(row1))
                        .secondaryRows(List.of(row2))
                        .summary(Map.of("narrative", "账面库存金额较低的商品已列出。"))
                        .build();

        List<Map<String, Object>> cards = WarehouseAnswerPlanCardSupport.buildRunCards(plan);

        assertEquals(1, cards.size());
        Map<String, Object> card = cards.get(0);
        assertEquals(WarehouseAnswerPlan.CARD_TYPE_STOCK_RANKING, card.get("cardType"));
        assertEquals("账面库存金额偏低商品", card.get("title"));
        assertEquals("当前库存（截至 2026-06-01）", card.get("subtitle"));
        assertEquals("TABLE", card.get("chartType"));

        @SuppressWarnings("unchecked")
        Map<String, Object> payload = (Map<String, Object>) card.get("payload");
        assertNotNull(payload);
        assertEquals("OK", payload.get("status"));
        assertEquals(WarehouseAnswerPlan.RANKING_TYPE_GOODS_AMOUNT_LOW, payload.get("rankingType"));
        assertEquals(WarehouseAnswerPlan.METRIC_LABEL_STOCK_AMOUNT, payload.get("metricLabel"));
        assertEquals("当前库存（截至 2026-06-01）", payload.get("stockSnapshotLabel"));
        assertEquals("集团范围", payload.get("scopeLabel"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) payload.get("rows");
        assertEquals(2, rows.size());
        assertEquals("西芹", rows.get(0).get("goodsName"));
        assertEquals("三元原味酸奶", rows.get(1).get("goodsName"));
    }

    @Test
    void buildRunCards_projectsStoreAmountRankingCard() {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("rank", 1);
        row.put("storeName", "AAA店");
        row.put("totalStockAmount", 9999.0);

        WarehouseAnswerPlan plan =
                WarehouseAnswerPlan.builder()
                        .planType(WarehouseAnswerPlan.TYPE_WAREHOUSE_STORE_AMOUNT_RANKING)
                        .scopeLabel("集团范围")
                        .stockSnapshotLabel("当前库存（截至 2026-06-01）")
                        .focusRows(List.of(row))
                        .build();

        List<Map<String, Object>> cards = WarehouseAnswerPlanCardSupport.buildRunCards(plan);
        assertEquals(1, cards.size());
        @SuppressWarnings("unchecked")
        Map<String, Object> payload = (Map<String, Object>) cards.get(0).get("payload");
        assertEquals(WarehouseAnswerPlan.RANKING_TYPE_STORE_AMOUNT, payload.get("rankingType"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) payload.get("rows");
        assertEquals("AAA店", rows.get(0).get("storeName"));
        assertEquals(9999.0, rows.get(0).get("restAmountTotal"));
    }

    @Test
    void buildRunCards_overviewPlanReturnsEmpty() {
        WarehouseAnswerPlan plan =
                WarehouseAnswerPlan.builder()
                        .planType(WarehouseAnswerPlan.TYPE_WAREHOUSE_STOCK_OVERVIEW)
                        .build();
        assertTrue(WarehouseAnswerPlanCardSupport.buildRunCards(plan).isEmpty());
    }

    @Test
    void companionPreview_isShortAndAvoidsRiskWording() {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("rank", 1);
        row.put("goodsName", "西芹");
        row.put("restAmountTotal", 12.5);

        WarehouseAnswerPlan plan =
                WarehouseAnswerPlan.builder()
                        .planType(WarehouseAnswerPlan.TYPE_WAREHOUSE_GOODS_AMOUNT_RANKING_LOW)
                        .scopeLabel("集团范围")
                        .stockSnapshotLabel("当前库存（截至 2026-06-01）")
                        .focusRows(List.of(row))
                        .build();

        assertTrue(WarehouseStockRankingCardCompanionAnswerPreviewSupport.shouldUseShortPreview(plan));
        String hint = WarehouseStockRankingCardCompanionAnswerPreviewSupport.composeCardCompanionHint(plan);
        assertTrue(hint.contains("详见下方卡片"));
        assertTrue(hint.contains("账面库存金额偏低"));
        assertFalse(hint.contains("报警"));
        assertFalse(hint.contains("缺货"));
        assertFalse(hint.contains("临期"));
    }
}
