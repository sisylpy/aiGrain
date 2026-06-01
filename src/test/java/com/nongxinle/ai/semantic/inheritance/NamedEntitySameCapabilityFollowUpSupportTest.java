package com.nongxinle.ai.semantic.inheritance;

import com.nongxinle.ai.conversation.AiConversationTurnMemory;
import com.nongxinle.ai.conversation.AiQuerySemanticLexicon;
import com.nongxinle.ai.conversation.AiSemanticWireConstants;
import com.nongxinle.ai.semantic.AiQuerySemanticParseResult;
import com.nongxinle.ai.semantic.AiQuerySemanticSlotMerge;
import com.nongxinle.ai.semantic.intake.SemanticIntakeResult;
import com.nongxinle.ai.semantic.intake.SemanticIntakeStatus;
import com.nongxinle.ai.semantic.matrix.DishCostAnalysisSemanticCapabilityMatrix;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NamedEntitySameCapabilityFollowUpSupportTest {

    @Test
    void ingredientCoverDaysFollowUp_matchesIntakeAndPreviousWire() {
        AiConversationTurnMemory previous = yogurtBowlCoverDaysPreviousTurn();
        AiQuerySemanticParseResult current = pepperChickenWeakCostParse();
        SemanticIntakeResult intake =
                SemanticIntakeResult.builder()
                        .status(SemanticIntakeStatus.READY)
                        .reason("named_dish_ingredient_cover_days_inherited")
                        .canonicalUserQuery("椒麻鸡还能卖几天")
                        .build();

        assertThat(
                        NamedEntitySameCapabilityFollowUpSupport.isNamedEntitySameCapabilityFollowUp(
                                current,
                                previous,
                                intake,
                                true))
                .isTrue();
        assertThat(
                        NamedEntitySameCapabilityFollowUpSupport.resolvePreviousStableContractId(
                                previous))
                .isEqualTo(DishCostAnalysisSemanticCapabilityMatrix.CONTRACT_DISH_INGREDIENT_COVER_DAYS);
        assertThat(
                        NamedEntitySameCapabilityFollowUpSupport.contractIdDeclaredByIntake(intake))
                .isEqualTo(DishCostAnalysisSemanticCapabilityMatrix.CONTRACT_DISH_INGREDIENT_COVER_DAYS);
    }

    @Test
    void rankingToSingleDish_explicitEntityWithoutMatchingIntake_isFalse() {
        AiConversationTurnMemory previous =
                AiConversationTurnMemory.builder()
                        .lastStructuredIntentDetail(
                                AiSemanticWireConstants.STRUCTURED_DISH_SALES_COUNT_RANKING_HIGH)
                        .lastSemanticSlots(
                                AiQuerySemanticParseResult.SemanticSlotsPart.builder()
                                        .selectedContractId("dish_sales.count_ranking_high")
                                        .structuredIntentDetailWire(
                                                AiSemanticWireConstants
                                                        .STRUCTURED_DISH_SALES_COUNT_RANKING_HIGH)
                                        .build())
                        .build();
        AiQuerySemanticParseResult current =
                AiQuerySemanticParseResult.builder()
                        .mentionedDishName("核桃芽菜西芹")
                        .semanticSlots(
                                AiQuerySemanticParseResult.SemanticSlotsPart.builder()
                                        .selectedContractId("dish_sales.single_dish")
                                        .mentionedDishName("核桃芽菜西芹")
                                        .anchorPolicy(AiQuerySemanticSlotMerge.ANCHOR_IGNORE_PREVIOUS)
                                        .build())
                        .build();

        assertThat(
                        NamedEntitySameCapabilityFollowUpSupport.isNamedEntitySameCapabilityFollowUp(
                                current, previous, null, true))
                .isFalse();
    }

    @Test
    void intakeDeclaresCoverButPreviousWasCost_isFalse() {
        AiConversationTurnMemory previous =
                AiConversationTurnMemory.builder()
                        .lastStructuredIntentDetail(
                                AiQuerySemanticLexicon.STRUCTURED_DISH_COST_ANALYSIS)
                        .lastSemanticSlots(
                                AiQuerySemanticParseResult.SemanticSlotsPart.builder()
                                        .selectedContractId(
                                                DishCostAnalysisSemanticCapabilityMatrix
                                                        .CONTRACT_DISH_COST_SINGLE)
                                        .structuredIntentDetailWire(
                                                AiQuerySemanticLexicon.STRUCTURED_DISH_COST_ANALYSIS)
                                        .build())
                        .build();
        SemanticIntakeResult intake =
                SemanticIntakeResult.builder()
                        .status(SemanticIntakeStatus.READY)
                        .reason("named_dish_ingredient_cover_days_inherited")
                        .build();

        assertThat(
                        NamedEntitySameCapabilityFollowUpSupport.isNamedEntitySameCapabilityFollowUp(
                                currentWithDish("椒麻鸡"), previous, intake, true))
                .isFalse();
    }

    private static AiConversationTurnMemory yogurtBowlCoverDaysPreviousTurn() {
        return AiConversationTurnMemory.builder()
                .lastPathCode("DISH_COST")
                .lastStructuredIntentDetail(
                        AiSemanticWireConstants.STRUCTURED_DISH_INGREDIENT_COVER_DAYS)
                .lastMentionedDishName("酸奶碗")
                .lastSemanticSlots(
                        AiQuerySemanticParseResult.SemanticSlotsPart.builder()
                                .selectedContractId(
                                        DishCostAnalysisSemanticCapabilityMatrix
                                                .CONTRACT_DISH_INGREDIENT_COVER_DAYS)
                                .structuredIntentDetailWire(
                                        AiSemanticWireConstants.STRUCTURED_DISH_INGREDIENT_COVER_DAYS)
                                .answerPlanType("DISH_INGREDIENT_COVER_DAYS")
                                .mentionedDishName("酸奶碗")
                                .build())
                .build();
    }

    private static AiQuerySemanticParseResult pepperChickenWeakCostParse() {
        return AiQuerySemanticParseResult.builder()
                .parseMissing(false)
                .intentAction("OVERRIDE")
                .mentionedDishName("椒麻鸡")
                .semanticSlots(
                        AiQuerySemanticParseResult.SemanticSlotsPart.builder()
                                .selectedContractId(
                                        DishCostAnalysisSemanticCapabilityMatrix.CONTRACT_DISH_COST_SINGLE)
                                .mentionedDishName("椒麻鸡")
                                .anchorPolicy(AiQuerySemanticSlotMerge.ANCHOR_IGNORE_PREVIOUS)
                                .structuredIntentDetailWire(
                                        AiQuerySemanticLexicon.STRUCTURED_DISH_COST_ANALYSIS)
                                .build())
                .build();
    }

    private static AiQuerySemanticParseResult currentWithDish(String dish) {
        return AiQuerySemanticParseResult.builder()
                .mentionedDishName(dish)
                .semanticSlots(
                        AiQuerySemanticParseResult.SemanticSlotsPart.builder()
                                .mentionedDishName(dish)
                                .anchorPolicy(AiQuerySemanticSlotMerge.ANCHOR_IGNORE_PREVIOUS)
                                .build())
                .build();
    }
}
