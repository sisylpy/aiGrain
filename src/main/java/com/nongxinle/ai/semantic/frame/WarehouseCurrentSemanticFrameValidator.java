package com.nongxinle.ai.semantic.frame;

import com.nongxinle.ai.conversation.AiConversationTurnMemory;
import com.nongxinle.ai.conversation.AiQuerySemanticLexicon;
import com.nongxinle.ai.semantic.AiQuerySemanticParseResult;
import com.nongxinle.ai.semantic.contract.DomainContractSelectionResult;
import com.nongxinle.ai.semantic.intake.SemanticIntakeResult;
import com.nongxinle.ai.semantic.intake.WarehouseInventoryShortageSemanticsSupport;

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
        return validate(
                frame,
                rawParse,
                previousTurn,
                normalizedUserMessage,
                followUpRewriteApplied,
                contractSelection,
                null);
    }

    public static SemanticFrameValidationResult validate(
            CurrentSemanticFrame frame,
            AiQuerySemanticParseResult rawParse,
            AiConversationTurnMemory previousTurn,
            String normalizedUserMessage,
            boolean followUpRewriteApplied,
            DomainContractSelectionResult contractSelection,
            SemanticIntakeResult semanticIntake) {
        SemanticFrameValidationResult shortageBlock =
                WarehouseInventoryShortageSemanticsSupport.validateGoodsAmountRankingLowBlocked(
                        rawParse, semanticIntake);
        if (shortageBlock.needSemanticClarification()) {
            return shortageBlock;
        }
        return ContractEntrySemanticFrameValidationSupport.validateSelectedContractEntry(
                frame,
                rawParse,
                contractSelection,
                CONFIG,
                null);
    }
}
