package com.nongxinle.ai.semantic.inheritance;

import com.nongxinle.ai.conversation.AiConversationTurnMemory;
import com.nongxinle.ai.conversation.AiQuerySemanticLexicon;
import com.nongxinle.ai.dto.business.GoodsSupportedDishCoverAnswerPlan;
import com.nongxinle.ai.semantic.AiQuerySemanticParseResult;
import com.nongxinle.ai.semantic.intake.SemanticIntakeGoodsAnchorFollowUpSupport;
import com.nongxinle.ai.semantic.intake.SemanticIntakeResult;
import com.nongxinle.ai.semantic.matrix.WarehouseSemanticCapabilityMatrix;
import com.nongxinle.ai.semantic.matrix.WarehouseSemanticCapabilityMatrixRow;
import org.springframework.util.StringUtils;

/**
 * 同 GOODS 锚点追问：上一轮 {@code warehouse.goods_supported_dish_cover.v1} 稳定 frame，
 * 当前轮裸库存/现量追问（Intake 结构化 follow-up）→ 恢复上一轮合同 + {@code USE_PREVIOUS_ANCHOR}。
 */
public final class GoodsAnchorSameEntityFollowUpSupport {

    public static final String FOLLOW_UP_PATH_GOODS_ANCHOR_SAME_ENTITY = "GOODS_ANCHOR_SAME_ENTITY";

    private GoodsAnchorSameEntityFollowUpSupport() {}

    public static boolean isGoodsAnchorSameEntityFollowUp(
            AiQuerySemanticParseResult current,
            AiConversationTurnMemory previousTurn,
            SemanticIntakeResult intake) {
        if (current == null || previousTurn == null) {
            return false;
        }
        if (!SemanticContractFamilySupport.previousTurnHasStableBusinessFrame(previousTurn)) {
            return false;
        }
        String previousContractId = resolvePreviousStableContractId(previousTurn);
        if (!GoodsSupportedDishCoverAnswerPlan.CONTRACT_ID.equals(previousContractId)) {
            return false;
        }
        if (!SemanticIntakeGoodsAnchorFollowUpSupport.intakeSignalsGoodsAnchorStockFollowUp(
                intake, previousTurn)) {
            return false;
        }
        if (StringUtils.hasText(current.effectiveMentionedGoodsName())
                && previousGoodsName(previousTurn) != null
                && !current.effectiveMentionedGoodsName().trim().equals(previousGoodsName(previousTurn))) {
            return false;
        }
        return true;
    }

    public static String resolvePreviousStableContractId(AiConversationTurnMemory previousTurn) {
        if (previousTurn == null) {
            return null;
        }
        String fromSlots = SemanticContractFamilySupport.contractIdFromPreviousTurn(previousTurn);
        if (StringUtils.hasText(fromSlots)) {
            return fromSlots.trim();
        }
        String wire = resolvePreviousStructuredWire(previousTurn);
        WarehouseSemanticCapabilityMatrixRow row =
                WarehouseSemanticCapabilityMatrix.findFirstTurnRowByWire(wire);
        if (row != null && GoodsSupportedDishCoverAnswerPlan.TYPE.equals(row.getTargetWarehousePlanType())) {
            return GoodsSupportedDishCoverAnswerPlan.CONTRACT_ID;
        }
        return null;
    }

    public static String previousGoodsName(AiConversationTurnMemory previousTurn) {
        if (previousTurn == null) {
            return null;
        }
        if (previousTurn.getLastSemanticSlots() != null
                && StringUtils.hasText(previousTurn.getLastSemanticSlots().getMentionedGoodsName())) {
            return previousTurn.getLastSemanticSlots().getMentionedGoodsName().trim();
        }
        if (previousTurn.getLastResultAnchors() != null) {
            for (var anchor : previousTurn.getLastResultAnchors()) {
                if (anchor == null || !StringUtils.hasText(anchor.getEntityType())) {
                    continue;
                }
                if (!com.nongxinle.ai.dto.business.AiResultAnchor.ENTITY_TYPE_GOODS.equalsIgnoreCase(
                        anchor.getEntityType().trim())) {
                    continue;
                }
                if (StringUtils.hasText(anchor.getEntityName())) {
                    return anchor.getEntityName().trim();
                }
            }
        }
        return null;
    }

    private static String resolvePreviousStructuredWire(AiConversationTurnMemory previousTurn) {
        if (previousTurn.getLastSemanticSlots() != null
                && StringUtils.hasText(
                        previousTurn.getLastSemanticSlots().getStructuredIntentDetailWire())) {
            return AiQuerySemanticLexicon.canonicalStructuredIntentDetailWire(
                    previousTurn.getLastSemanticSlots().getStructuredIntentDetailWire());
        }
        return AiQuerySemanticLexicon.canonicalStructuredIntentDetailWire(
                previousTurn.getLastStructuredIntentDetail());
    }
}
