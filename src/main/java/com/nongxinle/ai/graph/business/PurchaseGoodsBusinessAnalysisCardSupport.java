package com.nongxinle.ai.graph.business;

import com.nongxinle.ai.context.AiResolvedQueryContext;
import com.nongxinle.ai.dto.business.PurchaseGoodsBusinessAnalysisAnswerPlan;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 从 {@link PurchaseGoodsBusinessAnalysisAnswerPlan} 生成 {@code PURCHASE_GOODS_BUSINESS_ANALYSIS_CARD}。 */
public final class PurchaseGoodsBusinessAnalysisCardSupport {

    private PurchaseGoodsBusinessAnalysisCardSupport() {}

    public static List<Map<String, Object>> buildRunCards(PurchaseGoodsBusinessAnalysisAnswerPlan plan) {
        return buildRunCards(plan, null);
    }

    public static List<Map<String, Object>> buildRunCards(
            PurchaseGoodsBusinessAnalysisAnswerPlan plan, AiResolvedQueryContext rq) {
        if (plan == null || !PurchaseGoodsBusinessAnalysisAnswerPlan.TYPE.equals(plan.getPlanType())) {
            return List.of();
        }
        if (PurchaseGoodsBusinessAnalysisAnswerPlan.STATUS_FAILED.equals(plan.getStatus())) {
            return List.of();
        }
        String goodsName =
                PurchaseGoodsBusinessAnalysisDisplayNameSupport.resolveDisplayGoodsNameForPlan(
                        rq, plan.getGoodsName());

        Map<String, Object> card = new LinkedHashMap<>();
        card.put("cardType", PurchaseGoodsBusinessAnalysisAnswerPlan.CARD_TYPE);
        String title =
                goodsName == null || goodsName.isBlank()
                        ? "原料采购经营分析"
                        : goodsName + " · 采购经营分析";
        card.put("title", title);
        card.put("subtitle", plan.getPurchaseTimeLabel() == null ? "" : plan.getPurchaseTimeLabel());
        card.put("sourceAnswerPlanType", plan.getPlanType());

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("contractId", plan.getContractId());
        payload.put("status", plan.getStatus());
        payload.put("goodsName", goodsName);
        payload.put("disGoodsId", plan.getDisGoodsId());
        payload.put("scopeLabel", plan.getScopeLabel());
        payload.put("purchaseTimeLabel", plan.getPurchaseTimeLabel());
        payload.put("inventorySnapshotLabel", plan.getInventorySnapshotLabel());
        payload.put("salesBaselineLabel", plan.getSalesBaselineLabel());
        payload.put("dominantPurchaseSource", plan.getDominantPurchaseSource());
        payload.put("purchaseSourceSection", plan.getPurchaseSourceSection());
        payload.put("purchaseVolumeSection", plan.getPurchaseVolumeSection());
        payload.put("priceSection", plan.getPriceSection());
        payload.put("inventorySection", plan.getInventorySection());
        payload.put("salesMatchSection", plan.getSalesMatchSection());
        payload.put("dishRows", plan.getDishRows() == null ? List.of() : plan.getDishRows());
        payload.put("judgmentSignals", plan.getJudgmentSignals() == null ? List.of() : plan.getJudgmentSignals());
        payload.put("knownGaps", plan.getKnownGaps() == null ? List.of() : plan.getKnownGaps());
        card.put("payload", payload);
        return List.of(card);
    }
}
