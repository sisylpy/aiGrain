package com.nongxinle.ai.semantic.frame;

import com.nongxinle.ai.conversation.AiConversationTurnMemory;
import com.nongxinle.ai.conversation.AiQuerySemanticLexicon;
import com.nongxinle.ai.semantic.AiQuerySemanticParseResult;
import com.nongxinle.ai.semantic.contract.DomainContractSelectionResult;

/**
 * 营业额域 contract-entry 主链校验（D-CONTRACT-ENTRY-VALIDATION-P2B）。
 */
public final class RevenueCurrentSemanticFrameValidator {

    private static final ContractEntrySemanticFrameValidationSupport.DomainContractEntryConfig CONFIG =
            new ContractEntrySemanticFrameValidationSupport.DomainContractEntryConfig(
                    "REVENUE",
                    "营业额",
                    AiQuerySemanticLexicon::isStructuredRevenueDetail);

    private RevenueCurrentSemanticFrameValidator() {}

    public static SemanticFrameValidationResult validate(
            CurrentSemanticFrame frame,
            AiQuerySemanticParseResult rawParse,
            AiConversationTurnMemory previousTurn,
            String normalizedUserMessage,
            boolean followUpRewriteApplied,
            DomainContractSelectionResult contractSelection) {
        return ContractEntrySemanticFrameValidationSupport.validateSelectedContractEntry(
                frame,
                rawParse,
                contractSelection,
                CONFIG,
                (warnings, f, rp, authWire) -> {
                    // Removed: debug-only Matrix inferMatrixWireFromSemanticSlots derivation.
                    // Contract-locked wire is authoritative; no legacy slots→wire inference needed.
                });
    }
}
