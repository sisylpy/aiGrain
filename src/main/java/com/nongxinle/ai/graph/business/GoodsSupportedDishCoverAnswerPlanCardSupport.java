package com.nongxinle.ai.graph.business;

import com.nongxinle.ai.dto.business.GoodsSupportedDishCoverAnswerPlan;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class GoodsSupportedDishCoverAnswerPlanCardSupport {

    private GoodsSupportedDishCoverAnswerPlanCardSupport() {}

    public static List<Map<String, Object>> buildRunCards(GoodsSupportedDishCoverAnswerPlan plan) {
        if (plan == null || !GoodsSupportedDishCoverAnswerPlan.TYPE.equals(plan.getPlanType())) {
            return List.of();
        }
        Map<String, Object> card = new LinkedHashMap<>();
        card.put("cardType", GoodsSupportedDishCoverAnswerPlan.CARD_TYPE);
        String title =
                plan.getGoodsName() == null || plan.getGoodsName().isBlank()
                        ? "原料关联菜品"
                        : plan.getGoodsName() + " · 关联菜品";
        card.put("title", title);
        card.put("subtitle", plan.getStockSnapshotLabel() == null ? "" : plan.getStockSnapshotLabel());
        card.put("sourceAnswerPlanType", plan.getPlanType());

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("goodsName", plan.getGoodsName());
        payload.put("disGoodsId", plan.getDisGoodsId());
        payload.put("currentStockQty", plan.getCurrentStockQty());
        payload.put("stockUnit", plan.getStockUnit());
        payload.put("stockSnapshotLabel", plan.getStockSnapshotLabel());
        payload.put("salesBaselineLabel", plan.getSalesBaselineLabel());
        payload.put("firstImpactedDishName", plan.getFirstImpactedDishName());
        payload.put("firstImpactedCoverDays", plan.getFirstImpactedCoverDays());
        payload.put(
                "dishRowFields",
                List.of(
                        "dishName",
                        "recipeUnitPerDish",
                        "salesPortionsInBaseline",
                        "dailySalesPortions",
                        "supportedPortionsFromStock",
                        "coverDays"));
        payload.put("dishRows", plan.getDishRows() == null ? List.of() : plan.getDishRows());
        payload.put("summary", plan.getSummary());
        payload.put("knownGaps", plan.getKnownGaps());
        card.put("payload", payload);
        return List.of(card);
    }
}
