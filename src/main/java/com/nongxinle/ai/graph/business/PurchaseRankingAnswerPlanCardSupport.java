package com.nongxinle.ai.graph.business;

import com.nongxinle.ai.dto.business.PurchaseAnswerPlan;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 采购排行类 AnswerPlan → 专属排行卡（商品/门店；数量/次数/金额分口径）。 */
final class PurchaseRankingAnswerPlanCardSupport {

    private PurchaseRankingAnswerPlanCardSupport() {}

    static Map<String, Object> buildCard(PurchaseAnswerPlan plan) {
        if (plan == null || plan.getPlanType() == null) {
            return null;
        }
        String planType = plan.getPlanType();
        String cardType;
        String title;
        switch (planType) {
            case PurchaseAnswerPlan.TYPE_PURCHASE_GOODS_AMOUNT_RANKING -> {
                cardType = "PURCHASE_GOODS_AMOUNT_RANKING_CARD";
                title = "商品采购金额排行";
            }
            case PurchaseAnswerPlan.TYPE_PURCHASE_GOODS_COUNT_RANKING -> {
                cardType = "PURCHASE_GOODS_COUNT_RANKING_CARD";
                title = "商品采购次数排行";
            }
            case PurchaseAnswerPlan.TYPE_PURCHASE_GOODS_QUANTITY_RANKING -> {
                cardType = PurchaseAnswerPlan.CARD_TYPE_PURCHASE_GOODS_QUANTITY_RANKING;
                title = "商品采购数量排行";
            }
            case PurchaseAnswerPlan.TYPE_PURCHASE_STORE_AMOUNT_RANKING -> {
                cardType = PurchaseAnswerPlan.CARD_TYPE_PURCHASE_STORE_AMOUNT_RANKING;
                title = "门店采购金额排行";
            }
            case PurchaseAnswerPlan.TYPE_PURCHASE_SUPPLIER_AMOUNT_RANKING -> {
                cardType = "PURCHASE_SUPPLIER_AMOUNT_RANKING_CARD";
                title = "供货商采购金额排行";
            }
            default -> {
                return null;
            }
        }

        LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
        payload.put("planType", planType);
        payload.put("focusRows", plan.getFocusRows() != null ? plan.getFocusRows() : List.of());
        payload.put("secondaryRows", plan.getSecondaryRows() != null ? plan.getSecondaryRows() : List.of());
        payload.put("timeLabel", plan.getTimeLabel());
        payload.put("scopeLabel", plan.getScopeLabel());
        if (PurchaseAnswerPlan.TYPE_PURCHASE_GOODS_QUANTITY_RANKING.equals(planType)
                && plan.getDebug() != null) {
            Object unitComparable = plan.getDebug().get("quantityRankingUnitComparable");
            if (Boolean.FALSE.equals(unitComparable)) {
                payload.put("quantityRankingUnitComparable", Boolean.FALSE);
                payload.put(
                        "quantityRankingCaliberGapReason",
                        plan.getDebug().get("quantityRankingCaliberGapReason"));
            }
        }
        boolean hasRows = plan.getFocusRows() != null && !plan.getFocusRows().isEmpty();
        payload.put("status", hasRows
                ? BusinessStatusCardShellSupport.STATUS_OK
                : BusinessStatusCardShellSupport.STATUS_EMPTY);

        return BusinessStatusCardShellSupport.buildCard(
                cardType,
                title,
                plan.getScopeLabel(),
                BusinessStatusCardShellSupport.CHART_TABLE,
                payload,
                "purchaseRankingAnswerPlanCard");
    }
}
