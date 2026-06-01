package com.nongxinle.ai.resolver;

import com.nongxinle.ai.conversation.AiConversationTurnMemory;
import com.nongxinle.ai.semantic.AiQuerySemanticLlmMergeHelper;
import com.nongxinle.ai.semantic.AiQuerySemanticParseResult;
import com.nongxinle.ai.semantic.contract.DomainContractSelectionResult;
import com.nongxinle.ai.semantic.contract.SemanticContractCompletionEngine;
import com.nongxinle.ai.semantic.intake.SemanticIntakeResult;
import com.nongxinle.ai.semantic.frame.CurrentSemanticFrame;
import com.nongxinle.ai.semantic.frame.CurrentSemanticFrameValidatorRegistry;
import com.nongxinle.ai.semantic.frame.SemanticFrameValidationResult;
import org.springframework.util.StringUtils;

import java.util.Locale;
import java.util.Set;

/**
 * REVENUE / STOCK_REDUCE / WAREHOUSE contract-entry adoption 门禁（P2B）。
 */
final class BasicDomainContractEntryAdoptionSupport {

    private static final Set<String> BASIC_DOMAINS = Set.of("REVENUE", "STOCK_REDUCE", "WAREHOUSE");

    private BasicDomainContractEntryAdoptionSupport() {}

    static boolean shouldRunBasicDomainContractEntryAdoption(
            AiQuerySemanticParseResult sem,
            DomainContractSelectionResult contractSelection,
            boolean contractLocked,
            boolean purchaseFrameAdoption) {
        if (purchaseFrameAdoption || sem == null || sem.isParseMissing()) {
            return false;
        }
        String domain = resolveBasicDomainCode(contractSelection, sem);
        if (!isBasicDomain(domain)) {
            return false;
        }
        if (contractLocked) {
            return true;
        }
        return switch (domain) {
            case "REVENUE" -> AiQuerySemanticLlmMergeHelper.hasExplicitRevenueRouteSignal(sem);
            case "STOCK_REDUCE" -> AiQuerySemanticLlmMergeHelper.hasExplicitStockReduceRouteSignal(sem);
            case "WAREHOUSE" -> AiQuerySemanticLlmMergeHelper.hasExplicitWarehouseRouteSignal(sem);
            default -> false;
        };
    }

    static SemanticFrameValidationResult validateBasicDomainContractEntry(
            AiQuerySemanticParseResult sem,
            AiConversationTurnMemory previousTurn,
            String normalized,
            boolean followUpRewriteApplied,
            DomainContractSelectionResult contractSelection) {
        return validateBasicDomainContractEntry(
                sem, previousTurn, normalized, followUpRewriteApplied, contractSelection, null);
    }

    static SemanticFrameValidationResult validateBasicDomainContractEntry(
            AiQuerySemanticParseResult sem,
            AiConversationTurnMemory previousTurn,
            String normalized,
            boolean followUpRewriteApplied,
            DomainContractSelectionResult contractSelection,
            SemanticIntakeResult semanticIntake) {
        String domain = resolveBasicDomainCode(contractSelection, sem);
        CurrentSemanticFrame frame = CurrentSemanticFrame.buildFrame(sem);
        boolean contractLocked = SemanticContractCompletionEngine.isContractLockedParse(sem);
        SemanticFrameValidationResult result =
                CurrentSemanticFrameValidatorRegistry.validate(
                        domain,
                        frame,
                        sem,
                        previousTurn,
                        normalized,
                        followUpRewriteApplied,
                        contractSelection,
                        contractLocked,
                        semanticIntake);
        return result != null ? result : SemanticFrameValidationResult.success();
    }

    static String resolveBasicDomainCode(
            DomainContractSelectionResult contractSelection, AiQuerySemanticParseResult sem) {
        if (contractSelection != null && StringUtils.hasText(contractSelection.getSelectedDomain())) {
            String d = normalizeDomain(contractSelection.getSelectedDomain());
            if (isBasicDomain(d)) {
                return d;
            }
        }
        if (sem != null && StringUtils.hasText(sem.getSemanticDomain())) {
            String d = normalizeDomain(sem.getSemanticDomain());
            if ("WAREHOUSE_STOCK".equals(d) || "INVENTORY".equals(d)) {
                return "WAREHOUSE";
            }
            if ("STOCK_OUT".equals(d) || "WRITE_OFF".equals(d)) {
                return "STOCK_REDUCE";
            }
            if (isBasicDomain(d)) {
                return d;
            }
        }
        if (sem != null && SemanticContractCompletionEngine.hasSelectedContractId(sem)) {
            String contractId = SemanticContractCompletionEngine.extractSelectedContractId(sem);
            if (StringUtils.hasText(contractId)) {
                String id = contractId.trim().toLowerCase(Locale.ROOT);
                if (id.startsWith("revenue.")) {
                    return "REVENUE";
                }
                if (id.startsWith("stock_reduce.")) {
                    return "STOCK_REDUCE";
                }
                if (id.startsWith("warehouse.")) {
                    return "WAREHOUSE";
                }
            }
        }
        if (sem != null && AiQuerySemanticLlmMergeHelper.hasExplicitRevenueRouteSignal(sem)) {
            return "REVENUE";
        }
        if (sem != null && AiQuerySemanticLlmMergeHelper.hasExplicitStockReduceRouteSignal(sem)) {
            return "STOCK_REDUCE";
        }
        if (sem != null && AiQuerySemanticLlmMergeHelper.hasExplicitWarehouseRouteSignal(sem)) {
            return "WAREHOUSE";
        }
        return null;
    }

    private static boolean isBasicDomain(String domain) {
        return domain != null && BASIC_DOMAINS.contains(domain);
    }

    private static String normalizeDomain(String domain) {
        return domain == null ? null : domain.trim().toUpperCase(Locale.ROOT).replace('-', '_');
    }
}
