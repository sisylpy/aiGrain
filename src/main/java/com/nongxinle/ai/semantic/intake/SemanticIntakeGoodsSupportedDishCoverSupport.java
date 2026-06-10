package com.nongxinle.ai.semantic.intake;

import com.nongxinle.ai.semantic.AiQuerySemanticParseResult;
import com.nongxinle.ai.semantic.SemanticParserAllowedOutputContract;
import com.nongxinle.ai.semantic.contract.DomainContractSelectionResult;
import com.nongxinle.ai.semantic.contract.SemanticContractCompletionEngine;
import com.nongxinle.ai.semantic.intake.grounding.CoverDaysEntityGroundingService;
import com.nongxinle.ai.semantic.intake.grounding.CoverDaysEntityType;
import com.nongxinle.ai.semantic.intake.llm.LlmSemanticIntakeParsed;
import com.nongxinle.ai.semantic.matrix.WarehouseSemanticCapabilityMatrix;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Intake 后处理：原料 → 受影响菜品（{@code warehouse.goods_supported_dish_cover.v1}）与
 * 单菜配料可支撑天数 / 库房风险 / 金额排行边界。
 */
public final class SemanticIntakeGoodsSupportedDishCoverSupport {

    public static final String REASON_MARKER = "goods_supported_dish_cover";

    private SemanticIntakeGoodsSupportedDishCoverSupport() {}

    public static boolean reasonDeclaresGoodsSupportedDishCover(String reason) {
        if (!StringUtils.hasText(reason)) {
            return false;
        }
        String n = reason.trim().toLowerCase(Locale.ROOT);
        return n.contains(REASON_MARKER) || n.contains("goods_supported_dish");
    }

    public static boolean parsedDeclaresGoodsSupportedDishCover(LlmSemanticIntakeParsed parsed) {
        if (parsed == null) {
            return false;
        }
        return reasonDeclaresGoodsSupportedDishCover(parsed.getReason());
    }

    public static boolean intakeDeclaresGoodsSupportedDishCover(SemanticIntakeResult intake) {
        if (intake == null) {
            return false;
        }
        return reasonDeclaresGoodsSupportedDishCover(intake.getReason());
    }

    /**
     * Intake reconcile：巩固 WH-H（{@code warehouse.goods_supported_dish_cover.v1}），
     * 纠正 LLM 误标 {@code DISH_COST}/{@code PURCHASE} 或库房 cover 误标字段。
     * 必须在 {@link SemanticIntakeDishIngredientCoverDaysSupport#reconcile} 之前执行。
     */
    public static SemanticIntakeResult reconcile(SemanticIntakeInput input, SemanticIntakeResult intake) {
        if (intake == null || intake.getStatus() == SemanticIntakeStatus.INVALID) {
            return intake;
        }
        if (!shouldReconcileToGoodsSupportedDishCover(input, intake)) {
            return intake;
        }
        return promoteWarehouseGoodsCoverReady(intake);
    }

    /**
     * Adoption 存在性落地后：将 Intake canonical 为 WH-H 单商品库存详情（不读用户原文）。
     */
    public static SemanticIntakeResult canonicalizeForNamedGoodsDetail(
            SemanticIntakeResult intake, String entityName, String entityType) {
        if (!StringUtils.hasText(entityName)) {
            return intake;
        }
        SemanticIntakeResult base = intake;
        if (base == null) {
            base =
                    SemanticIntakeResult.builder()
                            .status(SemanticIntakeStatus.READY)
                            .primaryDomain(SemanticIntakePrimaryDomain.WAREHOUSE)
                            .candidateDomains(List.of(SemanticIntakePrimaryDomain.WAREHOUSE))
                            .routeType("EXPLICIT")
                            .build();
        }
        SemanticIntakeResult withEntity =
                SemanticIntakeResult.builder()
                        .status(base.getStatus())
                        .questionMode(base.getQuestionMode())
                        .normalizationType(base.getNormalizationType())
                        .canonicalUserQuery(base.getCanonicalUserQuery())
                        .isFollowUp(base.getIsFollowUp())
                        .usedPreviousContext(base.getUsedPreviousContext())
                        .primaryDomain(SemanticIntakePrimaryDomain.WAREHOUSE)
                        .candidateDomains(List.of(SemanticIntakePrimaryDomain.WAREHOUSE))
                        .routeType(StringUtils.hasText(base.getRouteType()) ? base.getRouteType() : "EXPLICIT")
                        .confidence(base.getConfidence())
                        .needClarification(false)
                        .clarificationQuestion(null)
                        .reason(base.getReason())
                        .warehouseInventorySemantics(null)
                        .coverDaysEntityType(
                                StringUtils.hasText(entityType)
                                        ? entityType.trim()
                                        : CoverDaysEntityType.GOODS)
                        .coverDaysEntityName(entityName.trim())
                        .expiryRiskFilter(base.getExpiryRiskFilter())
                        .subQuestions(base.getSubQuestions())
                        .promptId(base.getPromptId())
                        .llmRawText(base.getLlmRawText())
                        .parseError(base.getParseError())
                        .intakeRepairAttempted(base.getIntakeRepairAttempted())
                        .intakeRepairSuccess(base.getIntakeRepairSuccess())
                        .intakeRepairReason(base.getIntakeRepairReason())
                        .failureCode(base.getFailureCode())
                        .failureStage(base.getFailureStage())
                        .build();
        return promoteWarehouseGoodsCoverReady(withEntity);
    }

    private static boolean shouldReconcileToGoodsSupportedDishCover(
            SemanticIntakeInput input, SemanticIntakeResult intake) {
        if (SemanticIntakeDishIngredientCoverDaysSupport.reasonDeclaresDishIngredientCoverDays(
                intake.getReason())) {
            return false;
        }
        if (SemanticIntakeGoodsStockBatchDetailSupport.intakeDeclaresGoodsStockBatchDetail(intake)) {
            return false;
        }
        if (SemanticIntakeGoodsAnchorInventoryBundleSupport.intakeDeclaresGoodsAnchorInventoryBundle(
                intake)) {
            return false;
        }
        if (CoverDaysEntityType.DISH.equals(
                CoverDaysEntityType.normalize(intake.getCoverDaysEntityType()))) {
            return false;
        }
        if (intakeDeclaresGoodsSupportedDishCover(intake)) {
            if (WarehouseInventoryShortageSemanticsSupport.intakeHasAuthoritativeInventoryRisk(
                    intake)) {
                return false;
            }
            return true;
        }
        if (CoverDaysEntityGroundingService.intakeDeclaresCoverDaysSalesBaseline(intake.getReason())
                && CoverDaysEntityGroundingService.intakeDeclaresNamedGoodsEntityAnchor(intake)) {
            if (WarehouseInventoryShortageSemanticsSupport.intakeHasAuthoritativeInventoryRisk(
                    intake)) {
                return false;
            }
            return true;
        }
        if (input == null || !input.isHasPreviousTurn()) {
            return false;
        }
        if (!SemanticIntakeGoodsAnchorFollowUpSupport.previousTurnDeclaresGoodsSupportedDishCoverOnly(
                input)) {
            return false;
        }
        if (!Boolean.TRUE.equals(intake.getIsFollowUp())) {
            return false;
        }
        return reasonDeclaresGoodsSupportedDishCover(intake.getReason());
    }

    private static SemanticIntakeResult promoteWarehouseGoodsCoverReady(SemanticIntakeResult intake) {
        String reason = appendGoodsCoverReasonMarker(intake.getReason());
        boolean followUp = Boolean.TRUE.equals(intake.getIsFollowUp());
        return SemanticIntakeResult.builder()
                .status(SemanticIntakeStatus.READY)
                .questionMode(intake.getQuestionMode())
                .normalizationType(intake.getNormalizationType())
                .canonicalUserQuery(intake.getCanonicalUserQuery())
                .isFollowUp(followUp)
                .usedPreviousContext(intake.getUsedPreviousContext())
                .primaryDomain(SemanticIntakePrimaryDomain.WAREHOUSE)
                .candidateDomains(List.of(SemanticIntakePrimaryDomain.WAREHOUSE))
                .routeType(StringUtils.hasText(intake.getRouteType()) ? intake.getRouteType() : "EXPLICIT")
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
                .failureCode(intake.getFailureCode())
                .failureStage(intake.getFailureStage())
                .build();
    }

    public static String appendGoodsCoverReasonMarker(String reason) {
        if (reasonDeclaresGoodsSupportedDishCover(reason)) {
            return StringUtils.hasText(reason) ? reason.trim() : REASON_MARKER;
        }
        if (!StringUtils.hasText(reason)) {
            return REASON_MARKER;
        }
        return reason.trim() + ";" + REASON_MARKER;
    }

    public static boolean mustNotApplyWarehouseInventoryShortagePipeline(
            SemanticIntakeResult intake, AiQuerySemanticParseResult completedParse) {
        if (intakeDeclaresGoodsSupportedDishCover(intake)) {
            return true;
        }
        if (completedParse == null) {
            return false;
        }
        String selected = SemanticContractCompletionEngine.extractSelectedContractId(completedParse);
        return WarehouseSemanticCapabilityMatrix.CONTRACT_GOODS_SUPPORTED_DISH_COVER.equals(
                blank(selected));
    }

    public static boolean mustNotApplyDishIngredientCoverPipeline(LlmSemanticIntakeParsed parsed) {
        return parsedDeclaresGoodsSupportedDishCover(parsed);
    }

    /**
     * Intake 已锁定 WH-H 时，V2 allowed 合同收窄为 {@code warehouse.goods_supported_dish_cover.v1}。
     */
    public static DomainContractSelectionResult filterContractSelection(
            DomainContractSelectionResult selection, SemanticIntakeResult intake) {
        if (selection == null || !intakeDeclaresGoodsSupportedDishCover(intake)) {
            return selection;
        }
        if (SemanticIntakeGoodsAnchorInventoryBundleSupport.intakeDeclaresGoodsAnchorInventoryBundle(
                intake)) {
            return selection;
        }
        if (!SemanticIntakePrimaryDomain.WAREHOUSE.equals(blank(selection.getSelectedDomain()))) {
            return selection;
        }
        SemanticParserAllowedOutputContract contract = selection.getParserAllowedOutputContract();
        if (contract == null) {
            return selection;
        }
        List<SemanticParserAllowedOutputContract.AllowedContractEntry> allowed = new ArrayList<>();
        if (contract.getAllowedContracts() != null) {
            for (SemanticParserAllowedOutputContract.AllowedContractEntry e :
                    contract.getAllowedContracts()) {
                if (e != null
                        && WarehouseSemanticCapabilityMatrix.CONTRACT_GOODS_SUPPORTED_DISH_COVER.equals(
                                e.getContractId())) {
                    allowed.add(e);
                }
            }
        }
        return buildFilteredSelection(selection, contract, allowed, allowed.size());
    }

    public static void collectGoodsSupportedDishCoverProtocolErrors(
            LlmSemanticIntakeParsed parsed, List<String> errors) {
        if (parsed == null || errors == null) {
            return;
        }
        if (WarehouseInventorySupervisionSemanticsSupport.parsedDeclaresSupervision(parsed)
                && StringUtils.hasText(parsed.getCoverDaysEntityName())) {
            errors.add(
                    "warehouse_inventory_supervision: cannot combine SUPERVISION_QUERY with "
                            + "coverDaysEntityName; use goods_supported_dish_cover (§13e/§34b)");
        }
        if (!parsedDeclaresGoodsSupportedDishCover(parsed)) {
            return;
        }
        if (SemanticIntakeGoodsAnchorInventoryBundleSupport.parsedDeclaresGoodsAnchorInventoryBundle(
                parsed)) {
            errors.add(
                    "goods_supported_dish_cover: cannot combine with goods_anchor_inventory_bundle "
                            + "(§34b-cover vs §34b-bundle)");
        }
        if (SemanticIntakeGoodsStockBatchDetailSupport.parsedDeclaresGoodsStockBatchDetail(parsed)) {
            errors.add(
                    "goods_supported_dish_cover: cannot combine with goods_stock_batch_detail "
                            + "(§34b-cover vs §34c-batch)");
        }
        if (SemanticIntakeDishIngredientCoverDaysSupport.parsedDeclaresDishIngredientCoverDays(parsed)) {
            errors.add(
                    "goods_supported_dish_cover: cannot combine with dish_ingredient_cover_days (§34b)");
        }
        if (WarehouseInventoryShortageSemanticsSupport.parsedDeclaresInventoryRisk(parsed)) {
            errors.add(
                    "goods_supported_dish_cover: warehouseInventorySemantics must be empty (§34b)");
        }
        if (WarehouseInventorySupervisionSemanticsSupport.parsedDeclaresSupervision(parsed)) {
            errors.add(
                    "goods_supported_dish_cover: warehouseInventorySemantics must be empty; "
                            + "named goods inventory uses WH-H, not supervision (§34b/§13e)");
        }
        String primary = SemanticIntakePrimaryDomain.normalize(parsed.getPrimaryDomain());
        if (SemanticIntakePrimaryDomain.DISH_COST.equals(primary)
                || SemanticIntakePrimaryDomain.DISH_PROFIT.equals(primary)
                || SemanticIntakePrimaryDomain.DISH_SALES.equals(primary)) {
            errors.add(
                    "goods_supported_dish_cover: primaryDomain must be WAREHOUSE, not dish domain (§34b)");
        }
    }

    private static String blank(String s) {
        return s == null ? null : s.trim();
    }

    private static DomainContractSelectionResult buildFilteredSelection(
            DomainContractSelectionResult selection,
            SemanticParserAllowedOutputContract contract,
            List<SemanticParserAllowedOutputContract.AllowedContractEntry> allowedContracts,
            int activeCount) {
        SemanticParserAllowedOutputContract enriched =
                SemanticParserAllowedOutputContract.builder()
                        .selectedDomain(contract.getSelectedDomain())
                        .allowedContracts(allowedContracts)
                        .knownGapContracts(contract.getKnownGapContracts())
                        .contractSelectionBoundaryHints(null)
                        .allowedWires(contract.getAllowedWires())
                        .allowedQueryObjects(contract.getAllowedQueryObjects())
                        .allowedOperations(contract.getAllowedOperations())
                        .allowedMetrics(contract.getAllowedMetrics())
                        .allowedSourceFacets(contract.getAllowedSourceFacets())
                        .allowedDetailWanted(contract.getAllowedDetailWanted())
                        .allowedAnswerPlanTypes(contract.getAllowedAnswerPlanTypes())
                        .build();
        return DomainContractSelectionResult.builder()
                .selectedDomain(selection.getSelectedDomain())
                .selectedCapabilityContractCount(selection.getSelectedCapabilityContractCount())
                .selectedActiveContractCount(activeCount)
                .selectedKnownGapCount(selection.getSelectedKnownGapCount())
                .capabilityContractMissing(selection.isCapabilityContractMissing())
                .contractSelectionSkippedReason(selection.getContractSelectionSkippedReason())
                .parserAllowedOutputContract(enriched)
                .build();
    }
}
