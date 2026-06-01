package com.nongxinle.ai.semantic.intake.llm;

import com.nongxinle.ai.semantic.intake.SemanticIntakeInput;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LlmSemanticIntakeParserProtocolTest {

    @Test
    void collectDimensionSwitchReasonProtocolErrors_missingToken_flagsReason() {
        LlmSemanticIntakeParsed parsed =
                LlmSemanticIntakeParsed.builder()
                        .reason("dimension_switch_follow_up")
                        .build();
        List<String> errors = new ArrayList<>();
        LlmSemanticIntakeParser.collectDimensionSwitchReasonProtocolErrors(parsed, errors);
        assertThat(errors).hasSize(1);
        assertThat(errors.get(0)).startsWith("reason:");
    }

    @Test
    void collectDimensionSwitchReasonProtocolErrors_withCostToken_ok() {
        LlmSemanticIntakeParsed parsed =
                LlmSemanticIntakeParsed.builder()
                        .reason("dimension_switch_sales_to_cost_ranking")
                        .build();
        List<String> errors = new ArrayList<>();
        LlmSemanticIntakeParser.collectDimensionSwitchReasonProtocolErrors(parsed, errors);
        assertThat(errors).isEmpty();
    }

    @Test
    void collectDimensionSwitchReasonProtocolErrors_nonDimensionSwitchReason_ok() {
        LlmSemanticIntakeParsed parsed =
                LlmSemanticIntakeParsed.builder().reason("dish_sales_quantity_short_phrase").build();
        List<String> errors = new ArrayList<>();
        LlmSemanticIntakeParser.collectDimensionSwitchReasonProtocolErrors(parsed, errors);
        assertThat(errors).isEmpty();
    }

    @Test
    void collectDishSalesBossShortPhraseProtocolErrors_dishSalesWithOverviewAmbiguous_flags() {
        LlmSemanticIntakeParsed parsed =
                LlmSemanticIntakeParsed.builder()
                        .primaryDomain("DISH_SALES")
                        .candidateDomains(List.of("DISH_SALES", "BUSINESS_OVERVIEW"))
                        .routeType("AMBIGUOUS")
                        .needClarification(true)
                        .build();
        List<String> errors = new ArrayList<>();
        LlmSemanticIntakeParser.collectDishSalesBossShortPhraseProtocolErrors(parsed, errors);
        assertThat(errors).isNotEmpty();
        assertThat(errors.get(0)).contains("dish_sales_boss_short_phrase");
    }

    @Test
    void collectDishSalesBossShortPhraseProtocolErrors_explicitDishSalesOnly_ok() {
        LlmSemanticIntakeParsed parsed =
                LlmSemanticIntakeParsed.builder()
                        .primaryDomain("DISH_SALES")
                        .candidateDomains(List.of("DISH_SALES"))
                        .routeType("EXPLICIT")
                        .needClarification(false)
                        .reason("dish_sales_quantity_short_phrase")
                        .build();
        List<String> errors = new ArrayList<>();
        LlmSemanticIntakeParser.collectDishSalesBossShortPhraseProtocolErrors(parsed, errors);
        assertThat(errors).isEmpty();
    }

    @Test
    void collectExplicitMultiDishCostRankingIntakeProtocolErrors_dishCostExplicitRanking_flags() {
        LlmSemanticIntakeParsed parsed =
                LlmSemanticIntakeParsed.builder()
                        .primaryDomain("DISH_COST")
                        .canonicalUserQuery("上个月的菜品成本排行")
                        .reason("cost_follow_up")
                        .build();
        List<String> errors = new ArrayList<>();
        LlmSemanticIntakeParser.collectExplicitMultiDishCostRankingIntakeProtocolErrors(parsed, errors);
        assertThat(errors).isNotEmpty();
        assertThat(errors.get(0)).contains("dish_actual_cost_ranking_high_explicit");
    }

    @Test
    void collectBareRankingDimensionSwitchIntakeProtocolErrors_dishCostRankingCanonical_flags() {
        LlmSemanticIntakeParsed parsed =
                LlmSemanticIntakeParsed.builder()
                        .primaryDomain("DISH_COST")
                        .canonicalUserQuery("汀兰餐厅本月菜品成本排行")
                        .isFollowUp(true)
                        .reason("cost_follow_up")
                        .build();
        List<String> errors = new ArrayList<>();
        LlmSemanticIntakeParser.collectBareRankingDimensionSwitchIntakeProtocolErrors(
                parsed, null, errors);
        assertThat(errors).isNotEmpty();
        assertThat(errors.get(0)).contains("DISH_PROFIT");
    }

    @Test
    void collectBareRankingDimensionSwitchIntakeProtocolErrors_followUpWithoutToken_flags() {
        SemanticIntakeInput input =
                SemanticIntakeInput.builder()
                        .hasPreviousTurn(true)
                        .previousPathCode("dish_sales_query_path")
                        .previousStructuredIntentDetail("dish_sales_count_ranking_high")
                        .build();
        LlmSemanticIntakeParsed parsed =
                LlmSemanticIntakeParsed.builder()
                        .primaryDomain("DISH_COST")
                        .canonicalUserQuery("汀兰餐厅本月成本最高的菜品有哪些")
                        .isFollowUp(true)
                        .reason("cost_follow_up")
                        .build();
        List<String> errors = new ArrayList<>();
        LlmSemanticIntakeParser.collectBareRankingDimensionSwitchIntakeProtocolErrors(
                parsed, input, errors);
        assertThat(errors.stream().anyMatch(e -> e.contains("_to_cost_ranking"))).isTrue();
    }

    @Test
    void collectBareRankingDimensionSwitchIntakeProtocolErrors_multiQuestionAfterRanking_ok() {
        SemanticIntakeInput input =
                SemanticIntakeInput.builder()
                        .hasPreviousTurn(true)
                        .previousPathCode("dish_sales_query_path")
                        .previousStructuredIntentDetail("dish_sales_count_ranking_high")
                        .build();
        LlmSemanticIntakeParsed parsed =
                LlmSemanticIntakeParsed.builder()
                        .questionMode("MULTI_QUESTION")
                        .primaryDomain("MULTI_DOMAIN")
                        .routeType("MULTI_DOMAIN")
                        .canonicalUserQuery("哪些菜卖得好，利润也比较稳定？")
                        .isFollowUp(true)
                        .reason("multi_question_menu_composite")
                        .build();
        List<String> errors = new ArrayList<>();
        LlmSemanticIntakeParser.collectBareRankingDimensionSwitchIntakeProtocolErrors(
                parsed, input, errors);
        assertThat(errors).isEmpty();
    }

    void collectDimensionSwitchReasonProtocolErrors_multiQuestion_skipsDimensionSwitchCheck() {
        LlmSemanticIntakeParsed parsed =
                LlmSemanticIntakeParsed.builder()
                        .questionMode("MULTI_QUESTION")
                        .reason("dimension_switch_follow_up")
                        .build();
        List<String> errors = new ArrayList<>();
        LlmSemanticIntakeParser.collectDimensionSwitchReasonProtocolErrors(parsed, errors);
        assertThat(errors).isEmpty();
    }

    @Test
    void collectBareRankingDimensionSwitchIntakeProtocolErrors_rankingTimeFollowUp_ok() {
        SemanticIntakeInput input =
                SemanticIntakeInput.builder()
                        .hasPreviousTurn(true)
                        .previousPathCode("dish_sales_query_path")
                        .previousStructuredIntentDetail("dish_sales_count_ranking_high")
                        .build();
        LlmSemanticIntakeParsed parsed =
                LlmSemanticIntakeParsed.builder()
                        .primaryDomain("DISH_SALES")
                        .canonicalUserQuery("上个月汀兰餐厅销量高的菜品有哪些")
                        .isFollowUp(true)
                        .reason("dish_sales_ranking_time_follow_up")
                        .build();
        List<String> errors = new ArrayList<>();
        LlmSemanticIntakeParser.collectBareRankingDimensionSwitchIntakeProtocolErrors(
                parsed, input, errors);
        assertThat(errors).isEmpty();
    }

    @Test
    void collectDimensionSwitchReasonProtocolErrors_timeOnlyReason_skipsDimensionSwitchCheck() {
        LlmSemanticIntakeParsed parsed =
                LlmSemanticIntakeParsed.builder()
                        .reason("dish_sales_ranking_time_follow_up")
                        .build();
        List<String> errors = new ArrayList<>();
        LlmSemanticIntakeParser.collectDimensionSwitchReasonProtocolErrors(parsed, errors);
        assertThat(errors).isEmpty();
    }

    void collectBareRankingDimensionSwitchIntakeProtocolErrors_validCostSwitch_ok() {
        SemanticIntakeInput input =
                SemanticIntakeInput.builder()
                        .hasPreviousTurn(true)
                        .previousPathCode("dish_sales_query_path")
                        .previousStructuredIntentDetail("dish_sales_count_ranking_high")
                        .build();
        LlmSemanticIntakeParsed parsed =
                LlmSemanticIntakeParsed.builder()
                        .primaryDomain("DISH_PROFIT")
                        .canonicalUserQuery("汀兰餐厅本月成本最高的菜品有哪些")
                        .isFollowUp(true)
                        .reason("dimension_switch_sales_to_cost_ranking")
                        .build();
        List<String> errors = new ArrayList<>();
        LlmSemanticIntakeParser.collectBareRankingDimensionSwitchIntakeProtocolErrors(
                parsed, input, errors);
        assertThat(errors).isEmpty();
    }
}
