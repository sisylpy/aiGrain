package com.nongxinle.ai.graph.business;

import com.nongxinle.ai.dto.business.WarehouseAnswerPlan;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 从 {@link WarehouseAnswerPlan} 投影统一 Run {@code cards[]}；不重算指标。
 */
public final class WarehouseAnswerPlanCardSupport {

    public static final String CARD_TYPE_INVENTORY_RISK = "WAREHOUSE_INVENTORY_RISK_LIST_CARD";

    private static final String CHART_TYPE_TABLE = "TABLE";
    private static final String PAYLOAD_STATUS_OK = "OK";
    private static final String PAYLOAD_STATUS_EMPTY = "EMPTY";
    private static final String SOURCE = "warehouseAnswerPlan";

    private WarehouseAnswerPlanCardSupport() {}

    public static List<Map<String, Object>> buildRunCards(WarehouseAnswerPlan plan) {
        if (plan == null || !StringUtils.hasText(plan.getPlanType())) {
            return List.of();
        }
        String type = plan.getPlanType().trim();
        Map<String, Object> card;
        if (WarehouseAnswerPlan.TYPE_WAREHOUSE_LOW_STOCK_RISK.equals(type)) {
            card = buildInventoryRiskCard(plan);
        } else if (isStockAmountRankingPlanType(type)) {
            card = buildStockRankingCard(plan);
        } else {
            return List.of();
        }
        return card == null || card.isEmpty() ? List.of() : List.of(card);
    }

    public static boolean isStockAmountRankingPlanType(String planType) {
        if (!StringUtils.hasText(planType)) {
            return false;
        }
        String pt = planType.trim();
        return WarehouseAnswerPlan.TYPE_WAREHOUSE_GOODS_AMOUNT_RANKING_LOW.equals(pt)
                || WarehouseAnswerPlan.TYPE_WAREHOUSE_GOODS_AMOUNT_RANKING_HIGH.equals(pt)
                || WarehouseAnswerPlan.TYPE_WAREHOUSE_STORE_AMOUNT_RANKING.equals(pt);
    }

    private static Map<String, Object> buildInventoryRiskCard(WarehouseAnswerPlan plan) {
        Map<String, Object> card = new LinkedHashMap<>();
        card.put("cardType", CARD_TYPE_INVENTORY_RISK);
        card.put("title", "库存风险关注列表");
        card.put("subtitle", WarehouseAnswerPlanCardTimeSupport.cardSubtitle(plan));
        card.put("sourceAnswerPlanType", plan.getPlanType());
        card.put("payload", buildInventoryRiskPayload(plan));
        return card;
    }

    private static Map<String, Object> buildStockRankingCard(WarehouseAnswerPlan plan) {
        RankingMeta meta = resolveRankingMeta(plan.getPlanType());
        List<Map<String, Object>> rows = collectProjectedRankingRows(plan, meta.storeRanking());

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("status", rows.isEmpty() ? PAYLOAD_STATUS_EMPTY : PAYLOAD_STATUS_OK);
        payload.put("rankingType", meta.rankingType());
        payload.put("metricLabel", WarehouseAnswerPlan.METRIC_LABEL_STOCK_AMOUNT);
        payload.put("stockSnapshotLabel", plan.getStockSnapshotLabel());
        payload.put("scopeLabel", plan.getScopeLabel());
        payload.put("timeLabel", plan.getTimeLabel());
        payload.put("asOfDate", plan.getAsOfDate());
        payload.put("inventoryQueryTimeKind", plan.getInventoryQueryTimeKind());
        payload.put("rows", rows);
        if (plan.getSummary() != null && !plan.getSummary().isEmpty()) {
            payload.put("summary", plan.getSummary());
        }
        if (rows.isEmpty()) {
            payload.put("message", "当前范围内暂无账面库存金额排行数据。");
        }
        payload.put("source", SOURCE);
        if (plan.getDebug() != null) {
            payload.put("dataSources", plan.getDebug().get("dataSources"));
            payload.put("knownGaps", plan.getDebug().get("knownGaps"));
        }

        Map<String, Object> card = new LinkedHashMap<>();
        card.put("cardType", WarehouseAnswerPlan.CARD_TYPE_STOCK_RANKING);
        card.put("title", meta.title());
        card.put("subtitle", WarehouseAnswerPlanCardTimeSupport.cardSubtitle(plan));
        card.put("chartType", CHART_TYPE_TABLE);
        card.put("sourceAnswerPlanType", plan.getPlanType());
        card.put("payload", payload);
        Map<String, Object> source = new LinkedHashMap<>();
        source.put("answerPlan", SOURCE);
        source.put("dataRef", "focusRows+secondaryRows");
        card.put("source", source);
        return card;
    }

    private static Map<String, Object> buildInventoryRiskPayload(WarehouseAnswerPlan plan) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("scopeLabel", plan.getScopeLabel());
        payload.put("timeLabel", plan.getTimeLabel());
        payload.put("stockSnapshotLabel", plan.getStockSnapshotLabel());
        payload.put("periodFlowLabel", plan.getPeriodFlowLabel());
        payload.put("asOfDate", plan.getAsOfDate());
        payload.put("inventoryQueryTimeKind", plan.getInventoryQueryTimeKind());
        if (plan.getSummary() != null) {
            payload.put("summary", plan.getSummary());
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        if (plan.getFocusRows() != null) {
            rows.addAll(plan.getFocusRows());
        }
        if (plan.getSecondaryRows() != null) {
            rows.addAll(plan.getSecondaryRows());
        }
        payload.put("riskItems", rows);
        if (plan.getDebug() != null) {
            payload.put("dataSources", plan.getDebug().get("dataSources"));
            payload.put("knownGaps", plan.getDebug().get("knownGaps"));
        }
        return payload;
    }

    private static List<Map<String, Object>> collectProjectedRankingRows(
            WarehouseAnswerPlan plan, boolean storeRanking) {
        List<Map<String, Object>> merged = new ArrayList<>();
        if (plan.getFocusRows() != null) {
            merged.addAll(plan.getFocusRows());
        }
        if (plan.getSecondaryRows() != null) {
            merged.addAll(plan.getSecondaryRows());
        }
        List<Map<String, Object>> out = new ArrayList<>(merged.size());
        for (Map<String, Object> row : merged) {
            if (row == null || row.isEmpty()) {
                continue;
            }
            out.add(projectRankingRow(row, storeRanking));
        }
        return out;
    }

    private static Map<String, Object> projectRankingRow(Map<String, Object> row, boolean storeRanking) {
        Map<String, Object> projected = new LinkedHashMap<>();
        putIfPresent(projected, "rank", row.get("rank"));
        if (storeRanking) {
            putIfPresent(projected, "storeName", row.get("storeName"));
            putIfPresent(projected, "storeDepartmentId", row.get("storeDepartmentId"));
            Object amount = firstPresent(row.get("restAmountTotal"), row.get("totalStockAmount"));
            putIfPresent(projected, "restAmountTotal", amount);
        } else {
            putIfPresent(projected, "goodsName", row.get("goodsName"));
            putIfPresent(projected, "goodsId", row.get("goodsId"));
            putIfPresent(projected, "restAmountTotal", row.get("restAmountTotal"));
            putIfPresent(projected, "restWeightTotal", row.get("restWeightTotal"));
            putIfPresent(projected, "weightDisplayUnit", row.get("weightDisplayUnit"));
        }
        return projected;
    }

    private static Object firstPresent(Object... candidates) {
        for (Object c : candidates) {
            if (c != null) {
                return c;
            }
        }
        return null;
    }

    private static void putIfPresent(Map<String, Object> target, String key, Object value) {
        if (value != null) {
            target.put(key, value);
        }
    }

    private static RankingMeta resolveRankingMeta(String planType) {
        if (WarehouseAnswerPlan.TYPE_WAREHOUSE_GOODS_AMOUNT_RANKING_LOW.equals(planType)) {
            return new RankingMeta(
                    WarehouseAnswerPlan.RANKING_TYPE_GOODS_AMOUNT_LOW,
                    "账面库存金额偏低商品",
                    false);
        }
        if (WarehouseAnswerPlan.TYPE_WAREHOUSE_GOODS_AMOUNT_RANKING_HIGH.equals(planType)) {
            return new RankingMeta(
                    WarehouseAnswerPlan.RANKING_TYPE_GOODS_AMOUNT_HIGH,
                    "账面库存金额偏高商品",
                    false);
        }
        if (WarehouseAnswerPlan.TYPE_WAREHOUSE_STORE_AMOUNT_RANKING.equals(planType)) {
            return new RankingMeta(
                    WarehouseAnswerPlan.RANKING_TYPE_STORE_AMOUNT,
                    "门店账面库存金额排行",
                    true);
        }
        return new RankingMeta(
                WarehouseAnswerPlan.RANKING_TYPE_GOODS_AMOUNT_HIGH,
                "账面库存金额排行",
                false);
    }

    private record RankingMeta(String rankingType, String title, boolean storeRanking) {}
}
