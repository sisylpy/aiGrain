package com.nongxinle.ai.semantic.intake;

import com.nongxinle.ai.context.AiResolvedQueryIntent;
import com.nongxinle.ai.conversation.AiQuerySemanticLexicon;
import com.nongxinle.ai.semantic.contract.SemanticContractCompletionEngine;
import com.nongxinle.ai.semantic.AiQuerySemanticParseResult;
import com.nongxinle.ai.semantic.intake.grounding.CoverDaysEntityGroundingService;
import com.nongxinle.ai.semantic.intake.grounding.CoverDaysEntityType;
import com.nongxinle.ai.semantic.intake.llm.LlmSemanticIntakeParsed;
import com.nongxinle.ai.semantic.matrix.DishCostAnalysisSemanticCapabilityMatrix;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Locale;

/**
 * Intake 后处理：单菜配料可支撑天数（{@code dish.ingredient_cover_days.v1}）与库房库存风险语义边界。
 * <p>仅读 Intake 结构化字段（{@code reason} marker、{@code primaryDomain}、{@code warehouseInventorySemantics}、
 * {@code candidateDomains}）；不解析用户原文。多轮从 WAREHOUSE 切到 DISH_COST 时清除库房风险 marker，避免窄化 V2 allowed 合同。
 */
public final class SemanticIntakeDishIngredientCoverDaysSupport {

    /** Intake / V2 reason 自报：单菜配料可支撑天数（见 semantic_intake.v1 §34a）。 */
    public static final String REASON_MARKER = "dish_ingredient_cover_days";

    /**
     * LLM 误把「够用几天」语义写进 {@code warehouseInventorySemantics} 时的过渡枚举（非库房风险）。
     * Java 识别后注入 {@link #REASON_MARKER}，不得进入 {@link WarehouseInventoryShortageSemanticsSupport} 窄化链。
     */
    private static final List<String> WAREHOUSE_FIELD_DISH_COVER_MISLABELS =
            List.of(
                    "STOCK_DAYS",
                    "INGREDIENT_COVER_DAYS",
                    "DISH_INGREDIENT_COVER_DAYS",
                    "DISH_COVER_DAYS",
                    "COVER_DAYS",
                    "INGREDIENT_COVER",
                    "DISH_STOCK_DAYS",
                    "SELL_DAYS",
                    "DAYS_OF_STOCK");

    private SemanticIntakeDishIngredientCoverDaysSupport() {}

    public static boolean reasonDeclaresDishIngredientCoverDays(String reason) {
        if (!StringUtils.hasText(reason)) {
            return false;
        }
        String normalized = reason.trim().toLowerCase(Locale.ROOT);
        return normalized.contains(REASON_MARKER)
                || normalized.contains("ingredient_cover_days")
                || normalized.contains("named_dish_ingredient_cover");
    }

    /**
     * 原始 JSON {@code warehouseInventorySemantics}（normalize 前）是否实为菜品配料可支撑天数误标。
     */
    public static boolean rawWarehouseSemanticsDeclaresDishCoverMislabel(String rawWarehouseSemantics) {
        if (!StringUtils.hasText(rawWarehouseSemantics)) {
            return false;
        }
        String n = rawWarehouseSemantics.trim().toUpperCase(Locale.ROOT);
        for (String mislabel : WAREHOUSE_FIELD_DISH_COVER_MISLABELS) {
            if (mislabel.equals(n)) {
                return true;
            }
        }
        return n.contains("COVER_DAYS") || n.contains("INGREDIENT_COVER");
    }

    public static String appendDishCoverReasonMarker(String reason) {
        if (reasonDeclaresDishIngredientCoverDays(reason)) {
            return StringUtils.hasText(reason) ? reason.trim() : REASON_MARKER;
        }
        if (!StringUtils.hasText(reason)) {
            return REASON_MARKER;
        }
        return reason.trim() + ";" + REASON_MARKER;
    }

    public static boolean parsedDeclaresDishIngredientCoverDays(LlmSemanticIntakeParsed parsed) {
        if (parsed == null) {
            return false;
        }
        if (SemanticIntakeGoodsSupportedDishCoverSupport.parsedDeclaresGoodsSupportedDishCover(parsed)) {
            return false;
        }
        if (WarehouseInventoryShortageSemanticsSupport.parsedRawInventoryRiskSemantics(parsed)) {
            return false;
        }
        if (reasonDeclaresDishIngredientCoverDays(parsed.getReason())) {
            return true;
        }
        if (rawWarehouseSemanticsDeclaresDishCoverMislabel(parsed.getWarehouseInventorySemantics())) {
            String primary = SemanticIntakePrimaryDomain.normalize(parsed.getPrimaryDomain());
            return !SemanticIntakePrimaryDomain.WAREHOUSE.equals(primary);
        }
        return false;
    }

    public static boolean intakeDeclaresDishIngredientCoverDays(SemanticIntakeResult intake) {
        if (intake == null) {
            return false;
        }
        if (SemanticIntakeGoodsSupportedDishCoverSupport.intakeDeclaresGoodsSupportedDishCover(intake)) {
            return false;
        }
        if (WarehouseInventoryShortageSemanticsSupport.intakeHasAuthoritativeInventoryRisk(intake)) {
            return false;
        }
        if (reasonDeclaresDishIngredientCoverDays(intake.getReason())) {
            return true;
        }
        if (rawWarehouseSemanticsDeclaresDishCoverMislabel(intake.getWarehouseInventorySemantics())) {
            String primary = SemanticIntakePrimaryDomain.normalize(intake.getPrimaryDomain());
            return !SemanticIntakePrimaryDomain.WAREHOUSE.equals(primary);
        }
        return false;
    }

    /**
     * 上一轮稳定合约为单菜配料可支撑天数（structured wire / contractId），供跨域 follow-up 门禁。
     */
    public static boolean previousTurnDeclaresDishIngredientCoverDays(SemanticIntakeInput input) {
        if (input == null || !input.isHasPreviousTurn()) {
            return false;
        }
        String structured = trim(input.getPreviousStructuredIntentDetail());
        if (StringUtils.hasText(structured)) {
            String canon =
                    AiQuerySemanticLexicon.canonicalStructuredIntentDetailWire(structured);
            if (AiQuerySemanticLexicon.isStructuredDishIngredientCoverDaysDetail(canon)) {
                return true;
            }
        }
        AiQuerySemanticParseResult.SemanticSlotsPart prevSlots = input.getPreviousSemanticSlots();
        if (prevSlots != null) {
            if (DishCostAnalysisSemanticCapabilityMatrix.CONTRACT_DISH_INGREDIENT_COVER_DAYS.equals(
                    blank(prevSlots.getSelectedContractId()))) {
                return true;
            }
            if (StringUtils.hasText(prevSlots.getStructuredIntentDetailWire())) {
                String canon =
                        AiQuerySemanticLexicon.canonicalStructuredIntentDetailWire(
                                prevSlots.getStructuredIntentDetailWire().trim());
                if (AiQuerySemanticLexicon.isStructuredDishIngredientCoverDaysDetail(canon)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * 多轮：上一轮单菜配料 cover → 当前轮库房库存风险（无本轮点菜名/同菜追问），不得继承 DISH_COST frame。
     */
    public static boolean isDishCoverToWarehouseInventoryRiskCrossFollowUp(
            SemanticIntakeInput input, SemanticIntakeResult intake) {
        if (input == null || intake == null || !input.isHasPreviousTurn()) {
            return false;
        }
        if (!previousTurnDeclaresDishIngredientCoverDays(input)) {
            return false;
        }
        return WarehouseInventoryShortageSemanticsSupport.intakeHasAuthoritativeInventoryRisk(intake);
    }

    /**
     * 为 true 时，库房库存风险 Intake/V2 窄化与澄清兜底不得介入（当前轮菜品配料可支撑天数主权）。
     */
    public static boolean mustNotApplyWarehouseInventoryShortagePipeline(SemanticIntakeResult intake) {
        return intakeDeclaresDishIngredientCoverDays(intake);
    }

    /**
     * V2 已锁定 {@code dish.ingredient_cover_days.v1} 时，即使 Intake 仍带库房风险 marker 也不得走库房澄清兜底。
     */
    public static boolean mustNotApplyWarehouseInventoryShortagePipeline(
            SemanticIntakeResult intake, AiQuerySemanticParseResult completedParse) {
        if (mustNotApplyWarehouseInventoryShortagePipeline(intake)) {
            return true;
        }
        if (completedParse == null) {
            return false;
        }
        String selected = SemanticContractCompletionEngine.extractSelectedContractId(completedParse);
        return DishCostAnalysisSemanticCapabilityMatrix.CONTRACT_DISH_INGREDIENT_COVER_DAYS.equals(
                blank(selected));
    }

    /**
     * Intake reconcile：纠正 WAREHOUSE+风险/STOCK_DAYS 误标，或巩固 DISH_COST 并清空 {@code warehouseInventorySemantics}。
     */
    public static SemanticIntakeResult reconcile(SemanticIntakeInput input, SemanticIntakeResult intake) {
        if (intake == null || intake.getStatus() == SemanticIntakeStatus.INVALID) {
            return intake;
        }
        if (!shouldReconcileToDishCost(input, intake)) {
            return intake;
        }
        return promoteDishCostReady(intake, input);
    }

    public static void collectDishIngredientCoverProtocolErrors(
            LlmSemanticIntakeParsed parsed, List<String> errors) {
        if (parsed == null || errors == null) {
            return;
        }
        if (rawWarehouseSemanticsDeclaresDishCoverMislabel(parsed.getWarehouseInventorySemantics())) {
            errors.add(
                    "dish_ingredient_cover_days: warehouseInventorySemantics="
                            + parsed.getWarehouseInventorySemantics().trim()
                            + " is dish-cover mislabel; use primaryDomain=DISH_COST and reason="
                            + REASON_MARKER
                            + " (§34a)");
        }
        if (reasonDeclaresDishIngredientCoverDays(parsed.getReason())) {
            String primary = SemanticIntakePrimaryDomain.normalize(parsed.getPrimaryDomain());
            if (SemanticIntakePrimaryDomain.PURCHASE.equals(primary)) {
                errors.add(
                        "dish_ingredient_cover_days: primaryDomain must be DISH_COST, not PURCHASE (§34a)");
            }
            if (SemanticIntakePrimaryDomain.WAREHOUSE.equals(primary)) {
                errors.add(
                        "dish_ingredient_cover_days: primaryDomain must be DISH_COST, not WAREHOUSE (§34a)");
            }
            if (SemanticIntakePrimaryDomain.DISH_SALES.equals(primary)) {
                errors.add(
                        "dish_ingredient_cover_days: primaryDomain must be DISH_COST, not DISH_SALES (§34a/§34d)");
            }
            if (WarehouseInventoryShortageSemanticsSupport.parsedDeclaresInventoryRisk(parsed)) {
                errors.add(
                        "dish_ingredient_cover_days: warehouseInventorySemantics must be empty (§34a)");
            }
            if (parsed.isNeedClarification()) {
                errors.add(
                        "dish_ingredient_cover_days: needClarification must be false for named-dish cover days (§34a)");
            }
            return;
        }
        if (!parsedDeclaresDishIngredientCoverDays(parsed)) {
            return;
        }
        String primary = SemanticIntakePrimaryDomain.normalize(parsed.getPrimaryDomain());
        if (SemanticIntakePrimaryDomain.WAREHOUSE.equals(primary)) {
            errors.add(
                    "dish_ingredient_cover_days: primaryDomain must be DISH_COST, not WAREHOUSE (§34a)");
        }
        if (WarehouseInventoryShortageSemanticsSupport.parsedDeclaresInventoryRisk(parsed)) {
            errors.add(
                    "dish_ingredient_cover_days: warehouseInventorySemantics must be empty (§34a)");
        }
        if (parsed.isNeedClarification()) {
            errors.add(
                    "dish_ingredient_cover_days: needClarification must be false for named-dish cover days (§34a)");
        }
    }

    private static boolean shouldReconcileToDishCost(
            SemanticIntakeInput input, SemanticIntakeResult intake) {
        if (SemanticIntakeGoodsSupportedDishCoverSupport.intakeDeclaresGoodsSupportedDishCover(intake)) {
            return false;
        }
        if (CoverDaysEntityGroundingService.hasCoverDaysEntityGroundingSignals(intake)) {
            if (CoverDaysEntityType.DISH.equals(
                    CoverDaysEntityType.normalize(intake.getCoverDaysEntityType()))) {
                return true;
            }
            if (intakeDeclaresDishIngredientCoverDays(intake)) {
                return true;
            }
            return false;
        }
        if (isDishCoverToWarehouseInventoryRiskCrossFollowUp(input, intake)) {
            return false;
        }
        if (intakeDeclaresDishIngredientCoverDays(intake)) {
            return true;
        }
        return isCrossDomainWarehouseToDishCoverFollowUp(input, intake);
    }

    private static SemanticIntakeResult promoteDishCostReady(
            SemanticIntakeResult intake, SemanticIntakeInput input) {
        String reason = appendDishCoverReasonMarker(intake.getReason());
        boolean followUp = Boolean.TRUE.equals(intake.getIsFollowUp());
        boolean usedPrevious =
                Boolean.TRUE.equals(intake.getUsedPreviousContext())
                        || (input != null && input.isHasPreviousTurn() && followUp);
        return SemanticIntakeResult.builder()
                .status(SemanticIntakeStatus.READY)
                .questionMode(intake.getQuestionMode())
                .normalizationType(intake.getNormalizationType())
                .canonicalUserQuery(intake.getCanonicalUserQuery())
                .isFollowUp(followUp)
                .usedPreviousContext(usedPrevious)
                .primaryDomain(SemanticIntakePrimaryDomain.DISH_COST)
                .candidateDomains(List.of(SemanticIntakePrimaryDomain.DISH_COST))
                .routeType("EXPLICIT")
                .confidence(intake.getConfidence())
                .needClarification(false)
                .clarificationQuestion(null)
                .reason(reason)
                .warehouseInventorySemantics(null)
                .coverDaysEntityType(intake.getCoverDaysEntityType())
                .coverDaysEntityName(intake.getCoverDaysEntityName())
                .subQuestions(intake.getSubQuestions())
                .promptId(intake.getPromptId())
                .llmRawText(intake.getLlmRawText())
                .parseError(intake.getParseError())
                .intakeRepairAttempted(intake.getIntakeRepairAttempted())
                .intakeRepairSuccess(intake.getIntakeRepairSuccess())
                .intakeRepairReason(intake.getIntakeRepairReason())
                .failureCode(intake.getFailureCode())
                .failureStage(intake.getFailureStage())
                .build();
    }

    /**
     * 多轮：上一轮库房路径 + 当前轮结构化菜品配料可支撑天数信号 → 必须切 DISH_COST（仅继承 time/scope）。
     */
    public static boolean isCrossDomainWarehouseToDishCoverFollowUp(
            SemanticIntakeInput input, SemanticIntakeResult intake) {
        if (input == null || intake == null || !input.isHasPreviousTurn()) {
            return false;
        }
        if (!Boolean.TRUE.equals(intake.getIsFollowUp())) {
            return false;
        }
        if (!AiResolvedQueryIntent.PATH_WAREHOUSE_STOCK.equals(trim(input.getPreviousPathCode()))) {
            return false;
        }
        String primary = SemanticIntakePrimaryDomain.normalize(intake.getPrimaryDomain());
        if (!SemanticIntakePrimaryDomain.WAREHOUSE.equals(primary)) {
            return false;
        }
        if (WarehouseInventoryShortageSemanticsSupport.intakeExplicitAmountRankingLow(intake)) {
            return false;
        }
        return reasonDeclaresDishIngredientCoverDays(intake.getReason());
    }

    private static String blank(String s) {
        return s == null ? null : s.trim();
    }

    private static String trim(String s) {
        return StringUtils.hasText(s) ? s.trim() : null;
    }
}
