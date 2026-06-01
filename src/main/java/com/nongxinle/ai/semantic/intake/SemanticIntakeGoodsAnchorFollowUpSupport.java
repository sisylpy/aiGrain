package com.nongxinle.ai.semantic.intake;

import com.nongxinle.ai.conversation.AiConversationTurnMemory;
import com.nongxinle.ai.conversation.AiQuerySemanticLexicon;
import com.nongxinle.ai.dto.business.GoodsSupportedDishCoverAnswerPlan;
import com.nongxinle.ai.semantic.AiQuerySemanticParseResult;
import org.springframework.util.StringUtils;

import java.util.Locale;

/**
 * Intake：上一轮 {@code warehouse.goods_supported_dish_cover.v1} 后的裸库存/现量追问。
 * 仅读结构化 {@code isFollowUp}/{@code usedPreviousContext}/{@code reason}，不读用户原文。
 */
public final class SemanticIntakeGoodsAnchorFollowUpSupport {

    public static final String REASON_MARKER = "goods_anchor_stock_follow_up";

    private SemanticIntakeGoodsAnchorFollowUpSupport() {}

    public static boolean reasonDeclaresGoodsAnchorStockFollowUp(String reason) {
        if (!StringUtils.hasText(reason)) {
            return false;
        }
        String n = reason.trim().toLowerCase(Locale.ROOT);
        return n.contains(REASON_MARKER)
                || n.contains("inherit_previous_goods_anchor")
                || n.contains("goods_stock_follow_up");
    }

    public static boolean previousTurnDeclaresGoodsSupportedDishCover(AiConversationTurnMemory previousTurn) {
        if (previousTurn == null) {
            return false;
        }
        AiQuerySemanticParseResult.SemanticSlotsPart slots = previousTurn.getLastSemanticSlots();
        if (slots != null
                && GoodsSupportedDishCoverAnswerPlan.CONTRACT_ID.equals(
                        blank(slots.getSelectedContractId()))) {
            return true;
        }
        if (slots != null && StringUtils.hasText(slots.getStructuredIntentDetailWire())) {
            String canon =
                    AiQuerySemanticLexicon.canonicalStructuredIntentDetailWire(
                            slots.getStructuredIntentDetailWire().trim());
            return AiQuerySemanticLexicon.STRUCTURED_GOODS_SUPPORTED_DISH_COVER.equals(canon);
        }
        String wire =
                AiQuerySemanticLexicon.canonicalStructuredIntentDetailWire(
                        previousTurn.getLastStructuredIntentDetail());
        return AiQuerySemanticLexicon.STRUCTURED_GOODS_SUPPORTED_DISH_COVER.equals(wire);
    }

    public static boolean previousTurnDeclaresGoodsSupportedDishCover(SemanticIntakeInput input) {
        if (input == null || !input.isHasPreviousTurn()) {
            return false;
        }
        AiQuerySemanticParseResult.SemanticSlotsPart slots = input.getPreviousSemanticSlots();
        if (slots != null
                && GoodsSupportedDishCoverAnswerPlan.CONTRACT_ID.equals(
                        blank(slots.getSelectedContractId()))) {
            return true;
        }
        if (slots != null && StringUtils.hasText(slots.getStructuredIntentDetailWire())) {
            String canon =
                    AiQuerySemanticLexicon.canonicalStructuredIntentDetailWire(
                            slots.getStructuredIntentDetailWire().trim());
            return AiQuerySemanticLexicon.STRUCTURED_GOODS_SUPPORTED_DISH_COVER.equals(canon);
        }
        String wire =
                AiQuerySemanticLexicon.canonicalStructuredIntentDetailWire(
                        input.getPreviousStructuredIntentDetail());
        return AiQuerySemanticLexicon.STRUCTURED_GOODS_SUPPORTED_DISH_COVER.equals(wire);
    }

    /**
     * 当前轮 Intake 是否结构化声明「继承上一轮 GOODS 锚点的库存追问」。
     */
    public static boolean intakeSignalsGoodsAnchorStockFollowUp(
            SemanticIntakeResult intake, AiConversationTurnMemory previousTurn) {
        if (intake == null || previousTurn == null) {
            return false;
        }
        if (!previousTurnDeclaresGoodsSupportedDishCover(previousTurn)) {
            return false;
        }
        if (SemanticIntakeGoodsSupportedDishCoverSupport.intakeDeclaresGoodsSupportedDishCover(intake)) {
            return false;
        }
        if (WarehouseInventoryShortageSemanticsSupport.intakeHasAuthoritativeInventoryRisk(intake)) {
            return false;
        }
        return reasonDeclaresGoodsAnchorStockFollowUp(intake.getReason())
                || (Boolean.TRUE.equals(intake.getIsFollowUp())
                        && Boolean.TRUE.equals(intake.getUsedPreviousContext())
                        && SemanticIntakePrimaryDomain.WAREHOUSE.equals(
                                SemanticIntakePrimaryDomain.normalize(intake.getPrimaryDomain())));
    }

    public static SemanticIntakeResult reconcile(SemanticIntakeInput input, SemanticIntakeResult intake) {
        if (input == null || intake == null || intake.getStatus() == SemanticIntakeStatus.INVALID) {
            return intake;
        }
        if (!input.isHasPreviousTurn() || !previousTurnDeclaresGoodsSupportedDishCover(input)) {
            return intake;
        }
        if (!shouldPromoteGoodsAnchorStockFollowUp(input, intake)) {
            return intake;
        }
        return promoteWarehouseGoodsAnchorFollowUp(intake);
    }

    private static boolean shouldPromoteGoodsAnchorStockFollowUp(
            SemanticIntakeInput input, SemanticIntakeResult intake) {
        if (SemanticIntakeGoodsSupportedDishCoverSupport.intakeDeclaresGoodsSupportedDishCover(intake)) {
            return false;
        }
        if (WarehouseInventoryShortageSemanticsSupport.intakeHasAuthoritativeInventoryRisk(intake)) {
            return false;
        }
        if (reasonDeclaresGoodsAnchorStockFollowUp(intake.getReason())) {
            return true;
        }
        if (!Boolean.TRUE.equals(intake.getIsFollowUp())) {
            return false;
        }
        String primary = SemanticIntakePrimaryDomain.normalize(intake.getPrimaryDomain());
        if (!SemanticIntakePrimaryDomain.WAREHOUSE.equals(primary)) {
            return false;
        }
        if (Boolean.TRUE.equals(intake.getUsedPreviousContext())) {
            return true;
        }
        return intake.getNormalizationType() == SemanticIntakeNormalizationType.REWRITE;
    }

    private static SemanticIntakeResult promoteWarehouseGoodsAnchorFollowUp(SemanticIntakeResult intake) {
        String reason = trim(intake.getReason());
        if (!StringUtils.hasText(reason)) {
            reason = REASON_MARKER;
        } else if (!reasonDeclaresGoodsAnchorStockFollowUp(reason)) {
            reason = reason + ";" + REASON_MARKER;
        }
        return SemanticIntakeResult.builder()
                .status(SemanticIntakeStatus.READY)
                .questionMode(intake.getQuestionMode())
                .normalizationType(intake.getNormalizationType())
                .canonicalUserQuery(intake.getCanonicalUserQuery())
                .isFollowUp(true)
                .usedPreviousContext(true)
                .primaryDomain(SemanticIntakePrimaryDomain.WAREHOUSE)
                .candidateDomains(intake.getCandidateDomains())
                .routeType("INHERITED")
                .confidence(intake.getConfidence())
                .needClarification(false)
                .clarificationQuestion(null)
                .reason(reason)
                .warehouseInventorySemantics(null)
                .subQuestions(intake.getSubQuestions())
                .promptId(intake.getPromptId())
                .llmRawText(intake.getLlmRawText())
                .parseError(intake.getParseError())
                .intakeRepairAttempted(intake.getIntakeRepairAttempted())
                .intakeRepairSuccess(intake.getIntakeRepairSuccess())
                .intakeRepairReason(intake.getIntakeRepairReason())
                .build();
    }

    private static String blank(String s) {
        return s == null ? null : s.trim();
    }

    private static String trim(String s) {
        return StringUtils.hasText(s) ? s.trim() : null;
    }
}
