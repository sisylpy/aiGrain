package com.nongxinle.ai.graph.business;

import com.nongxinle.ai.dto.business.PurchaseAnswerPlan;
import com.nongxinle.ai.identity.BusinessEntityExistenceLookup;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** {@link PurchaseAnswerPlan#TYPE_PURCHASE_GOODS_SOURCE_BREAKDOWN} → {@code PURCHASE_GOODS_ANCHOR_DETAIL_CARD}。 */
public final class PurchaseGoodsAnchorDetailCardSupport {

    public static final String CARD_TITLE = "采购商品";

    private PurchaseGoodsAnchorDetailCardSupport() {}

    public static boolean isPurchaseGoodsAnchorDetailCardType(String cardType) {
        return PurchaseAnswerPlan.CARD_TYPE_PURCHASE_GOODS_ANCHOR_DETAIL.equals(cardType);
    }

    public static boolean hasPurchaseGoodsAnchorDetailCard(List<Map<String, Object>> cards) {
        if (cards == null || cards.isEmpty()) {
            return false;
        }
        for (Map<String, Object> card : cards) {
            if (card == null || card.isEmpty()) {
                continue;
            }
            Object ct = card.get("cardType");
            if (ct != null && isPurchaseGoodsAnchorDetailCardType(ct.toString().trim())) {
                return true;
            }
        }
        return false;
    }

    public static Map<String, Object> buildCard(PurchaseAnswerPlan plan) {
        if (plan == null
                || !PurchaseAnswerPlan.TYPE_PURCHASE_GOODS_SOURCE_BREAKDOWN.equals(plan.getPlanType())) {
            return null;
        }
        if (isGoodsAnchorIdentityFailurePlan(plan)) {
            return buildAnchorIdentityFailureCard(plan);
        }
        Map<String, Object> aggregate = aggregateRow(plan);
        List<Map<String, Object>> lines = copyRows(plan.getSecondaryRows());
        boolean hasLines = !lines.isEmpty();
        boolean hasAggregate = aggregate != null && !aggregate.isEmpty();

        LinkedHashMap<String, Object> summary = new LinkedHashMap<>();
        String goodsName = goodsNameFromPlan(plan, aggregate);
        if (StringUtils.hasText(goodsName)) {
            summary.put("goodsName", goodsName);
        }
        putOptional(summary, "unit", unitFromPlan(plan, aggregate, lines));
        putOptional(summary, "totalPurchaseQuantity", totalQuantity(plan, aggregate));
        putOptional(summary, "totalPurchaseAmount", totalAmount(plan, aggregate));
        putOptional(summary, "purchaseLineCount", purchaseLineCount(plan, aggregate, lines));

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("summary", summary);
        payload.put("lines", lines);
        putOptional(payload, "timeLabel", plan.getTimeLabel());
        putOptional(payload, "scopeLabel", plan.getScopeLabel());
        payload.put("itemsScope", "GOODS_ANCHOR_PURCHASE_LINES");
        payload.put("source", "purchaseAnswerPlan");
        payload.put(
                "status",
                hasLines || hasAggregate
                        ? BusinessStatusCardShellSupport.STATUS_OK
                        : BusinessStatusCardShellSupport.STATUS_EMPTY);
        if (!hasLines && !hasAggregate) {
            payload.put("emptyReason", resolveEmptyReason(plan));
        }

        Map<String, Object> card = new LinkedHashMap<>();
        card.put("cardType", PurchaseAnswerPlan.CARD_TYPE_PURCHASE_GOODS_ANCHOR_DETAIL);
        card.put("title", CARD_TITLE);
        card.put("payload", payload);
        return card;
    }

    private static boolean isGoodsAnchorIdentityFailurePlan(PurchaseAnswerPlan plan) {
        Map<String, Object> debug = plan.getDebug();
        if (debug == null) {
            return false;
        }
        if (Boolean.TRUE.equals(debug.get("anchorIdentityBlocked"))
                || Boolean.TRUE.equals(debug.get("goodsAnchorIdMissing"))) {
            return true;
        }
        return "GOODS_ANCHOR_ID_MISSING".equalsIgnoreCase(stringLoose(debug.get("noDataReason")));
    }

    private static Map<String, Object> buildAnchorIdentityFailureCard(PurchaseAnswerPlan plan) {
        Map<String, Object> debug = plan.getDebug();
        String requestedName = null;
        if (plan.getSummary() != null) {
            Object name = plan.getSummary().get("requestedGoodsName");
            if (name != null && StringUtils.hasText(name.toString())) {
                requestedName = name.toString().trim();
            }
        }
        if (requestedName == null && debug != null) {
            requestedName = stringLoose(debug.get("requestedGoodsName"));
            if (requestedName == null) {
                requestedName = stringLoose(debug.get("focusEntityName"));
            }
        }

        LinkedHashMap<String, Object> summary = new LinkedHashMap<>();
        if (StringUtils.hasText(requestedName)) {
            summary.put("goodsName", requestedName);
        }

        String clarification =
                debug != null ? stringLoose(debug.get("clarificationMessage")) : null;
        if (clarification == null) {
            clarification = BusinessEntityExistenceLookup.CLARIFICATION_GOODS_NOT_FOUND;
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("summary", summary);
        payload.put("lines", List.of());
        putOptional(payload, "timeLabel", plan.getTimeLabel());
        putOptional(payload, "scopeLabel", plan.getScopeLabel());
        payload.put("itemsScope", "GOODS_ANCHOR_IDENTITY_FAILURE");
        payload.put("source", "purchaseAnswerPlan");
        payload.put("status", BusinessStatusCardShellSupport.STATUS_EMPTY);
        payload.put("emptyReason", clarification);
        payload.put("noDataReason", "GOODS_ANCHOR_ID_MISSING");

        Map<String, Object> card = new LinkedHashMap<>();
        card.put("cardType", PurchaseAnswerPlan.CARD_TYPE_PURCHASE_GOODS_ANCHOR_DETAIL);
        card.put("title", CARD_TITLE);
        card.put("payload", payload);
        return card;
    }

    private static String stringLoose(Object value) {
        if (value == null) {
            return null;
        }
        String s = value.toString().trim();
        return s.isEmpty() ? null : s;
    }

    private static Map<String, Object> aggregateRow(PurchaseAnswerPlan plan) {
        if (plan.getFocusRows() == null || plan.getFocusRows().isEmpty()) {
            return null;
        }
        Map<String, Object> row = plan.getFocusRows().get(0);
        return row == null || row.isEmpty() ? null : row;
    }

    private static String goodsNameFromPlan(PurchaseAnswerPlan plan, Map<String, Object> aggregate) {
        if (aggregate != null) {
            Object name = aggregate.get("goodsName");
            if (name != null && StringUtils.hasText(name.toString())) {
                return name.toString().trim();
            }
        }
        Map<String, Object> debug = plan.getDebug();
        if (debug != null) {
            Object focus = debug.get("focusEntityName");
            if (focus != null && StringUtils.hasText(focus.toString())) {
                return focus.toString().trim();
            }
            Object requested = debug.get("requestedGoodsName");
            if (requested != null && StringUtils.hasText(requested.toString())) {
                return requested.toString().trim();
            }
        }
        return null;
    }

    private static String unitFromPlan(
            PurchaseAnswerPlan plan, Map<String, Object> aggregate, List<Map<String, Object>> lines) {
        String fromLines = PurchaseGoodsAnchorLineRowSupport.resolveDefaultUnit(lines);
        if (StringUtils.hasText(fromLines)) {
            return fromLines;
        }
        if (aggregate != null) {
            Object unit = aggregate.get("unit");
            if (unit != null && StringUtils.hasText(unit.toString())) {
                return unit.toString().trim();
            }
        }
        Map<String, Object> summary = plan.getSummary();
        if (summary != null) {
            Object unit = summary.get("unit");
            if (unit != null && StringUtils.hasText(unit.toString())) {
                return unit.toString().trim();
            }
        }
        return null;
    }

    private static Object totalQuantity(PurchaseAnswerPlan plan, Map<String, Object> aggregate) {
        if (aggregate != null && aggregate.get("totalPurchaseQuantity") != null) {
            return aggregate.get("totalPurchaseQuantity");
        }
        Map<String, Object> summary = plan.getSummary();
        if (summary != null && summary.get("totalPurchaseQuantity") != null) {
            return summary.get("totalPurchaseQuantity");
        }
        return null;
    }

    private static Object totalAmount(PurchaseAnswerPlan plan, Map<String, Object> aggregate) {
        if (aggregate != null && aggregate.get("totalPurchaseAmount") != null) {
            return aggregate.get("totalPurchaseAmount");
        }
        Map<String, Object> summary = plan.getSummary();
        if (summary != null) {
            if (summary.get("totalAmount") != null) {
                return summary.get("totalAmount");
            }
            if (summary.get("totalPurchaseAmount") != null) {
                return summary.get("totalPurchaseAmount");
            }
        }
        return null;
    }

    private static int purchaseLineCount(
            PurchaseAnswerPlan plan, Map<String, Object> aggregate, List<Map<String, Object>> lines) {
        if (!lines.isEmpty()) {
            return lines.size();
        }
        if (aggregate != null) {
            int self = intLoose(aggregate.get("selfPurchaseLineCount"));
            int sup = intLoose(aggregate.get("supplierPurchaseLineCount"));
            int other = intLoose(aggregate.get("otherPurchaseLineCount"));
            int total = self + sup + other;
            if (total > 0) {
                return total;
            }
        }
        Map<String, Object> summary = plan.getSummary();
        if (summary != null && summary.get("purchaseLineCount") != null) {
            return intLoose(summary.get("purchaseLineCount"));
        }
        return 0;
    }

    private static String resolveEmptyReason(PurchaseAnswerPlan plan) {
        Map<String, Object> debug = plan.getDebug();
        if (debug != null) {
            Object code = debug.get("noDataReason");
            if (code != null && StringUtils.hasText(code.toString())) {
                return mapNoDataReason(code.toString().trim());
            }
        }
        return "该时间范围内暂无该商品采购记录";
    }

    private static String mapNoDataReason(String code) {
        return switch (code) {
            case "NO_PURCHASE_RECORD_FOR_SCOPE" -> "该时间范围内暂无采购记录";
            case "NO_PURCHASE_LINES_FOR_FOCUSED_GOODS" -> "该时间范围内暂无该商品采购明细";
            case "GOODS_ANCHOR_ID_MISSING" -> "未能定位到该商品，暂无采购明细";
            default -> "该时间范围内暂无该商品采购记录";
        };
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

    private static int intLoose(Object value) {
        if (value == null) {
            return 0;
        }
        if (value instanceof Number n) {
            return n.intValue();
        }
        try {
            return Integer.parseInt(value.toString().trim());
        } catch (Exception e) {
            return 0;
        }
    }
}
