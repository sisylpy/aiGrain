package com.nongxinle.ai.platform;

import com.nongxinle.ai.core.AiRunState;
import com.nongxinle.ai.graph.business.WarehouseGoodsAnchorAnswerPlanCardWireSupport;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** GOODS 锚点库房 AnswerPlan 卡片唯一挂载入口（可同轮输出多张卡）。 */
@Service
public class WarehouseGoodsAnchorAnswerPlanCardWireService {

    public void attachCardsIfApplicable(AiRunState state) {
        if (state == null || !WarehouseGoodsAnchorAnswerPlanCardWireSupport.ownsAnyCardProjection(state)) {
            return;
        }
        List<Map<String, Object>> cards = WarehouseGoodsAnchorAnswerPlanCardWireSupport.buildRunCards(state);
        if (cards.isEmpty()) {
            mirrorProjectionDebug(state, "build_cards_empty", false);
            return;
        }
        state.setCards(cards);
        state.setCardPayload(AiCardPayloadWireSupport.buildDeprecatedCardPayloadCompatFromCards(cards));
        mirrorProjectionDebug(state, null, true);
    }

    private static void mirrorProjectionDebug(AiRunState state, String skipReason, boolean attached) {
        Map<String, Object> md = state.getMasterBusinessAgentDebug();
        LinkedHashMap<String, Object> next =
                md == null || md.isEmpty() ? new LinkedHashMap<>() : new LinkedHashMap<>(md);
        LinkedHashMap<String, Object> probe = new LinkedHashMap<>();
        probe.put("warehouseGoodsAnchorCardsAttached", attached);
        probe.put(
                "warehouseGoodsAnchorCardCount",
                state.getCards() == null ? 0 : state.getCards().size());
        if (skipReason != null) {
            probe.put("warehouseGoodsAnchorCardSkipReason", skipReason);
        }
        next.put("warehouseGoodsAnchorCardProjection", probe);
        state.setMasterBusinessAgentDebug(next);
    }
}
