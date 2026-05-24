package com.nongxinle.ai.semantic.frame;

import com.nongxinle.ai.conversation.AiConversationTurnMemory;
import com.nongxinle.ai.conversation.AiQuerySemanticLexicon;
import com.nongxinle.ai.semantic.AiQuerySemanticParseResult;
import com.nongxinle.ai.semantic.contract.DomainContractSelectionResult;

/**
 * 出库域 contract-entry 主链校验（D-CONTRACT-ENTRY-VALIDATION-P2B）。
 */
public final class StockReduceCurrentSemanticFrameValidator {

    private static final ContractEntrySemanticFrameValidationSupport.DomainContractEntryConfig CONFIG =
            new ContractEntrySemanticFrameValidationSupport.DomainContractEntryConfig(
                    "STOCK_REDUCE",
                    "出库",
                    AiQuerySemanticLexicon::isStructuredStockReduceDetail);

    private StockReduceCurrentSemanticFrameValidator() {}

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
