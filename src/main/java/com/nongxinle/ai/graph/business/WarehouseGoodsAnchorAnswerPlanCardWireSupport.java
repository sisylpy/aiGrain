package com.nongxinle.ai.graph.business;

import com.nongxinle.ai.core.AiRunState;
import com.nongxinle.ai.dto.business.GoodsStockBatchDetailAnswerPlan;
import com.nongxinle.ai.dto.business.GoodsSupportedDishCoverAnswerPlan;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** GOODS 锚点库房能力：各自 AnswerPlan → 各自 CardType，可同轮并列挂载。 */
public final class WarehouseGoodsAnchorAnswerPlanCardWireSupport {

    private WarehouseGoodsAnchorAnswerPlanCardWireSupport() {}

    public static boolean ownsAnyCardProjection(AiRunState state) {
        if (state == null) {
            return false;
        }
        return state.getGoodsSupportedDishCoverAnswerPlan() != null
                || state.getGoodsStockBatchDetailAnswerPlan() != null;
    }

    /** Composer 已挂载 GOODS 锚点库房卡时，refresh 只做归一化、不重投影。 */
    public static boolean hasWarehouseGoodsAnchorAnswerPlanCard(List<Map<String, Object>> cards) {
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
            if (GoodsSupportedDishCoverAnswerPlan.CARD_TYPE.equals(type)
                    || GoodsStockBatchDetailAnswerPlan.CARD_TYPE.equals(type)) {
                return true;
            }
        }
        return false;
    }

    public static List<Map<String, Object>> buildRunCards(AiRunState state) {
        if (state == null) {
            return List.of();
        }
        List<Map<String, Object>> cards = new ArrayList<>();
        GoodsSupportedDishCoverAnswerPlan cover = state.getGoodsSupportedDishCoverAnswerPlan();
        if (cover != null) {
            for (Map<String, Object> card :
                    GoodsSupportedDishCoverAnswerPlanCardSupport.buildRunCards(
                            cover, state.getResolvedQueryContext())) {
                if (card != null && !card.isEmpty()) {
                    cards.add(card);
                }
            }
        }
        GoodsStockBatchDetailAnswerPlan batch = state.getGoodsStockBatchDetailAnswerPlan();
        if (batch != null) {
            for (Map<String, Object> card :
                    GoodsStockBatchDetailCardSupport.buildRunCards(batch, state.getResolvedQueryContext())) {
                if (card != null && !card.isEmpty()) {
                    cards.add(card);
                }
            }
        }
        return cards;
    }
}
