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
 * Intake：商品库存综合详情（{@code warehouse.goods_anchor_inventory_bundle.v1}，cover + batch 双卡）。
 */
public final class SemanticIntakeGoodsAnchorInventoryBundleSupport {

    public static final String REASON_MARKER = "goods_anchor_inventory_bundle";

    private SemanticIntakeGoodsAnchorInventoryBundleSupport() {}

    public static boolean reasonDeclaresGoodsAnchorInventoryBundle(String reason) {
        if (!StringUtils.hasText(reason)) {
            return false;
        }
        String n = reason.trim().toLowerCase(Locale.ROOT);
        return n.contains(REASON_MARKER) || n.contains("goods_inventory_bundle");
    }

    public static boolean parsedDeclaresGoodsAnchorInventoryBundle(LlmSemanticIntakeParsed parsed) {
        return parsed != null && reasonDeclaresGoodsAnchorInventoryBundle(parsed.getReason());
    }

    public static boolean intakeDeclaresGoodsAnchorInventoryBundle(SemanticIntakeResult intake) {
        return intake != null && reasonDeclaresGoodsAnchorInventoryBundle(intake.getReason());
    }

    public static SemanticIntakeResult reconcile(SemanticIntakeInput input, SemanticIntakeResult intake) {
        if (intake == null || intake.getStatus() == SemanticIntakeStatus.INVALID) {
            return intake;
        }
        if (!shouldReconcileToGoodsAnchorInventoryBundle(input, intake)) {
            return intake;
        }
        return promoteWarehouseGoodsAnchorInventoryBundleReady(intake);
    }

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
        return promoteWarehouseGoodsAnchorInventoryBundleReady(withEntity);
    }

    private static boolean shouldReconcileToGoodsAnchorInventoryBundle(
            SemanticIntakeInput input, SemanticIntakeResult intake) {
        if (SemanticIntakeSovereignDomainSupport.intakeDeclaresSovereignCrossFamilyCapability(
                input, intake)) {
            return false;
        }
        if (isWarehouseGoodsAnchorBundleCompositeMultiQuestion(intake)) {
            return true;
        }
        if (SemanticIntakeDishIngredientCoverDaysSupport.intakeDeclaresDishIngredientCoverDays(intake)) {
            return false;
        }
        if (CoverDaysEntityType.DISH.equals(
                CoverDaysEntityType.normalize(intake.getCoverDaysEntityType()))) {
            return false;
        }
        if (SemanticIntakeGoodsSupportedDishCoverSupport.intakeDeclaresGoodsSupportedDishCover(
                intake)) {
            return false;
        }
        if (SemanticIntakeGoodsStockBatchDetailSupport.intakeDeclaresGoodsStockBatchDetail(intake)) {
            return false;
        }
        if (WarehouseInventoryShortageSemanticsSupport.intakeHasAuthoritativeInventoryRisk(intake)) {
            return false;
        }
        if (WarehouseInventorySupervisionSemanticsSupport.intakeDeclaresSupervisionQuery(intake)) {
            return false;
        }
        if (WarehouseInventoryShortageSemanticsSupport.intakeExplicitAmountRankingLow(intake)) {
            return false;
        }
        if (intakeDeclaresGoodsAnchorInventoryBundle(intake)) {
            return true;
        }
        if (CoverDaysEntityGroundingService.intakeDeclaresNamedGoodsEntityAnchor(intake)
                && SemanticIntakePrimaryDomain.WAREHOUSE.equals(
                        SemanticIntakePrimaryDomain.normalize(intake.getPrimaryDomain()))) {
            return true;
        }
        if (input == null || !input.isHasPreviousTurn()) {
            return false;
        }
        if (!SemanticIntakeGoodsAnchorFollowUpSupport.previousTurnDeclaresGoodsAnchorInventory(
                input)) {
            return false;
        }
        if (!Boolean.TRUE.equals(intake.getIsFollowUp())) {
            return false;
        }
        if (!SemanticIntakePrimaryDomain.WAREHOUSE.equals(
                SemanticIntakePrimaryDomain.normalize(intake.getPrimaryDomain()))) {
            return false;
        }
        return SemanticIntakeGoodsAnchorFollowUpSupport.reasonDeclaresGoodsAnchorStockFollowUp(
                        intake.getReason())
                || Boolean.TRUE.equals(intake.getUsedPreviousContext());
    }

    private static SemanticIntakeResult promoteWarehouseGoodsAnchorInventoryBundleReady(
            SemanticIntakeResult intake) {
        String reason = appendBundleReasonMarker(intake.getReason());
        return SemanticIntakeResult.builder()
                .status(SemanticIntakeStatus.READY)
                .questionMode(SemanticIntakeQuestionMode.SINGLE_QUESTION)
                .normalizationType(intake.getNormalizationType())
                .canonicalUserQuery(intake.getCanonicalUserQuery())
                .isFollowUp(Boolean.TRUE.equals(intake.getIsFollowUp()))
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
                .subQuestions(null)
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

    public static String appendBundleReasonMarker(String reason) {
        if (reasonDeclaresGoodsAnchorInventoryBundle(reason)) {
            return StringUtils.hasText(reason) ? reason.trim() : REASON_MARKER;
        }
        if (!StringUtils.hasText(reason)) {
            return REASON_MARKER;
        }
        return reason.trim() + ";" + REASON_MARKER;
    }

    public static boolean mustNotApplyWarehouseInventoryShortagePipeline(
            SemanticIntakeResult intake, AiQuerySemanticParseResult completedParse) {
        if (intakeDeclaresGoodsAnchorInventoryBundle(intake)) {
            return true;
        }
        if (completedParse == null) {
            return false;
        }
        String selected = SemanticContractCompletionEngine.extractSelectedContractId(completedParse);
        return WarehouseSemanticCapabilityMatrix.CONTRACT_GOODS_ANCHOR_INVENTORY_BUNDLE.equals(
                blank(selected));
    }

    public static DomainContractSelectionResult filterContractSelection(
            DomainContractSelectionResult selection, SemanticIntakeResult intake) {
        if (selection == null || !intakeDeclaresGoodsAnchorInventoryBundle(intake)) {
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
                        && WarehouseSemanticCapabilityMatrix.CONTRACT_GOODS_ANCHOR_INVENTORY_BUNDLE
                                .equals(e.getContractId())) {
                    allowed.add(e);
                }
            }
        }
        return buildFilteredSelection(selection, contract, allowed, allowed.size());
    }

    public static void collectGoodsAnchorInventoryBundleProtocolErrors(
            LlmSemanticIntakeParsed parsed, List<String> errors) {
        if (parsed == null || errors == null) {
            return;
        }
        if (isWarehouseGoodsAnchorBundleCompositeMultiQuestion(parsed)) {
            errors.add(
                    "goods_anchor_inventory_bundle: composite stock+cover must be "
                            + "questionMode=SINGLE_QUESTION, not MULTI_QUESTION (§34b-bundle-composite)");
        }
        if (!parsedDeclaresGoodsAnchorInventoryBundle(parsed)) {
            return;
        }
        if (SemanticIntakeGoodsSupportedDishCoverSupport.parsedDeclaresGoodsSupportedDishCover(
                parsed)) {
            errors.add(
                    "goods_anchor_inventory_bundle: cannot combine with goods_supported_dish_cover "
                            + "(§34b-bundle vs §34b-cover)");
        }
        if (SemanticIntakeGoodsStockBatchDetailSupport.parsedDeclaresGoodsStockBatchDetail(parsed)) {
            errors.add(
                    "goods_anchor_inventory_bundle: cannot combine with goods_stock_batch_detail "
                            + "(§34c-batch)");
        }
        if (WarehouseInventoryShortageSemanticsSupport.parsedDeclaresInventoryRisk(parsed)) {
            errors.add("goods_anchor_inventory_bundle: warehouseInventorySemantics must be empty");
        }
        if (WarehouseInventorySupervisionSemanticsSupport.parsedDeclaresSupervision(parsed)) {
            errors.add(
                    "goods_anchor_inventory_bundle: named goods inventory uses bundle, not "
                            + "supervision (§34b-bundle)");
        }
        if (!StringUtils.hasText(parsed.getCoverDaysEntityName())) {
            errors.add("coverDaysEntityName: required for goods_anchor_inventory_bundle (§34b-bundle)");
        }
        if (parsedDeclaresGoodsAnchorInventoryBundle(parsed)
                && SemanticIntakeQuestionMode.MULTI_QUESTION.name().equalsIgnoreCase(
                        blank(parsed.getQuestionMode()))) {
            errors.add(
                    "goods_anchor_inventory_bundle: questionMode must be SINGLE_QUESTION "
                            + "(§34b-bundle-composite)");
        }
    }

    /**
     * 同一 GOODS 锚点 + 全 WAREHOUSE 子问题：Bundle 组合输出，非 MULTI_QUESTION（§34b-bundle-composite）。
     */
    static boolean isWarehouseGoodsAnchorBundleCompositeMultiQuestion(SemanticIntakeResult intake) {
        if (intake == null || intake.getQuestionMode() != SemanticIntakeQuestionMode.MULTI_QUESTION) {
            return false;
        }
        if (!StringUtils.hasText(intake.getCoverDaysEntityName())) {
            return false;
        }
        List<SemanticIntakeSubQuestion> subs = intake.getSubQuestions();
        if (subs == null || subs.size() < 2) {
            return false;
        }
        String parentDomain = SemanticIntakePrimaryDomain.normalize(intake.getPrimaryDomain());
        if (!SemanticIntakePrimaryDomain.WAREHOUSE.equals(parentDomain)
                && !SemanticIntakePrimaryDomain.MULTI_DOMAIN.equals(parentDomain)) {
            return false;
        }
        for (SemanticIntakeSubQuestion sq : subs) {
            if (sq == null) {
                continue;
            }
            String d = SemanticIntakePrimaryDomain.normalize(sq.getPrimaryDomain());
            if (StringUtils.hasText(d) && !SemanticIntakePrimaryDomain.WAREHOUSE.equals(d)) {
                return false;
            }
        }
        return true;
    }

    private static boolean isWarehouseGoodsAnchorBundleCompositeMultiQuestion(
            LlmSemanticIntakeParsed parsed) {
        if (parsed == null
                || !SemanticIntakeQuestionMode.MULTI_QUESTION.name().equalsIgnoreCase(
                        blank(parsed.getQuestionMode()))) {
            return false;
        }
        if (!StringUtils.hasText(parsed.getCoverDaysEntityName())) {
            return false;
        }
        List<SemanticIntakeSubQuestion> subs = parsed.getSubQuestions();
        if (subs == null || subs.size() < 2) {
            return false;
        }
        String parentDomain = SemanticIntakePrimaryDomain.normalize(parsed.getPrimaryDomain());
        if (!SemanticIntakePrimaryDomain.WAREHOUSE.equals(parentDomain)
                && !SemanticIntakePrimaryDomain.MULTI_DOMAIN.equals(parentDomain)) {
            return false;
        }
        for (SemanticIntakeSubQuestion sq : subs) {
            if (sq == null) {
                continue;
            }
            String d = SemanticIntakePrimaryDomain.normalize(sq.getPrimaryDomain());
            if (StringUtils.hasText(d) && !SemanticIntakePrimaryDomain.WAREHOUSE.equals(d)) {
                return false;
            }
        }
        return true;
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
