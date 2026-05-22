package com.nongxinle.ai.semantic.contract;

import lombok.experimental.UtilityClass;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * ACTIVE {@link SemanticCapabilityContract} 与 LLM 槽位组合的只读匹配（Validator / strict 前置）。
 * <p>{@code wire} = 系统登记的规范能力编号（仅来自 ACTIVE 合同 entry）；模型输出合同外 wire → {@code UNSUPPORTED_WIRE}，
 * 禁止 alias / synonym / compat 归一后放行。
 */
@UtilityClass
final class SemanticCapabilityContractMatcher {

    static MatchResult matchActiveContracts(
            EffectiveSemanticContractFrame frame,
            List<SemanticCapabilityContract> active,
            String wire) {
        if (frame == null || active == null || active.isEmpty() || !StringUtils.hasText(wire)) {
            return MatchResult.noCandidates();
        }
        String canonWire = wire.trim();
        List<SemanticCapabilityContract> wireCandidates = new ArrayList<>();
        for (SemanticCapabilityContract contract : active) {
            if (contract != null && canonWire.equals(blank(contract.getWire()))) {
                wireCandidates.add(contract);
            }
        }
        if (wireCandidates.isEmpty()) {
            return MatchResult.noCandidates();
        }

        SlotSnapshot slots = frame.slotSnapshot();
        SemanticCapabilityContract matched = null;
        List<String> bestMissing = null;

        for (SemanticCapabilityContract candidate : wireCandidates) {
            List<String> missing = missingRequiredSlots(candidate, slots);
            if (!missing.isEmpty()) {
                if (bestMissing == null || missing.size() < bestMissing.size()) {
                    bestMissing = missing;
                }
                continue;
            }
            if (slotComboMatches(candidate, slots)) {
                matched = candidate;
                break;
            }
        }

        if (matched != null) {
            if (matched.isRequiresAnchor() && !frame.hasAnchorEvidence(matched.getAnchorType())) {
                return MatchResult.anchorMismatch(matched, wireCandidates.size());
            }
            return MatchResult.ok(matched, wireCandidates.size());
        }
        if (bestMissing != null && !bestMissing.isEmpty()) {
            return MatchResult.missingSlots(bestMissing, wireCandidates.size());
        }
        return MatchResult.unsupportedCombo(wireCandidates.size());
    }

    private static boolean slotComboMatches(SemanticCapabilityContract contract, SlotSnapshot slots) {
        if (!queryObjectMatches(contract.getQueryObjects(), slots.queryObject())) {
            return false;
        }
        if (!operationMatches(contract.getOperations(), slots.operation())) {
            return false;
        }
        if (!metricMatches(contract.getMetrics(), slots.metric())) {
            return false;
        }
        if (!sourceFacetMatches(contract.getSourceFacet(), slots.sourceFacet())) {
            return false;
        }
        if (!detailWantedMatches(contract.getDetailWanted(), slots.detailWanted())) {
            return false;
        }
        if (StringUtils.hasText(contract.getAnswerPlanType()) && StringUtils.hasText(slots.answerPlanType())) {
            return contract.getAnswerPlanType().trim().equalsIgnoreCase(slots.answerPlanType().trim());
        }
        return true;
    }

    private static List<String> missingRequiredSlots(
            SemanticCapabilityContract contract, SlotSnapshot slots) {
        List<String> missing = new ArrayList<>();
        if (contract.getQueryObjects() != null
                && !contract.getQueryObjects().isEmpty()
                && !StringUtils.hasText(slots.queryObject())) {
            missing.add("queryObject");
        }
        if (contract.getOperations() != null
                && !contract.getOperations().isEmpty()
                && !StringUtils.hasText(slots.operation())) {
            missing.add("operation");
        }
        if (contract.getMetrics() != null
                && !contract.getMetrics().isEmpty() && !StringUtils.hasText(slots.metric())) {
            missing.add("metric");
        }
        if (StringUtils.hasText(contract.getSourceFacet()) && !StringUtils.hasText(slots.sourceFacet())) {
            missing.add("sourceFacet");
        }
        if (StringUtils.hasText(contract.getDetailWanted()) && !StringUtils.hasText(slots.detailWanted())) {
            missing.add("detailWanted");
        }
        return missing;
    }

    private static boolean queryObjectMatches(Set<String> allowed, String slotValue) {
        if (allowed == null || allowed.isEmpty()) {
            return true;
        }
        if (!StringUtils.hasText(slotValue)) {
            return false;
        }
        return allowed.contains(normalizeToken(slotValue));
    }

    private static boolean operationMatches(Set<String> allowed, String slotValue) {
        if (allowed == null || allowed.isEmpty()) {
            return true;
        }
        if (!StringUtils.hasText(slotValue)) {
            return false;
        }
        String op = normalizeToken(slotValue);
        for (String allowedOp : allowed) {
            String normalized = normalizeToken(allowedOp);
            if (op.equals(normalized) || op.contains(normalized) || normalized.contains(op)) {
                return true;
            }
        }
        return false;
    }

    private static boolean metricMatches(Set<String> allowed, String slotValue) {
        if (allowed == null || allowed.isEmpty()) {
            return true;
        }
        if (!StringUtils.hasText(slotValue)) {
            return false;
        }
        String metric = normalizeToken(slotValue);
        for (String allowedMetric : allowed) {
            if (metric.contains(normalizeToken(allowedMetric))) {
                return true;
            }
        }
        return false;
    }

    private static boolean sourceFacetMatches(String contractFacet, String slotFacet) {
        if (!StringUtils.hasText(contractFacet)) {
            return true;
        }
        if (!StringUtils.hasText(slotFacet)) {
            return false;
        }
        return normalizeToken(contractFacet).equals(normalizeToken(slotFacet));
    }

    private static boolean detailWantedMatches(String contractDetail, String slotDetail) {
        if (!StringUtils.hasText(contractDetail)) {
            return true;
        }
        if (!StringUtils.hasText(slotDetail)) {
            return false;
        }
        return normalizeToken(contractDetail).equals(normalizeToken(slotDetail));
    }

    private static String normalizeToken(String value) {
        return value == null ? null : value.trim().toUpperCase(Locale.ROOT);
    }

    private static String blank(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    record SlotSnapshot(
            String queryObject,
            String operation,
            String metric,
            String sourceFacet,
            String detailWanted,
            String answerPlanType,
            String anchorPolicy,
            String structuredIntentDetailWire) {

        static SlotSnapshot empty() {
            return new SlotSnapshot(null, null, null, null, null, null, null, null);
        }
    }

    record MatchResult(
            SemanticCapabilityContract matchedContract,
            SemanticContractViolationCode violationCode,
            String violationReason,
            List<String> missingSlots,
            int wireCandidateCount) {

        static MatchResult noCandidates() {
            return new MatchResult(null, null, null, List.of(), 0);
        }

        static MatchResult ok(SemanticCapabilityContract matched, int wireCandidateCount) {
            return new MatchResult(matched, null, null, List.of(), wireCandidateCount);
        }

        static MatchResult unsupportedCombo(int wireCandidateCount) {
            return new MatchResult(
                    null,
                    SemanticContractViolationCode.UNSUPPORTED_SLOT_COMBO,
                    "wire_allowed_but_slot_combo_unmatched",
                    List.of(),
                    wireCandidateCount);
        }

        static MatchResult missingSlots(List<String> missing, int wireCandidateCount) {
            return new MatchResult(
                    null,
                    SemanticContractViolationCode.MISSING_REQUIRED_SLOT,
                    "missing:" + String.join(",", missing),
                    missing,
                    wireCandidateCount);
        }

        static MatchResult anchorMismatch(SemanticCapabilityContract contract, int wireCandidateCount) {
            return new MatchResult(
                    contract,
                    SemanticContractViolationCode.ANCHOR_CONTRACT_MISMATCH,
                    "requiresAnchor:" + contract.getAnchorType(),
                    List.of(),
                    wireCandidateCount);
        }

        boolean isViolation() {
            return violationCode != null;
        }
    }
}
