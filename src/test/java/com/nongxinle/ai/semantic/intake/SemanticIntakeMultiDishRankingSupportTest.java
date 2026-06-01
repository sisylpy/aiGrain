package com.nongxinle.ai.semantic.intake;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SemanticIntakeMultiDishRankingSupportTest {

    @Test
    void looksLikeMultiDishCostRankingCanonical_dishCostRankingPhrase_true() {
        assertThat(
                        SemanticIntakeMultiDishRankingSupport.looksLikeMultiDishCostRankingCanonical(
                                "上个月的菜品成本排行"))
                .isTrue();
    }

    @Test
    void reconcileExplicitMultiDishRankingDomain_dishCostPromotedToDishProfit() {
        SemanticIntakeResult intake =
                SemanticIntakeResult.builder()
                        .status(SemanticIntakeStatus.READY)
                        .primaryDomain("DISH_COST")
                        .candidateDomains(List.of("DISH_COST"))
                        .routeType("EXPLICIT")
                        .canonicalUserQuery("上个月的菜品成本排行")
                        .reason("cost_follow_up")
                        .confidence(0.9)
                        .build();

        SemanticIntakeResult reconciled =
                SemanticIntakeMultiDishRankingSupport.reconcileExplicitMultiDishRankingDomain(
                        null, intake);

        assertThat(reconciled.getPrimaryDomain()).isEqualTo("DISH_PROFIT");
        assertThat(reconciled.getCandidateDomains()).containsExactly("DISH_PROFIT");
        assertThat(reconciled.getReason()).contains("dish_actual_cost_ranking_high_explicit");
    }

    @Test
    void suppressRewriteAnchorInjection_explicitCostRanking_true() {
        SemanticIntakeResult intake =
                SemanticIntakeResult.builder()
                        .status(SemanticIntakeStatus.READY)
                        .primaryDomain("DISH_PROFIT")
                        .canonicalUserQuery("上个月成本最高的是什么菜？")
                        .reason("dish_actual_cost_ranking_high_explicit")
                        .build();

        assertThat(SemanticIntakeMultiDishRankingSupport.suppressRewriteAnchorInjection(intake))
                .isTrue();
    }

    @Test
    void suppressRewriteAnchorInjection_namedDish_false() {
        SemanticIntakeResult intake =
                SemanticIntakeResult.builder()
                        .status(SemanticIntakeStatus.READY)
                        .primaryDomain("DISH_COST")
                        .canonicalUserQuery("酸奶碗成本怎么样")
                        .reason("named_dish_cost_explicit")
                        .build();

        assertThat(SemanticIntakeMultiDishRankingSupport.suppressRewriteAnchorInjection(intake))
                .isFalse();
    }
}
