package com.nongxinle.ai.semantic;

import com.nongxinle.ai.conversation.AiConversationTurnMemory;
import com.nongxinle.ai.conversation.AiQuerySemanticLexicon;
import com.nongxinle.ai.dto.business.GoodsSupportedDishCoverAnswerPlan;
import com.nongxinle.ai.semantic.contract.SemanticContractCompletionEngine;
import com.nongxinle.ai.semantic.intake.SemanticIntakeInput;
import com.nongxinle.ai.semantic.matrix.DishCostAnalysisSemanticCapabilityMatrix;
import com.nongxinle.ai.semantic.matrix.DishCostAnalysisSemanticCapabilityMatrixRow;
import com.nongxinle.ai.semantic.matrix.WarehouseSemanticCapabilityMatrix;
import com.nongxinle.ai.semantic.matrix.WarehouseSemanticCapabilityMatrixRow;
import org.springframework.util.StringUtils;

/**
 * 库存快照 + 独立销量基线合同族：{@code dish.ingredient_cover_days.v1} /
 * {@code warehouse.goods_supported_dish_cover.v1} /
 * {@code warehouse.goods_anchor_inventory_bundle.v1}（WH-K cover 子计划）。只读 contractId / wire。
 */
public final class InventoryCoverDaysContractSupport {

    private InventoryCoverDaysContractSupport() {}

    public static boolean isInventoryCoverDaysContractId(String contractId) {
        if (!StringUtils.hasText(contractId)) {
            return false;
        }
        String id = contractId.trim();
        return DishCostAnalysisSemanticCapabilityMatrix.CONTRACT_DISH_INGREDIENT_COVER_DAYS.equals(id)
                || WarehouseSemanticCapabilityMatrix.CONTRACT_GOODS_SUPPORTED_DISH_COVER.equals(id)
                || WarehouseSemanticCapabilityMatrix.CONTRACT_GOODS_ANCHOR_INVENTORY_BUNDLE.equals(id);
    }

    public static boolean parseSelectsInventoryCoverDaysContract(AiQuerySemanticParseResult parse) {
        return isInventoryCoverDaysContractId(
                SemanticContractCompletionEngine.extractSelectedContractId(parse));
    }

    public static String resolveInventoryCoverDaysContractIdFromIntakeInput(SemanticIntakeInput input) {
        if (input == null || !input.isHasPreviousTurn()) {
            return null;
        }
        String fromSlots =
                input.getPreviousSemanticSlots() != null
                        ? input.getPreviousSemanticSlots().getSelectedContractId()
                        : null;
        if (isInventoryCoverDaysContractId(fromSlots)) {
            return fromSlots.trim();
        }
        String wire =
                input.getPreviousSemanticSlots() != null
                        ? input.getPreviousSemanticSlots().getStructuredIntentDetailWire()
                        : input.getPreviousStructuredIntentDetail();
        return resolveInventoryCoverDaysContractIdFromWire(wire);
    }

    public static String resolveInventoryCoverDaysContractIdFromPreviousTurn(
            AiConversationTurnMemory previousTurn) {
        if (previousTurn == null) {
            return null;
        }
        String fromSlots =
                previousTurn.getLastSemanticSlots() != null
                        ? previousTurn.getLastSemanticSlots().getSelectedContractId()
                        : null;
        if (isInventoryCoverDaysContractId(fromSlots)) {
            return fromSlots.trim();
        }
        String wire =
                previousTurn.getLastSemanticSlots() != null
                        ? previousTurn.getLastSemanticSlots().getStructuredIntentDetailWire()
                        : previousTurn.getLastStructuredIntentDetail();
        return resolveInventoryCoverDaysContractIdFromWire(wire);
    }

    private static String resolveInventoryCoverDaysContractIdFromWire(String wire) {
        if (!StringUtils.hasText(wire)) {
            return null;
        }
        String canon = AiQuerySemanticLexicon.canonicalStructuredIntentDetailWire(wire.trim());
        if (AiQuerySemanticLexicon.isStructuredDishIngredientCoverDaysDetail(canon)) {
            return DishCostAnalysisSemanticCapabilityMatrix.CONTRACT_DISH_INGREDIENT_COVER_DAYS;
        }
        if (AiQuerySemanticLexicon.STRUCTURED_GOODS_SUPPORTED_DISH_COVER.equals(canon)) {
            return WarehouseSemanticCapabilityMatrix.CONTRACT_GOODS_SUPPORTED_DISH_COVER;
        }
        if (AiQuerySemanticLexicon.STRUCTURED_GOODS_ANCHOR_INVENTORY_BUNDLE.equals(canon)) {
            return WarehouseSemanticCapabilityMatrix.CONTRACT_GOODS_ANCHOR_INVENTORY_BUNDLE;
        }
        DishCostAnalysisSemanticCapabilityMatrixRow dishRow =
                DishCostAnalysisSemanticCapabilityMatrix.findFirstTurnRowByWire(wire.trim());
        if (dishRow != null
                && DishCostAnalysisSemanticCapabilityMatrix.CONTRACT_DISH_INGREDIENT_COVER_DAYS.equals(
                        dishRow.getCapabilityId())) {
            return dishRow.getCapabilityId();
        }
        WarehouseSemanticCapabilityMatrixRow whRow =
                WarehouseSemanticCapabilityMatrix.findFirstTurnRowByWire(wire.trim());
        if (whRow != null
                && AiQuerySemanticLexicon.STRUCTURED_GOODS_ANCHOR_INVENTORY_BUNDLE.equals(
                        whRow.getStructuredIntentDetailWire())) {
            return WarehouseSemanticCapabilityMatrix.CONTRACT_GOODS_ANCHOR_INVENTORY_BUNDLE;
        }
        if (whRow != null
                && GoodsSupportedDishCoverAnswerPlan.TYPE.equals(whRow.getTargetWarehousePlanType())) {
            return WarehouseSemanticCapabilityMatrix.CONTRACT_GOODS_SUPPORTED_DISH_COVER;
        }
        return null;
    }
}
