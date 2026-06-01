package com.nongxinle.ai.semantic.contract;

import com.nongxinle.ai.conversation.AiConversationTurnMemory;
import com.nongxinle.ai.conversation.AiQuerySemanticLexicon;
import com.nongxinle.ai.semantic.AiQuerySemanticParseResult;
import com.nongxinle.ai.semantic.AiQuerySemanticSlotMerge;
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
 * <p>contract-locked 后 {@code structuredIntentDetailWire}、{@code answerPlanType}、{@code selectedTools}
 * 等 execution metadata 以 contract entry 为准；LLM 槽位 wire 仅作 debug 观测（{@code llmObservedStructuredIntentDetailWire}）。
 */
public final class SemanticContractCompletionEngine {

    /** completion trace：{@code selectedContractId} 已命中 ACTIVE Catalog 且槽位校验通过。 */
    public static final String TRACE_CONTRACT_ENTRY_VALIDATED = "contractEntryValidated";
    /** completion trace：无 ACTIVE contract entry 时走 legacy-only 收养路径（非 Catalog 域或 selection skipped）。 */
    public static final String TRACE_LEGACY_NO_CATALOG_PATH = "legacyNoCatalogPath";

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
        selectedContractId = selectedContractId.trim();

        if (!allowedContractsContain(entries, selectedContractId)) {
            return violation(
                    raw,
                    SemanticContractViolationCode.UNSUPPORTED_CONTRACT,
                    "selectedContractId_not_in_allowed_contracts",
                    Map.of("selectedContractId", selectedContractId, "selectedDomain", domain));
        }

        SemanticCapabilityContract contract = findActiveContract(domain, selectedContractId);
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

        // 先按 ACTIVE entry 补齐 contract-owned wire/operation/metric 等，再校验残留冲突（避免
        // 「single_dish + 上一轮排行 wire」在 apply 前误判 UNSUPPORTED 并保留 ranking wire）。
        AiQuerySemanticParseResult completed =
                applyContractToParse(
                        raw,
                        contract,
                        request.getPreviousTurn(),
                        request.getRewriteInheritedAnchorType(),
                        request.getRewriteInheritedAnchorName());

        com.nongxinle.ai.semantic.frame.CurrentSemanticFrame completedFrame =
                com.nongxinle.ai.semantic.frame.CurrentSemanticFrame.buildFrame(
                        com.nongxinle.ai.semantic.contract.canonicalizer.ContractFrameLightNormalizer.normalize(
                                completed));
        List<String> slotMismatches =
                com.nongxinle.ai.semantic.frame.ContractEntrySemanticFrameValidationSupport
                        .slotMismatchesAgainstContract(completedFrame, completed, contract);
        if (!slotMismatches.isEmpty()) {
            return violation(
                    completed,
                    SemanticContractViolationCode.UNSUPPORTED_CONTRACT,
                    "selectedContractId_slot_mismatch:" + String.join(",", slotMismatches),
                    Map.of(
                            "selectedContractId",
                            contract.getContractId(),
                            "slotMismatchFields",
                            slotMismatches,
                            "missingSlots",
                            slotMismatches));
        }

        EffectiveSemanticContractFrame anchorFrame =
                EffectiveSemanticContractFrame.of(
                        completed,
                        domain,
                        request.getPreviousTurn(),
                        request.getRewriteInheritedAnchorType(),
                        request.getRewriteInheritedAnchorName());
        if (contract.isRequiresAnchor()
                && anchorFrame != null
                && !anchorFrame.hasAnchorEvidence(contract.getAnchorType())) {
            return violation(
                    completed,
                    SemanticContractViolationCode.ANCHOR_CONTRACT_MISMATCH,
                    "requiresAnchor:" + contract.getAnchorType(),
                    Map.of("selectedContractId", contract.getContractId()));
        }

        Map<String, Object> trace = new LinkedHashMap<>();
        trace.put(TRACE_CONTRACT_ENTRY_VALIDATED, true);
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

    /**
     * ACTIVE contract entry 已通过 {@link #complete} 校验并补齐 execution metadata。
     * 不得用 {@link #hasSelectedContractId} 替代本方法。
     */
    public static boolean isContractEntryValidated(AiQuerySemanticParseResult parse) {
        if (parse == null) {
            return false;
        }
        Map<String, Object> trace = parse.getContractCompletionTrace();
        return trace != null && Boolean.TRUE.equals(trace.get(TRACE_CONTRACT_ENTRY_VALIDATED));
    }

    /**
     * P2C contract-locked：{@code contractEntryValidated=true} 的 parse（非 {@code selectedContractId} 非空）。
     * 主链不得再调用 Matrix {@code resolveStructuredIntentDetailWire} / slots→wire 推导覆盖 wire 或 execution path。
     */
    public static boolean isContractLockedParse(AiQuerySemanticParseResult parse) {
        return isContractEntryValidated(parse);
    }

    /** {@link #complete} passThrough（无 ACTIVE Catalog）后的 legacy-only 收养路径。 */
    public static boolean isLegacyNoCatalogParse(AiQuerySemanticParseResult parse) {
        if (parse == null) {
            return false;
        }
        Map<String, Object> trace = parse.getContractCompletionTrace();
        return trace != null && Boolean.TRUE.equals(trace.get(TRACE_LEGACY_NO_CATALOG_PATH));
    }

    public static String extractSelectedContractId(AiQuerySemanticParseResult parse) {
        if (parse == null || parse.getSemanticSlots() == null) {
            return null;
        }
        return blank(parse.getSemanticSlots().getSelectedContractId());
    }

    private static SemanticCapabilityContract findActiveContract(String domain, String contractId) {
        return SemanticContractCatalog.findActiveCapabilityContractById(contractId, domain);
    }

    private static AiQuerySemanticParseResult applyContractToParse(
            AiQuerySemanticParseResult raw,
            SemanticCapabilityContract contract,
            AiConversationTurnMemory previousTurn,
            String rewriteInheritedAnchorType,
            String rewriteInheritedAnchorName) {
        AiQuerySemanticParseResult.SemanticSlotsPart prev =
                raw.getSemanticSlots() != null ? raw.getSemanticSlots() : new AiQuerySemanticParseResult.SemanticSlotsPart();

        String queryObject = coalesceSlot(prev.getQueryObject(), contract.getQueryObjects());
        String operation = coalesceSlot(prev.getOperation(), contract.getOperations());
        String metric =
                ContractBusinessSlotRequirementSupport.coalesceMetricFromContract(
                        prev.getMetric(), contract, operation);
        String sourceFacet =
                coalesceSlot(prev.getSourceFacet(), contract.getSourceFacet());
        String detailWanted =
                coalesceSlot(prev.getDetailWanted(), contract.getDetailWanted());
        String llmObservedWire =
                StringUtils.hasText(prev.getStructuredIntentDetailWire())
                        ? AiQuerySemanticLexicon.canonicalStructuredIntentDetailWire(
                                prev.getStructuredIntentDetailWire().trim())
                        : null;
        String wire = authoritativeContractWire(contract);
        String answerPlanType =
                StringUtils.hasText(contract.getAnswerPlanType())
                        ? normalizeToken(contract.getAnswerPlanType())
                        : coalesceSlot(prev.getAnswerPlanType(), contract.getAnswerPlanType());
        boolean rankingOperation = "RANKING".equals(normalizeToken(operation));
        String mentionedDishName = coalesceMentionedDishName(prev, raw);
        if (!StringUtils.hasText(mentionedDishName) && !rankingOperation) {
            mentionedDishName =
                    SemanticContractAnchorInheritanceSupport.resolveInheritedDishAnchorWhenUsePrevious(
                            raw, previousTurn, rewriteInheritedAnchorType, rewriteInheritedAnchorName);
        }
        String mentionedGoodsName = coalesceMentionedGoodsName(prev, raw);
        if (!StringUtils.hasText(mentionedGoodsName) && contract.isRequiresAnchor()) {
            mentionedGoodsName =
                    SemanticContractAnchorInheritanceSupport.resolveInheritedGoodsAnchorWhenUsePrevious(
                            raw, previousTurn, rewriteInheritedAnchorType, rewriteInheritedAnchorName);
        }
        String anchorPolicy = normalizeToken(prev.getAnchorPolicy());
        if (rankingOperation && !contract.isRequiresAnchor()) {
            anchorPolicy = AiQuerySemanticSlotMerge.ANCHOR_IGNORE_PREVIOUS;
        }

        AiQuerySemanticParseResult.SemanticSlotsPart slots =
                AiQuerySemanticParseResult.SemanticSlotsPart.builder()
                        .selectedContractId(contract.getContractId())
                        .queryObject(normalizeToken(queryObject))
                        .operation(normalizeToken(operation))
                        .metric(normalizeToken(metric))
                        .sourceFacet(normalizeToken(sourceFacet))
                        .anchorPolicy(anchorPolicy)
                        .detailWanted(normalizeToken(detailWanted))
                        .structuredIntentDetailWire(wire)
                        .answerPlanType(normalizeToken(answerPlanType))
                        .mentionedDishName(mentionedDishName)
                        .mentionedGoodsName(mentionedGoodsName)
                        .requestedTargetGrossMarginRate(
                                coalesceRequestedTargetGrossMarginRate(prev, raw))
                        .build();

        AiQuerySemanticParseResult.OrchestrationDecisionCandidatePart orch =
                mergeSelectedTools(raw.getOrchestrationDecisionCandidate(), contract.getSelectedTools());

        Map<String, Object> trace = new LinkedHashMap<>();
        trace.put(TRACE_CONTRACT_ENTRY_VALIDATED, true);
        trace.put("rawSelectedContractId", contract.getContractId());
        trace.put("llmObservedStructuredIntentDetailWire", llmObservedWire);
        trace.put("completedWire", wire);
        trace.put("wire", wire);
        trace.putAll(ContractExecutionMappingSupport.executionTraceFields(contract));

        return raw.toBuilder()
                .semanticSlots(slots)
                .mentionedDishName(coalesceMentionedDishNameTopLevel(raw, mentionedDishName))
                .mentionedGoodsName(coalesceMentionedGoodsNameTopLevel(raw, mentionedGoodsName))
                .orchestrationDecisionCandidate(orch)
                .currentTurnStructuredIntentDetailWire(wire)
                .contractCompletionTrace(trace)
                .build();
    }

    /** 与 {@link AiQuerySemanticParseResult#effectiveMentionedDishName()} 一致：顶层优先，不读 rawMessage。 */
    private static String coalesceMentionedDishName(
            AiQuerySemanticParseResult.SemanticSlotsPart prev, AiQuerySemanticParseResult raw) {
        if (raw == null) {
            return prev != null && StringUtils.hasText(prev.getMentionedDishName())
                    ? prev.getMentionedDishName().trim()
                    : null;
        }
        return raw.effectiveMentionedDishName();
    }

    /** 与 {@link AiQuerySemanticParseResult#effectiveMentionedGoodsName()} 一致：顶层优先，不读 rawMessage。 */
    private static String coalesceMentionedGoodsName(
            AiQuerySemanticParseResult.SemanticSlotsPart prev, AiQuerySemanticParseResult raw) {
        if (raw == null) {
            return prev != null && StringUtils.hasText(prev.getMentionedGoodsName())
                    ? prev.getMentionedGoodsName().trim()
                    : null;
        }
        return raw.effectiveMentionedGoodsName();
    }

    private static String coalesceMentionedDishNameTopLevel(
            AiQuerySemanticParseResult raw, String slotMentionedDishName) {
        if (raw != null && StringUtils.hasText(raw.getMentionedDishName())) {
            return raw.getMentionedDishName().trim();
        }
        return slotMentionedDishName;
    }

    private static String coalesceMentionedGoodsNameTopLevel(
            AiQuerySemanticParseResult raw, String slotMentionedGoodsName) {
        if (raw != null && StringUtils.hasText(raw.getMentionedGoodsName())) {
            return raw.getMentionedGoodsName().trim();
        }
        return slotMentionedGoodsName;
    }

    /** 合同 completion 重建 slots 时透传 LLM 槽位；不读 rawMessage。 */
    private static String coalesceRequestedTargetGrossMarginRate(
            AiQuerySemanticParseResult.SemanticSlotsPart prev, AiQuerySemanticParseResult raw) {
        if (prev != null && StringUtils.hasText(prev.getRequestedTargetGrossMarginRate())) {
            return prev.getRequestedTargetGrossMarginRate().trim();
        }
        if (raw != null) {
            return raw.effectiveRequestedTargetGrossMarginRate();
        }
        return null;
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
        Map<String, Object> trace = new LinkedHashMap<>();
        trace.put("skipped", reason);
        trace.put(TRACE_CONTRACT_ENTRY_VALIDATED, false);
        if ("no_allowed_contracts".equals(reason)) {
            trace.put(TRACE_LEGACY_NO_CATALOG_PATH, true);
        }
        AiQuerySemanticParseResult completed = preparePassThroughParse(raw, trace);
        return Result.builder()
                .rawParse(raw)
                .completedParse(completed)
                .violation(false)
                .completionTrace(trace)
                .build();
    }

    /**
     * 无 ACTIVE Catalog 时不得保留 LLM {@code selectedContractId}，避免假 contract-locked 绕开 Matrix 校验。
     */
    private static AiQuerySemanticParseResult preparePassThroughParse(
            AiQuerySemanticParseResult raw, Map<String, Object> trace) {
        if (raw == null) {
            return null;
        }
        if (!hasSelectedContractId(raw) || raw.getSemanticSlots() == null) {
            return raw.toBuilder().contractCompletionTrace(new LinkedHashMap<>(trace)).build();
        }
        trace.put("ignoredSelectedContractId", extractSelectedContractId(raw));
        trace.put("ignoredSelectedContractIdReason", "no_active_catalog_entry");
        AiQuerySemanticParseResult.SemanticSlotsPart s = raw.getSemanticSlots();
        AiQuerySemanticParseResult.SemanticSlotsPart cleared =
                AiQuerySemanticParseResult.SemanticSlotsPart.builder()
                        .queryObject(s.getQueryObject())
                        .operation(s.getOperation())
                        .metric(s.getMetric())
                        .sourceFacet(s.getSourceFacet())
                        .anchorPolicy(s.getAnchorPolicy())
                        .detailWanted(s.getDetailWanted())
                        .structuredIntentDetailWire(s.getStructuredIntentDetailWire())
                        .answerPlanType(s.getAnswerPlanType())
                        .mentionedDishName(s.getMentionedDishName())
                        .mentionedGoodsName(s.getMentionedGoodsName())
                        .requestedTargetGrossMarginRate(s.getRequestedTargetGrossMarginRate())
                        .build();
        return raw.toBuilder()
                .semanticSlots(cleared)
                .contractCompletionTrace(new LinkedHashMap<>(trace))
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
        trace.put("contractCompletionFailureReason", reason);
        trace.put("violationCode", code != null ? code.name() : null);
        trace.put(TRACE_CONTRACT_ENTRY_VALIDATED, false);
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

    private static String authoritativeContractWire(SemanticCapabilityContract contract) {
        if (contract == null || !StringUtils.hasText(contract.getWire())) {
            return null;
        }
        return AiQuerySemanticLexicon.canonicalStructuredIntentDetailWire(contract.getWire().trim());
    }

    private static String coalesceSlot(String llmValue, Set<String> contractValues) {
        if (StringUtils.hasText(llmValue)) {
            return normalizeToken(llmValue);
        }
        if (contractValues != null && !contractValues.isEmpty()) {
            return normalizeToken(contractValues.iterator().next());
        }
        return null;
    }

    private static String coalesceSlot(String llmValue, String contractValue) {
        if (StringUtils.hasText(llmValue)) {
            return normalizeToken(llmValue);
        }
        return blank(contractValue);
    }

    private static boolean allowedContractsContain(
            List<SemanticParserAllowedOutputContract.AllowedContractEntry> entries, String contractId) {
        if (entries == null || entries.isEmpty() || !StringUtils.hasText(contractId)) {
            return false;
        }
        for (SemanticParserAllowedOutputContract.AllowedContractEntry entry : entries) {
            if (entry != null && contractId.equals(entry.getContractId())) {
                return true;
            }
        }
        return false;
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
