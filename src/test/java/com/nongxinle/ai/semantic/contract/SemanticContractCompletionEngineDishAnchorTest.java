package com.nongxinle.ai.semantic.contract;

import com.nongxinle.ai.conversation.AiConversationTurnMemory;
import com.nongxinle.ai.semantic.AiQuerySemanticParseResult;
import com.nongxinle.ai.semantic.AiQuerySemanticSlotMerge;
import com.nongxinle.ai.semantic.SemanticParserAllowedOutputContract;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SemanticContractCompletionEngineDishAnchorTest {

    @Test
    void complete_topLevelDishWinsOverStaleSlotsMentionedDishName() {
        AiQuerySemanticParseResult raw =
                AiQuerySemanticParseResult.builder()
                        .parseMissing(false)
                        .mentionedDishName("烩菜")
                        .semanticSlots(
                                AiQuerySemanticParseResult.SemanticSlotsPart.builder()
                                        .selectedContractId("dish_sales.single_dish")
                                        .queryObject("DISH")
                                        .operation("DETAIL")
                                        .metric("SOLD_PORTIONS")
                                        .mentionedDishName("椒麻鸡")
                                        .build())
                        .build();

        DomainContractSelectionResult selection =
                DomainContractSelectionResult.builder()
                        .selectedDomain("DISH_SALES")
                        .parserAllowedOutputContract(
                                SemanticParserAllowedOutputContract.builder()
                                        .selectedDomain("DISH_SALES")
                                        .allowedContracts(
                                                List.of(
                                                        SemanticParserAllowedOutputContract.AllowedContractEntry
                                                                .builder()
                                                                .contractId("dish_sales.single_dish")
                                                                .build()))
                                        .build())
                        .build();

        SemanticContractCompletionEngine.Result result =
                SemanticContractCompletionEngine.complete(
                        SemanticContractCompletionEngine.Request.builder()
                                .rawParse(raw)
                                .selectedDomain("DISH_SALES")
                                .contractSelection(selection)
                                .build());

        assertThat(result.isViolation()).isFalse();
        assertThat(result.getCompletedParse().getMentionedDishName()).isEqualTo("烩菜");
        assertThat(result.getCompletedParse().getSemanticSlots().getMentionedDishName())
                .isEqualTo("烩菜");
    }

    @Test
    void complete_usePreviousAnchor_inheritsStructuredDishFromPreviousTurn() {
        AiQuerySemanticParseResult raw =
                AiQuerySemanticParseResult.builder()
                        .parseMissing(false)
                        .semanticSlots(
                                AiQuerySemanticParseResult.SemanticSlotsPart.builder()
                                        .selectedContractId("dish_sales.store_single_dish")
                                        .queryObject("DISH")
                                        .operation("DETAIL")
                                        .metric("SOLD_PORTIONS")
                                        .anchorPolicy(AiQuerySemanticSlotMerge.ANCHOR_USE_PREVIOUS)
                                        .build())
                        .build();

        AiConversationTurnMemory previousTurn =
                AiConversationTurnMemory.builder().lastMentionedDishName("烩菜").build();

        DomainContractSelectionResult selection =
                DomainContractSelectionResult.builder()
                        .selectedDomain("DISH_SALES")
                        .parserAllowedOutputContract(
                                SemanticParserAllowedOutputContract.builder()
                                        .selectedDomain("DISH_SALES")
                                        .allowedContracts(
                                                List.of(
                                                        SemanticParserAllowedOutputContract.AllowedContractEntry
                                                                .builder()
                                                                .contractId("dish_sales.store_single_dish")
                                                                .build()))
                                        .build())
                        .build();

        SemanticContractCompletionEngine.Result result =
                SemanticContractCompletionEngine.complete(
                        SemanticContractCompletionEngine.Request.builder()
                                .rawParse(raw)
                                .selectedDomain("DISH_SALES")
                                .contractSelection(selection)
                                .previousTurn(previousTurn)
                                .build());

        assertThat(result.isViolation()).isFalse();
        assertThat(result.getCompletedParse().getMentionedDishName()).isEqualTo("烩菜");
        assertThat(result.getCompletedParse().getSemanticSlots().getMentionedDishName())
                .isEqualTo("烩菜");
    }
}
