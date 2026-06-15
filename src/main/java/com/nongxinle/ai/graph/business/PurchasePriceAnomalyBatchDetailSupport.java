package com.nongxinle.ai.graph.business;

import com.nongxinle.entity.GbDistributerPurchaseGoodsEntity;
import com.nongxinle.service.GbDistributerPurchaseGoodsService;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 采购单价异常：为汇总行挂载参与比较的采购批次明细（Tool → Plan → Card 共用）。 */
public final class PurchasePriceAnomalyBatchDetailSupport {

    public static final String PRICE_COMPARE_CURRENT_VS_PREVIOUS_BATCH = "CURRENT_VS_PREVIOUS_BATCH";
    public static final String BATCH_ROLE_CURRENT = "CURRENT";
    public static final String BATCH_ROLE_PREVIOUS = "PREVIOUS";

    private PurchasePriceAnomalyBatchDetailSupport() {}

    /**
     * 为 {@code queryGbPurchaseGoodsUnitPriceChangedVsPrevious} 结果补齐 {@code compareBatches}。
     */
    public static List<Map<String, Object>> enrichUnitPriceChangedRows(
            List<Map<String, Object>> anomalyRows,
            Map<String, Object> queryBase,
            GbDistributerPurchaseGoodsService purchaseGoodsService) {
        if (anomalyRows == null || anomalyRows.isEmpty() || purchaseGoodsService == null) {
            return anomalyRows == null ? List.of() : anomalyRows;
        }
        Set<Integer> purchaseGoodsIds = new LinkedHashSet<>();
        for (Map<String, Object> row : anomalyRows) {
            if (row == null) {
                continue;
            }
            addPositiveInt(purchaseGoodsIds, row.get("currentPurchaseGoodsId"));
            addPositiveInt(purchaseGoodsIds, row.get("previousPurchaseGoodsId"));
        }
        if (purchaseGoodsIds.isEmpty()) {
            return copyRows(anomalyRows);
        }

        Map<String, Object> detailQuery = queryBase == null ? new HashMap<>() : new HashMap<>(queryBase);
        detailQuery.remove("limit");
        detailQuery.remove("offset");
        detailQuery.remove("startDate");
        detailQuery.remove("stopDate");
        detailQuery.put("purchaseGoodsIds", new ArrayList<>(purchaseGoodsIds));

        List<GbDistributerPurchaseGoodsEntity> entities =
                purchaseGoodsService.queryPurchaseGoodsWithDetailByParams(detailQuery);
        Map<Integer, Map<String, Object>> lineByPurchaseId = indexLinesByPurchaseId(entities);

        List<Map<String, Object>> out = new ArrayList<>(anomalyRows.size());
        for (Map<String, Object> src : anomalyRows) {
            if (src == null) {
                continue;
            }
            LinkedHashMap<String, Object> row = new LinkedHashMap<>(src);
            row.put("priceCompareMode", PRICE_COMPARE_CURRENT_VS_PREVIOUS_BATCH);
            row.put("compareBatches", buildCompareBatches(row, lineByPurchaseId));
            out.add(row);
        }
        return out;
    }

    /** Card 投影：保留汇总字段并规范化 {@code compareBatches} 供前端直接渲染。 */
    public static List<Map<String, Object>> projectPriceAnomalyFocusRows(List<Map<String, Object>> focusRows) {
        if (focusRows == null || focusRows.isEmpty()) {
            return List.of();
        }
        List<Map<String, Object>> out = new ArrayList<>(focusRows.size());
        for (Map<String, Object> src : focusRows) {
            if (src == null || src.isEmpty()) {
                continue;
            }
            LinkedHashMap<String, Object> row = new LinkedHashMap<>();
            row.put("disGoodsId", src.get("disGoodsId"));
            row.put("goodsName", src.get("goodsName"));
            row.put("standardName", src.get("standardName"));
            row.put("currentUnitPrice", src.get("currentUnitPrice"));
            row.put("previousUnitPrice", src.get("previousUnitPrice"));
            row.put("priceChangePercent", firstPresent(src, "priceChangePercent", "priceFluctuationPercent"));
            row.put("stockFinishDate", src.get("stockFinishDate"));
            row.put("priceCompareMode", firstNonBlankString(
                    src.get("priceCompareMode"), PRICE_COMPARE_CURRENT_VS_PREVIOUS_BATCH));
            Object batches = src.get("compareBatches");
            row.put("compareBatches", batches instanceof List<?> list ? list : List.of());
            if (row.get("goodsName") != null) {
                out.add(row);
            }
        }
        return out;
    }

    private static List<Map<String, Object>> buildCompareBatches(
            Map<String, Object> summaryRow, Map<Integer, Map<String, Object>> lineByPurchaseId) {
        List<Map<String, Object>> batches = new ArrayList<>(2);
        appendBatchIfPresent(
                batches,
                lineByPurchaseId,
                summaryRow.get("currentPurchaseGoodsId"),
                BATCH_ROLE_CURRENT,
                summaryRow.get("goodsName"));
        appendBatchIfPresent(
                batches,
                lineByPurchaseId,
                summaryRow.get("previousPurchaseGoodsId"),
                BATCH_ROLE_PREVIOUS,
                summaryRow.get("goodsName"));
        return batches;
    }

    private static void appendBatchIfPresent(
            List<Map<String, Object>> batches,
            Map<Integer, Map<String, Object>> lineByPurchaseId,
            Object purchaseGoodsIdRaw,
            String batchRole,
            Object goodsNameFallback) {
        Integer purchaseGoodsId = parsePositiveInt(purchaseGoodsIdRaw);
        if (purchaseGoodsId == null) {
            return;
        }
        Map<String, Object> line = lineByPurchaseId.get(purchaseGoodsId);
        if (line == null || line.isEmpty()) {
            return;
        }
        LinkedHashMap<String, Object> batch = new LinkedHashMap<>(line);
        batch.put("batchRole", batchRole);
        if (batch.get("goodsName") == null && goodsNameFallback != null) {
            batch.put("goodsName", goodsNameFallback);
        }
        batches.add(batch);
    }

    private static Map<Integer, Map<String, Object>> indexLinesByPurchaseId(
            List<GbDistributerPurchaseGoodsEntity> entities) {
        Map<Integer, Map<String, Object>> out = new HashMap<>();
        if (entities == null || entities.isEmpty()) {
            return out;
        }
        for (Map<String, Object> line : PurchaseGoodsAnchorLineRowSupport.mapDistinctPurchaseLines(entities)) {
            if (line == null) {
                continue;
            }
            Integer id = parsePositiveInt(line.get("purchaseGoodsId"));
            if (id != null) {
                out.putIfAbsent(id, line);
            }
        }
        return out;
    }

    private static List<Map<String, Object>> copyRows(List<Map<String, Object>> rows) {
        List<Map<String, Object>> out = new ArrayList<>(rows.size());
        for (Map<String, Object> row : rows) {
            if (row != null) {
                out.add(new LinkedHashMap<>(row));
            }
        }
        return out;
    }

    private static void addPositiveInt(Set<Integer> ids, Object raw) {
        Integer id = parsePositiveInt(raw);
        if (id != null) {
            ids.add(id);
        }
    }

    private static Integer parsePositiveInt(Object raw) {
        if (raw == null) {
            return null;
        }
        if (raw instanceof Number n) {
            int v = n.intValue();
            return v > 0 ? v : null;
        }
        String s = raw.toString().trim();
        if (!StringUtils.hasText(s)) {
            return null;
        }
        try {
            int v = Integer.parseInt(s);
            return v > 0 ? v : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static Object firstPresent(Map<String, Object> row, String... keys) {
        for (String key : keys) {
            if (row.get(key) != null) {
                return row.get(key);
            }
        }
        return null;
    }

    private static String firstNonBlankString(Object... values) {
        for (Object v : values) {
            if (v == null) {
                continue;
            }
            String s = v.toString().trim();
            if (StringUtils.hasText(s)) {
                return s;
            }
        }
        return null;
    }
}
