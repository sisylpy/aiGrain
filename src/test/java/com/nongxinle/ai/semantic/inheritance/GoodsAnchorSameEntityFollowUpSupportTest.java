package com.nongxinle.ai.semantic.inheritance;

import com.nongxinle.ai.conversation.AiConversationTurnMemory;
import com.nongxinle.ai.conversation.AiQuerySemanticLexicon;
import com.nongxinle.ai.dto.business.GoodsSupportedDishCoverAnswerPlan;
import com.nongxinle.ai.semantic.AiQuerySemanticParseResult;
import com.nongxinle.ai.semantic.intake.SemanticIntakeGoodsAnchorFollowUpSupport;
import com.nongxinle.ai.semantic.intake.SemanticIntakeResult;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GoodsAnchorSameEntityFollowUpSupportTest {

    @Test
    void detectsGoodsAnchorStockFollowUpWhenIntakeSignalsFollowUp() {
        AiConversationTurnMemory previous =
                AiConversationTurnMemory.builder()
                        .lastSemanticSlots(
                                AiQuerySemanticParseResult.SemanticSlotsPart.builder()
                                        .selectedContractId(GoodsSupportedDishCoverAnswerPlan.CONTRACT_ID)
                                        .structuredIntentDetailWire(
                                                AiQuerySemanticLexicon.STRUCTURED_GOODS_SUPPORTED_DISH_COVER)
                                        .mentionedGoodsName("三黄鸡")
                                        .build())
                        .build();
        AiQuerySemanticParseResult current =
                AiQuerySemanticParseResult.builder()
                        .semanticSlots(
                                AiQuerySemanticParseResult.SemanticSlotsPart.builder()
                                        .selectedContractId("warehouse.overview")
                                        .structuredIntentDetailWire(
                                                AiQuerySemanticLexicon.STRUCTURED_WAREHOUSE_STOCK_OVERVIEW)
                                        .build())
                        .build();
        SemanticIntakeResult intake =
                SemanticIntakeResult.builder()
                        .isFollowUp(true)
                        .usedPreviousContext(true)
                        .reason(SemanticIntakeGoodsAnchorFollowUpSupport.REASON_MARKER)
                        .build();

        assertTrue(
                GoodsAnchorSameEntityFollowUpSupport.isGoodsAnchorSameEntityFollowUp(
                        current, previous, intake));
    }

    @Test
    void rejectsWhenPreviousContractIsNotGoodsSupportedDishCover() {
        AiConversationTurnMemory previous =
                AiConversationTurnMemory.builder()
                        .lastSemanticSlots(
                                AiQuerySemanticParseResult.SemanticSlotsPart.builder()
                                        .selectedContractId("warehouse.overview")
                                        .build())
                        .build();
        SemanticIntakeResult intake =
                SemanticIntakeResult.builder()
                        .isFollowUp(true)
                        .usedPreviousContext(true)
                        .reason(SemanticIntakeGoodsAnchorFollowUpSupport.REASON_MARKER)
                        .build();

        assertFalse(
                GoodsAnchorSameEntityFollowUpSupport.isGoodsAnchorSameEntityFollowUp(
                        AiQuerySemanticParseResult.builder().build(), previous, intake));
    }

    @Test
    void previousGoodsNameFromSemanticSlots() {
        AiConversationTurnMemory previous =
                AiConversationTurnMemory.builder()
                        .lastSemanticSlots(
                                AiQuerySemanticParseResult.SemanticSlotsPart.builder()
                                        .mentionedGoodsName("三黄鸡")
                                        .build())
                        .build();
        assertEquals("三黄鸡", GoodsAnchorSameEntityFollowUpSupport.previousGoodsName(previous));
    }
}
