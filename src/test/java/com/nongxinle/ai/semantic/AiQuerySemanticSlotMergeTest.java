package com.nongxinle.ai.semantic;

import com.nongxinle.ai.conversation.AiConversationTurnMemory;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AiQuerySemanticSlotMergeTest {

    @Test
    void reconcileExplicitCurrentTurnDishAnchor_topLevelOverridesStaleSlots() {
        AiQuerySemanticParseResult sem =
                AiQuerySemanticParseResult.builder()
                        .mentionedDishName("烩菜")
                        .semanticSlots(
                                AiQuerySemanticParseResult.SemanticSlotsPart.builder()
                                        .selectedContractId("dish_sales.single_dish")
                                        .anchorPolicy("USE_PREVIOUS_ANCHOR")
                                        .mentionedDishName("椒麻鸡")
                                        .build())
                        .build();
        AiConversationTurnMemory prev =
                AiConversationTurnMemory.builder().lastMentionedDishName("椒麻鸡").build();

        AiQuerySemanticParseResult merged =
                AiQuerySemanticSlotMerge.reconcileExplicitCurrentTurnDishAnchor(sem, prev);

        assertThat(merged.getMentionedDishName()).isEqualTo("烩菜");
        assertThat(merged.getSemanticSlots().getMentionedDishName()).isEqualTo("烩菜");
        assertThat(merged.getSemanticSlots().getAnchorPolicy())
                .isEqualTo(AiQuerySemanticSlotMerge.ANCHOR_IGNORE_PREVIOUS);
    }

    @Test
    void reconcileExplicitCurrentTurnDishAnchor_slotsNewDishOverridesPreviousTurn() {
        AiQuerySemanticParseResult sem =
                AiQuerySemanticParseResult.builder()
                        .semanticSlots(
                                AiQuerySemanticParseResult.SemanticSlotsPart.builder()
                                        .selectedContractId("dish_sales.single_dish")
                                        .anchorPolicy("USE_PREVIOUS_ANCHOR")
                                        .mentionedDishName("烩菜")
                                        .build())
                        .build();
        AiConversationTurnMemory prev =
                AiConversationTurnMemory.builder().lastMentionedDishName("椒麻鸡").build();

        AiQuerySemanticParseResult merged =
                AiQuerySemanticSlotMerge.reconcileExplicitCurrentTurnDishAnchor(sem, prev);

        assertThat(merged.effectiveMentionedDishName()).isEqualTo("烩菜");
        assertThat(merged.getSemanticSlots().getAnchorPolicy())
                .isEqualTo(AiQuerySemanticSlotMerge.ANCHOR_IGNORE_PREVIOUS);
    }

    @Test
    void reconcileExplicitCurrentTurnDishAnchor_inheritsPreviousWhenCurrentTurnHasNoDish() {
        AiQuerySemanticParseResult sem =
                AiQuerySemanticParseResult.builder()
                        .semanticSlots(
                                AiQuerySemanticParseResult.SemanticSlotsPart.builder()
                                        .selectedContractId("dish_sales.single_dish")
                                        .anchorPolicy("USE_PREVIOUS_ANCHOR")
                                        .build())
                        .build();
        AiConversationTurnMemory prev =
                AiConversationTurnMemory.builder().lastMentionedDishName("椒麻鸡").build();

        AiQuerySemanticParseResult merged =
                AiQuerySemanticSlotMerge.reconcileExplicitCurrentTurnDishAnchor(sem, prev);

        assertThat(merged.getMentionedDishName()).isEqualTo("椒麻鸡");
        assertThat(merged.getSemanticSlots().getMentionedDishName()).isEqualTo("椒麻鸡");
        assertThat(merged.getSemanticSlots().getAnchorPolicy())
                .isEqualTo(AiQuerySemanticSlotMerge.ANCHOR_USE_PREVIOUS);
    }
}
