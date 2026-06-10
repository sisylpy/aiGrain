package com.nongxinle.ai.graph.business;

import com.nongxinle.ai.conversation.AiQuerySemanticLexicon;
import com.nongxinle.ai.dto.business.PurchaseAnswerPlan;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * {@code PURCHASE_CHECK_CARD} 采购金额投影：以 {@link PurchaseAnswerPlan#getSummary()} 为 SSOT；
 * 自采/供货商 overview 只暴露对应渠道字段，避免 Card 层重查 SQL 混入其它渠道金额。
 */
final class PurchaseCheckCardSummaryProjection {

    private PurchaseCheckCardSummaryProjection() {}

    static Map<String, Object> resolve(PurchaseAnswerPlan plan, Map<String, Object> sqlFallbackSummary) {
        if (plan != null && plan.getSummary() != null && !plan.getSummary().isEmpty()) {
            return fromAnswerPlan(plan);
        }
        return sqlFallbackSummary != null ? sqlFallbackSummary : Map.of();
    }

    static Map<String, Object> fromAnswerPlan(PurchaseAnswerPlan plan) {
        LinkedHashMap<String, Object> out = new LinkedHashMap<>();
        if (plan == null || plan.getSummary() == null || plan.getSummary().isEmpty()) {
            out.put("totalPurchaseAmount", 0.0);
            out.put("selfPurchaseAmount", 0.0);
            out.put("supplierPurchaseAmount", 0.0);
            return out;
        }
        Map<String, Object> raw = plan.getSummary();
        double self = parseDoubleLoose(raw.get("selfPurchaseAmount"));
        double sup = parseDoubleLoose(raw.get("supplierPurchaseAmount"));
        double total = parseDoubleLoose(raw.get("totalAmount"));
        if (total <= 0) {
            total = parseDoubleLoose(raw.get("totalPurchaseAmount"));
        }

        if (isSelfOverview(plan)) {
            double focusTotal = self > 0 ? self : total;
            out.put("totalPurchaseAmount", focusTotal);
            out.put("selfPurchaseAmount", focusTotal);
            out.put("supplierPurchaseAmount", 0.0);
            return out;
        }
        if (isSupplierOverview(plan)) {
            double focusTotal = sup > 0 ? sup : total;
            out.put("totalPurchaseAmount", focusTotal);
            out.put("selfPurchaseAmount", 0.0);
            out.put("supplierPurchaseAmount", focusTotal);
            return out;
        }
        out.put("totalPurchaseAmount", total);
        out.put("selfPurchaseAmount", self);
        out.put("supplierPurchaseAmount", sup);
        return out;
    }

    static String titleSuffix(PurchaseAnswerPlan plan) {
        if (isSelfOverview(plan)) {
            return "自采";
        }
        if (isSupplierOverview(plan)) {
            return "供货商订货";
        }
        return "采购";
    }

    static boolean isSelfOverview(PurchaseAnswerPlan plan) {
        if (plan == null) {
            return false;
        }
        if (PurchaseAnswerPlan.TYPE_PURCHASE_SELF_OVERVIEW.equals(plan.getPlanType())) {
            return true;
        }
        return AiQuerySemanticLexicon.SOURCE_SELF_PURCHASE.equalsIgnoreCase(
                normalize(plan.getPurchaseSourceType()));
    }

    static boolean isSupplierOverview(PurchaseAnswerPlan plan) {
        if (plan == null) {
            return false;
        }
        if (PurchaseAnswerPlan.TYPE_PURCHASE_SUPPLIER_OVERVIEW.equals(plan.getPlanType())) {
            return true;
        }
        return AiQuerySemanticLexicon.SOURCE_SUPPLIER_PURCHASE.equalsIgnoreCase(
                normalize(plan.getPurchaseSourceType()));
    }

    private static String normalize(String s) {
        return s == null ? "" : s.trim();
    }

    private static double parseDoubleLoose(Object v) {
        if (v == null) {
            return 0.0;
        }
        if (v instanceof Number n) {
            return n.doubleValue();
        }
        try {
            return Double.parseDouble(v.toString().trim());
        } catch (Exception e) {
            return 0.0;
        }
    }
}
