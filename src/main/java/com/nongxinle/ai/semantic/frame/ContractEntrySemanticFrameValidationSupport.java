package com.nongxinle.ai.semantic.frame;

import com.nongxinle.ai.conversation.AiQuerySemanticLexicon;
import com.nongxinle.ai.semantic.AiQuerySemanticParseResult;
import com.nongxinle.ai.semantic.contract.DomainContractSelectionResult;
import com.nongxinle.ai.semantic.contract.SemanticCapabilityContract;
import com.nongxinle.ai.semantic.contract.SemanticCapabilityContractStatus;
import com.nongxinle.ai.semantic.contract.SemanticContractCatalog;
import com.nongxinle.ai.semantic.contract.SemanticContractCompletionEngine;
import com.nongxinle.ai.semantic.SemanticParserAllowedOutputContract;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Predicate;

/**
 * 基础域 contract-entry 主链校验：selectedContractId + active contract entry + semanticSlots 一致性。
 * <p>不做 slots→wire 推导、不改写 contract；违例仅 clarify。
 * <p>缺失且可由 contract entry 补齐的槽位不算 mismatch；仅 LLM 已输出且与合同冲突时 block。
 * <p>{@code structuredIntentDetailWire} 为 contract-owned execution metadata：命中 ACTIVE entry 后由
 * {@link com.nongxinle.ai.semantic.contract.SemanticContractCompletionEngine} 从 {@code contract.getWire()} 补齐，
 * 不作为 slot mismatch 阻断条件。
 */
public final class ContractEntrySemanticFrameValidationSupport {

    private ContractEntrySemanticFrameValidationSupport() {}

    public record DomainContractEntryConfig(
            String domainCode,
            String domainLabel,
            Predicate<String> legacyCanonicalWireCheck) {}

    public static SemanticFrameValidationResult validateSelectedContractEntry(
            CurrentSemanticFrame frame,
            AiQuerySemanticParseResult rawParse,
            DomainContractSelectionResult contractSelection,
            DomainContractEntryConfig config,
            SlotsWireDerivationDebugObserver debugObserver) {
        if (config == null) {
            return SemanticFrameValidationResult.success();
        }
        String qMissingContract =
                "当前"
                        + config.domainLabel()
                        + "问题无法匹配已支持的能力合同，请在解析 JSON 中给出有效的 selectedContractId。";
        String qContractSlotMismatch =
                "当前"
                        + config.domainLabel()
                        + "语义与所选能力合同不一致，请核对 semanticSlots 与 selectedContractId。";
        String qWire =
                "当前"
                        + config.domainLabel()
                        + "语义不完整：请在解析 JSON 中给出有效 structuredIntentDetailWire。";

        List<String> warnings = new ArrayList<>();
        boolean allowedContractsPresent = hasAllowedContracts(contractSelection);
        String selectedContractId = SemanticContractCompletionEngine.extractSelectedContractId(rawParse);

        if (allowedContractsPresent && !StringUtils.hasText(selectedContractId)) {
            return SemanticFrameValidationResult.clarify(
                    qMissingContract, List.of("MISSING_SELECTED_CONTRACT_ID"));
        }

        if (StringUtils.hasText(selectedContractId)) {
            String contractId = selectedContractId.trim();
            if (allowedContractsPresent && !allowedContractsContain(contractSelection, contractId)) {
                return SemanticFrameValidationResult.clarify(
                        qMissingContract, List.of("SELECTED_CONTRACT_ID_NOT_IN_ALLOWED_CONTRACTS"));
            }
            SemanticCapabilityContract contract = findActiveContract(config.domainCode(), contractId);
            if (contract == null) {
                return SemanticFrameValidationResult.clarify(
                        qMissingContract, List.of("SELECTED_CONTRACT_ID_NOT_ACTIVE"));
            }
            List<String> slotMismatches = slotMismatchesAgainstContract(frame, rawParse, contract);
            if (!slotMismatches.isEmpty()) {
                return SemanticFrameValidationResult.clarify(
                        qContractSlotMismatch,
                        List.of("SELECTED_CONTRACT_ID_SLOT_MISMATCH:" + String.join(",", slotMismatches)));
            }
            if (debugObserver != null) {
                debugObserver.observe(warnings, frame, rawParse, contract.getWire());
            }
            return warnings.isEmpty()
                    ? SemanticFrameValidationResult.success()
                    : SemanticFrameValidationResult.successWithWarnings(warnings);
        }

        if (allowedContractsPresent) {
            return SemanticFrameValidationResult.clarify(
                    qMissingContract, List.of("MISSING_SELECTED_CONTRACT_ID"));
        }

        String frameWire = frame != null ? frame.getStructuredIntentDetailWire() : null;
        if (!StringUtils.hasText(frameWire)
                || config.legacyCanonicalWireCheck() == null
                || !config.legacyCanonicalWireCheck()
                        .test(AiQuerySemanticLexicon.canonicalStructuredIntentDetailWire(frameWire.trim()))) {
            return SemanticFrameValidationResult.clarify(Q_WIRE_INVALID(config.domainLabel()), List.of("STRUCTURED_WIRE_INVALID"));
        }
        if (debugObserver != null) {
            debugObserver.observe(warnings, frame, rawParse, frameWire);
        }
        return warnings.isEmpty()
                ? SemanticFrameValidationResult.success()
                : SemanticFrameValidationResult.successWithWarnings(warnings);
    }

    /**
     * LLM 已输出且与 ACTIVE contract entry 冲突的槽位名；缺失字段由 {@link
     * com.nongxinle.ai.semantic.contract.SemanticContractCompletionEngine} 补齐，不在此列。
     */
    public static List<String> slotMismatchesAgainstContract(
            CurrentSemanticFrame frame,
            AiQuerySemanticParseResult rawParse,
            SemanticCapabilityContract contract) {
        ArrayList<String> mismatches = new ArrayList<>();
        if (contract == null || frame == null) {
            return mismatches;
        }
        if (contract.getQueryObjects() != null && !contract.getQueryObjects().isEmpty()) {
            String frameToken = normalizeToken(frame.getQueryObject());
            if (StringUtils.hasText(frameToken) && !tokenInSet(frameToken, contract.getQueryObjects())) {
                mismatches.add("queryObject");
            }
        }
        if (contract.getOperations() != null && !contract.getOperations().isEmpty()) {
            String frameToken = normalizeToken(frame.getOperation());
            if (StringUtils.hasText(frameToken) && !tokenInSet(frameToken, contract.getOperations())) {
                mismatches.add("operation");
            }
        }
        if (contract.getMetrics() != null && !contract.getMetrics().isEmpty()) {
            String frameToken = normalizeToken(frame.getMetric());
            if (StringUtils.hasText(frameToken) && !tokenInSet(frameToken, contract.getMetrics())) {
                mismatches.add("metric");
            }
        }
        if (StringUtils.hasText(contract.getSourceFacet())) {
            String contractFacet = normalizeToken(contract.getSourceFacet());
            String frameFacet = normalizeToken(frame.getSourceFacet());
            if (StringUtils.hasText(frameFacet) && !contractFacet.equals(frameFacet)) {
                mismatches.add("sourceFacet");
            }
        }
        if (StringUtils.hasText(contract.getDetailWanted())) {
            String contractDetail = normalizeToken(contract.getDetailWanted());
            String frameDetail = normalizeToken(frame.getDetailWanted());
            if (StringUtils.hasText(frameDetail) && !contractDetail.equals(frameDetail)) {
                mismatches.add("detailWanted");
            }
        }
        if (rawParse != null && rawParse.getSemanticSlots() != null) {
            String answerPlanType = rawParse.getSemanticSlots().getAnswerPlanType();
            if (StringUtils.hasText(contract.getAnswerPlanType())
                    && StringUtils.hasText(answerPlanType)
                    && !contract.getAnswerPlanType().trim().equalsIgnoreCase(answerPlanType.trim())) {
                mismatches.add("answerPlanType");
            }
        }
        return mismatches;
    }

    @FunctionalInterface
    public interface SlotsWireDerivationDebugObserver {
        void observe(
                List<String> warnings,
                CurrentSemanticFrame frame,
                AiQuerySemanticParseResult rawParse,
                String authoritativeWire);
    }

    private static String Q_WIRE_INVALID(String domainLabel) {
        return "当前" + domainLabel + "语义不完整：请在解析 JSON 中给出有效 structuredIntentDetailWire。";
    }

    private static boolean hasAllowedContracts(DomainContractSelectionResult contractSelection) {
        if (contractSelection == null || contractSelection.getParserAllowedOutputContract() == null) {
            return false;
        }
        List<SemanticParserAllowedOutputContract.AllowedContractEntry> entries =
                contractSelection.getParserAllowedOutputContract().getAllowedContracts();
        return entries != null && !entries.isEmpty();
    }

    private static boolean allowedContractsContain(
            DomainContractSelectionResult contractSelection, String contractId) {
        if (contractSelection == null || contractSelection.getParserAllowedOutputContract() == null) {
            return false;
        }
        List<SemanticParserAllowedOutputContract.AllowedContractEntry> entries =
                contractSelection.getParserAllowedOutputContract().getAllowedContracts();
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

    static SemanticCapabilityContract findActiveContract(String domainCode, String contractId) {
        if (!StringUtils.hasText(contractId)) {
            return null;
        }
        for (SemanticCapabilityContract contract :
                SemanticContractCatalog.listActiveCapabilityContracts(domainCode)) {
            if (contract != null
                    && contractId.equals(contract.getContractId())
                    && contract.getStatus() == SemanticCapabilityContractStatus.ACTIVE) {
                return contract;
            }
        }
        return null;
    }

    private static boolean tokenInSet(String token, Set<String> allowed) {
        if (allowed == null || allowed.isEmpty()) {
            return true;
        }
        if (!StringUtils.hasText(token)) {
            return false;
        }
        for (String allowedToken : allowed) {
            if (token.equals(normalizeToken(allowedToken))) {
                return true;
            }
        }
        return false;
    }

    static String normalizeToken(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        return raw.trim().toUpperCase(Locale.ROOT).replace('-', '_');
    }
}
