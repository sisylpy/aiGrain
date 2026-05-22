package com.nongxinle.ai.semantic.contract;

import com.nongxinle.ai.conversation.AiConversationTurnMemory;
import com.nongxinle.ai.conversation.AiQuerySemanticLexicon;
import com.nongxinle.ai.semantic.AiQuerySemanticParseResult;
import com.nongxinle.ai.semantic.SemanticParserAllowedOutputContract;
import lombok.Builder;
import lombok.Value;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * P4-J2：根据 Parser 输出的 {@code selectedContractId} 从 ACTIVE {@link SemanticCapabilityContract}
 * 补齐 completedParse；禁止读用户原文或按槽位形状改选合同。
 */
public final class SemanticContractCompletionEngine {

    private SemanticContractCompletionEngine() {}

    @Value
    @Builder
    public static class Request {
        AiQuerySemanticParseResult rawParse;
        String selectedDomain;
        DomainContractSelectionResult contractSelection;
        AiConversationTurnMemory previousTurn;
        String rewriteInheritedAnchorType;
        String rewriteInheritedAnchorName;
    }

    @Value
    @Builder
    public static class Result {
        AiQuerySemanticParseResult completedParse;
        AiQuerySemanticParseResult rawParse;
        boolean violation;
        SemanticContractViolationCode violationCode;
        String violationReason;
        Map<String, Object> completionTrace;
    }

    public static Result complete(Request request) {
        if (request == null || request.getRawParse() == null || request.getRawParse().isParseMissing()) {
            return Result.builder()
                    .rawParse(request != null ? request.getRawParse() : null)
                    .completedParse(request != null ? request.getRawParse() : null)
                    .violation(false)
                    .build();
        }
        AiQuerySemanticParseResult raw = request.getRawParse();
        DomainContractSelectionResult selection = request.getContractSelection();
        SemanticParserAllowedOutputContract allowed =
                selection != null ? selection.getParserAllowedOutputContract() : null;
        List<SemanticParserAllowedOutputContract.AllowedContractEntry> entries =
                allowed != null && allowed.getAllowedContracts() != null
                        ? allowed.getAllowedContracts()
                        : List.of();

        if (entries.isEmpty()) {
            return passThrough(raw, "no_allowed_contracts");
        }

        String domain = blank(request.getSelectedDomain());
        if (!StringUtils.hasText(domain) && selection != null) {
            domain = blank(selection.getSelectedDomain());
        }

        String selectedContractId = extractSelectedContractId(raw);
        if (!StringUtils.hasText(selectedContractId)) {
            return violation(
                    raw,
                    SemanticContractViolationCode.MISSING_SELECTED_CONTRACT_ID,
                    "missing_selectedContractId",
                    Map.of("selectedDomain", domain));
        }

        SemanticCapabilityContract contract =
                findActiveContract(domain, selectedContractId.trim());
        if (contract == null) {
            return violation(
                    raw,
                    SemanticContractViolationCode.UNSUPPORTED_CONTRACT,
                    "contract_not_found_or_inactive:" + selectedContractId.trim(),
                    Map.of("selectedContractId", selectedContractId.trim(), "selectedDomain", domain));
        }

        if (StringUtils.hasText(domain)
                && StringUtils.hasText(contract.getDomain())
                && !domain.equalsIgnoreCase(contract.getDomain().trim())) {
            return violation(
                    raw,
                    SemanticContractViolationCode.UNSUPPORTED_CONTRACT,
                    "contract_domain_mismatch",
                    Map.of(
                            "selectedContractId", contract.getContractId(),
                            "expectedDomain", domain,
                            "contractDomain", contract.getDomain()));
        }

        EffectiveSemanticContractFrame anchorFrame =
                EffectiveSemanticContractFrame.of(
                        raw,
                        domain,
                        request.getPreviousTurn(),
                        request.getRewriteInheritedAnchorType(),
                        request.getRewriteInheritedAnchorName());
        if (contract.isRequiresAnchor()
                && anchorFrame != null
                && !anchorFrame.hasAnchorEvidence(contract.getAnchorType())) {
            return violation(
                    raw,
                    SemanticContractViolationCode.ANCHOR_CONTRACT_MISMATCH,
                    "requiresAnchor:" + contract.getAnchorType(),
                    Map.of("selectedContractId", contract.getContractId()));
        }

        AiQuerySemanticParseResult completed = applyContractToParse(raw, contract);
        Map<String, Object> trace = new LinkedHashMap<>();
        trace.put("selectedContractId", contract.getContractId());
        trace.put("wire", contract.getWire());
        trace.put("domain", domain);
        return Result.builder()
                .rawParse(raw)
                .completedParse(completed)
                .violation(false)
                .completionTrace(trace)
                .build();
    }

    public static boolean hasSelectedContractId(AiQuerySemanticParseResult parse) {
        return StringUtils.hasText(extractSelectedContractId(parse));
    }

    public static String extractSelectedContractId(AiQuerySemanticParseResult parse) {
        if (parse == null || parse.getSemanticSlots() == null) {
            return null;
        }
        return blank(parse.getSemanticSlots().getSelectedContractId());
    }

    private static SemanticCapabilityContract findActiveContract(String domain, String contractId) {
        if (!StringUtils.hasText(contractId)) {
            return null;
        }
        List<SemanticCapabilityContract> active =
                StringUtils.hasText(domain)
                        ? SemanticContractCatalog.listActiveCapabilityContracts(domain)
                        : List.of();
        for (SemanticCapabilityContract c : active) {
            if (c != null
                    && contractId.equals(c.getContractId())
                    && c.getStatus() == SemanticCapabilityContractStatus.ACTIVE) {
                return c;
            }
        }
        return null;
    }

    private static AiQuerySemanticParseResult applyContractToParse(
            AiQuerySemanticParseResult raw, SemanticCapabilityContract contract) {
        AiQuerySemanticParseResult.SemanticSlotsPart prev =
                raw.getSemanticSlots() != null ? raw.getSemanticSlots() : new AiQuerySemanticParseResult.SemanticSlotsPart();

        String queryObject = firstOfSet(contract.getQueryObjects(), prev.getQueryObject());
        String operation = firstOfSet(contract.getOperations(), prev.getOperation());
        String metric = firstOfSet(contract.getMetrics(), prev.getMetric());
        String sourceFacet =
                StringUtils.hasText(contract.getSourceFacet())
                        ? contract.getSourceFacet().trim()
                        : blank(prev.getSourceFacet());
        String detailWanted =
                StringUtils.hasText(contract.getDetailWanted())
                        ? contract.getDetailWanted().trim()
                        : blank(prev.getDetailWanted());
        String wire =
                StringUtils.hasText(contract.getWire())
                        ? AiQuerySemanticLexicon.canonicalStructuredIntentDetailWire(contract.getWire().trim())
                        : blank(prev.getStructuredIntentDetailWire());
        String answerPlanType =
                StringUtils.hasText(contract.getAnswerPlanType())
                        ? contract.getAnswerPlanType().trim()
                        : blank(prev.getAnswerPlanType());

        AiQuerySemanticParseResult.SemanticSlotsPart slots =
                AiQuerySemanticParseResult.SemanticSlotsPart.builder()
                        .selectedContractId(contract.getContractId())
                        .queryObject(normalizeToken(queryObject))
                        .operation(normalizeToken(operation))
                        .metric(normalizeToken(metric))
                        .sourceFacet(normalizeToken(sourceFacet))
                        .anchorPolicy(normalizeToken(prev.getAnchorPolicy()))
                        .detailWanted(normalizeToken(detailWanted))
                        .structuredIntentDetailWire(wire)
                        .answerPlanType(normalizeToken(answerPlanType))
                        .build();

        AiQuerySemanticParseResult.OrchestrationDecisionCandidatePart orch =
                mergeSelectedTools(raw.getOrchestrationDecisionCandidate(), contract.getSelectedTools());

        Map<String, Object> trace = new LinkedHashMap<>();
        trace.put("rawSelectedContractId", contract.getContractId());
        trace.put("completedWire", wire);

        return raw.toBuilder()
                .semanticSlots(slots)
                .orchestrationDecisionCandidate(orch)
                .currentTurnStructuredIntentDetailWire(wire)
                .contractCompletionTrace(trace)
                .build();
    }

    private static AiQuerySemanticParseResult.OrchestrationDecisionCandidatePart mergeSelectedTools(
            AiQuerySemanticParseResult.OrchestrationDecisionCandidatePart orch,
            List<String> contractTools) {
        if (contractTools == null || contractTools.isEmpty()) {
            return orch;
        }
        if (orch == null) {
            return AiQuerySemanticParseResult.OrchestrationDecisionCandidatePart.builder()
                    .selectedTools(new ArrayList<>(contractTools))
                    .build();
        }
        List<String> merged = new ArrayList<>();
        if (orch.getSelectedTools() != null) {
            merged.addAll(orch.getSelectedTools());
        }
        for (String t : contractTools) {
            if (StringUtils.hasText(t) && !merged.contains(t.trim())) {
                merged.add(t.trim());
            }
        }
        return orch.toBuilder().selectedTools(merged).build();
    }

    private static Result passThrough(AiQuerySemanticParseResult raw, String reason) {
        return Result.builder()
                .rawParse(raw)
                .completedParse(raw)
                .violation(false)
                .completionTrace(Map.of("skipped", reason))
                .build();
    }

    private static Result violation(
            AiQuerySemanticParseResult raw,
            SemanticContractViolationCode code,
            String reason,
            Map<String, Object> traceExtra) {
        Map<String, Object> trace = new LinkedHashMap<>();
        if (traceExtra != null) {
            trace.putAll(traceExtra);
        }
        trace.put("violationReason", reason);
        trace.put("violationCode", code != null ? code.name() : null);
        AiQuerySemanticParseResult flagged =
                raw.toBuilder()
                        .needClarification(true)
                        .reason(reason)
                        .contractCompletionTrace(trace)
                        .build();
        return Result.builder()
                .rawParse(raw)
                .completedParse(flagged)
                .violation(true)
                .violationCode(code)
                .violationReason(reason)
                .completionTrace(trace)
                .build();
    }

    private static String firstOfSet(Set<String> set, String fallback) {
        if (set != null && !set.isEmpty()) {
            return set.iterator().next();
        }
        return blank(fallback);
    }

    private static String normalizeToken(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        return raw.trim().toUpperCase(Locale.ROOT).replace('-', '_');
    }

    private static String blank(String s) {
        return StringUtils.hasText(s) ? s.trim() : null;
    }
}
