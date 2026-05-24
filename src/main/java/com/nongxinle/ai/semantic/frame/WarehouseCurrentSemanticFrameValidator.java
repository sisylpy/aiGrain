package com.nongxinle.ai.semantic.frame;

import com.nongxinle.ai.conversation.AiConversationTurnMemory;
import com.nongxinle.ai.conversation.AiQuerySemanticLexicon;
import com.nongxinle.ai.semantic.AiQuerySemanticParseResult;
import com.nongxinle.ai.semantic.contract.DomainContractSelectionResult;

/**
 * 库房域 contract-entry 主链校验（D-CONTRACT-ENTRY-VALIDATION-P2B）。
 */
public final class WarehouseCurrentSemanticFrameValidator {

    private static final ContractEntrySemanticFrameValidationSupport.DomainContractEntryConfig CONFIG =
            new ContractEntrySemanticFrameValidationSupport.DomainContractEntryConfig(
                    "WAREHOUSE",
                    "库房",
                    wire -> AiQuerySemanticLexicon.isStructuredWarehouseStockDetail(wire));

    private WarehouseCurrentSemanticFrameValidator() {}

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
                null);
    }
}
