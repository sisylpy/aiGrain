package com.nongxinle.ai.semantic.contract;

import com.nongxinle.ai.semantic.routing.SemanticDomainRouteResult;
import com.nongxinle.ai.semantic.routing.SemanticDomainRouteType;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * 合同 strict 决策评估（Router + ContractSelector + Validator 快照；无 alias / 无用户原话猜测）。
 */
public final class SemanticContractStrictDecisionEvaluator {

    private SemanticContractStrictDecisionEvaluator() {
    }

    public static SemanticContractStrictDecision evaluate(
            SemanticDomainRouteResult route,
            DomainContractSelectionResult selection,
            EffectiveSemanticContractFrame frame,
            boolean strictEnabled) {
        List<String> blockers = SemanticContractStrictBlockerCatalog.activeBlockerIds();

        if (route == null) {
            return buildViolation(
                    strictEnabled,
                    SemanticContractViolationCode.ROUTE_UNKNOWN,
                    "route_null",
                    null,
                    null,
                    null,
                    null,
                    List.of(),
                    0,
                    null,
                    blockers);
        }

        SemanticDomainRouteType routeType = route.getRouteType();
        String primary = blank(route.getPrimaryDomain());
        List<String> candidates =
                route.getCandidateDomains() != null
                        ? new ArrayList<>(route.getCandidateDomains())
                        : List.of();

        if (routeType == SemanticDomainRouteType.UNKNOWN
                || (!StringUtils.hasText(primary) && routeType != SemanticDomainRouteType.AMBIGUOUS)) {
            return buildViolation(
                    strictEnabled,
                    SemanticContractViolationCode.ROUTE_UNKNOWN,
                    "route_type_unknown_or_no_primary",
                    null,
                    null,
                    null,
                    candidates,
                    List.of(),
                    0,
                    null,
                    blockers);
        }

        if (routeType == SemanticDomainRouteType.AMBIGUOUS
                || route.isNeedsClarification()
                || "ambiguous_no_primary".equals(
                        selection != null ? selection.getContractSelectionSkippedReason() : null)) {
            return buildViolation(
                    strictEnabled,
                    SemanticContractViolationCode.ROUTE_AMBIGUOUS,
                    "route_ambiguous",
                    primary,
                    null,
                    null,
                    candidates,
                    List.of(),
                    0,
                    null,
                    blockers);
        }

        if (selection == null || selection.isCapabilityContractMissing()) {
            String domain = selection != null ? blank(selection.getSelectedDomain()) : primary;
            String reason =
                    selection != null && StringUtils.hasText(selection.getContractSelectionSkippedReason())
                            ? selection.getContractSelectionSkippedReason()
                            : "no_active_capability_contract";
            return buildViolation(
                    strictEnabled,
                    SemanticContractViolationCode.NO_CAPABILITY_CONTRACT,
                    reason,
                    domain,
                    null,
                    null,
                    candidates,
                    List.of(),
                    selection != null ? selection.getSelectedActiveContractCount() : 0,
                    null,
                    blockers);
        }

        SemanticContractValidationDebug validation =
                SemanticContractValidator.observe(frame, selection);
        if (validation == null || !validation.isModelContractViolation()) {
            return SemanticContractStrictDecision.builder()
                    .strictEnabled(strictEnabled)
                    .modelContractViolation(false)
                    .enforceClarification(false)
                    .selectedDomain(blank(validation != null ? validation.getSelectedDomain() : primary))
                    .allowedWires(
                            validation != null && validation.getAllowedWires() != null
                                    ? validation.getAllowedWires()
                                    : List.of())
                    .allowedContractCount(
                            validation != null ? validation.getAllowedContractCount() : 0)
                    .matchedContractId(
                            validation != null ? validation.getMatchedContractId() : null)
                    .activeStrictBlockers(blockers)
                    .build();
        }

        return buildViolation(
                strictEnabled,
                validation.getViolationCode(),
                validation.getViolationReason(),
                validation.getSelectedDomain(),
                validation.getUnsupportedWire(),
                validation.getMissingSlots(),
                candidates,
                validation.getAllowedWires() != null ? validation.getAllowedWires() : List.of(),
                validation.getAllowedContractCount(),
                validation.getMatchedContractId(),
                blockers);
    }

    private static SemanticContractStrictDecision buildViolation(
            boolean strictEnabled,
            SemanticContractViolationCode code,
            String reason,
            String selectedDomain,
            String unsupportedWire,
            List<String> missingSlots,
            List<String> candidateDomains,
            List<String> allowedWires,
            int allowedContractCount,
            String matchedContractId,
            List<String> blockers) {
        String question =
                SemanticContractClarificationQuestionFactory.buildQuestion(
                        SemanticContractClarificationQuestionFactory.SemanticContractClarificationRequest
                                .builder()
                                .violationCode(code)
                                .selectedDomain(selectedDomain)
                                .unsupportedWire(unsupportedWire)
                                .missingSlots(missingSlots)
                                .candidateDomains(candidateDomains)
                                .build());
        return SemanticContractStrictDecision.builder()
                .strictEnabled(strictEnabled)
                .modelContractViolation(true)
                .enforceClarification(strictEnabled)
                .violationCode(code)
                .violationReason(reason)
                .selectedDomain(selectedDomain)
                .unsupportedWire(unsupportedWire)
                .missingSlots(missingSlots != null ? missingSlots : List.of())
                .candidateDomains(candidateDomains != null ? candidateDomains : List.of())
                .allowedWires(allowedWires != null ? allowedWires : List.of())
                .allowedContractCount(allowedContractCount)
                .matchedContractId(matchedContractId)
                .clarificationQuestion(question)
                .activeStrictBlockers(blockers)
                .build();
    }

    private static String blank(String s) {
        return StringUtils.hasText(s) ? s.trim() : null;
    }
}
