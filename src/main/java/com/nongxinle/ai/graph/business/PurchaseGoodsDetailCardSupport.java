package com.nongxinle.ai.graph.business;

import com.nongxinle.ai.conversation.AiQuerySemanticLexicon;
import com.nongxinle.ai.dto.business.PurchaseAnswerPlan;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 从 {@link PurchaseAnswerPlan} 生成 {@code PURCHASE_GOODS_DETAIL_CARD}；不查库、不重算明细。 */
public final class PurchaseGoodsDetailCardSupport {

    private static final String SOURCE = "purchaseAnswerPlan";

    private PurchaseGoodsDetailCardSupport() {}

    public static boolean isPurchaseGoodsDetailCardType(String cardType) {
        return PurchaseAnswerPlan.CARD_TYPE_PURCHASE_GOODS_DETAIL.equals(cardType);
    }

    public static boolean hasPurchaseGoodsDetailCard(List<Map<String, Object>> cards) {
        if (cards == null || cards.isEmpty()) {
            return false;
        }
        for (Map<String, Object> card : cards) {
            if (card == null || card.isEmpty()) {
                continue;
            }
            Object ct = card.get("cardType");
            if (ct != null && isPurchaseGoodsDetailCardType(ct.toString().trim())) {
                return true;
            }
        }
        return false;
    }

    public static Map<String, Object> buildCard(PurchaseAnswerPlan plan) {
        if (plan == null
                || !PurchaseAnswerPlan.TYPE_PURCHASE_PERIOD_GOODS_DETAIL.equals(plan.getPlanType())) {
            return null;
        }
        List<Map<String, Object>> items = mergePlanRows(plan);
        boolean hasItems = hasNonEmptyItemRows(items);
        Map<String, Object> summary = plan.getSummary() == null ? Map.of() : plan.getSummary();
        Object totalAmount = summary.get("totalAmount");
        if (totalAmount == null) {
            totalAmount = summary.get("totalPurchaseAmount");
        }
        Object orderCount = summary.get("purchaseOrderCount");
        if (orderCount == null) {
            orderCount = summary.get("orderCount");
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        putOptional(payload, "totalPurchaseAmount", totalAmount);
        putOptional(payload, "purchaseOrderCount", orderCount);
        putOptional(payload, "timeLabel", plan.getTimeLabel());
        putOptional(payload, "scopeLabel", plan.getScopeLabel());
        putOptional(payload, "purchaseSourceType", plan.getPurchaseSourceType());
        payload.put("itemsScope", "PERIOD_GOODS_LIST");
        payload.put("items", copyRows(items));
        payload.put("source", SOURCE);
        payload.put(
                "status",
                hasItems ? BusinessStatusCardShellSupport.STATUS_OK : BusinessStatusCardShellSupport.STATUS_EMPTY);
        if (!hasItems) {
            payload.put("emptyReason", resolveEmptyReason(plan));
        }

        Map<String, Object> card = new LinkedHashMap<>();
        card.put("cardType", PurchaseAnswerPlan.CARD_TYPE_PURCHASE_GOODS_DETAIL);
        card.put("title", composeTitle(plan.getTimeLabel(), plan.getPurchaseSourceType()));
        card.put("payload", payload);
        return card;
    }

    private static boolean hasNonEmptyItemRows(List<Map<String, Object>> items) {
        if (items == null || items.isEmpty()) {
            return false;
        }
        for (Map<String, Object> row : items) {
            if (row != null && !row.isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private static String resolveEmptyReason(PurchaseAnswerPlan plan) {
        Map<String, Object> debug = plan.getDebug();
        if (debug != null) {
            Object code = debug.get("purchasePeriodGoodsDetailNoDataReason");
            if (code != null && StringUtils.hasText(code.toString())) {
                return mapNoDataReasonCode(code.toString().trim());
            }
        }
        return "该时间范围内暂无采购明细";
    }

    private static String mapNoDataReasonCode(String code) {
        return switch (code) {
            case "NO_PURCHASE_RECORD_FOR_SCOPE" -> "该时间范围内暂无采购记录";
            case "NO_PURCHASE_LINES_FOR_SCOPE" -> "该时间范围内暂无采购明细";
            default -> "该时间范围内暂无采购明细";
        };
    }

    private static String composeTitle(String timeLabel, String purchaseSourceType) {
        String suffix = sourceTitleSuffix(purchaseSourceType);
        if (StringUtils.hasText(timeLabel)) {
            return timeLabel.trim() + "·" + suffix;
        }
        return suffix;
    }

    private static String sourceTitleSuffix(String purchaseSourceType) {
        if (!StringUtils.hasText(purchaseSourceType)) {
            return "原料采购";
        }
        String normalized = purchaseSourceType.trim();
        if (AiQuerySemanticLexicon.SOURCE_SELF_PURCHASE.equalsIgnoreCase(normalized)) {
            return "自采商品";
        }
        if (AiQuerySemanticLexicon.SOURCE_SUPPLIER_PURCHASE.equalsIgnoreCase(normalized)) {
            return "供货商订货";
        }
        return "原料采购";
    }

    private static List<Map<String, Object>> mergePlanRows(PurchaseAnswerPlan plan) {
        List<Map<String, Object>> out = new ArrayList<>();
        if (plan.getFocusRows() != null) {
            out.addAll(plan.getFocusRows());
        }
        if (plan.getSecondaryRows() != null) {
            out.addAll(plan.getSecondaryRows());
        }
        return out;
    }

    private static List<Map<String, Object>> copyRows(List<Map<String, Object>> rows) {
        List<Map<String, Object>> out = new ArrayList<>(rows == null ? 0 : rows.size());
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
