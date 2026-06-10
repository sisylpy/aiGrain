package com.nongxinle.ai.graph.business;

import com.nongxinle.ai.core.AiRunState;
import com.nongxinle.ai.dto.business.PurchaseAnswerPlan;
import com.nongxinle.ai.dto.business.PurchaseGoodsBusinessAnalysisAnswerPlan;

import java.util.List;
import java.util.Map;

/**
 * 采购 AnswerPlan → 卡片 SSOT：仅 {@link PurchaseAnswerPlan#getPlanType()} 决定专属卡片类型。
 */
public final class PurchaseAnswerPlanCardSupport {

    private PurchaseAnswerPlanCardSupport() {}

    /** 该 PlanType 已由专属采购域卡片承接，不得再投影 {@code PURCHASE_CHECK_CARD}。 */
    public static boolean ownsExclusiveCardProjection(String planType) {
        if (planType == null || planType.isBlank()) {
            return false;
        }
        return PurchaseAnswerPlan.TYPE_PURCHASE_PERIOD_GOODS_DETAIL.equals(planType)
                || PurchaseAnswerPlan.TYPE_PURCHASE_GOODS_SOURCE_BREAKDOWN.equals(planType)
                || PurchaseAnswerPlan.TYPE_PURCHASE_SUPPLIER_GOODS_DETAIL.equals(planType)
                || PurchaseAnswerPlan.TYPE_PURCHASE_GOODS_AMOUNT_RANKING.equals(planType)
                || PurchaseAnswerPlan.TYPE_PURCHASE_GOODS_COUNT_RANKING.equals(planType)
                || PurchaseAnswerPlan.TYPE_PURCHASE_GOODS_QUANTITY_RANKING.equals(planType)
                || PurchaseAnswerPlan.TYPE_PURCHASE_STORE_AMOUNT_RANKING.equals(planType)
                || PurchaseAnswerPlan.TYPE_PURCHASE_SUPPLIER_AMOUNT_RANKING.equals(planType)
                || PurchaseAnswerPlan.TYPE_PURCHASE_ANOMALY.equals(planType);
    }

    /** 采购域专属 AnswerPlan 已绑定卡片，或 GOODS 经营分析 Plan 已接管主链。 */
    public static boolean suppressesPurchaseBusinessStatusProjection(AiRunState state) {
        if (state == null) {
            return false;
        }
        PurchaseAnswerPlan pap = state.getPurchaseAnswerPlan();
        if (pap != null && ownsExclusiveCardProjection(pap.getPlanType())) {
            return true;
        }
        PurchaseGoodsBusinessAnalysisAnswerPlan gba = state.getPurchaseGoodsBusinessAnalysisAnswerPlan();
        return gba != null && PurchaseGoodsBusinessAnalysisAnswerPlan.TYPE.equals(gba.getPlanType());
    }

    /** 采购检查卡仅承接明确的采购概览/检查 PlanType。 */
    public static boolean isPurchaseCheckCardEligiblePlanType(String planType) {
        if (planType == null || planType.isBlank()) {
            return false;
        }
        return PurchaseAnswerPlan.TYPE_PURCHASE_OVERVIEW.equals(planType)
                || PurchaseAnswerPlan.TYPE_PURCHASE_SELF_OVERVIEW.equals(planType)
                || PurchaseAnswerPlan.TYPE_PURCHASE_SUPPLIER_OVERVIEW.equals(planType);
    }

    public static Map<String, Object> buildCard(PurchaseAnswerPlan plan) {
        if (plan == null || plan.getPlanType() == null) {
            return null;
        }
        return switch (plan.getPlanType()) {
            case PurchaseAnswerPlan.TYPE_PURCHASE_PERIOD_GOODS_DETAIL ->
                    PurchaseGoodsDetailCardSupport.buildCard(plan);
            case PurchaseAnswerPlan.TYPE_PURCHASE_GOODS_SOURCE_BREAKDOWN ->
                    PurchaseGoodsAnchorDetailCardSupport.buildCard(plan);
            case PurchaseAnswerPlan.TYPE_PURCHASE_SUPPLIER_GOODS_DETAIL ->
                    PurchaseSupplierGoodsDetailCardSupport.buildCard(plan);
            case PurchaseAnswerPlan.TYPE_PURCHASE_ANOMALY ->
                    PurchaseAnomalyAnswerPlanCardSupport.buildCard(plan);
            case PurchaseAnswerPlan.TYPE_PURCHASE_GOODS_AMOUNT_RANKING,
                    PurchaseAnswerPlan.TYPE_PURCHASE_GOODS_COUNT_RANKING,
                    PurchaseAnswerPlan.TYPE_PURCHASE_GOODS_QUANTITY_RANKING,
                    PurchaseAnswerPlan.TYPE_PURCHASE_STORE_AMOUNT_RANKING,
                    PurchaseAnswerPlan.TYPE_PURCHASE_SUPPLIER_AMOUNT_RANKING ->
                    PurchaseRankingAnswerPlanCardSupport.buildCard(plan);
            default -> null;
        };
    }

    public static String cardTypeForPlanType(String planType) {
        if (PurchaseAnswerPlan.TYPE_PURCHASE_PERIOD_GOODS_DETAIL.equals(planType)) {
            return PurchaseAnswerPlan.CARD_TYPE_PURCHASE_GOODS_DETAIL;
        }
        if (PurchaseAnswerPlan.TYPE_PURCHASE_GOODS_SOURCE_BREAKDOWN.equals(planType)) {
            return PurchaseAnswerPlan.CARD_TYPE_PURCHASE_GOODS_ANCHOR_DETAIL;
        }
        if (PurchaseAnswerPlan.TYPE_PURCHASE_SUPPLIER_GOODS_DETAIL.equals(planType)) {
            return PurchaseSupplierGoodsDetailCardSupport.CARD_TYPE;
        }
        return null;
    }

    public static boolean hasPurchaseAnswerPlanCard(List<Map<String, Object>> cards) {
        if (cards == null || cards.isEmpty()) {
            return false;
        }
        for (Map<String, Object> card : cards) {
            if (card == null || card.isEmpty()) {
                continue;
            }
            Object ct = card.get("cardType");
            if (ct == null) {
                continue;
            }
            String type = ct.toString().trim();
            if (PurchaseGoodsDetailCardSupport.isPurchaseGoodsDetailCardType(type)
                    || PurchaseGoodsAnchorDetailCardSupport.isPurchaseGoodsAnchorDetailCardType(type)
                    || PurchaseSupplierGoodsDetailCardSupport.isPurchaseSupplierGoodsDetailCardType(type)) {
                return true;
            }
        }
        return false;
    }
}
