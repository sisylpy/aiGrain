package com.nongxinle.ai.graph.business;

import com.nongxinle.ai.dto.business.PurchaseAnswerPlan;
import com.nongxinle.ai.tool.business.AiBusinessToolIds;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** {@link PurchaseAnswerPlan#TYPE_PURCHASE_SUPPLIER_GOODS_DETAIL} → 专属采购卡片（含 empty/noData）。 */
public final class PurchaseSupplierGoodsDetailCardSupport {

    public static final String CARD_TITLE = "供货商采购明细";

    private PurchaseSupplierGoodsDetailCardSupport() {}

    public static final String CARD_TYPE = "PURCHASE_SUPPLIER_GOODS_DETAIL_CARD";

    public static boolean isPurchaseSupplierGoodsDetailCardType(String cardType) {
        return CARD_TYPE.equals(cardType);
    }

    public static Map<String, Object> buildCard(PurchaseAnswerPlan plan) {
        if (plan == null
                || !PurchaseAnswerPlan.TYPE_PURCHASE_SUPPLIER_GOODS_DETAIL.equals(plan.getPlanType())) {
            return null;
        }
        List<Map<String, Object>> focus = copyRows(plan.getFocusRows());
        List<Map<String, Object>> secondary = copyRows(plan.getSecondaryRows());
        boolean hasRows = !focus.isEmpty() || !secondary.isEmpty();

        LinkedHashMap<String, Object> summary = new LinkedHashMap<>();
        String goodsName = goodsNameFromPlan(plan);
        if (StringUtils.hasText(goodsName)) {
            summary.put("goodsName", goodsName);
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("summary", summary);
        payload.put("focusRows", focus);
        payload.put("secondaryRows", secondary);
        putOptional(payload, "timeLabel", plan.getTimeLabel());
        putOptional(payload, "scopeLabel", plan.getScopeLabel());
        payload.put("itemsScope", "GOODS_SUPPLIER_BREAKDOWN");
        payload.put("source", "purchaseAnswerPlan");
        payload.put(
                "status",
                hasRows
                        ? BusinessStatusCardShellSupport.STATUS_OK
                        : BusinessStatusCardShellSupport.STATUS_EMPTY);
        if (!hasRows) {
            payload.put("emptyReason", resolveEmptyReason(plan));
        }

        Map<String, Object> card = new LinkedHashMap<>();
        card.put("cardType", CARD_TYPE);
        card.put("title", CARD_TITLE);
        card.put("payload", payload);
        return card;
    }

    private static String goodsNameFromPlan(PurchaseAnswerPlan plan) {
        Map<String, Object> debug = plan.getDebug();
        if (debug != null) {
            Object fromDebug =
                    firstNonBlank(
                            debug.get(AiBusinessToolIds.PAYLOAD_PURCHASE_GOODS_ANCHOR_EXECUTION_TARGET_GOODS_NAME),
                            debug.get("requestedGoodsName"),
                            debug.get("focusEntityName"),
                            debug.get("inheritedAnchorName"));
            if (fromDebug != null) {
                return fromDebug.toString().trim();
            }
        }
        Map<String, Object> summary = plan.getSummary();
        if (summary != null) {
            Object name = summary.get("goodsName");
            if (name != null && StringUtils.hasText(name.toString())) {
                return name.toString().trim();
            }
        }
        if (plan.getFocusRows() != null && !plan.getFocusRows().isEmpty()) {
            Map<String, Object> row = plan.getFocusRows().get(0);
            if (row != null) {
                Object name = row.get("goodsName");
                if (name != null && StringUtils.hasText(name.toString())) {
                    return name.toString().trim();
                }
            }
        }
        return null;
    }

    private static String resolveEmptyReason(PurchaseAnswerPlan plan) {
        Map<String, Object> debug = plan.getDebug();
        if (debug != null) {
            Object code = firstNonBlank(debug.get("noDataReason"), debug.get("purchaseSupplierGoodsDetailNoDataReason"));
            if (code != null && StringUtils.hasText(code.toString())) {
                return mapNoDataReason(code.toString().trim());
            }
        }
        return "该时间范围内暂无该商品的供货商采购明细";
    }

    private static String mapNoDataReason(String code) {
        return switch (code) {
            case "GOODS_SUPPLIER_BREAKDOWN_NO_DATA" -> "该时间范围内暂无该商品的供货商采购明细";
            case "GOODS_SUPPLIER_UNIT_PRICE_NO_DATA" -> "该时间范围内暂无该商品的供货商采购单价";
            case "NO_SUPPLIER_PURCHASE_FOR_FOCUSED_GOODS", "NO_SUPPLIER_PURCHASE_FOR_GOODS" ->
                    "该商品在供货商采购口径下暂未查到采购记录";
            default -> "该时间范围内暂无该商品的供货商采购明细";
        };
    }

    private static Object firstNonBlank(Object... values) {
        if (values == null) {
            return null;
        }
        for (Object value : values) {
            if (value != null && StringUtils.hasText(value.toString())) {
                return value;
            }
        }
        return null;
    }

    private static List<Map<String, Object>> copyRows(List<Map<String, Object>> rows) {
        List<Map<String, Object>> out = new ArrayList<>();
        if (rows == null) {
            return out;
        }
        for (Map<String, Object> row : rows) {
            if (row == null || row.isEmpty()) {
                continue;
            }
            out.add(new LinkedHashMap<>(row));
        }
        return out;
    }

    private static void putOptional(Map<String, Object> target, String key, Object value) {
        if (value == null) {
            return;
        }
        if (value instanceof String s && s.isBlank()) {
            return;
        }
        target.put(key, value);
    }
}
