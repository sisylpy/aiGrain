package com.nongxinle.ai.semantic.frame;

import com.nongxinle.ai.context.AiResolvedOrgScope;
import com.nongxinle.ai.conversation.AiConversationTurnMemory;
import com.nongxinle.ai.conversation.AiQuerySemanticLexicon;
import com.nongxinle.ai.semantic.AiQuerySemanticParseResult;
import com.nongxinle.ai.semantic.AiQuerySemanticSlotMerge;
import com.nongxinle.ai.semantic.SemanticParserAllowedOutputContract;
import com.nongxinle.ai.semantic.contract.DomainContractSelectionResult;
import com.nongxinle.ai.semantic.contract.SemanticCapabilityContract;
import com.nongxinle.ai.semantic.contract.SemanticCapabilityContractStatus;
import com.nongxinle.ai.semantic.contract.SemanticContractCatalog;
import com.nongxinle.ai.semantic.contract.SemanticContractCompletionEngine;
import com.nongxinle.ai.graph.business.PurchaseCapabilityBoundarySupport;
import com.nongxinle.ai.semantic.matrix.PurchaseSemanticCapabilityMatrix;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 采购域 {@link CurrentSemanticFrame} 完备性与自洽性校验器。
 * <p>
 * 仅服务 <b>PURCHASE</b> 域；不得向本类加入其它业务域规则。
 * 其它业务域应新增对应 Domain Frame Validator 或通过 Registry 分发。
 * </p>
 * <p>D-CONTRACT-ENTRY-VALIDATION-P1：主链以 {@code selectedContractId} + contract entry + semanticSlots
 * 一致性为准；禁止 {@link PurchaseSemanticCapabilityMatrix#resolveStructuredIntentDetailWire} 影响放行/澄清。</p>
 */
public final class PurchaseCurrentSemanticFrameValidator {

    private static final String DOMAIN_PURCHASE = "PURCHASE";

    private static final Set<String> PURCHASE_CANONICAL_WIRES =
            Set.of(
                    AiQuerySemanticLexicon.STRUCTURED_PURCHASE_OVERVIEW_SUMMARY,
                    AiQuerySemanticLexicon.STRUCTURED_PURCHASE_SOURCE_SUMMARY,
                    AiQuerySemanticLexicon.STRUCTURED_PURCHASE_SOURCE_AMOUNT_QUERY,
                    AiQuerySemanticLexicon.STRUCTURED_PURCHASE_SOURCE_GOODS_QUERY,
                    AiQuerySemanticLexicon.STRUCTURED_PURCHASE_GOODS_AMOUNT_RANKING,
                    AiQuerySemanticLexicon.STRUCTURED_PURCHASE_GOODS_COUNT_RANKING,
                    AiQuerySemanticLexicon.STRUCTURED_PURCHASE_GOODS_QUANTITY_RANKING,
                    AiQuerySemanticLexicon.STRUCTURED_PURCHASE_GOODS_ANOMALY,
                    AiQuerySemanticLexicon.STRUCTURED_PURCHASE_PRICE_ANOMALY,
                    AiQuerySemanticLexicon.STRUCTURED_PURCHASE_FREQUENCY_ANOMALY,
                    AiQuerySemanticLexicon.STRUCTURED_PURCHASE_QUANTITY_ANOMALY,
                    AiQuerySemanticLexicon.STRUCTURED_PURCHASE_GOODS_AMOUNT_SPIKE,
                    AiQuerySemanticLexicon.STRUCTURED_PURCHASE_STOCK_REDUCE_MISMATCH,
                    AiQuerySemanticLexicon.STRUCTURED_PURCHASE_SLOW_MOVING_RISK,
                    AiQuerySemanticLexicon.STRUCTURED_PURCHASE_INVENTORY_OVERSTOCK_RISK,
                    AiQuerySemanticLexicon.STRUCTURED_PURCHASE_FRESHNESS_RISK,
                    AiQuerySemanticLexicon.STRUCTURED_PURCHASE_STORE_AMOUNT_RANKING,
                    AiQuerySemanticLexicon.STRUCTURED_PURCHASE_STORE_COMPARE,
                    AiQuerySemanticLexicon.STRUCTURED_PURCHASE_STORE_PAIR_AMOUNT_COMPARE,
                    AiQuerySemanticLexicon.STRUCTURED_SUPPLIER_AMOUNT_RANKING);

    private static final String Q_SLOT_MISSING =
            "当前采购语义不完整：请在解析 JSON 中补齐 queryObject、operation、metric、sourceFacet。";
    private static final String Q_MISSING_CONTRACT =
            "当前采购问题无法匹配已支持的能力合同，请在解析 JSON 中给出有效的 selectedContractId。";
    private static final String Q_CONTRACT_SLOT_MISMATCH =
            "当前采购语义与所选能力合同不一致，请核对 semanticSlots 与 selectedContractId。";
    private static final String Q_WIRE =
            "当前采购语义不完整：请在解析 JSON 中给出有效 structuredIntentDetailWire（合法采购子口径 wire）。";
    private static final String Q_BREAKDOWN_DETAIL_OP_MISMATCH =
            "detailWanted=SOURCE_BREAKDOWN 时 operation 必须为 BREAKDOWN；请勿落成排行或渠道总览。";
    private static final String Q_BREAKDOWN_GOODS_OBJECT =
            "operation=BREAKDOWN 且按来源拆桶时 queryObject 必须为 GOODS。";
    private static final String Q_BREAKDOWN_METRIC =
            "operation=BREAKDOWN 时 metric 须为 PURCHASE_AMOUNT、PURCHASE_QUANTITY 或 PURCHASE_COUNT（采购金额或采购数量）。";
    private static final String Q_BREAKDOWN_DETAIL_SLOT =
            "operation=BREAKDOWN 时 detailWanted 须与矩阵行一致（SOURCE_BREAKDOWN / SUPPLIER_BREAKDOWN / SUPPLIER_UNIT_PRICE）。";

    private PurchaseCurrentSemanticFrameValidator() {}

    public static SemanticFrameValidationResult validate(
            CurrentSemanticFrame frame,
            AiQuerySemanticParseResult rawParse,
            AiConversationTurnMemory previousTurn,
            String normalizedUserMessage) {
        return validate(frame, rawParse, previousTurn, normalizedUserMessage, false, null);
    }

    public static SemanticFrameValidationResult validate(
            CurrentSemanticFrame frame,
            AiQuerySemanticParseResult rawParse,
            AiConversationTurnMemory previousTurn,
            String normalizedUserMessage,
            boolean followUpRewriteApplied) {
        return validate(frame, rawParse, previousTurn, normalizedUserMessage, followUpRewriteApplied, null);
    }

    public static SemanticFrameValidationResult validate(
            CurrentSemanticFrame frame,
            AiQuerySemanticParseResult rawParse,
            AiConversationTurnMemory previousTurn,
            String normalizedUserMessage,
            boolean followUpRewriteApplied,
            DomainContractSelectionResult contractSelection) {
        return validate(
                frame, rawParse, previousTurn, normalizedUserMessage, followUpRewriteApplied, contractSelection, null);
    }

    public static SemanticFrameValidationResult validate(
            CurrentSemanticFrame frame,
            AiQuerySemanticParseResult rawParse,
            AiConversationTurnMemory previousTurn,
            String normalizedUserMessage,
            boolean followUpRewriteApplied,
            DomainContractSelectionResult contractSelection,
            AiResolvedOrgScope orgScope) {
        if (frame == null) {
            return SemanticFrameValidationResult.clarify(Q_SLOT_MISSING, List.of("FRAME_NULL"));
        }
        boolean contractLocked =
                rawParse != null && SemanticContractCompletionEngine.isContractLockedParse(rawParse);
        List<String> warnings = new ArrayList<>();
        if (contractLocked) {
            SemanticFrameValidationResult contractGate =
                    validateSelectedContractAndWire(frame, rawParse, contractSelection, warnings);
            if (contractGate != null) {
                return contractGate;
            }
            SemanticFrameValidationResult capabilityGate =
                    PurchaseCapabilityBoundarySupport.validateCapabilityBoundary(
                            frame, rawParse, contractSelection, orgScope);
            if (capabilityGate != null) {
                return capabilityGate;
            }
            return warnings.isEmpty()
                    ? SemanticFrameValidationResult.success()
                    : SemanticFrameValidationResult.successWithWarnings(warnings);
        }

        List<String> codes = new ArrayList<>();
        if (!StringUtils.hasText(frame.getQueryObject())
                || AiQuerySemanticSlotMerge.UNKNOWN.equals(frame.getQueryObject())) {
            codes.add("QUERY_OBJECT_UNKNOWN");
        }
        if (!StringUtils.hasText(frame.getOperation())
                || AiQuerySemanticSlotMerge.UNKNOWN.equals(frame.getOperation())) {
            codes.add("OPERATION_UNKNOWN");
        }
        if (!StringUtils.hasText(frame.getMetric()) || AiQuerySemanticSlotMerge.UNKNOWN.equals(frame.getMetric())) {
            codes.add("METRIC_UNKNOWN");
        }
        if (!StringUtils.hasText(frame.getSourceFacet())
                || AiQuerySemanticSlotMerge.UNKNOWN.equals(frame.getSourceFacet())) {
            codes.add("SOURCE_FACET_UNKNOWN");
        }
        if (!codes.isEmpty()) {
            return SemanticFrameValidationResult.clarify(Q_SLOT_MISSING, codes);
        }

        List<String> legacyWarnings = new ArrayList<>();
        if (rawParse != null
                && rawParse.getMetric() != null
                && StringUtils.hasText(rawParse.getMetric().getPurchaseSourceType())
                && hasExplicitCanonicalSourceFacet(frame.getSourceFacet())) {
            String pst = canonicalPurchaseSourceToken(rawParse.getMetric().getPurchaseSourceType());
            String sf = canonicalPurchaseSourceToken(frame.getSourceFacet());
            if (pst != null && sf != null && !pst.equals(sf)) {
                legacyWarnings.add("METRIC_PURCHASE_SOURCE_TYPE_VS_SEMANTIC_SOURCE_FACET");
            }
        }

        SemanticFrameValidationResult contractGate =
                validateSelectedContractAndWire(frame, rawParse, contractSelection, legacyWarnings);
        if (contractGate != null) {
            return contractGate;
        }

        SemanticFrameValidationResult capabilityGate =
                PurchaseCapabilityBoundarySupport.validateCapabilityBoundary(
                        frame, rawParse, contractSelection, orgScope);
        if (capabilityGate != null) {
            return capabilityGate;
        }

        if (AiQuerySemanticLexicon.DETAIL_WANTED_SOURCE_BREAKDOWN.equals(frame.getDetailWanted())
                && !PurchaseSemanticCapabilityMatrix.operationAccepted(
                        PurchaseSemanticCapabilityMatrix.SOURCE_BREAKDOWN, frame.getOperation())) {
            return SemanticFrameValidationResult.clarify(
                    Q_BREAKDOWN_DETAIL_OP_MISMATCH, List.of("SOURCE_BREAKDOWN_REQUIRES_BREAKDOWN_OPERATION"));
        }

        if (AiQuerySemanticLexicon.DETAIL_WANTED_SUPPLIER_UNIT_PRICE.equals(frame.getDetailWanted())
                && !PurchaseSemanticCapabilityMatrix.frameMatchesRow(
                        frame, PurchaseSemanticCapabilityMatrix.SUPPLIER_UNIT_PRICE)) {
            return SemanticFrameValidationResult.clarify(
                    Q_BREAKDOWN_METRIC,
                    List.of(PurchaseSemanticCapabilityMatrix.VALIDATION_SUPPLIER_UNIT_PRICE_INCOMPLETE));
        }
        if (AiQuerySemanticLexicon.DETAIL_WANTED_SUPPLIER_BREAKDOWN.equals(frame.getDetailWanted())
                && !PurchaseSemanticCapabilityMatrix.frameMatchesRow(
                        frame, PurchaseSemanticCapabilityMatrix.SUPPLIER_BREAKDOWN)) {
            return SemanticFrameValidationResult.clarify(
                    Q_BREAKDOWN_DETAIL_SLOT, List.of("SUPPLIER_BREAKDOWN_FRAME_INCOMPLETE"));
        }
        if (AiQuerySemanticLexicon.DETAIL_WANTED_SOURCE_BREAKDOWN.equals(frame.getDetailWanted())
                && !PurchaseSemanticCapabilityMatrix.frameMatchesRow(
                        frame, PurchaseSemanticCapabilityMatrix.SOURCE_BREAKDOWN)) {
            return SemanticFrameValidationResult.clarify(
                    Q_BREAKDOWN_DETAIL_SLOT,
                    List.of(PurchaseSemanticCapabilityMatrix.VALIDATION_SOURCE_BREAKDOWN_INCOMPLETE));
        }

        if ("BREAKDOWN".equals(frame.getOperation())) {
            if (!"GOODS".equals(frame.getQueryObject())) {
                return SemanticFrameValidationResult.clarify(
                        Q_BREAKDOWN_GOODS_OBJECT, List.of("BREAKDOWN_REQUIRES_GOODS_QUERY_OBJECT"));
            }
            String detailWanted = frame.getDetailWanted();
            if (!AiQuerySemanticLexicon.DETAIL_WANTED_SOURCE_BREAKDOWN.equals(detailWanted)
                    && !AiQuerySemanticLexicon.DETAIL_WANTED_SUPPLIER_BREAKDOWN.equals(detailWanted)
                    && !AiQuerySemanticLexicon.DETAIL_WANTED_SUPPLIER_UNIT_PRICE.equals(detailWanted)) {
                return SemanticFrameValidationResult.clarify(
                        Q_BREAKDOWN_DETAIL_SLOT, List.of("BREAKDOWN_REQUIRES_DETAIL_SOURCE_OR_SUPPLIER_BREAKDOWN"));
            }
        }

        if (!legacyWarnings.isEmpty()) {
            return SemanticFrameValidationResult.successWithWarnings(legacyWarnings);
        }
        return SemanticFrameValidationResult.success();
    }

    /**
     * selectedContractId / wire 主链门禁：不 derive wire、不改写 contract。
     *
     * @return null 表示通过；非 null 为 clarify 结果
     */
    private static SemanticFrameValidationResult validateSelectedContractAndWire(
            CurrentSemanticFrame frame,
            AiQuerySemanticParseResult rawParse,
            DomainContractSelectionResult contractSelection,
            List<String> warnings) {
        boolean allowedContractsPresent = hasAllowedContracts(contractSelection);
        String selectedContractId = SemanticContractCompletionEngine.extractSelectedContractId(rawParse);

        if (allowedContractsPresent && !StringUtils.hasText(selectedContractId)) {
            return SemanticFrameValidationResult.clarify(
                    Q_MISSING_CONTRACT, List.of("MISSING_SELECTED_CONTRACT_ID"));
        }

        if (StringUtils.hasText(selectedContractId)) {
            String contractId = selectedContractId.trim();
            if (allowedContractsPresent && !allowedContractsContain(contractSelection, contractId)) {
                return SemanticFrameValidationResult.clarify(
                        Q_MISSING_CONTRACT, List.of("SELECTED_CONTRACT_ID_NOT_IN_ALLOWED_CONTRACTS"));
            }
            SemanticCapabilityContract contract = findActivePurchaseContract(contractId);
            if (contract == null) {
                return SemanticFrameValidationResult.clarify(
                        Q_MISSING_CONTRACT, List.of("SELECTED_CONTRACT_ID_NOT_ACTIVE"));
            }
            List<String> slotMismatches =
                    ContractEntrySemanticFrameValidationSupport.slotMismatchesAgainstContract(
                            frame, rawParse, contract);
            if (!slotMismatches.isEmpty()) {
                return SemanticFrameValidationResult.clarify(
                        Q_CONTRACT_SLOT_MISMATCH,
                        List.of("SELECTED_CONTRACT_ID_SLOT_MISMATCH:" + String.join(",", slotMismatches)));
            }
            return null;
        }

        String frameWire = frame.getStructuredIntentDetailWire();
        if (!StringUtils.hasText(frameWire) || !PURCHASE_CANONICAL_WIRES.contains(frameWire)) {
            return SemanticFrameValidationResult.clarify(
                    Q_WIRE, List.of("STRUCTURED_WIRE_INVALID"));
        }
        return null;
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

    private static SemanticCapabilityContract findActivePurchaseContract(String contractId) {
        if (!StringUtils.hasText(contractId)) {
            return null;
        }
        for (SemanticCapabilityContract contract :
                SemanticContractCatalog.listActiveCapabilityContracts(DOMAIN_PURCHASE)) {
            if (contract != null
                    && contractId.equals(contract.getContractId())
                    && contract.getStatus() == SemanticCapabilityContractStatus.ACTIVE) {
                return contract;
            }
        }
        return null;
    }

    private static boolean hasExplicitCanonicalSourceFacet(String sourceFacet) {
        return canonicalPurchaseSourceToken(sourceFacet) != null;
    }

    private static String canonicalPurchaseSourceToken(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        String u = raw.trim().toUpperCase(Locale.ROOT).replace('-', '_');
        if (AiQuerySemanticLexicon.SOURCE_ALL.equals(u) || "ALL".equals(u)) {
            return AiQuerySemanticLexicon.SOURCE_ALL;
        }
        if (AiQuerySemanticLexicon.SOURCE_SELF_PURCHASE.equals(u)) {
            return AiQuerySemanticLexicon.SOURCE_SELF_PURCHASE;
        }
        if (AiQuerySemanticLexicon.SOURCE_SUPPLIER_PURCHASE.equals(u)) {
            return AiQuerySemanticLexicon.SOURCE_SUPPLIER_PURCHASE;
        }
        return null;
    }
}
