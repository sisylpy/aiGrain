package com.nongxinle.ai.semantic.dimension;

import com.nongxinle.ai.context.AiResolvedQueryIntent;
import com.nongxinle.ai.conversation.AiConversationTurnMemory;
import com.nongxinle.ai.conversation.AiQuerySemanticLexicon;
import com.nongxinle.ai.dto.business.AiResultAnchor;
import com.nongxinle.ai.semantic.AiQuerySemanticParseResult;
import com.nongxinle.ai.semantic.AiQuerySemanticSlotMerge;
import com.nongxinle.ai.semantic.SemanticParserAllowedOutputContract;
import com.nongxinle.ai.semantic.contract.DomainContractSelectionResult;
import com.nongxinle.ai.semantic.contract.SemanticContractCompletionEngine;
import com.nongxinle.ai.semantic.inheritance.SemanticSlotInheritanceApplier;
import com.nongxinle.ai.semantic.inheritance.SemanticSlotInheritanceDecision;
import com.nongxinle.ai.semantic.inheritance.SemanticSlotInheritanceMode;
import com.nongxinle.ai.semantic.inheritance.SemanticSlotInheritancePolicy;
import com.nongxinle.ai.semantic.inheritance.SemanticSlotInheritanceRequest;
import com.nongxinle.ai.semantic.intake.SemanticIntakeInput;
import com.nongxinle.ai.semantic.intake.SemanticIntakePrimaryDomain;
import com.nongxinle.ai.semantic.intake.SemanticIntakeResult;
import com.nongxinle.ai.semantic.intake.SemanticIntakeStatus;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class BareRankingDimensionSwitchSupportTest {

    @Test
    void buildPlan_costRankingFollowUpSales_activeWithSalesRankingContract() {
        AiConversationTurnMemory previous = costRankingPreviousTurn();
        SemanticIntakeResult intake =
                SemanticIntakeResult.builder()
                        .status(SemanticIntakeStatus.READY)
                        .canonicalUserQuery("本月销量高的菜品有哪些")
                        .isFollowUp(true)
                        .primaryDomain(SemanticIntakePrimaryDomain.DISH_SALES)
                        .needClarification(false)
                        .reason("dimension_switch_cost_to_sales_ranking_reconciled")
                        .build();

        BareRankingDimensionSwitchPlan plan =
                BareRankingDimensionSwitchSupport.buildPlanFromPreviousTurn(intake, previous);

        assertThat(plan.isActive()).isTrue();
        assertThat(plan.getTargetDomain()).isEqualTo(SemanticIntakePrimaryDomain.DISH_SALES);
        assertThat(plan.getTargetContractId()).isEqualTo("dish_sales.count_ranking_high");
        assertThat(plan.getTargetFacet()).isEqualTo(RankingMetricFacet.SOLD_PORTIONS);
    }

    @Test
    void pipeline_costRankingFollowUpSales_overridesSingleDishUsePreviousAnchor() {
        AiConversationTurnMemory previous = costRankingPreviousTurn();
        SemanticIntakeResult intake =
                SemanticIntakeResult.builder()
                        .status(SemanticIntakeStatus.READY)
                        .canonicalUserQuery("本月销量高的菜品有哪些")
                        .isFollowUp(true)
                        .primaryDomain(SemanticIntakePrimaryDomain.DISH_SALES)
                        .needClarification(false)
                        .reason("dimension_switch_cost_to_sales_ranking_reconciled")
                        .build();
        BareRankingDimensionSwitchPlan plan =
                BareRankingDimensionSwitchSupport.buildPlanFromPreviousTurn(intake, previous);
        AiQuerySemanticParseResult v2Misroute =
                AiQuerySemanticParseResult.builder()
                        .semanticDomain("DISH_SALES")
                        .mentionedDishName("酸奶碗")
                        .semanticSlots(
                                AiQuerySemanticParseResult.SemanticSlotsPart.builder()
                                        .selectedContractId("dish_sales.single_dish")
                                        .queryObject("DISH")
                                        .operation("DETAIL")
                                        .metric("SOLD_PORTIONS")
                                        .structuredIntentDetailWire(
                                                AiQuerySemanticLexicon.STRUCTURED_DISH_SALES_SINGLE_DISH)
                                        .anchorPolicy(AiQuerySemanticSlotMerge.ANCHOR_USE_PREVIOUS)
                                        .mentionedDishName("酸奶碗")
                                        .build())
                        .build();

        AiQuerySemanticParseResult out = runPipeline(v2Misroute, previous, plan);

        assertThat(out.getSemanticSlots().getSelectedContractId())
                .isEqualTo("dish_sales.count_ranking_high");
        assertThat(out.getSemanticSlots().getOperation()).isEqualTo("RANKING");
        assertThat(out.getSemanticSlots().getMetric()).isEqualTo("SOLD_PORTIONS");
        assertThat(out.getSemanticSlots().getAnchorPolicy())
                .isEqualTo(AiQuerySemanticSlotMerge.ANCHOR_IGNORE_PREVIOUS);
        assertThat(out.effectiveMentionedDishName()).isNull();
        assertThat(SemanticSlotInheritanceApplier.suppressPreviousDishAnchor(out)).isTrue();
    }

    @Test
    void pipeline_salesRankingFollowUpCost_overridesDishCostSingleContract() {
        AiConversationTurnMemory previous = salesRankingPreviousTurn();
        SemanticIntakeResult intake =
                SemanticIntakeResult.builder()
                        .status(SemanticIntakeStatus.READY)
                        .canonicalUserQuery("本月成本高的菜品有哪些")
                        .isFollowUp(true)
                        .primaryDomain(SemanticIntakePrimaryDomain.DISH_PROFIT)
                        .needClarification(false)
                        .reason("dimension_switch_sales_to_cost_ranking_reconciled")
                        .build();
        BareRankingDimensionSwitchPlan plan =
                BareRankingDimensionSwitchSupport.buildPlanFromPreviousTurn(intake, previous);
        AiQuerySemanticParseResult v2Misroute =
                AiQuerySemanticParseResult.builder()
                        .semanticDomain("DISH_COST")
                        .mentionedDishName("酸奶碗")
                        .semanticSlots(
                                AiQuerySemanticParseResult.SemanticSlotsPart.builder()
                                        .selectedContractId("dish_cost.single_dish_analysis")
                                        .queryObject("DISH")
                                        .operation("DETAIL")
                                        .structuredIntentDetailWire(
                                                AiQuerySemanticLexicon.STRUCTURED_DISH_COST_ANALYSIS)
                                        .anchorPolicy(AiQuerySemanticSlotMerge.ANCHOR_USE_PREVIOUS)
                                        .mentionedDishName("酸奶碗")
                                        .build())
                        .build();

        AiQuerySemanticParseResult out =
                runPipeline(
                        v2Misroute,
                        previous,
                        plan,
                        selection(
                                "DISH_PROFIT",
                                "dish_profit.ranking_high_actual_cost",
                                "dish_sales.count_ranking_high"));

        assertThat(out.getSemanticSlots().getSelectedContractId())
                .isEqualTo("dish_profit.ranking_high_actual_cost");
        assertThat(out.getSemanticSlots().getOperation()).isEqualTo("RANKING");
        assertThat(out.getSemanticSlots().getMetric()).isEqualTo("ACTUAL_COST");
        assertThat(out.getSemanticSlots().getAnchorPolicy())
                .isEqualTo(AiQuerySemanticSlotMerge.ANCHOR_IGNORE_PREVIOUS);
        assertThat(out.effectiveMentionedDishName()).isNull();
        assertThat(plan.getTargetFacet()).isEqualTo(RankingMetricFacet.ACTUAL_COST);
        assertThat(plan.getTargetFacetResolveSource())
                .isEqualTo(BareRankingDimensionSwitchSupport.FACET_SOURCE_INTAKE_REASON_TOKEN);
        assertThat(plan.isTargetFacetFallbackUsed()).isFalse();
    }

    @Test
    void buildPlan_salesRankingFollowUpMargin_resolvesMarginFromReasonNotFallback() {
        AiConversationTurnMemory previous = salesRankingPreviousTurn();
        SemanticIntakeResult intake =
                SemanticIntakeResult.builder()
                        .status(SemanticIntakeStatus.READY)
                        .canonicalUserQuery("本月毛利最高的菜品有哪些")
                        .isFollowUp(true)
                        .primaryDomain(SemanticIntakePrimaryDomain.DISH_PROFIT)
                        .needClarification(false)
                        .reason("dimension_switch_sales_to_margin_ranking_reconciled")
                        .build();

        BareRankingDimensionSwitchPlan plan =
                BareRankingDimensionSwitchSupport.buildPlanFromPreviousTurn(intake, previous);

        assertThat(plan.isActive()).isTrue();
        assertThat(plan.getTargetFacet()).isEqualTo(RankingMetricFacet.GROSS_MARGIN_RATE);
        assertThat(plan.getTargetFacetResolveSource())
                .isEqualTo(BareRankingDimensionSwitchSupport.FACET_SOURCE_INTAKE_REASON_TOKEN);
        assertThat(plan.isTargetFacetFallbackUsed()).isFalse();
        assertThat(plan.getTargetContractId()).isEqualTo("dish_profit.ranking_high_margin");
    }

    @Test
    void buildPlan_salesRankingFollowUpCost_resolvesCostFromReasonNotFallback() {
        AiConversationTurnMemory previous = salesRankingPreviousTurn();
        SemanticIntakeResult intake =
                SemanticIntakeResult.builder()
                        .status(SemanticIntakeStatus.READY)
                        .canonicalUserQuery("本月成本高的菜品有哪些")
                        .isFollowUp(true)
                        .primaryDomain(SemanticIntakePrimaryDomain.DISH_PROFIT)
                        .needClarification(false)
                        .reason("dimension_switch_sales_to_cost_ranking_reconciled")
                        .build();

        BareRankingDimensionSwitchPlan plan =
                BareRankingDimensionSwitchSupport.buildPlanFromPreviousTurn(intake, previous);

        assertThat(plan.isActive()).isTrue();
        assertThat(plan.getTargetFacet()).isEqualTo(RankingMetricFacet.ACTUAL_COST);
        assertThat(plan.getTargetFacetResolveSource())
                .isEqualTo(BareRankingDimensionSwitchSupport.FACET_SOURCE_INTAKE_REASON_TOKEN);
        assertThat(plan.isTargetFacetFallbackUsed()).isFalse();
        assertThat(plan.getTargetContractId()).isEqualTo("dish_profit.ranking_high_actual_cost");
    }

    @Test
    void buildPlan_costRankingFollowUpSales_primaryStuckOnProfit_activeWhenIntakeTokenPresent() {
        AiConversationTurnMemory previous = costRankingPreviousTurn();
        SemanticIntakeResult intake =
                SemanticIntakeResult.builder()
                        .status(SemanticIntakeStatus.READY)
                        .canonicalUserQuery("本月销量高的菜品有哪些")
                        .isFollowUp(true)
                        .primaryDomain(SemanticIntakePrimaryDomain.DISH_PROFIT)
                        .needClarification(false)
                        .reason("dimension_switch_cost_to_sales_ranking_reconciled")
                        .build();

        BareRankingDimensionSwitchPlan plan =
                BareRankingDimensionSwitchSupport.buildPlanFromPreviousTurn(intake, previous);

        assertThat(plan.isActive()).isTrue();
        assertThat(plan.getTargetDomain()).isEqualTo(SemanticIntakePrimaryDomain.DISH_SALES);
        assertThat(plan.getTargetContractId()).isEqualTo("dish_sales.count_ranking_high");
    }

    @Test
    void buildPlan_costRankingFollowUpSales_withoutIntakeToken_inactiveKnownGap() {
        AiConversationTurnMemory previous = costRankingPreviousTurn();
        SemanticIntakeResult intake =
                SemanticIntakeResult.builder()
                        .status(SemanticIntakeStatus.READY)
                        .canonicalUserQuery("本月销量高的菜品有哪些")
                        .isFollowUp(true)
                        .primaryDomain(SemanticIntakePrimaryDomain.DISH_PROFIT)
                        .needClarification(false)
                        .reason("follow_up_rewrite")
                        .build();

        BareRankingDimensionSwitchPlan plan =
                BareRankingDimensionSwitchSupport.buildPlanFromPreviousTurn(intake, previous);

        assertThat(plan.isActive()).isFalse();
        assertThat(plan.getTargetFacetResolveSource())
                .isEqualTo(BareRankingDimensionSwitchSupport.FACET_SOURCE_UNRESOLVED);
    }

    @Test
    void buildPlan_salesFollowUpRevenuePrimary_resolvesAmountRankingContract() {
        SemanticIntakeInput input =
                SemanticIntakeInput.builder()
                        .hasPreviousTurn(true)
                        .previousPathCode(AiResolvedQueryIntent.PATH_DISH_SALES_QUERY)
                        .previousStructuredIntentDetail(
                                AiQuerySemanticLexicon.STRUCTURED_DISH_SALES_COUNT_RANKING_HIGH)
                        .normalizedUserMessage("销售额呢")
                        .build();
        SemanticIntakeResult intake =
                SemanticIntakeResult.builder()
                        .status(SemanticIntakeStatus.READY)
                        .canonicalUserQuery("本月销售额最高的菜品有哪些")
                        .isFollowUp(true)
                        .primaryDomain(SemanticIntakePrimaryDomain.REVENUE)
                        .candidateDomains(List.of(SemanticIntakePrimaryDomain.DISH_SALES))
                        .needClarification(false)
                        .reason("dimension_switch_sales_to_amount_ranking")
                        .build();

        BareRankingDimensionSwitchPlan plan =
                BareRankingDimensionSwitchSupport.buildPlan(input, intake, null);

        assertThat(plan.isActive()).isTrue();
        assertThat(plan.getTargetDomain()).isEqualTo(SemanticIntakePrimaryDomain.DISH_SALES);
        assertThat(plan.getTargetFacet()).isEqualTo(RankingMetricFacet.SALES_AMOUNT);
        assertThat(plan.getTargetContractId()).isEqualTo("dish_sales.amount_ranking_high");
    }

    @Test
    void pipeline_salesRankingFollowUpMargin_overridesProfitOverviewMisroute() {
        AiConversationTurnMemory previous = salesRankingPreviousTurn();
        SemanticIntakeResult intake =
                SemanticIntakeResult.builder()
                        .status(SemanticIntakeStatus.READY)
                        .canonicalUserQuery("本月毛利最高的菜品有哪些")
                        .isFollowUp(true)
                        .primaryDomain(SemanticIntakePrimaryDomain.DISH_PROFIT)
                        .needClarification(false)
                        .reason("dimension_switch_sales_to_margin_ranking_reconciled")
                        .build();
        BareRankingDimensionSwitchPlan plan =
                BareRankingDimensionSwitchSupport.buildPlanFromPreviousTurn(intake, previous);
        AiQuerySemanticParseResult v2Misroute =
                AiQuerySemanticParseResult.builder()
                        .semanticDomain("DISH_PROFIT")
                        .semanticSlots(
                                AiQuerySemanticParseResult.SemanticSlotsPart.builder()
                                        .selectedContractId("dish_profit.overview")
                                        .queryObject("DISH")
                                        .operation("OVERVIEW")
                                        .metric("GROSS_MARGIN")
                                        .structuredIntentDetailWire(
                                                AiQuerySemanticLexicon.STRUCTURED_DISH_PROFIT_OVERVIEW)
                                        .build())
                        .build();

        AiQuerySemanticParseResult out =
                runPipelineWithPlanEnforcement(
                        v2Misroute,
                        previous,
                        plan,
                        selection(
                                "DISH_PROFIT",
                                "dish_profit.ranking_high_margin",
                                "dish_profit.overview"));

        assertThat(out.getSemanticSlots().getSelectedContractId())
                .isEqualTo("dish_profit.ranking_high_margin");
        assertThat(out.getSemanticSlots().getOperation()).isEqualTo("RANKING");
        assertThat(out.getSemanticSlots().getMetric()).isEqualTo("GROSS_MARGIN_RATE");
    }

    @Test
    void buildPlan_salesFollowUpAmount_resolvesAmountRankingContract() {
        SemanticIntakeInput input =
                SemanticIntakeInput.builder()
                        .hasPreviousTurn(true)
                        .previousPathCode(AiResolvedQueryIntent.PATH_DISH_SALES_QUERY)
                        .previousStructuredIntentDetail(
                                AiQuerySemanticLexicon.STRUCTURED_DISH_SALES_COUNT_RANKING_HIGH)
                        .normalizedUserMessage("销售额呢")
                        .build();
        SemanticIntakeResult intake =
                SemanticIntakeResult.builder()
                        .status(SemanticIntakeStatus.READY)
                        .canonicalUserQuery("本月销售额最高的菜品有哪些")
                        .isFollowUp(true)
                        .primaryDomain(SemanticIntakePrimaryDomain.DISH_SALES)
                        .candidateDomains(List.of(SemanticIntakePrimaryDomain.DISH_SALES))
                        .needClarification(false)
                        .reason("dimension_switch_sales_to_amount_ranking")
                        .build();

        BareRankingDimensionSwitchPlan plan =
                BareRankingDimensionSwitchSupport.buildPlan(input, intake, null);

        assertThat(plan.isActive()).isTrue();
        assertThat(plan.getTargetFacet()).isEqualTo(RankingMetricFacet.SALES_AMOUNT);
        assertThat(plan.getTargetContractId()).isEqualTo("dish_sales.amount_ranking_high");
    }

    @Test
    void resolveTargetFacet_profitAmountRankingReasonToken_notSalesAmount() {
        assertThat(
                        BareRankingDimensionSwitchSupport.resolveTargetFacet(
                                "dimension_switch_to_profit_amount_ranking_reconciled"))
                .isEqualTo(RankingMetricFacet.GROSS_PROFIT_AMOUNT);
        assertThat(
                        BareRankingDimensionSwitchSupport.resolveTargetContractId(
                                SemanticIntakePrimaryDomain.DISH_PROFIT,
                                RankingMetricFacet.GROSS_PROFIT_AMOUNT,
                                RankingPolarity.HIGH))
                .isEqualTo("dish_profit.ranking_high_profit_amount");
    }

    @Test
    void buildPlan_dishSalesRankingTimeFollowUpReason_inactiveWithTimeOnlyPath() {
        AiConversationTurnMemory previous = salesRankingPreviousTurn();
        SemanticIntakeResult intake =
                SemanticIntakeResult.builder()
                        .status(SemanticIntakeStatus.READY)
                        .canonicalUserQuery("上个月销量高的菜品有哪些")
                        .isFollowUp(true)
                        .usedPreviousContext(true)
                        .primaryDomain(SemanticIntakePrimaryDomain.DISH_SALES)
                        .needClarification(false)
                        .reason("dish_sales_ranking_time_follow_up")
                        .build();

        BareRankingDimensionSwitchPlan plan =
                BareRankingDimensionSwitchSupport.buildPlanFromPreviousTurn(intake, previous);

        assertThat(plan.isActive()).isFalse();
        assertThat(plan.getTargetFacetResolveSource())
                .isEqualTo("STRUCTURED_RANKING_TIME_ONLY");
    }

    @Test
    void buildPlan_dishSalesRankingShortPhraseFollowUpWithoutDimensionSwitch_inactive() {
        AiConversationTurnMemory previous = salesRankingPreviousTurn();
        SemanticIntakeResult intake =
                SemanticIntakeResult.builder()
                        .status(SemanticIntakeStatus.READY)
                        .canonicalUserQuery("上个月销量高的菜品有哪些")
                        .isFollowUp(true)
                        .primaryDomain(SemanticIntakePrimaryDomain.DISH_SALES)
                        .needClarification(false)
                        .reason("dish_sales_ranking_short_phrase")
                        .build();

        BareRankingDimensionSwitchPlan plan =
                BareRankingDimensionSwitchSupport.buildPlanFromPreviousTurn(intake, previous);

        assertThat(plan.isActive()).isFalse();
    }

    @Test
    void buildPlan_namedDishCostFollowUp_inactive() {
        AiConversationTurnMemory previous = salesRankingPreviousTurn();
        SemanticIntakeResult intake =
                SemanticIntakeResult.builder()
                        .status(SemanticIntakeStatus.READY)
                        .canonicalUserQuery("酸奶碗成本怎么样")
                        .isFollowUp(true)
                        .primaryDomain(SemanticIntakePrimaryDomain.DISH_COST)
                        .needClarification(false)
                        .reason("named_dish_cost_explicit")
                        .build();

        BareRankingDimensionSwitchPlan plan =
                BareRankingDimensionSwitchSupport.buildPlanFromPreviousTurn(intake, previous);

        assertThat(plan.isActive()).isFalse();
    }

    private static AiQuerySemanticParseResult runPipeline(
            AiQuerySemanticParseResult current,
            AiConversationTurnMemory previous,
            BareRankingDimensionSwitchPlan plan) {
        return runPipeline(current, previous, plan, selection("DISH_SALES", "dish_sales.count_ranking_high"));
    }

    private static AiQuerySemanticParseResult runPipeline(
            AiQuerySemanticParseResult current,
            AiConversationTurnMemory previous,
            BareRankingDimensionSwitchPlan plan,
            DomainContractSelectionResult selection) {
        DomainContractSelectionResult effectiveSelection =
                BareRankingDimensionSwitchSupport.contractSelectionForPlan(plan, selection);
        SemanticSlotInheritanceDecision decision =
                SemanticSlotInheritancePolicy.decide(
                        SemanticSlotInheritanceRequest.builder()
                                .currentParse(current)
                                .previousTurn(previous)
                                .contractSelection(effectiveSelection)
                                .bareRankingDimensionSwitchPlan(plan)
                                .build());
        assertThat(decision.getMode())
                .isEqualTo(SemanticSlotInheritanceMode.INHERIT_BARE_RANKING_DIMENSION_SWITCH);
        AiQuerySemanticParseResult inherited =
                SemanticSlotInheritanceApplier.apply(current, previous, decision);
        inherited =
                AiQuerySemanticSlotMerge.reconcileExplicitCurrentTurnDishAnchor(inherited, previous);
        SemanticContractCompletionEngine.Result completion =
                SemanticContractCompletionEngine.complete(
                        SemanticContractCompletionEngine.Request.builder()
                                .rawParse(inherited)
                                .selectedDomain(effectiveSelection.getSelectedDomain())
                                .contractSelection(effectiveSelection)
                                .previousTurn(previous)
                                .build());
        return completion.getCompletedParse();
    }

    private static AiQuerySemanticParseResult runPipelineWithPlanEnforcement(
            AiQuerySemanticParseResult current,
            AiConversationTurnMemory previous,
            BareRankingDimensionSwitchPlan plan,
            DomainContractSelectionResult selection) {
        AiQuerySemanticParseResult out = runPipeline(current, previous, plan, selection);
        return BareRankingDimensionSwitchSupport.enforcePlanSovereignFrame(out, plan);
    }

    private static AiConversationTurnMemory costRankingPreviousTurn() {
        return AiConversationTurnMemory.builder()
                .lastPathCode(AiResolvedQueryIntent.PATH_DISH_PROFIT)
                .lastStructuredIntentDetail(
                        AiQuerySemanticLexicon.STRUCTURED_DISH_ACTUAL_COST_RANKING_HIGH)
                .lastMentionedDishName("酸奶碗")
                .lastResultAnchors(
                        List.of(
                                AiResultAnchor.builder()
                                        .entityType(AiResultAnchor.ENTITY_TYPE_DISH)
                                        .entityName("酸奶碗")
                                        .rank(1)
                                        .build()))
                .lastSemanticSlots(
                        AiQuerySemanticParseResult.SemanticSlotsPart.builder()
                                .selectedContractId("dish_profit.ranking_high_actual_cost")
                                .structuredIntentDetailWire(
                                        AiQuerySemanticLexicon.STRUCTURED_DISH_ACTUAL_COST_RANKING_HIGH)
                                .operation("RANKING")
                                .build())
                .build();
    }

    private static AiConversationTurnMemory salesRankingPreviousTurn() {
        return AiConversationTurnMemory.builder()
                .lastPathCode(AiResolvedQueryIntent.PATH_DISH_SALES_QUERY)
                .lastStructuredIntentDetail(
                        AiQuerySemanticLexicon.STRUCTURED_DISH_SALES_COUNT_RANKING_HIGH)
                .lastMentionedDishName("酸奶碗")
                .lastResultAnchors(
                        List.of(
                                AiResultAnchor.builder()
                                        .entityType(AiResultAnchor.ENTITY_TYPE_DISH)
                                        .entityName("酸奶碗")
                                        .rank(1)
                                        .build()))
                .lastSemanticSlots(
                        AiQuerySemanticParseResult.SemanticSlotsPart.builder()
                                .selectedContractId("dish_sales.count_ranking_high")
                                .structuredIntentDetailWire(
                                        AiQuerySemanticLexicon.STRUCTURED_DISH_SALES_COUNT_RANKING_HIGH)
                                .operation("RANKING")
                                .build())
                .build();
    }

    private static DomainContractSelectionResult selection(String domain, String... contractIds) {
        List<SemanticParserAllowedOutputContract.AllowedContractEntry> allowed =
                java.util.Arrays.stream(contractIds)
                        .map(
                                id ->
                                        SemanticParserAllowedOutputContract.AllowedContractEntry
                                                .builder()
                                                .contractId(id)
                                                .build())
                        .toList();
        return DomainContractSelectionResult.builder()
                .selectedDomain(domain)
                .parserAllowedOutputContract(
                        SemanticParserAllowedOutputContract.builder()
                                .selectedDomain(domain)
                                .allowedContracts(allowed)
                                .build())
                .build();
    }
}
