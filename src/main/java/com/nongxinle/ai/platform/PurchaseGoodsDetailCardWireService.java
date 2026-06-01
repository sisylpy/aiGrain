package com.nongxinle.ai.platform;

import com.nongxinle.ai.core.AiRunState;
import com.nongxinle.ai.dto.business.PurchaseAnswerPlan;
import com.nongxinle.ai.graph.business.PurchaseGoodsDetailCardSupport;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 原料采购清单卡：单域「买了什么」问法；与经营四卡 {@code PURCHASE_CHECK_CARD} 隔离。 */
@Service
public class PurchaseGoodsDetailCardWireService {

    public void attachPurchaseGoodsDetailCardIfApplicable(AiRunState state) {
        if (state == null || state.getPurchaseAnswerPlan() == null) {
            return;
        }
        PurchaseAnswerPlan plan = state.getPurchaseAnswerPlan();
        if (!PurchaseAnswerPlan.TYPE_PURCHASE_PERIOD_GOODS_DETAIL.equals(plan.getPlanType())) {
            return;
        }
        Map<String, Object> card = PurchaseGoodsDetailCardSupport.buildCard(plan);
        if (card == null || card.isEmpty()) {
            mirrorCardProjectionDebug(state, "build_card_null_or_empty", false);
            return;
        }
        state.setCards(List.of(card));
        state.setCardPayload(AiCardPayloadWireSupport.buildDeprecatedCardPayloadCompatFromCards(state.getCards()));
        mirrorCardProjectionDebug(state, null, true);
    }

    private static void mirrorCardProjectionDebug(AiRunState state, String skipReason, boolean attached) {
        if (state == null) {
            return;
        }
        LinkedHashMap<String, Object> probe = new LinkedHashMap<>();
        probe.put("purchaseGoodsDetailCardAttached", attached);
        if (skipReason != null) {
            probe.put("purchaseGoodsDetailCardSkipReason", skipReason);
        }
        Map<String, Object> planDebug =
                state.getPurchaseAnswerPlan() != null ? state.getPurchaseAnswerPlan().getDebug() : null;
        if (planDebug != null) {
            copyIfPresent(probe, planDebug, "purchasePeriodGoodsDetailActive");
            copyIfPresent(probe, planDebug, "purchasePeriodGoodsDetailQueryMethod");
            copyIfPresent(probe, planDebug, "purchasePeriodGoodsDetailRowsCount");
            copyIfPresent(probe, planDebug, "purchasePeriodGoodsDetailNoDataReason");
            copyIfPresent(probe, planDebug, "periodGoodsDetailFocusRowsSize");
        }
        Map<String, Object> md = state.getMasterBusinessAgentDebug();
        LinkedHashMap<String, Object> next =
                md == null || md.isEmpty() ? new LinkedHashMap<>() : new LinkedHashMap<>(md);
        next.put("purchasePeriodGoodsDetailCardProjection", probe);
        state.setMasterBusinessAgentDebug(next);
    }

    private static void copyIfPresent(
            Map<String, Object> target, Map<String, Object> source, String key) {
        if (source.containsKey(key)) {
            target.put(key, source.get(key));
        }
    }
}
