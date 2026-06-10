package com.nongxinle.ai.semantic.intake;

import com.nongxinle.ai.semantic.SemanticParserAllowedOutputContract;
import com.nongxinle.ai.semantic.contract.DomainContractSelectionResult;
import com.nongxinle.ai.semantic.intake.grounding.CoverDaysEntityGroundingService;
import com.nongxinle.ai.semantic.intake.llm.LlmSemanticIntakeParsed;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 库房库存监督/诊断语义（{@code warehouse.inventory_supervision.v1}）。
 * 只读 Intake 结构化字段；不解析用户原文。
 */
public final class WarehouseInventorySupervisionSemanticsSupport {

    public static final String SEMANTICS_SUPERVISION_QUERY = "SUPERVISION_QUERY";

    public static final String REASON_MARKER = "warehouse_inventory_supervision";

    /** Intake reason 自报：库存状态/健康入口（§13e；非用户原文 NL）。 */
    public static final String REASON_MARKER_STATUS = "warehouse_inventory_status";

    public static final String CONTRACT_INVENTORY_SUPERVISION = "warehouse.inventory_supervision.v1";

    private WarehouseInventorySupervisionSemanticsSupport() {}

    public static boolean intakeDeclaresSupervisionQuery(SemanticIntakeResult intake) {
        if (intake == null) {
            return false;
        }
        if (SEMANTICS_SUPERVISION_QUERY.equals(normalizeSemantics(intake.getWarehouseInventorySemantics()))) {
            return true;
        }
        return reasonDeclaresSupervision(intake.getReason());
    }

    public static boolean parsedDeclaresSupervision(LlmSemanticIntakeParsed parsed) {
        if (parsed == null) {
            return false;
        }
        if (SEMANTICS_SUPERVISION_QUERY.equals(
                normalizeSemantics(parsed.getWarehouseInventorySemantics()))) {
            return true;
        }
        return reasonDeclaresSupervision(parsed.getReason());
    }

    public static String normalizeSemantics(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        String n = raw.trim().toUpperCase(Locale.ROOT);
        if (isSupervisionAlias(n)) {
            return SEMANTICS_SUPERVISION_QUERY;
        }
        return SEMANTICS_SUPERVISION_QUERY.equals(n) ? n : null;
    }

    private static boolean isSupervisionAlias(String normalized) {
        return switch (normalized) {
            case "INVENTORY_SUPERVISION",
                    "SUPERVISION",
                    "INVENTORY_STATUS",
                    "CURRENT_STATUS",
                    "STOCK_HEALTH_OVERVIEW",
                    "INVENTORY_HEALTH",
                    "STOCK_HEALTH",
                    "STOCK_STATUS_OVERVIEW",
                    "INVENTORY_SITUATION",
                    "STOCK_SITUATION" -> true;
            default -> false;
        };
    }

    public static SemanticIntakeResult reconcileIntake(
            SemanticIntakeInput input, SemanticIntakeResult mapped) {
        if (mapped == null || mapped.getStatus() == SemanticIntakeStatus.INVALID) {
            return mapped;
        }
        if (CoverDaysEntityGroundingService.intakeDeclaresNamedGoodsInventoryDetail(mapped)
                || SemanticIntakeGoodsSupportedDishCoverSupport.intakeDeclaresGoodsSupportedDishCover(
                        mapped)) {
            if (intakeDeclaresSupervisionQuery(mapped)) {
                return stripSupervisionFromIntake(mapped);
            }
            return mapped;
        }
        if (SemanticIntakeDishIngredientCoverDaysSupport.intakeDeclaresDishIngredientCoverDays(mapped)
                || SemanticIntakeGoodsSupportedDishCoverSupport.intakeDeclaresGoodsSupportedDishCover(
                        mapped)) {
            return mapped;
        }
        if (WarehouseInventoryShortageSemanticsSupport.intakeExplicitAmountRankingLow(mapped)) {
            return mapped;
        }
        if (!intakeDeclaresSupervisionQuery(mapped)) {
            return mapped;
        }
        return promoteSupervisionReadyIntake(mapped);
    }

    public static DomainContractSelectionResult filterContractSelection(
            DomainContractSelectionResult selection, SemanticIntakeResult intake) {
        if (selection == null) {
            return selection;
        }
        if (CoverDaysEntityGroundingService.intakeDeclaresNamedGoodsInventoryDetail(intake)
                || SemanticIntakeGoodsSupportedDishCoverSupport.intakeDeclaresGoodsSupportedDishCover(
                        intake)) {
            return selection;
        }
        if (!intakeDeclaresSupervisionQuery(intake)) {
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
            for (SemanticParserAllowedOutputContract.AllowedContractEntry e : contract.getAllowedContracts()) {
                if (e != null && CONTRACT_INVENTORY_SUPERVISION.equals(e.getContractId())) {
                    allowed.add(e);
                }
            }
        }
        return buildFilteredSelection(selection, contract, allowed, allowed.size());
    }

    private static SemanticIntakeResult promoteSupervisionReadyIntake(SemanticIntakeResult mapped) {
        String reason =
                StringUtils.hasText(mapped.getReason())
                        ? mapped.getReason().trim()
                        : REASON_MARKER;
        if (!reason.contains(REASON_MARKER)) {
            reason = reason + ";" + REASON_MARKER;
        }
        return SemanticIntakeResult.builder()
                .status(SemanticIntakeStatus.READY)
                .questionMode(mapped.getQuestionMode())
                .normalizationType(mapped.getNormalizationType())
                .canonicalUserQuery(mapped.getCanonicalUserQuery())
                .isFollowUp(mapped.getIsFollowUp())
                .usedPreviousContext(mapped.getUsedPreviousContext())
                .primaryDomain(SemanticIntakePrimaryDomain.WAREHOUSE)
                .candidateDomains(
                        mapped.getCandidateDomains() != null
                                ? mapped.getCandidateDomains()
                                : List.of(SemanticIntakePrimaryDomain.WAREHOUSE))
                .routeType("EXPLICIT")
                .confidence(mapped.getConfidence())
                .needClarification(false)
                .clarificationQuestion(null)
                .reason(reason)
                .warehouseInventorySemantics(SEMANTICS_SUPERVISION_QUERY)
                .expiryRiskFilter(mapped.getExpiryRiskFilter())
                .coverDaysEntityType(mapped.getCoverDaysEntityType())
                .coverDaysEntityName(mapped.getCoverDaysEntityName())
                .subQuestions(mapped.getSubQuestions())
                .promptId(mapped.getPromptId())
                .llmRawText(mapped.getLlmRawText())
                .parseError(mapped.getParseError())
                .intakeRepairAttempted(mapped.getIntakeRepairAttempted())
                .intakeRepairSuccess(mapped.getIntakeRepairSuccess())
                .intakeRepairReason(mapped.getIntakeRepairReason())
                .failureCode(mapped.getFailureCode())
                .failureStage(mapped.getFailureStage())
                .build();
    }

    private static SemanticIntakeResult stripSupervisionFromIntake(SemanticIntakeResult mapped) {
        String reason = mapped.getReason();
        if (StringUtils.hasText(reason)) {
            reason =
                    reason.replace(REASON_MARKER, "")
                            .replace(REASON_MARKER_STATUS, "")
                            .replace(";;", ";")
                            .replaceAll("^;+|;+$", "")
                            .trim();
            if (!StringUtils.hasText(reason)) {
                reason = null;
            }
        }
        return SemanticIntakeResult.builder()
                .status(mapped.getStatus())
                .questionMode(mapped.getQuestionMode())
                .normalizationType(mapped.getNormalizationType())
                .canonicalUserQuery(mapped.getCanonicalUserQuery())
                .isFollowUp(mapped.getIsFollowUp())
                .usedPreviousContext(mapped.getUsedPreviousContext())
                .primaryDomain(mapped.getPrimaryDomain())
                .candidateDomains(mapped.getCandidateDomains())
                .routeType(mapped.getRouteType())
                .confidence(mapped.getConfidence())
                .needClarification(mapped.getNeedClarification())
                .clarificationQuestion(mapped.getClarificationQuestion())
                .reason(reason)
                .warehouseInventorySemantics(null)
                .expiryRiskFilter(mapped.getExpiryRiskFilter())
                .coverDaysEntityType(mapped.getCoverDaysEntityType())
                .coverDaysEntityName(mapped.getCoverDaysEntityName())
                .subQuestions(mapped.getSubQuestions())
                .promptId(mapped.getPromptId())
                .llmRawText(mapped.getLlmRawText())
                .parseError(mapped.getParseError())
                .intakeRepairAttempted(mapped.getIntakeRepairAttempted())
                .intakeRepairSuccess(mapped.getIntakeRepairSuccess())
                .intakeRepairReason(mapped.getIntakeRepairReason())
                .failureCode(mapped.getFailureCode())
                .failureStage(mapped.getFailureStage())
                .build();
    }

    private static boolean reasonDeclaresSupervision(String reason) {
        if (!StringUtils.hasText(reason)) {
            return false;
        }
        String normalized = reason.trim().toLowerCase(Locale.ROOT);
        return normalized.contains(REASON_MARKER.toLowerCase(Locale.ROOT))
                || normalized.contains(REASON_MARKER_STATUS)
                || normalized.contains("warehouse_inventory_health")
                || normalized.contains("warehouse_stock_supervision");
    }

    private static String blank(String s) {
        return s == null ? "" : s.trim();
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
