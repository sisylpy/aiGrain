package com.nongxinle.ai.semantic.inheritance;

import com.nongxinle.ai.conversation.AiConversationTurnMemory;
import com.nongxinle.ai.semantic.AiQuerySemanticParseResult;
import com.nongxinle.ai.semantic.AiQuerySemanticSlotMerge;
import com.nongxinle.ai.semantic.dimension.BareRankingDimensionSwitchPlan;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SemanticSlotInheritancePolicyTest {

    @Test
    void dishSalesRankingTimeFollowUp_classifiesSameFamilyNotBareDimensionSwitch() {
        AiConversationTurnMemory prev = dishSalesRankingPreviousTurn();
        AiQuerySemanticParseResult current =
                timeFollowUpParse(
                        "dish_sales.store_count_ranking",
                        "DISH",
                        "RANKING",
                        "SOLD_PORTIONS",
                        "dish_sales_store_count_ranking",
                        "DISH_SALES_STORE_COUNT_RANKING",
                        "LAST_MONTH",
                        null,
                        null);

        SemanticSlotInheritanceDecision decision =
                SemanticSlotInheritancePolicy.decide(
                        SemanticSlotInheritanceRequest.builder()
                                .currentParse(current)
                                .previousTurn(prev)
                                .bareRankingDimensionSwitchPlan(
                                        BareRankingDimensionSwitchPlan.builder().active(false).build())
                                .build());

        assertThat(decision.getMode())
                .isEqualTo(SemanticSlotInheritanceMode.INHERIT_SAME_FAMILY_TIME_FOLLOWUP);
        assertThat(decision.getReasonCode())
                .isEqualTo(SemanticSlotInheritancePolicy.REASON_SAME_FAMILY_TIME_ONLY_FOLLOWUP);
        assertThat(decision.getTrace().get("currentContractId"))
                .isEqualTo("dish_sales.store_count_ranking");
        assertThat(decision.getTrace().get("previousContractId"))
                .isEqualTo("dish_sales.count_ranking_high");
    }

    @Test
    void dishSalesRankingTimeFollowUp_restoresRankingWhenLlmMisselectedSingleDish() {
        AiConversationTurnMemory prev = dishSalesRankingPreviousTurn();
        AiQuerySemanticParseResult current =
                timeFollowUpParse(
                        "dish_sales.store_single_dish",
                        "DISH",
                        "DETAIL",
                        "SOLD_PORTIONS",
                        "dish_sales_store_single_dish",
                        "DISH_SALES_SINGLE_DISH",
                        "LAST_MONTH",
                        "核桃芽菜西芹",
                        "USE_PREVIOUS_ANCHOR");

        AiQuerySemanticParseResult merged = apply(current, prev);

        assertThat(merged.getSemanticSlots().getSelectedContractId())
                .isEqualTo("dish_sales.count_ranking_high");
        assertThat(merged.getSemanticSlots().getOperation()).isEqualTo("RANKING");
        assertThat(merged.getSemanticSlots().getStructuredIntentDetailWire())
                .isEqualTo("dish_sales_count_ranking_high");
        assertThat(merged.getTime().getTimeType()).isEqualTo("LAST_MONTH");
        assertThat(merged.getMentionedDishName()).isNull();
        assertThat(merged.getSemanticSlots().getMentionedDishName()).isNull();
    }

    @Test
    void dishSalesToBusinessOverview_preservesBusinessOverviewContract() {
        AiConversationTurnMemory prev = dishSalesRankingPreviousTurn();
        AiQuerySemanticParseResult current =
                timeFollowUpParse(
                        "business_overview.summary",
                        "BUSINESS",
                        "SUMMARY",
                        "BUSINESS_STATUS",
                        "business_overview_summary",
                        "BUSINESS_OVERVIEW_MULTI_AGENT_V1",
                        "YESTERDAY",
                        null,
                        null);

        SemanticSlotInheritanceDecision decision =
                SemanticSlotInheritancePolicy.decide(
                        SemanticSlotInheritanceRequest.builder()
                                .currentParse(current)
                                .previousTurn(prev)
                                .build());
        AiQuerySemanticParseResult merged = apply(current, prev);

        assertThat(decision.getMode()).isIn(
                SemanticSlotInheritanceMode.INHERIT_NONE,
                SemanticSlotInheritanceMode.INHERIT_CONTEXT_ONLY);
        assertThat(decision.isCurrentHasSovereignActiveContract()).isTrue();
        assertThat(merged.getSemanticSlots().getSelectedContractId())
                .isEqualTo("business_overview.summary");
        assertThat(merged.getSemanticSlots().getQueryObject()).isEqualTo("BUSINESS");
        assertThat(merged.getSemanticSlots().getOperation()).isEqualTo("SUMMARY");
        assertThat(merged.getSemanticSlots().getStructuredIntentDetailWire())
                .isEqualTo("business_overview_summary");
    }

    @Test
    void purchaseListTimeFollowUp_restoresPeriodGoodsListWhenLlmMisselectedOverview() {
        AiConversationTurnMemory prev = purchasePeriodGoodsListPreviousTurn();
        AiQuerySemanticParseResult current =
                timeFollowUpParse(
                        "purchase.overview_summary",
                        "PURCHASE",
                        "OVERVIEW",
                        null,
                        "purchase_overview_summary",
                        "PURCHASE_OVERVIEW",
                        "LAST_MONTH",
                        null,
                        null);

        AiQuerySemanticParseResult merged = apply(current, prev);

        assertThat(merged.getSemanticSlots().getSelectedContractId())
                .isEqualTo("purchase.period_goods_list");
        assertThat(merged.getSemanticSlots().getStructuredIntentDetailWire())
                .isEqualTo("purchase_period_goods_list");
        assertThat(merged.getSemanticSlots().getAnswerPlanType())
                .isEqualTo("PURCHASE_PERIOD_GOODS_DETAIL");
        assertThat(merged.getTime().getTimeType()).isEqualTo("LAST_MONTH");
    }

    @Test
    void purchaseToBusinessOverview_preservesBusinessOverviewContract() {
        AiConversationTurnMemory prev = purchasePeriodGoodsListPreviousTurn();
        AiQuerySemanticParseResult current =
                timeFollowUpParse(
                        "business_overview.summary",
                        "BUSINESS",
                        "SUMMARY",
                        "BUSINESS_STATUS",
                        "business_overview_summary",
                        "BUSINESS_OVERVIEW_MULTI_AGENT_V1",
                        "YESTERDAY",
                        null,
                        null);

        AiQuerySemanticParseResult merged = apply(current, prev);

        assertThat(merged.getSemanticSlots().getSelectedContractId())
                .isEqualTo("business_overview.summary");
        assertThat(merged.getSemanticSlots().getStructuredIntentDetailWire())
                .isEqualTo("business_overview_summary");
    }

    @Test
    void explicitEntityFollowUp_keepsSingleDishPathWithoutRankingRestore() {
        AiConversationTurnMemory prev = dishSalesRankingPreviousTurn();
        AiQuerySemanticParseResult current =
                AiQuerySemanticParseResult.builder()
                        .parseMissing(false)
                        .timeAction("NEW")
                        .mentionedDishName("核桃芽菜西芹")
                        .time(
                                AiQuerySemanticParseResult.TimePart.builder()
                                        .timeSource("CURRENT_MESSAGE_EXPLICIT")
                                        .timeType("LAST_MONTH")
                                        .build())
                        .semanticSlots(
                                AiQuerySemanticParseResult.SemanticSlotsPart.builder()
                                        .selectedContractId("dish_sales.single_dish")
                                        .queryObject("DISH")
                                        .operation("DETAIL")
                                        .metric("SOLD_PORTIONS")
                                        .anchorPolicy(AiQuerySemanticSlotMerge.ANCHOR_IGNORE_PREVIOUS)
                                        .mentionedDishName("核桃芽菜西芹")
                                        .structuredIntentDetailWire("dish_sales_single_dish")
                                        .answerPlanType("DISH_SALES_SINGLE_DISH")
                                        .build())
                        .build();

        SemanticSlotInheritanceDecision decision =
                SemanticSlotInheritancePolicy.decide(
                        SemanticSlotInheritanceRequest.builder()
                                .currentParse(current)
                                .previousTurn(prev)
                                .build());
        AiQuerySemanticParseResult merged = apply(current, prev);

        assertThat(decision.getMode()).isEqualTo(SemanticSlotInheritanceMode.INHERIT_NONE);
        assertThat(decision.isExplicitEntityFollowUp()).isTrue();
        assertThat(merged.getSemanticSlots().getSelectedContractId())
                .isEqualTo("dish_sales.single_dish");
        assertThat(merged.getSemanticSlots().getOperation()).isEqualTo("DETAIL");
        assertThat(merged.effectiveMentionedDishName()).isEqualTo("核桃芽菜西芹");
    }

    private static AiQuerySemanticParseResult apply(
            AiQuerySemanticParseResult current, AiConversationTurnMemory previous) {
        SemanticSlotInheritanceDecision decision =
                SemanticSlotInheritancePolicy.decide(
                        SemanticSlotInheritanceRequest.builder()
                                .currentParse(current)
                                .previousTurn(previous)
                                .build());
        return SemanticSlotInheritanceApplier.apply(current, previous, decision);
    }

    private static AiConversationTurnMemory dishSalesRankingPreviousTurn() {
        return AiConversationTurnMemory.builder()
                .lastPathCode("DISH_SALES_QUERY")
                .lastStructuredIntentDetail("dish_sales_count_ranking_high")
                .lastSemanticSlots(
                        AiQuerySemanticParseResult.SemanticSlotsPart.builder()
                                .selectedContractId("dish_sales.count_ranking_high")
                                .queryObject("DISH")
                                .operation("RANKING")
                                .metric("SOLD_PORTIONS")
                                .anchorPolicy(AiQuerySemanticSlotMerge.ANCHOR_IGNORE_PREVIOUS)
                                .structuredIntentDetailWire("dish_sales_count_ranking_high")
                                .answerPlanType("DISH_SALES_COUNT_RANKING_HIGH")
                                .build())
                .lastMentionedDishName("核桃芽菜西芹")
                .build();
    }

    private static AiConversationTurnMemory purchasePeriodGoodsListPreviousTurn() {
        return AiConversationTurnMemory.builder()
                .lastPathCode("purchase_overview_path")
                .lastStructuredIntentDetail("purchase_period_goods_list")
                .lastPurchaseSourceType("ALL")
                .lastSemanticSlots(
                        AiQuerySemanticParseResult.SemanticSlotsPart.builder()
                                .selectedContractId("purchase.period_goods_list")
                                .queryObject("GOODS")
                                .operation("DETAIL")
                                .sourceFacet("ALL")
                                .structuredIntentDetailWire("purchase_period_goods_list")
                                .answerPlanType("PURCHASE_PERIOD_GOODS_DETAIL")
                                .build())
                .build();
    }

    private static AiQuerySemanticParseResult timeFollowUpParse(
            String contractId,
            String queryObject,
            String operation,
            String metric,
            String wire,
            String answerPlanType,
            String timeType,
            String mentionedDishName,
            String anchorPolicy) {
        AiQuerySemanticParseResult.AiQuerySemanticParseResultBuilder builder =
                AiQuerySemanticParseResult.builder()
                        .parseMissing(false)
                        .timeAction("NEW")
                        .time(
                                AiQuerySemanticParseResult.TimePart.builder()
                                        .timeSource("CURRENT_MESSAGE_EXPLICIT")
                                        .timeType(timeType)
                                        .build())
                        .semanticSlots(
                                AiQuerySemanticParseResult.SemanticSlotsPart.builder()
                                        .selectedContractId(contractId)
                                        .queryObject(queryObject)
                                        .operation(operation)
                                        .metric(metric)
                                        .structuredIntentDetailWire(wire)
                                        .answerPlanType(answerPlanType)
                                        .anchorPolicy(anchorPolicy)
                                        .mentionedDishName(mentionedDishName)
                                        .build());
        if (mentionedDishName != null) {
            builder.mentionedDishName(mentionedDishName);
        }
        return builder.build();
    }
}
