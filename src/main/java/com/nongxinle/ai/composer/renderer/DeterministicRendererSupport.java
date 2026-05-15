package com.nongxinle.ai.composer.renderer;

import com.nongxinle.ai.core.AiRunState;
import com.nongxinle.ai.tool.business.AiBusinessToolIds;
import com.nongxinle.ai.util.AiNumericPlainText;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Shared helpers for deterministic renderers (tool envelopes, numeric hints, warehouse snippet).
 */
final class DeterministicRendererSupport {

    static final String W_STOCK_WEIGHT_UNIT = "斤";

    private DeterministicRendererSupport() {
    }

    static String nz(String s) {
        return s == null ? "" : s;
    }

    static String nz(Object o) {
        return o == null ? "" : o.toString();
    }

    static String plainNumericHint(Object v) {
        if (v == null) {
            return "暂无";
        }
        if (v instanceof java.math.BigDecimal bd) {
            return AiNumericPlainText.plainNumber(bd);
        }
        if (v instanceof Number n) {
            return AiNumericPlainText.plainNumber(n);
        }
        String s = v.toString().trim();
        return s.isEmpty() ? "暂无" : s;
    }

    static int intHint(Object v) {
        if (v == null) {
            return 0;
        }
        if (v instanceof Number n) {
            return n.intValue();
        }
        try {
            return Integer.parseInt(v.toString().trim());
        } catch (Exception e) {
            return 0;
        }
    }

    static double parseDoubleLoose(Object v) {
        if (v == null) {
            return 0;
        }
        if (v instanceof Number n) {
            return n.doubleValue();
        }
        try {
            return Double.parseDouble(v.toString().trim());
        } catch (Exception e) {
            return 0;
        }
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> toolEnvelope(AiRunState state, String toolKey) {
        Object env = state.getToolResults().get(toolKey);
        return env instanceof Map ? (Map<String, Object>) env : Map.of();
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> toolDataInnerMap(AiRunState state, String toolKey) {
        Object env = state.getToolResults().get(toolKey);
        if (!(env instanceof Map)) {
            return Map.of();
        }
        Object data = ((Map<String, Object>) env).get("data");
        if (!(data instanceof Map)) {
            return Map.of();
        }
        return (Map<String, Object>) data;
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> extractPurchaseOverviewPayloadForRenderer(AiRunState state) {
        Map<String, Object> inner = toolDataInnerMap(state, AiBusinessToolIds.PURCHASE_OVERVIEW);
        Object po = inner.get("purchaseOverview");
        if (!(po instanceof Map)) {
            return Map.of();
        }
        Map<String, Object> raw = (Map<String, Object>) po;
        if (raw.isEmpty()) {
            return Map.of();
        }
        return new LinkedHashMap<>(raw);
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> extractWarehouseOverviewPayloadForRenderer(AiRunState state) {
        Map<String, Object> inner = toolDataInnerMap(state, AiBusinessToolIds.WAREHOUSE_STOCK_OVERVIEW);
        Object wo = inner.get("warehouseOverview");
        if (!(wo instanceof Map)) {
            return Map.of();
        }
        Map<String, Object> raw = (Map<String, Object>) wo;
        if (raw.isEmpty()) {
            return Map.of();
        }
        return new LinkedHashMap<>(raw);
    }

    static boolean stockSnapshotHasSignal(Map<String, Object> sq, Map<String, Object> stk,
            Map<String, Object> wo) {
        if (wo != null && !wo.isEmpty()) {
            int sku = intHint(wo.get("stockItemCount"));
            double amt = parseDoubleLoose(wo.get("totalStockAmount"));
            double wt = parseDoubleLoose(wo.get("totalStockWeight"));
            double inbound = parseDoubleLoose(wo.get("inboundAmount"));
            double reduce = parseDoubleLoose(wo.get("stockReduceAmount"));
            return sku > 0 || amt > 0 || wt > 0 || inbound > 0 || reduce > 0;
        }
        if (sq == null || sq.isEmpty()) {
            return stk != null && !stk.isEmpty();
        }
        Object rc = sq.get("stockBatchRowCount");
        int rows = 0;
        if (rc instanceof Number n) {
            rows = n.intValue();
        } else if (rc != null) {
            try {
                rows = Integer.parseInt(rc.toString().trim());
            } catch (Exception ignored) {
                rows = 0;
            }
        }
        double rest = parseDoubleLoose(sq.get("stockRestSubtotal"));
        double inbound = parseDoubleLoose(sq.get("periodInboundSubtotal"));
        return rows > 0 || rest > 0 || inbound > 0;
    }

    static String fmtStockWeightCn(Object value) {
        return plainNumericHint(value) + " " + W_STOCK_WEIGHT_UNIT;
    }

    /** 写入「剩余 {n} 斤」时中间的数字部分（斤前留空格）。 */
    static String stockWeightNumberOnly(Object value) {
        return plainNumericHint(value);
    }

    static boolean warehouseOverviewHasVisibleWarehouses(Map<String, Object> wo) {
        if (wo == null) {
            return false;
        }
        Object v = wo.get("visibleWarehouses");
        return v instanceof List<?> l && !l.isEmpty();
    }

    static void appendWarehouseRecommendations(StringBuilder sb, Object recObj) {
        if (!(recObj instanceof List<?> list) || list.isEmpty()) {
            return;
        }
        sb.append("建议：\n");
        int i = 1;
        for (Object o : list) {
            if (o == null || o.toString().isBlank()) {
                continue;
            }
            sb.append(i++).append(". ").append(o.toString().trim()).append("\n");
            if (i > 6) {
                break;
            }
        }
    }
}
