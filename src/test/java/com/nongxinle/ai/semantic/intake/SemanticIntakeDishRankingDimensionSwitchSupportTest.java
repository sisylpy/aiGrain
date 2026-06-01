package com.nongxinle.ai.semantic.intake;

import com.nongxinle.ai.context.AiResolvedQueryIntent;
import com.nongxinle.ai.conversation.AiQuerySemanticLexicon;
import com.nongxinle.ai.dto.business.AiResultAnchor;
import com.nongxinle.ai.semantic.AiQuerySemanticParseResult;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SemanticIntakeDishRankingDimensionSwitchSupportTest {

    @Test
    void reconcile_salesRankingFollowUpCostMisroutedToDishCost_promotesDishProfit() {
        SemanticIntakeInput input =
                SemanticIntakeInput.builder()
                        .hasPreviousTurn(true)
                        .previousPathCode(AiResolvedQueryIntent.PATH_DISH_SALES_QUERY)
                        .previousStructuredIntentDetail(
                                AiQuerySemanticLexicon.STRUCTURED_DISH_SALES_COUNT_RANKING_HIGH)
                        .resultAnchors(
                                List.of(
                                        AiResultAnchor.builder()
                                                .entityType(AiResultAnchor.ENTITY_TYPE_DISH)
                                                .entityName("酸奶碗")
                                                .rank(1)
                                                .build()))
                        .normalizedUserMessage("成本呢")
                        .build();
        SemanticIntakeResult intake =
                SemanticIntakeResult.builder()
                        .status(SemanticIntakeStatus.READY)
                        .normalizationType(SemanticIntakeNormalizationType.REWRITE)
                        .canonicalUserQuery("本月成本高的菜品有哪些")
                        .isFollowUp(true)
                        .usedPreviousContext(true)
                        .primaryDomain(SemanticIntakePrimaryDomain.DISH_COST)
                        .candidateDomains(List.of(SemanticIntakePrimaryDomain.DISH_COST))
                        .routeType("EXPLICIT")
                        .needClarification(false)
                        .reason("dimension_switch_sales_to_cost_ranking")
                        .build();

        SemanticIntakeResult out =
                SemanticIntakeDishRankingDimensionSwitchSupport.reconcile(input, intake);

        assertThat(out.getPrimaryDomain()).isEqualTo(SemanticIntakePrimaryDomain.DISH_PROFIT);
        assertThat(out.getCandidateDomains()).containsExactly(SemanticIntakePrimaryDomain.DISH_PROFIT);
        assertThat(out.getRouteType()).isEqualTo("EXPLICIT");
    }

    @Test
    void reconcile_salesRankingFollowUpMarginStaleDomain_promotesDishProfit() {
        SemanticIntakeInput input =
                SemanticIntakeInput.builder()
                        .hasPreviousTurn(true)
                        .previousPathCode(AiResolvedQueryIntent.PATH_DISH_SALES_QUERY)
                        .previousStructuredIntentDetail(
                                AiQuerySemanticLexicon.STRUCTURED_DISH_SALES_RANKING_HIGH)
                        .normalizedUserMessage("毛利呢")
                        .build();
        SemanticIntakeResult intake =
                SemanticIntakeResult.builder()
                        .status(SemanticIntakeStatus.READY)
                        .normalizationType(SemanticIntakeNormalizationType.REWRITE)
                        .canonicalUserQuery("本月毛利最高的菜品有哪些")
                        .isFollowUp(true)
                        .usedPreviousContext(true)
                        .primaryDomain(SemanticIntakePrimaryDomain.DISH_SALES)
                        .candidateDomains(List.of(SemanticIntakePrimaryDomain.DISH_PROFIT))
                        .routeType("INHERITED")
                        .needClarification(false)
                        .reason("dimension_switch_sales_to_margin_ranking")
                        .build();

        SemanticIntakeResult out =
                SemanticIntakeDishRankingDimensionSwitchSupport.reconcile(input, intake);

        assertThat(out.getPrimaryDomain()).isEqualTo(SemanticIntakePrimaryDomain.DISH_PROFIT);
    }

    @Test
    void reconcile_costRankingFollowUpSales_promotesDishSales() {
        SemanticIntakeInput input =
                SemanticIntakeInput.builder()
                        .hasPreviousTurn(true)
                        .previousPathCode(AiResolvedQueryIntent.PATH_DISH_PROFIT)
                        .previousStructuredIntentDetail(
                                AiQuerySemanticLexicon.STRUCTURED_DISH_ACTUAL_COST_RANKING_HIGH)
                        .normalizedUserMessage("销量呢")
                        .build();
        SemanticIntakeResult intake =
                SemanticIntakeResult.builder()
                        .status(SemanticIntakeStatus.READY)
                        .normalizationType(SemanticIntakeNormalizationType.REWRITE)
                        .canonicalUserQuery("本月销量高的菜品有哪些")
                        .isFollowUp(true)
                        .usedPreviousContext(true)
                        .primaryDomain(SemanticIntakePrimaryDomain.DISH_PROFIT)
                        .candidateDomains(List.of(SemanticIntakePrimaryDomain.DISH_SALES))
                        .routeType("INHERITED")
                        .needClarification(false)
                        .reason("dimension_switch_cost_to_sales_ranking")
                        .build();

        SemanticIntakeResult out =
                SemanticIntakeDishRankingDimensionSwitchSupport.reconcile(input, intake);

        assertThat(out.getPrimaryDomain()).isEqualTo(SemanticIntakePrimaryDomain.DISH_SALES);
    }

    @Test
    void resolveTargetRankingDomain_profitRankingToSalesPrimaryAlreadySales_returnsDishSales() {
        SemanticIntakeInput input =
                SemanticIntakeInput.builder()
                        .hasPreviousTurn(true)
                        .previousPathCode(AiResolvedQueryIntent.PATH_DISH_PROFIT)
                        .previousStructuredIntentDetail(
                                AiQuerySemanticLexicon.STRUCTURED_DISH_ACTUAL_COST_RANKING_HIGH)
                        .normalizedUserMessage("销量呢")
                        .build();
        SemanticIntakeResult intake =
                SemanticIntakeResult.builder()
                        .status(SemanticIntakeStatus.READY)
                        .canonicalUserQuery("本月销量高的菜品有哪些")
                        .isFollowUp(true)
                        .primaryDomain(SemanticIntakePrimaryDomain.DISH_SALES)
                        .needClarification(false)
                        .reason("dimension_switch_cost_to_sales_ranking")
                        .build();

        assertThat(
                        SemanticIntakeDishRankingDimensionSwitchSupport.resolveTargetRankingDomainForSwitch(
                                input, intake))
                .isEqualTo(SemanticIntakePrimaryDomain.DISH_SALES);
    }

    @Test
    void reconcile_namedDishCostFollowUp_leavesDishCostUntouched() {
        SemanticIntakeInput input =
                SemanticIntakeInput.builder()
                        .hasPreviousTurn(true)
                        .previousPathCode(AiResolvedQueryIntent.PATH_DISH_SALES_QUERY)
                        .previousStructuredIntentDetail(
                                AiQuerySemanticLexicon.STRUCTURED_DISH_SALES_COUNT_RANKING_HIGH)
                        .resultAnchors(
                                List.of(
                                        AiResultAnchor.builder()
                                                .entityType(AiResultAnchor.ENTITY_TYPE_DISH)
                                                .entityName("酸奶碗")
                                                .rank(1)
                                                .build()))
                        .normalizedUserMessage("酸奶碗成本呢")
                        .build();
        SemanticIntakeResult intake =
                SemanticIntakeResult.builder()
                        .status(SemanticIntakeStatus.READY)
                        .normalizationType(SemanticIntakeNormalizationType.REWRITE)
                        .canonicalUserQuery("酸奶碗成本怎么样")
                        .isFollowUp(true)
                        .usedPreviousContext(true)
                        .primaryDomain(SemanticIntakePrimaryDomain.DISH_COST)
                        .candidateDomains(List.of(SemanticIntakePrimaryDomain.DISH_COST))
                        .routeType("EXPLICIT")
                        .needClarification(false)
                        .reason("named_dish_cost_explicit")
                        .build();

        SemanticIntakeResult out =
                SemanticIntakeDishRankingDimensionSwitchSupport.reconcile(input, intake);

        assertThat(out.getPrimaryDomain()).isEqualTo(SemanticIntakePrimaryDomain.DISH_COST);
    }
}
