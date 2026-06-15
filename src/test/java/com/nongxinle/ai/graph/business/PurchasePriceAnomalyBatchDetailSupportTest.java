package com.nongxinle.ai.graph.business;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PurchasePriceAnomalyBatchDetailSupportTest {

    @Test
    void projectPriceAnomalyFocusRows_preservesCompareBatchesForCard() {
        LinkedHashMap<String, Object> currentBatch = new LinkedHashMap<>();
        currentBatch.put("batchRole", PurchasePriceAnomalyBatchDetailSupport.BATCH_ROLE_CURRENT);
        currentBatch.put("goodsName", "西红柿");
        currentBatch.put("purchaseDate", "2026-06-08");
        currentBatch.put("quantity", "20");
        currentBatch.put("unit", "斤");
        currentBatch.put("unitPrice", "6.5");
        currentBatch.put("amount", "130.0");
        currentBatch.put("purchaseSourceType", "SUPPLIER_PURCHASE");
        currentBatch.put("supplierName", "鲜丰配送");

        LinkedHashMap<String, Object> previousBatch = new LinkedHashMap<>();
        previousBatch.put("batchRole", PurchasePriceAnomalyBatchDetailSupport.BATCH_ROLE_PREVIOUS);
        previousBatch.put("goodsName", "西红柿");
        previousBatch.put("purchaseDate", "2026-06-01");
        previousBatch.put("quantity", "18");
        previousBatch.put("unit", "斤");
        previousBatch.put("unitPrice", "5.0");
        previousBatch.put("amount", "90.0");
        previousBatch.put("purchaseSourceType", "SELF_PURCHASE");
        previousBatch.put("purchaserName", "张采购");

        LinkedHashMap<String, Object> summary = new LinkedHashMap<>();
        summary.put("disGoodsId", 101);
        summary.put("goodsName", "西红柿");
        summary.put("currentUnitPrice", "6.5");
        summary.put("previousUnitPrice", "5.0");
        summary.put("priceChangePercent", "30.0");
        summary.put("stockFinishDate", "2026-06-08");
        summary.put("priceCompareMode", PurchasePriceAnomalyBatchDetailSupport.PRICE_COMPARE_CURRENT_VS_PREVIOUS_BATCH);
        summary.put("compareBatches", List.of(currentBatch, previousBatch));

        List<Map<String, Object>> projected =
                PurchasePriceAnomalyBatchDetailSupport.projectPriceAnomalyFocusRows(List.of(summary));

        assertEquals(1, projected.size());
        assertEquals("西红柿", projected.get(0).get("goodsName"));
        assertEquals(
                PurchasePriceAnomalyBatchDetailSupport.PRICE_COMPARE_CURRENT_VS_PREVIOUS_BATCH,
                projected.get(0).get("priceCompareMode"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> batches = (List<Map<String, Object>>) projected.get(0).get("compareBatches");
        assertEquals(2, batches.size());
        assertEquals(PurchasePriceAnomalyBatchDetailSupport.BATCH_ROLE_CURRENT, batches.get(0).get("batchRole"));
        assertEquals("鲜丰配送", batches.get(0).get("supplierName"));
        assertEquals(PurchasePriceAnomalyBatchDetailSupport.BATCH_ROLE_PREVIOUS, batches.get(1).get("batchRole"));
        assertEquals("张采购", batches.get(1).get("purchaserName"));
    }

    @Test
    void projectPriceAnomalyFocusRows_defaultsEmptyCompareBatchesWhenMissing() {
        LinkedHashMap<String, Object> summary = new LinkedHashMap<>();
        summary.put("goodsName", "黄瓜");
        summary.put("currentUnitPrice", "3.2");
        summary.put("previousUnitPrice", "2.8");

        List<Map<String, Object>> projected =
                PurchasePriceAnomalyBatchDetailSupport.projectPriceAnomalyFocusRows(List.of(summary));

        assertEquals(1, projected.size());
        assertTrue(((List<?>) projected.get(0).get("compareBatches")).isEmpty());
    }
}
