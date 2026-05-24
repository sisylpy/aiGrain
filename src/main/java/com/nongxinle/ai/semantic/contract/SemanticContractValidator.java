package com.nongxinle.ai.semantic.contract;

import com.nongxinle.ai.semantic.AiQuerySemanticParseResult;
import com.nongxinle.ai.semantic.SemanticParserAllowedOutputContract;
import com.nongxinle.ai.semantic.contract.SemanticContractCompletionEngine;
import com.nongxinle.ai.semantic.intake.route.SemanticDomainRouteResult;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 合同校验（P2.5：observe model output vs selected ACTIVE contracts；不改变业务执行）。
 * <p>P4-H：基于 {@link EffectiveSemanticContractFrame}（SlotMerge + frame + anchor 证据），不直接读 raw LLM slots。
 */
public final class SemanticContractValidator {

    private SemanticContractValidator() {
    }

    public static SemanticContractValidationDebug observe(
            EffectiveSemanticContractFrame frame, DomainContractSelectionResult selection) {
        if (frame == null || frame.getParse() == null || frame.getParse().isParseMissing() || selection == null) {
            return null;
        }
        String selectedDomain = blank(selection.getSelectedDomain());
        SemanticParserAllowedOutputContract allowed = selection.getParserAllowedOutputContract();
        List<String> allowedWires =
                allowed != null && allowed.getAllowedWires() != null
                        ? allowed.getAllowedWires()
                        : List.of();
        int allowedContractCount = selection.getSelectedActiveContractCount();

        if (allowedWires.isEmpty()) {
            return SemanticContractValidationDebug.builder()
                    .modelContractViolation(false)
                    .selectedDomain(selectedDomain)
                    .allowedWires(allowedWires.isEmpty() ? null : allowedWires)
                    .allowedContractCount(allowedContractCount)
                    .build();
        }

        List<SemanticParserAllowedOutputContract.AllowedContractEntry> allowedEntries =
                allowed != null && allowed.getAllowedContracts() != null
                        ? allowed.getAllowedContracts()
                        : List.of();
        String selectedContractId = SemanticContractCompletionEngine.extractSelectedContractId(frame.getParse());

        if (!allowedEntries.isEmpty()) {
            if (!StringUtils.hasText(selectedContractId)) {
                return SemanticContractValidationDebug.builder()
                        .modelContractViolation(true)
                        .violationCode(SemanticContractViolationCode.MISSING_SELECTED_CONTRACT_ID)
                        .violationReason("missing_selectedContractId")
                        .selectedDomain(selectedDomain)
                        .allowedWires(allowedWires.isEmpty() ? null : allowedWires)
                        .allowedContractCount(allowedContractCount)
                        .build();
            }
            return validateBySelectedContractId(
                    frame, selection, selectedDomain, allowedWires, allowedContractCount, selectedContractId.trim());
        }

        // TODO(LEGACY_ONLY-CLEANUP): allowedContracts 为空时（legacy 路径）仍走 wire 匹配主链；
        // REVENUE/STOCK_REDUCE/WAREHOUSE 应全面切到 selectedContractId + contract entry 主导。
        String wire = extractWire(frame);

        if (StringUtils.hasText(selectedContractId)) {
            return validateBySelectedContractId(
                    frame, selection, selectedDomain, allowedWires, allowedContractCount, selectedContractId.trim());
        }

        if (!StringUtils.hasText(wire)) {
            return SemanticContractValidationDebug.builder()
                    .modelContractViolation(false)
                    .selectedDomain(selectedDomain)
                    .allowedWires(allowedWires)
                    .allowedContractCount(allowedContractCount)
                    .build();
        }

        String canonWire = wire.trim();
        if (!allowedWires.contains(canonWire)) {
            return SemanticContractValidationDebug.builder()
                    .modelContractViolation(true)
                    .unsupportedWire(canonWire)
                    .violationCode(SemanticContractViolationCode.UNSUPPORTED_WIRE)
                    .violationReason("wire_not_in_allowedWires")
                    .selectedDomain(selectedDomain)
                    .allowedWires(allowedWires)
                    .allowedContractCount(allowedContractCount)
                    .build();
        }

        List<SemanticCapabilityContract> active =
                StringUtils.hasText(selectedDomain)
                        ? SemanticContractCatalog.listActiveCapabilityContracts(selectedDomain)
                        : List.of();
        SemanticCapabilityContractMatcher.SlotSnapshot slotView = frame.slotSnapshot();
        String matchWire =
                StringUtils.hasText(slotView.structuredIntentDetailWire())
                        ? slotView.structuredIntentDetailWire().trim()
                        : canonWire;
        SemanticCapabilityContractMatcher.MatchResult match =
                SemanticCapabilityContractMatcher.matchActiveContracts(frame, active, matchWire);

        if (!match.isViolation()) {
            String matchedId =
                    match.matchedContract() != null ? match.matchedContract().getContractId() : null;
            return SemanticContractValidationDebug.builder()
                    .modelContractViolation(false)
                    .selectedDomain(selectedDomain)
                    .allowedWires(allowedWires)
                    .allowedContractCount(allowedContractCount)
                    .matchedContractId(matchedId)
                    .build();
        }

        return SemanticContractValidationDebug.builder()
                .modelContractViolation(true)
                .unsupportedWire(canonWire)
                .violationCode(match.violationCode())
                .violationReason(match.violationReason())
                .selectedDomain(selectedDomain)
                .allowedWires(allowedWires)
                .allowedContractCount(allowedContractCount)
                .matchedContractId(
                        match.matchedContract() != null ? match.matchedContract().getContractId() : null)
                .missingSlots(
                        match.missingSlots() != null && !match.missingSlots().isEmpty()
                                ? match.missingSlots()
                                : null)
                .build();
    }

    /**
     * Strict enforce 路径（P3）：违例时返回澄清决策；不 mutate parse、不做 alias。
     * <p>由 {@link SemanticContractStrictDecisionEvaluator} 统一编排；单独调用时需自行检查 {@link SemanticContractStrictProperties#isEnabled()}。
     */
    public static SemanticContractStrictDecision enforce(
            SemanticDomainRouteResult route,
            DomainContractSelectionResult selection,
            EffectiveSemanticContractFrame frame,
            boolean strictEnabled) {
        return SemanticContractStrictDecisionEvaluator.evaluate(route, selection, frame, strictEnabled);
    }

    private static SemanticContractValidationDebug validateBySelectedContractId(
            EffectiveSemanticContractFrame frame,
            DomainContractSelectionResult selection,
            String selectedDomain,
            List<String> allowedWires,
            int allowedContractCount,
            String selectedContractId) {
        List<SemanticCapabilityContract> active =
                StringUtils.hasText(selectedDomain)
                        ? SemanticContractCatalog.listActiveCapabilityContracts(selectedDomain)
                        : List.of();
        SemanticCapabilityContract matched = null;
        for (SemanticCapabilityContract c : active) {
            if (c != null && selectedContractId.equals(c.getContractId())) {
                matched = c;
                break;
            }
        }
        if (matched == null || matched.getStatus() != SemanticCapabilityContractStatus.ACTIVE) {
            return SemanticContractValidationDebug.builder()
                    .modelContractViolation(true)
                    .unsupportedWire(selectedContractId)
                    .violationCode(SemanticContractViolationCode.UNSUPPORTED_CONTRACT)
                    .violationReason("selectedContractId_not_active")
                    .selectedDomain(selectedDomain)
                    .allowedWires(allowedWires.isEmpty() ? null : allowedWires)
                    .allowedContractCount(allowedContractCount)
                    .build();
        }
        if (hasAllowedContracts(selection) && !allowedContractsContain(selection, selectedContractId)) {
            return SemanticContractValidationDebug.builder()
                    .modelContractViolation(true)
                    .unsupportedWire(selectedContractId)
                    .violationCode(SemanticContractViolationCode.UNSUPPORTED_CONTRACT)
                    .violationReason("selectedContractId_not_in_allowed_contracts")
                    .selectedDomain(selectedDomain)
                    .allowedWires(allowedWires.isEmpty() ? null : allowedWires)
                    .allowedContractCount(allowedContractCount)
                    .build();
        }
        if (matched.isRequiresAnchor() && !frame.hasAnchorEvidence(matched.getAnchorType())) {
            return SemanticContractValidationDebug.builder()
                    .modelContractViolation(true)
                    .violationCode(SemanticContractViolationCode.ANCHOR_CONTRACT_MISMATCH)
                    .violationReason("requiresAnchor:" + matched.getAnchorType())
                    .selectedDomain(selectedDomain)
                    .allowedWires(allowedWires.isEmpty() ? null : allowedWires)
                    .allowedContractCount(allowedContractCount)
                    .matchedContractId(matched.getContractId())
                    .build();
        }
        com.nongxinle.ai.semantic.frame.CurrentSemanticFrame contractFrame =
                com.nongxinle.ai.semantic.frame.CurrentSemanticFrame.buildFrame(frame.getParse());
        List<String> missing =
                com.nongxinle.ai.semantic.frame.ContractEntrySemanticFrameValidationSupport
                        .slotMismatchesAgainstContract(contractFrame, frame.getParse(), matched);
        if (!missing.isEmpty()) {
            return SemanticContractValidationDebug.builder()
                    .modelContractViolation(true)
                    .unsupportedWire(matched.getWire())
                    .violationCode(SemanticContractViolationCode.UNSUPPORTED_CONTRACT)
                    .violationReason("selectedContractId_slot_mismatch:" + String.join(",", missing))
                    .selectedDomain(selectedDomain)
                    .allowedWires(allowedWires.isEmpty() ? null : allowedWires)
                    .allowedContractCount(allowedContractCount)
                    .matchedContractId(matched.getContractId())
                    .missingSlots(missing)
                    .build();
        }
        return SemanticContractValidationDebug.builder()
                .modelContractViolation(false)
                .selectedDomain(selectedDomain)
                .allowedWires(allowedWires.isEmpty() ? null : allowedWires)
                .allowedContractCount(allowedContractCount)
                .matchedContractId(matched.getContractId())
                .build();
    }

    private static boolean hasAllowedContracts(DomainContractSelectionResult selection) {
        if (selection == null || selection.getParserAllowedOutputContract() == null) {
            return false;
        }
        List<SemanticParserAllowedOutputContract.AllowedContractEntry> entries =
                selection.getParserAllowedOutputContract().getAllowedContracts();
        return entries != null && !entries.isEmpty();
    }

    private static boolean allowedContractsContain(
            DomainContractSelectionResult selection, String contractId) {
        if (selection == null || selection.getParserAllowedOutputContract() == null) {
            return false;
        }
        List<SemanticParserAllowedOutputContract.AllowedContractEntry> entries =
                selection.getParserAllowedOutputContract().getAllowedContracts();
        if (entries == null || entries.isEmpty()) {
            return false;
        }
        for (SemanticParserAllowedOutputContract.AllowedContractEntry entry : entries) {
            if (entry != null && contractId.equals(entry.getContractId())) {
                return true;
            }
        }
        return false;
    }

    private static String extractWire(EffectiveSemanticContractFrame frame) {
        SemanticCapabilityContractMatcher.SlotSnapshot slots = frame.slotSnapshot();
        if (StringUtils.hasText(slots.structuredIntentDetailWire())) {
            return slots.structuredIntentDetailWire().trim();
        }
        AiQuerySemanticParseResult parse = frame.getParse();
        if (parse.getSemanticSlots() != null
                && StringUtils.hasText(parse.getSemanticSlots().getStructuredIntentDetailWire())) {
            return parse.getSemanticSlots().getStructuredIntentDetailWire().trim();
        }
        if (StringUtils.hasText(parse.getCurrentTurnStructuredIntentDetailWire())) {
            return parse.getCurrentTurnStructuredIntentDetailWire().trim();
        }
        return null;
    }

    private static String blank(String s) {
        return StringUtils.hasText(s) ? s.trim() : null;
    }
}
