package com.nongxinle.ai.semantic.frame;

import com.nongxinle.ai.conversation.AiConversationTurnMemory;
import com.nongxinle.ai.dto.business.AiResultAnchor;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * anchorPolicy=USE_PREVIOUS_ANCHOR 时：判断上一轮是否存在可作为追问锚的唯一明确实体（口述 ID 或名称）。
 */
public final class PurchaseSemanticAnchorGate {

    private PurchaseSemanticAnchorGate() {}

    public static boolean hasUniqueExplicitPurchaseAnchor(AiConversationTurnMemory previousTurn) {
        if (previousTurn == null || previousTurn.getLastResultAnchors() == null) {
            return false;
        }
        List<AiResultAnchor> typed = new ArrayList<>();
        for (AiResultAnchor a : previousTurn.getLastResultAnchors()) {
            if (a == null || !StringUtils.hasText(a.getEntityType())) {
                continue;
            }
            String et = a.getEntityType().trim();
            if (!AiResultAnchor.ENTITY_TYPE_GOODS.equalsIgnoreCase(et)
                    && !AiResultAnchor.ENTITY_TYPE_SUPPLIER.equalsIgnoreCase(et)) {
                continue;
            }
            if (!StringUtils.hasText(a.getEntityId()) && !StringUtils.hasText(a.getEntityName())) {
                continue;
            }
            typed.add(a);
        }
        if (typed.isEmpty()) {
            return false;
        }
        if (typed.size() == 1) {
            return true;
        }
        long rankOne =
                typed.stream()
                        .filter(a -> a.getRank() != null && a.getRank() == 1)
                        .count();
        return rankOne == 1;
    }

    /**
     * GOODS 维度追问（如按采购来源拆桶）：只允许 GOODS 锚参与唯一性判定（不接受供货商锚顶替）。
     */
    public static boolean hasUniqueExplicitGoodsAnchor(AiConversationTurnMemory previousTurn) {
        if (previousTurn == null || previousTurn.getLastResultAnchors() == null) {
            return false;
        }
        List<AiResultAnchor> goods = new ArrayList<>();
        for (AiResultAnchor a : previousTurn.getLastResultAnchors()) {
            if (a == null || !StringUtils.hasText(a.getEntityType())) {
                continue;
            }
            if (!AiResultAnchor.ENTITY_TYPE_GOODS.equalsIgnoreCase(a.getEntityType().trim())) {
                continue;
            }
            if (!StringUtils.hasText(a.getEntityId()) && !StringUtils.hasText(a.getEntityName())) {
                continue;
            }
            goods.add(a);
        }
        if (goods.isEmpty()) {
            return false;
        }
        if (goods.size() == 1) {
            return true;
        }
        long rankOne =
                goods.stream()
                        .filter(a -> a.getRank() != null && a.getRank() == 1)
                        .count();
        return rankOne == 1;
    }
}
