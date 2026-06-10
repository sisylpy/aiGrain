package com.nongxinle.ai.semantic.intake;

import com.nongxinle.ai.conversation.AiConversationTurnMemory;
import com.nongxinle.ai.conversation.AiQuerySemanticLexicon;
import com.nongxinle.ai.dto.business.GoodsStockBatchDetailAnswerPlan;
import com.nongxinle.ai.dto.business.GoodsSupportedDishCoverAnswerPlan;
import com.nongxinle.ai.semantic.AiQuerySemanticParseResult;
import com.nongxinle.ai.semantic.matrix.WarehouseSemanticCapabilityMatrix;
import org.springframework.util.StringUtils;

import java.util.Locale;

/**
 * Intake：上一轮 GOODS 锚点库存详情（WH-H / WH-K / WH-J）后的裸库存/现量追问。
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

    public static boolean previousTurnDeclaresGoodsAnchorInventory(SemanticIntakeInput input) {
        if (input == null || !input.isHasPreviousTurn()) {
            return false;
        }
        return previousTurnDeclaresGoodsAnchorInventoryFromFlattened(
                input.getPreviousSemanticSlots(), input.getPreviousStructuredIntentDetail());
    }

    public static boolean previousTurnDeclaresGoodsSupportedDishCover(AiConversationTurnMemory previousTurn) {
        return previousTurnDeclaresGoodsAnchorInventoryContract(previousTurn);
    }

    public static boolean previousTurnDeclaresGoodsSupportedDishCover(SemanticIntakeInput input) {
        return previousTurnDeclaresGoodsAnchorInventory(input);
    }

    /** WH-H cover-days 多轮专用：上一轮必须是 WH-H，不含 bundle/WH-J。 */
    public static boolean previousTurnDeclaresGoodsSupportedDishCoverOnly(SemanticIntakeInput input) {
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

    private static boolean previousTurnDeclaresGoodsAnchorInventoryContract(
            AiConversationTurnMemory previousTurn) {
        if (previousTurn == null) {
            return false;
        }
        return previousTurnDeclaresGoodsAnchorInventoryFromFlattened(
                previousTurn.getLastSemanticSlots(), previousTurn.getLastStructuredIntentDetail());
    }

    private static boolean previousTurnDeclaresGoodsAnchorInventoryFromFlattened(
            AiQuerySemanticParseResult.SemanticSlotsPart slots, String previousStructuredIntentDetail) {
        if (slots != null && StringUtils.hasText(slots.getSelectedContractId())) {
            if (isGoodsAnchorInventoryContract(blank(slots.getSelectedContractId()))) {
                return true;
            }
        }
        if (slots != null && StringUtils.hasText(slots.getStructuredIntentDetailWire())) {
            String canon =
                    AiQuerySemanticLexicon.canonicalStructuredIntentDetailWire(
                            slots.getStructuredIntentDetailWire().trim());
            if (isGoodsAnchorInventoryWire(canon)) {
                return true;
            }
        }
        String wire =
                AiQuerySemanticLexicon.canonicalStructuredIntentDetailWire(previousStructuredIntentDetail);
        return isGoodsAnchorInventoryWire(wire);
    }

    private static boolean isGoodsAnchorInventoryContract(String contractId) {
        return GoodsSupportedDishCoverAnswerPlan.CONTRACT_ID.equals(contractId)
                || GoodsStockBatchDetailAnswerPlan.CONTRACT_ID.equals(contractId)
                || WarehouseSemanticCapabilityMatrix.CONTRACT_GOODS_ANCHOR_INVENTORY_BUNDLE.equals(
                        contractId);
    }

    private static boolean isGoodsAnchorInventoryWire(String wire) {
        return AiQuerySemanticLexicon.STRUCTURED_GOODS_SUPPORTED_DISH_COVER.equals(wire)
                || AiQuerySemanticLexicon.STRUCTURED_GOODS_STOCK_BATCH_DETAIL.equals(wire)
                || AiQuerySemanticLexicon.STRUCTURED_GOODS_ANCHOR_INVENTORY_BUNDLE.equals(wire);
    }

    public static boolean intakeSignalsGoodsAnchorStockFollowUp(
            SemanticIntakeResult intake, AiConversationTurnMemory previousTurn) {
        if (intake == null || previousTurn == null) {
            return false;
        }
        if (!previousTurnDeclaresGoodsAnchorInventoryContract(previousTurn)) {
            return false;
        }
        if (SemanticIntakeGoodsSupportedDishCoverSupport.intakeDeclaresGoodsSupportedDishCover(intake)) {
            return false;
        }
        if (SemanticIntakeGoodsStockBatchDetailSupport.intakeDeclaresGoodsStockBatchDetail(intake)) {
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
        if (!input.isHasPreviousTurn() || !previousTurnDeclaresGoodsAnchorInventory(input)) {
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
        if (SemanticIntakeGoodsStockBatchDetailSupport.intakeDeclaresGoodsStockBatchDetail(intake)) {
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
                .coverDaysEntityType(intake.getCoverDaysEntityType())
                .coverDaysEntityName(intake.getCoverDaysEntityName())
                .expiryRiskFilter(intake.getExpiryRiskFilter())
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
