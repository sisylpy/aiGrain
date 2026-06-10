package com.nongxinle.ai.semantic.intake;

import com.nongxinle.ai.semantic.SemanticParserAllowedOutputContract;
import com.nongxinle.ai.semantic.contract.DomainContractSelectionResult;
import com.nongxinle.ai.semantic.intake.grounding.CoverDaysEntityGroundingService;
import com.nongxinle.ai.semantic.intake.grounding.CoverDaysEntityType;
import com.nongxinle.ai.semantic.intake.llm.LlmSemanticIntakeParsed;
import com.nongxinle.ai.semantic.matrix.WarehouseSemanticCapabilityMatrix;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Intake：指定商品库存批次明细（{@code warehouse.goods_stock_batch_detail.v1}，WH-J）。
 */
public final class SemanticIntakeGoodsStockBatchDetailSupport {

    public static final String REASON_MARKER = "goods_stock_batch_detail";

    private SemanticIntakeGoodsStockBatchDetailSupport() {}

    public static boolean reasonDeclaresGoodsStockBatchDetail(String reason) {
        if (!StringUtils.hasText(reason)) {
            return false;
        }
        String n = reason.trim().toLowerCase(Locale.ROOT);
        return n.contains(REASON_MARKER) || n.contains("stock_batch_detail");
    }

    public static boolean parsedDeclaresGoodsStockBatchDetail(LlmSemanticIntakeParsed parsed) {
        return parsed != null && reasonDeclaresGoodsStockBatchDetail(parsed.getReason());
    }

    public static boolean intakeDeclaresGoodsStockBatchDetail(SemanticIntakeResult intake) {
        return intake != null && reasonDeclaresGoodsStockBatchDetail(intake.getReason());
    }

    public static SemanticIntakeResult reconcile(SemanticIntakeInput input, SemanticIntakeResult intake) {
        if (intake == null || intake.getStatus() == SemanticIntakeStatus.INVALID) {
            return intake;
        }
        if (!shouldReconcileToGoodsStockBatchDetail(input, intake)) {
            return intake;
        }
        return promoteWarehouseGoodsStockBatchDetailReady(intake);
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
        return promoteWarehouseGoodsStockBatchDetailReady(withEntity);
    }

    private static boolean shouldReconcileToGoodsStockBatchDetail(
            SemanticIntakeInput input, SemanticIntakeResult intake) {
        if (SemanticIntakeDishIngredientCoverDaysSupport.intakeDeclaresDishIngredientCoverDays(intake)) {
            return false;
        }
        if (SemanticIntakeGoodsSupportedDishCoverSupport.intakeDeclaresGoodsSupportedDishCover(
                intake)) {
            return false;
        }
        if (WarehouseInventoryShortageSemanticsSupport.intakeHasAuthoritativeInventoryRisk(intake)) {
            return false;
        }
        return intakeDeclaresGoodsStockBatchDetail(intake);
    }

    private static SemanticIntakeResult promoteWarehouseGoodsStockBatchDetailReady(
            SemanticIntakeResult intake) {
        String reason = appendBatchReasonMarker(intake.getReason());
        return SemanticIntakeResult.builder()
                .status(SemanticIntakeStatus.READY)
                .questionMode(intake.getQuestionMode())
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

    public static String appendBatchReasonMarker(String reason) {
        if (reasonDeclaresGoodsStockBatchDetail(reason)) {
            return StringUtils.hasText(reason) ? reason.trim() : REASON_MARKER;
        }
        if (!StringUtils.hasText(reason)) {
            return REASON_MARKER;
        }
        return reason.trim() + ";" + REASON_MARKER;
    }

    public static DomainContractSelectionResult filterContractSelection(
            DomainContractSelectionResult selection, SemanticIntakeResult intake) {
        if (selection == null || !intakeDeclaresGoodsStockBatchDetail(intake)) {
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
                        && WarehouseSemanticCapabilityMatrix.CONTRACT_GOODS_STOCK_BATCH_DETAIL.equals(
                                e.getContractId())) {
                    allowed.add(e);
                }
            }
        }
        return buildFilteredSelection(selection, contract, allowed, allowed.size());
    }

    public static void collectGoodsStockBatchDetailProtocolErrors(
            LlmSemanticIntakeParsed parsed, List<String> errors) {
        if (parsed == null || errors == null || !parsedDeclaresGoodsStockBatchDetail(parsed)) {
            return;
        }
        if (SemanticIntakeGoodsSupportedDishCoverSupport.parsedDeclaresGoodsSupportedDishCover(parsed)) {
            errors.add(
                    "goods_stock_batch_detail: cannot combine with goods_supported_dish_cover (§34c-batch)");
        }
        if (SemanticIntakeGoodsAnchorInventoryBundleSupport.parsedDeclaresGoodsAnchorInventoryBundle(
                parsed)) {
            errors.add(
                    "goods_stock_batch_detail: cannot combine with goods_anchor_inventory_bundle");
        }
        if (WarehouseInventoryShortageSemanticsSupport.parsedDeclaresInventoryRisk(parsed)) {
            errors.add("goods_stock_batch_detail: warehouseInventorySemantics must be empty");
        }
        if (!StringUtils.hasText(parsed.getCoverDaysEntityName())) {
            errors.add("coverDaysEntityName: required for goods_stock_batch_detail (§34c-batch)");
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
