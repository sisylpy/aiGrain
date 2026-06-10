package com.nongxinle.ai.platform;

import com.nongxinle.ai.core.AiRunState;
import com.nongxinle.ai.dto.business.PurchaseAnswerPlan;
import com.nongxinle.ai.graph.business.PurchaseAnswerPlanCardSupport;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 采购域 AnswerPlan 卡片唯一挂载入口（Composer 之前写入 RunState）。 */
@Service
public class PurchaseAnswerPlanCardWireService {

    public void attachCardsIfApplicable(AiRunState state) {
        if (state == null) {
            return;
        }
        PurchaseAnswerPlan plan = state.getPurchaseAnswerPlan();
        if (plan == null || !PurchaseAnswerPlanCardSupport.ownsExclusiveCardProjection(plan.getPlanType())) {
            return;
        }
        Map<String, Object> card = PurchaseAnswerPlanCardSupport.buildCard(plan);
        if (card == null || card.isEmpty()) {
            mirrorProjectionDebug(state, plan.getPlanType(), "build_card_null_or_empty", false);
            return;
        }
        state.setCards(List.of(card));
        state.setCardPayload(AiCardPayloadWireSupport.buildDeprecatedCardPayloadCompatFromCards(state.getCards()));
        mirrorProjectionDebug(state, plan.getPlanType(), null, true);
    }

    private static void mirrorProjectionDebug(
            AiRunState state, String planType, String skipReason, boolean attached) {
        if (state == null) {
            return;
        }
        LinkedHashMap<String, Object> probe = new LinkedHashMap<>();
        probe.put("purchaseAnswerPlanCardAttached", attached);
        probe.put("purchaseAnswerPlanType", planType);
        probe.put("purchaseAnswerPlanCardType", PurchaseAnswerPlanCardSupport.cardTypeForPlanType(planType));
        if (skipReason != null) {
            probe.put("purchaseAnswerPlanCardSkipReason", skipReason);
        }
        Map<String, Object> planDebug =
                state.getPurchaseAnswerPlan() != null ? state.getPurchaseAnswerPlan().getDebug() : null;
        if (planDebug != null) {
            copyIfPresent(probe, planDebug, "purchasePeriodGoodsDetailRowsCount");
            copyIfPresent(probe, planDebug, "purchasePeriodGoodsDetailQueryMethod");
            copyIfPresent(probe, planDebug, "purchaseGoodsAnchorLineRowsCount");
            copyIfPresent(probe, planDebug, "purchaseGoodsAnchorLineQueryMethod");
            copyIfPresent(probe, planDebug, "noDataReason");
        }
        Map<String, Object> md = state.getMasterBusinessAgentDebug();
        LinkedHashMap<String, Object> next =
                md == null || md.isEmpty() ? new LinkedHashMap<>() : new LinkedHashMap<>(md);
        next.put("purchaseAnswerPlanCardProjection", probe);
        state.setMasterBusinessAgentDebug(next);
    }

    private static void copyIfPresent(
            Map<String, Object> target, Map<String, Object> source, String key) {
        if (source.containsKey(key)) {
            target.put(key, source.get(key));
        }
    }
}
