package com.nongxinle.ai.semantic.inheritance;

import com.nongxinle.ai.conversation.AiConversationTurnMemory;
import com.nongxinle.ai.conversation.AiQuerySemanticLexicon;
import com.nongxinle.ai.semantic.AiQuerySemanticParseResult;
import com.nongxinle.ai.semantic.intake.SemanticIntakeDishIngredientCoverDaysSupport;
import com.nongxinle.ai.semantic.intake.SemanticIntakeGoodsSupportedDishCoverSupport;
import com.nongxinle.ai.semantic.intake.SemanticIntakeResult;
import com.nongxinle.ai.semantic.matrix.DishCostAnalysisSemanticCapabilityMatrix;
import com.nongxinle.ai.semantic.matrix.DishCostAnalysisSemanticCapabilityMatrixRow;
import com.nongxinle.ai.semantic.matrix.WarehouseSemanticCapabilityMatrix;
import org.springframework.util.StringUtils;

import java.util.Locale;

/**
 * 同能力换菜名追问：上一轮稳定 Business Frame + Intake 结构化 reason 对齐同一 DISH_COST 子合同，
 * 仅替换 {@code mentionedDishName}，禁止 V2 弱选 {@code dish_cost.single_dish_analysis} 抢主权。
 */
public final class NamedEntitySameCapabilityFollowUpSupport {

    public static final String FOLLOW_UP_PATH_NAMED_ENTITY_SAME_CAPABILITY =
            "NAMED_ENTITY_SAME_CAPABILITY";

    private NamedEntitySameCapabilityFollowUpSupport() {}

    /**
     * 是否应从上一轮恢复完整 Business Frame（合同 + wire），并保留当前轮结构化菜名。
     */
    public static boolean isNamedEntitySameCapabilityFollowUp(
            AiQuerySemanticParseResult current,
            AiConversationTurnMemory previousTurn,
            SemanticIntakeResult intake,
            boolean explicitEntityFollowUp) {
        if (!explicitEntityFollowUp || current == null || previousTurn == null) {
            return false;
        }
        if (!SemanticContractFamilySupport.previousTurnHasStableBusinessFrame(previousTurn)) {
            return false;
        }
        String previousContractId = resolvePreviousStableContractId(previousTurn);
        if (!StringUtils.hasText(previousContractId)) {
            return false;
        }
        if (DishCostAnalysisSemanticCapabilityMatrix.findByContractId(previousContractId) == null) {
            return false;
        }
        String intakeDeclared = contractIdDeclaredByIntake(intake);
        if (!StringUtils.hasText(intakeDeclared)) {
            return false;
        }
        return previousContractId.trim().equals(intakeDeclared.trim());
    }

    /** 上一轮稳定合同 id（slots.contractId 优先，否则 wire → matrix）。 */
    public static String resolvePreviousStableContractId(AiConversationTurnMemory previousTurn) {
        if (previousTurn == null) {
            return null;
        }
        String fromSlots = SemanticContractFamilySupport.contractIdFromPreviousTurn(previousTurn);
        if (StringUtils.hasText(fromSlots)) {
            return fromSlots.trim();
        }
        String wire = resolvePreviousStructuredWire(previousTurn);
        DishCostAnalysisSemanticCapabilityMatrixRow row =
                DishCostAnalysisSemanticCapabilityMatrix.findFirstTurnRowByWire(wire);
        return row != null ? row.getCapabilityId() : null;
    }

    /**
     * Intake 结构化 reason 声明的本轮目标合同（仅 DISH_COST 三子能力，不读用户原文）。
     */
    public static String contractIdDeclaredByIntake(SemanticIntakeResult intake) {
        if (intake == null) {
            return null;
        }
        if (SemanticIntakeGoodsSupportedDishCoverSupport.intakeDeclaresGoodsSupportedDishCover(intake)) {
            return WarehouseSemanticCapabilityMatrix.CONTRACT_GOODS_SUPPORTED_DISH_COVER;
        }
        if (SemanticIntakeDishIngredientCoverDaysSupport.intakeDeclaresDishIngredientCoverDays(intake)) {
            return DishCostAnalysisSemanticCapabilityMatrix.CONTRACT_DISH_INGREDIENT_COVER_DAYS;
        }
        String reason = intake.getReason();
        if (!StringUtils.hasText(reason)) {
            return null;
        }
        String normalized = reason.trim().toLowerCase(Locale.ROOT);
        if (declaresProfitPrescription(normalized)) {
            return DishCostAnalysisSemanticCapabilityMatrix.CONTRACT_DISH_PROFIT_PRESCRIPTION;
        }
        if (declaresSingleDishCost(normalized)) {
            return DishCostAnalysisSemanticCapabilityMatrix.CONTRACT_DISH_COST_SINGLE;
        }
        return null;
    }

    private static boolean declaresProfitPrescription(String normalizedReason) {
        return normalizedReason.contains("profit_prescription")
                || normalizedReason.contains("dish_profit_prescription")
                || normalizedReason.contains("target_margin_prescription");
    }

    private static boolean declaresSingleDishCost(String normalizedReason) {
        if (normalizedReason.contains("ingredient_cover")) {
            return false;
        }
        return normalizedReason.contains("named_dish_cost")
                || normalizedReason.contains("single_dish_cost")
                || normalizedReason.contains("named_dish_cost_explicit");
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
