package com.nongxinle.ai.semantic.inheritance;

import com.nongxinle.ai.conversation.AiConversationTurnMemory;
import com.nongxinle.ai.conversation.AiQuerySemanticLexicon;
import com.nongxinle.ai.conversation.AiSemanticWireConstants;
import com.nongxinle.ai.dto.business.GoodsSupportedDishCoverAnswerPlan;
import com.nongxinle.ai.dto.business.MenuOperationAnswerPlan;
import com.nongxinle.ai.dto.business.PurchaseAnswerPlan;
import com.nongxinle.ai.semantic.AiQuerySemanticParseResult;
import com.nongxinle.ai.semantic.AiQuerySemanticSlotMerge;
import com.nongxinle.ai.semantic.SemanticParserAllowedOutputContract;
import com.nongxinle.ai.semantic.contract.DomainContractSelectionResult;
import com.nongxinle.ai.semantic.contract.SemanticContractCompletionEngine;
import com.nongxinle.ai.semantic.intake.SemanticIntakeGoodsAnchorFollowUpSupport;
import com.nongxinle.ai.semantic.intake.SemanticIntakeResult;
import com.nongxinle.ai.semantic.intake.SemanticIntakeStatus;
import com.nongxinle.ai.semantic.intake.WarehouseInventoryShortageSemanticsSupport;
import com.nongxinle.ai.semantic.matrix.DishCostAnalysisSemanticCapabilityMatrix;
import com.nongxinle.ai.semantic.matrix.MenuOperationSemanticCapabilityMatrix;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Policy → Applier → ContractCompletion 串联：Business Frame 须完整来自同一条 ACTIVE contract entry。
 */
class SemanticSlotInheritanceApplierIntegrationTest {

    @Test
    void menuOperationOverviewTimeFollowUp_restoresFullContractFrameFromCatalog() {
        AiConversationTurnMemory previous = menuOperationOverviewPreviousTurn();
        AiQuerySemanticParseResult current =
                timeFollowUpParse(
                        "MENU_OPERATION",
                        "menu.operation.overview.v1",
                        "MENU",
                        "OVERVIEW",
                        "PORTFOLIO",
                        MenuOperationSemanticCapabilityMatrix.MENU_FACET_ACTION_RECOMMENDATION,
                        AiSemanticWireConstants.STRUCTURED_MENU_OPERATION_OVERVIEW,
                        MenuOperationAnswerPlan.TYPE_MENU_OPERATION_OVERVIEW,
                        "LAST_MONTH");

        PipelineResult result =
                runPipeline(current, previous, selection("MENU_OPERATION", "menu.operation.overview.v1"));

        assertThat(result.decision().getMode())
                .isEqualTo(SemanticSlotInheritanceMode.INHERIT_SAME_FAMILY_TIME_FOLLOWUP);
        assertThat(result.completed().getSemanticSlots().getSelectedContractId())
                .isEqualTo("menu.operation.overview.v1");
        assertThat(result.completed().getSemanticSlots().getSourceFacet())
                .isEqualTo(MenuOperationSemanticCapabilityMatrix.MENU_FACET_OVERVIEW);
        assertThat(result.completed().getSemanticSlots().getOperation()).isEqualTo("OVERVIEW");
        assertThat(result.completed().getSemanticSlots().getMetric()).isEqualTo("PORTFOLIO");
        assertThat(result.completed().getSemanticSlots().getStructuredIntentDetailWire())
                .isEqualTo(AiSemanticWireConstants.STRUCTURED_MENU_OPERATION_OVERVIEW);
        assertThat(result.completed().getTime().getTimeType()).isEqualTo("LAST_MONTH");
        assertThat(result.completed().effectiveMentionedDishName()).isNull();
    }

    @Test
    void dishSalesRankingTimeFollowUp_restoresFullRankingFrameWithoutTop1Dish() {
        AiConversationTurnMemory previous = dishSalesRankingPreviousTurn();
        AiQuerySemanticParseResult current =
                timeFollowUpParse(
                        "DISH_SALES",
                        "dish_sales.store_single_dish",
                        "DISH",
                        "DETAIL",
                        "SOLD_PORTIONS",
                        null,
                        AiSemanticWireConstants.STRUCTURED_DISH_SALES_SINGLE_DISH,
                        "DISH_SALES_SINGLE_DISH",
                        "LAST_MONTH",
                        "核桃芽菜西芹",
                        AiQuerySemanticSlotMerge.ANCHOR_USE_PREVIOUS);

        PipelineResult result =
                runPipeline(current, previous, selection("DISH_SALES", "dish_sales.count_ranking_high"));

        assertThat(result.decision().getMode())
                .isEqualTo(SemanticSlotInheritanceMode.INHERIT_SAME_FAMILY_TIME_FOLLOWUP);
        assertThat(result.completed().getSemanticSlots().getSelectedContractId())
                .isEqualTo("dish_sales.count_ranking_high");
        assertThat(result.completed().getSemanticSlots().getOperation()).isEqualTo("RANKING");
        assertThat(result.completed().getSemanticSlots().getStructuredIntentDetailWire())
                .isEqualTo(AiSemanticWireConstants.STRUCTURED_DISH_SALES_COUNT_RANKING_HIGH);
        assertThat(result.completed().getTime().getTimeType()).isEqualTo("LAST_MONTH");
        assertThat(result.completed().effectiveMentionedDishName()).isNull();
        assertThat(result.completed().getSemanticSlots().getMentionedDishName()).isNull();
    }

    @Test
    void purchasePeriodGoodsListTimeFollowUp_restoresFullListFrameNotOverview() {
        AiConversationTurnMemory previous = purchasePeriodGoodsListPreviousTurn();
        AiQuerySemanticParseResult current =
                timeFollowUpParse(
                        "PURCHASE",
                        "purchase.overview_summary",
                        "PURCHASE",
                        "OVERVIEW",
                        null,
                        null,
                        AiSemanticWireConstants.STRUCTURED_PURCHASE_OVERVIEW_SUMMARY,
                        PurchaseAnswerPlan.TYPE_PURCHASE_OVERVIEW,
                        "LAST_MONTH");

        PipelineResult result =
                runPipeline(current, previous, selection("PURCHASE", "purchase.period_goods_list"));

        assertThat(result.decision().getMode())
                .isEqualTo(SemanticSlotInheritanceMode.INHERIT_SAME_FAMILY_TIME_FOLLOWUP);
        assertThat(result.completed().getSemanticSlots().getSelectedContractId())
                .isEqualTo("purchase.period_goods_list");
        assertThat(result.completed().getSemanticSlots().getStructuredIntentDetailWire())
                .isEqualTo(AiSemanticWireConstants.STRUCTURED_PURCHASE_PERIOD_GOODS_LIST);
        assertThat(result.completed().getSemanticSlots().getAnswerPlanType())
                .isEqualTo(PurchaseAnswerPlan.TYPE_PURCHASE_PERIOD_GOODS_DETAIL);
        assertThat(result.completed().getSemanticSlots().getSourceFacet())
                .isEqualTo(AiQuerySemanticLexicon.SOURCE_ALL);
        assertThat(result.completed().getTime().getTimeType()).isEqualTo("LAST_MONTH");
    }

    @Test
    void dishSalesToBusinessOverview_preservesCurrentSovereignContract() {
        AiConversationTurnMemory previous = dishSalesRankingPreviousTurn();
        AiQuerySemanticParseResult current =
                timeFollowUpParse(
                        "BUSINESS_OVERVIEW",
                        "business_overview.summary",
                        "BUSINESS",
                        "SUMMARY",
                        "BUSINESS_STATUS",
                        null,
                        AiSemanticWireConstants.STRUCTURED_BUSINESS_OVERVIEW_SUMMARY,
                        "BUSINESS_OVERVIEW_MULTI_AGENT_V1",
                        "YESTERDAY");

        PipelineResult result =
                runPipeline(current, previous, selection("BUSINESS_OVERVIEW", "business_overview.summary"));

        assertThat(result.decision().isCurrentHasSovereignActiveContract()).isTrue();
        assertThat(result.decision().getMode())
                .isIn(
                        SemanticSlotInheritanceMode.INHERIT_NONE,
                        SemanticSlotInheritanceMode.INHERIT_CONTEXT_ONLY);
        assertThat(result.completed().getSemanticSlots().getSelectedContractId())
                .isEqualTo("business_overview.summary");
        assertThat(result.completed().getSemanticSlots().getStructuredIntentDetailWire())
                .isEqualTo(AiSemanticWireConstants.STRUCTURED_BUSINESS_OVERVIEW_SUMMARY);
        assertThat(result.completed().getSemanticSlots().getOperation()).isEqualTo("SUMMARY");
    }

    @Test
    void purchaseToBusinessOverview_preservesCurrentSovereignContract() {
        AiConversationTurnMemory previous = purchasePeriodGoodsListPreviousTurn();
        AiQuerySemanticParseResult current =
                timeFollowUpParse(
                        "BUSINESS_OVERVIEW",
                        "business_overview.summary",
                        "BUSINESS",
                        "SUMMARY",
                        "BUSINESS_STATUS",
                        null,
                        AiSemanticWireConstants.STRUCTURED_BUSINESS_OVERVIEW_SUMMARY,
                        "BUSINESS_OVERVIEW_MULTI_AGENT_V1",
                        "YESTERDAY");

        PipelineResult result =
                runPipeline(current, previous, selection("BUSINESS_OVERVIEW", "business_overview.summary"));

        assertThat(result.decision().isCurrentHasSovereignActiveContract()).isTrue();
        assertThat(result.completed().getSemanticSlots().getSelectedContractId())
                .isEqualTo("business_overview.summary");
        assertThat(result.completed().getSemanticSlots().getStructuredIntentDetailWire())
                .isEqualTo(AiSemanticWireConstants.STRUCTURED_BUSINESS_OVERVIEW_SUMMARY);
    }

    @Test
    void dishIngredientCoverDaysNamedEntityFollowUp_restoresCoverDaysContractNotWeakCost() {
        AiConversationTurnMemory previous =
                AiConversationTurnMemory.builder()
                        .lastPathCode("DISH_COST")
                        .lastStructuredIntentDetail(
                                AiSemanticWireConstants.STRUCTURED_DISH_INGREDIENT_COVER_DAYS)
                        .lastMentionedDishName("酸奶碗")
                        .lastSemanticSlots(
                                AiQuerySemanticParseResult.SemanticSlotsPart.builder()
                                        .selectedContractId(
                                                DishCostAnalysisSemanticCapabilityMatrix
                                                        .CONTRACT_DISH_INGREDIENT_COVER_DAYS)
                                        .queryObject("DISH")
                                        .operation("DETAIL")
                                        .metric("INGREDIENT_COVER_DAYS")
                                        .structuredIntentDetailWire(
                                                AiSemanticWireConstants
                                                        .STRUCTURED_DISH_INGREDIENT_COVER_DAYS)
                                        .answerPlanType("DISH_INGREDIENT_COVER_DAYS")
                                        .mentionedDishName("酸奶碗")
                                        .build())
                        .build();
        AiQuerySemanticParseResult current =
                AiQuerySemanticParseResult.builder()
                        .parseMissing(false)
                        .semanticDomain("DISH_COST")
                        .intentAction("OVERRIDE")
                        .mentionedDishName("椒麻鸡")
                        .semanticSlots(
                                AiQuerySemanticParseResult.SemanticSlotsPart.builder()
                                        .selectedContractId(
                                                DishCostAnalysisSemanticCapabilityMatrix
                                                        .CONTRACT_DISH_COST_SINGLE)
                                        .mentionedDishName("椒麻鸡")
                                        .anchorPolicy(AiQuerySemanticSlotMerge.ANCHOR_IGNORE_PREVIOUS)
                                        .structuredIntentDetailWire("dish_cost_analysis")
                                        .answerPlanType("DISH_COST_ANALYSIS")
                                        .build())
                        .build();
        SemanticIntakeResult intake =
                SemanticIntakeResult.builder()
                        .status(SemanticIntakeStatus.READY)
                        .reason("named_dish_ingredient_cover_days_inherited")
                        .canonicalUserQuery("椒麻鸡还能卖几天")
                        .build();

        PipelineResult result =
                runPipeline(
                        current,
                        previous,
                        selection(
                                "DISH_COST",
                                DishCostAnalysisSemanticCapabilityMatrix.CONTRACT_DISH_INGREDIENT_COVER_DAYS,
                                DishCostAnalysisSemanticCapabilityMatrix.CONTRACT_DISH_COST_SINGLE),
                        intake);

        assertThat(result.decision().getMode())
                .isEqualTo(SemanticSlotInheritanceMode.INHERIT_SAME_CAPABILITY_NAMED_ENTITY);
        assertThat(result.decision().getReasonCode())
                .isEqualTo(SemanticSlotInheritancePolicy.REASON_SAME_CAPABILITY_NAMED_ENTITY);
        assertThat(result.completed().getSemanticSlots().getSelectedContractId())
                .isEqualTo(
                        DishCostAnalysisSemanticCapabilityMatrix.CONTRACT_DISH_INGREDIENT_COVER_DAYS);
        assertThat(result.completed().getSemanticSlots().getStructuredIntentDetailWire())
                .isEqualTo(AiSemanticWireConstants.STRUCTURED_DISH_INGREDIENT_COVER_DAYS);
        assertThat(result.completed().getSemanticSlots().getAnswerPlanType())
                .isEqualTo("DISH_INGREDIENT_COVER_DAYS");
        assertThat(result.completed().effectiveMentionedDishName()).isEqualTo("椒麻鸡");
    }

    @Test
    void dishIngredientCoverPrevious_warehouseRiskFollowUp_crossFamilyClearsDishAnchor() {
        AiConversationTurnMemory previous =
                AiConversationTurnMemory.builder()
                        .lastPathCode("DISH_COST")
                        .lastStructuredIntentDetail(
                                AiSemanticWireConstants.STRUCTURED_DISH_INGREDIENT_COVER_DAYS)
                        .lastMentionedDishName("椒麻鸡")
                        .lastSemanticSlots(
                                AiQuerySemanticParseResult.SemanticSlotsPart.builder()
                                        .selectedContractId(
                                                DishCostAnalysisSemanticCapabilityMatrix
                                                        .CONTRACT_DISH_INGREDIENT_COVER_DAYS)
                                        .queryObject("DISH")
                                        .operation("DETAIL")
                                        .metric("INGREDIENT_COVER_DAYS")
                                        .structuredIntentDetailWire(
                                                AiSemanticWireConstants
                                                        .STRUCTURED_DISH_INGREDIENT_COVER_DAYS)
                                        .answerPlanType("DISH_INGREDIENT_COVER_DAYS")
                                        .mentionedDishName("椒麻鸡")
                                        .build())
                        .build();
        AiQuerySemanticParseResult current =
                AiQuerySemanticParseResult.builder()
                        .parseMissing(false)
                        .semanticDomain("WAREHOUSE")
                        .semanticSlots(
                                AiQuerySemanticParseResult.SemanticSlotsPart.builder()
                                        .selectedContractId(
                                                WarehouseInventoryShortageSemanticsSupport
                                                        .CONTRACT_INVENTORY_RISK_LIST)
                                        .queryObject("GOODS")
                                        .operation("RISK")
                                        .metric("LOW_STOCK")
                                        .structuredIntentDetailWire(
                                                AiQuerySemanticLexicon.STRUCTURED_WAREHOUSE_STOCK_LOW_RISK)
                                        .answerPlanType("WAREHOUSE_LOW_STOCK_RISK")
                                        .anchorPolicy(AiQuerySemanticSlotMerge.ANCHOR_USE_PREVIOUS)
                                        .build())
                        .build();
        SemanticIntakeResult intake =
                SemanticIntakeResult.builder()
                        .status(SemanticIntakeStatus.READY)
                        .primaryDomain("WAREHOUSE")
                        .reason("warehouse_inventory_shortage_semantics")
                        .warehouseInventorySemantics(
                                WarehouseInventoryShortageSemanticsSupport.SEMANTICS_UNDERSTOCK_QUERY)
                        .isFollowUp(true)
                        .usedPreviousContext(true)
                        .build();

        PipelineResult result =
                runPipeline(
                        current,
                        previous,
                        selection(
                                "WAREHOUSE",
                                WarehouseInventoryShortageSemanticsSupport.CONTRACT_INVENTORY_RISK_LIST),
                        intake);

        assertThat(result.decision().getMode())
                .isEqualTo(SemanticSlotInheritanceMode.INHERIT_CONTEXT_ONLY);
        assertThat(result.decision().getReasonCode())
                .isEqualTo(SemanticSlotInheritancePolicy.REASON_CROSS_FAMILY_SOVEREIGN);
        assertThat(result.decision().isSuppressPreviousDishAnchor()).isTrue();
        assertThat(result.completed().getSemanticSlots().getSelectedContractId())
                .isEqualTo(WarehouseInventoryShortageSemanticsSupport.CONTRACT_INVENTORY_RISK_LIST);
        assertThat(result.completed().effectiveMentionedDishName()).isNull();
    }

    @Test
    void explicitDishFollowUp_keepsSingleDishPathWithoutRankingRestore() {
        AiConversationTurnMemory previous = dishSalesRankingPreviousTurn();
        AiQuerySemanticParseResult current =
                AiQuerySemanticParseResult.builder()
                        .parseMissing(false)
                        .semanticDomain("DISH_SALES")
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
                                        .structuredIntentDetailWire(
                                                AiSemanticWireConstants.STRUCTURED_DISH_SALES_SINGLE_DISH)
                                        .answerPlanType("DISH_SALES_SINGLE_DISH")
                                        .build())
                        .build();

        PipelineResult result =
                runPipeline(current, previous, selection("DISH_SALES", "dish_sales.single_dish"));

        assertThat(result.decision().getMode()).isEqualTo(SemanticSlotInheritanceMode.INHERIT_NONE);
        assertThat(result.decision().isExplicitEntityFollowUp()).isTrue();
        assertThat(result.completed().getSemanticSlots().getSelectedContractId())
                .isEqualTo("dish_sales.single_dish");
        assertThat(result.completed().getSemanticSlots().getOperation()).isEqualTo("DETAIL");
        assertThat(result.completed().effectiveMentionedDishName()).isEqualTo("核桃芽菜西芹");
        assertThat(result.completed().getSemanticSlots().getStructuredIntentDetailWire())
                .isEqualTo(AiSemanticWireConstants.STRUCTURED_DISH_SALES_SINGLE_DISH);
    }

    private record PipelineResult(
            SemanticSlotInheritanceDecision decision,
            AiQuerySemanticParseResult afterInheritance,
            AiQuerySemanticParseResult completed) {}

    private static PipelineResult runPipeline(
            AiQuerySemanticParseResult current,
            AiConversationTurnMemory previous,
            DomainContractSelectionResult selection) {
        return runPipeline(current, previous, selection, null);
    }

    private static PipelineResult runPipeline(
            AiQuerySemanticParseResult current,
            AiConversationTurnMemory previous,
            DomainContractSelectionResult selection,
            SemanticIntakeResult semanticIntake) {
        SemanticSlotInheritanceDecision decision =
                SemanticSlotInheritancePolicy.decide(
                        SemanticSlotInheritanceRequest.builder()
                                .currentParse(current)
                                .previousTurn(previous)
                                .contractSelection(selection)
                                .semanticIntake(semanticIntake)
                                .build());
        AiQuerySemanticParseResult afterInheritance =
                SemanticSlotInheritanceApplier.apply(current, previous, decision);
        SemanticContractCompletionEngine.Result completion =
                SemanticContractCompletionEngine.complete(
                        SemanticContractCompletionEngine.Request.builder()
                                .rawParse(afterInheritance)
                                .selectedDomain(selection.getSelectedDomain())
                                .contractSelection(selection)
                                .previousTurn(previous)
                                .build());
        assertThat(completion.isViolation())
                .as("contract completion violation: %s", completion.getViolationReason())
                .isFalse();
        return new PipelineResult(decision, afterInheritance, completion.getCompletedParse());
    }

    private static DomainContractSelectionResult selection(String domain, String... contractIds) {
        SemanticParserAllowedOutputContract.AllowedContractEntry[] entries =
                new SemanticParserAllowedOutputContract.AllowedContractEntry[contractIds.length];
        for (int i = 0; i < contractIds.length; i++) {
            entries[i] =
                    SemanticParserAllowedOutputContract.AllowedContractEntry.builder()
                            .contractId(contractIds[i])
                            .capabilityStatus("ACTIVE")
                            .build();
        }
        return DomainContractSelectionResult.builder()
                .selectedDomain(domain)
                .parserAllowedOutputContract(
                        SemanticParserAllowedOutputContract.builder()
                                .selectedDomain(domain)
                                .allowedContracts(List.of(entries))
                                .build())
                .build();
    }

    @Test
    void goodsSupportedDishCoverPrevious_bareStockFollowUp_restoresGoodsAnchorContract() {
        AiConversationTurnMemory previous =
                AiConversationTurnMemory.builder()
                        .lastPathCode("WAREHOUSE_STOCK")
                        .lastStructuredIntentDetail(
                                AiQuerySemanticLexicon.STRUCTURED_GOODS_SUPPORTED_DISH_COVER)
                        .lastSemanticSlots(
                                AiQuerySemanticParseResult.SemanticSlotsPart.builder()
                                        .selectedContractId(GoodsSupportedDishCoverAnswerPlan.CONTRACT_ID)
                                        .queryObject("GOODS")
                                        .operation("DETAIL")
                                        .metric("SUPPORTED_DISH_COVER")
                                        .structuredIntentDetailWire(
                                                AiQuerySemanticLexicon.STRUCTURED_GOODS_SUPPORTED_DISH_COVER)
                                        .answerPlanType(GoodsSupportedDishCoverAnswerPlan.TYPE)
                                        .mentionedGoodsName("三黄鸡")
                                        .build())
                        .build();
        AiQuerySemanticParseResult current =
                AiQuerySemanticParseResult.builder()
                        .parseMissing(false)
                        .semanticDomain("WAREHOUSE")
                        .semanticSlots(
                                AiQuerySemanticParseResult.SemanticSlotsPart.builder()
                                        .selectedContractId("warehouse.overview")
                                        .queryObject("ALL")
                                        .operation("SUMMARY")
                                        .metric("STOCK_AMOUNT")
                                        .structuredIntentDetailWire(
                                                AiQuerySemanticLexicon.STRUCTURED_WAREHOUSE_STOCK_OVERVIEW)
                                        .answerPlanType("WAREHOUSE_STOCK_OVERVIEW")
                                        .build())
                        .build();
        SemanticIntakeResult intake =
                SemanticIntakeResult.builder()
                        .status(SemanticIntakeStatus.READY)
                        .primaryDomain("WAREHOUSE")
                        .isFollowUp(true)
                        .usedPreviousContext(true)
                        .reason(SemanticIntakeGoodsAnchorFollowUpSupport.REASON_MARKER)
                        .build();

        PipelineResult result =
                runPipeline(
                        current,
                        previous,
                        selection(
                                "WAREHOUSE",
                                GoodsSupportedDishCoverAnswerPlan.CONTRACT_ID,
                                "warehouse.overview"),
                        intake);

        assertThat(result.decision().getMode())
                .isEqualTo(SemanticSlotInheritanceMode.INHERIT_SAME_GOODS_ANCHOR_FOLLOWUP);
        assertThat(result.completed().getSemanticSlots().getSelectedContractId())
                .isEqualTo(GoodsSupportedDishCoverAnswerPlan.CONTRACT_ID);
        assertThat(result.completed().getSemanticSlots().getStructuredIntentDetailWire())
                .isEqualTo(AiQuerySemanticLexicon.STRUCTURED_GOODS_SUPPORTED_DISH_COVER);
        assertThat(result.completed().effectiveMentionedGoodsName()).isEqualTo("三黄鸡");
        assertThat(result.completed().getSemanticSlots().getAnchorPolicy())
                .isEqualTo(AiQuerySemanticSlotMerge.ANCHOR_USE_PREVIOUS);
    }

    private static AiConversationTurnMemory menuOperationOverviewPreviousTurn() {
        return AiConversationTurnMemory.builder()
                .lastPathCode("MENU_OPERATION")
                .lastStructuredIntentDetail(AiSemanticWireConstants.STRUCTURED_MENU_OPERATION_OVERVIEW)
                .lastSemanticSlots(
                        AiQuerySemanticParseResult.SemanticSlotsPart.builder()
                                .selectedContractId("menu.operation.overview.v1")
                                .queryObject("MENU")
                                .operation("OVERVIEW")
                                .metric("PORTFOLIO")
                                .sourceFacet(MenuOperationSemanticCapabilityMatrix.MENU_FACET_OVERVIEW)
                                .structuredIntentDetailWire(
                                        AiSemanticWireConstants.STRUCTURED_MENU_OPERATION_OVERVIEW)
                                .answerPlanType(MenuOperationAnswerPlan.TYPE_MENU_OPERATION_OVERVIEW)
                                .build())
                .build();
    }

    private static AiConversationTurnMemory dishSalesRankingPreviousTurn() {
        return AiConversationTurnMemory.builder()
                .lastPathCode("DISH_SALES_QUERY")
                .lastStructuredIntentDetail(
                        AiSemanticWireConstants.STRUCTURED_DISH_SALES_COUNT_RANKING_HIGH)
                .lastSemanticSlots(
                        AiQuerySemanticParseResult.SemanticSlotsPart.builder()
                                .selectedContractId("dish_sales.count_ranking_high")
                                .queryObject("DISH")
                                .operation("RANKING")
                                .metric("SOLD_PORTIONS")
                                .anchorPolicy(AiQuerySemanticSlotMerge.ANCHOR_IGNORE_PREVIOUS)
                                .structuredIntentDetailWire(
                                        AiSemanticWireConstants.STRUCTURED_DISH_SALES_COUNT_RANKING_HIGH)
                                .answerPlanType("DISH_SALES_COUNT_RANKING_HIGH")
                                .build())
                .lastMentionedDishName("核桃芽菜西芹")
                .build();
    }

    private static AiConversationTurnMemory purchasePeriodGoodsListPreviousTurn() {
        return AiConversationTurnMemory.builder()
                .lastPathCode("purchase_overview_path")
                .lastStructuredIntentDetail(AiSemanticWireConstants.STRUCTURED_PURCHASE_PERIOD_GOODS_LIST)
                .lastPurchaseSourceType("ALL")
                .lastSemanticSlots(
                        AiQuerySemanticParseResult.SemanticSlotsPart.builder()
                                .selectedContractId("purchase.period_goods_list")
                                .queryObject("GOODS")
                                .operation("DETAIL")
                                .sourceFacet(AiQuerySemanticLexicon.SOURCE_ALL)
                                .structuredIntentDetailWire(
                                        AiSemanticWireConstants.STRUCTURED_PURCHASE_PERIOD_GOODS_LIST)
                                .answerPlanType(PurchaseAnswerPlan.TYPE_PURCHASE_PERIOD_GOODS_DETAIL)
                                .build())
                .build();
    }

    private static AiQuerySemanticParseResult timeFollowUpParse(
            String semanticDomain,
            String contractId,
            String queryObject,
            String operation,
            String metric,
            String sourceFacet,
            String wire,
            String answerPlanType,
            String timeType) {
        return timeFollowUpParse(
                semanticDomain,
                contractId,
                queryObject,
                operation,
                metric,
                sourceFacet,
                wire,
                answerPlanType,
                timeType,
                null,
                null);
    }

    private static AiQuerySemanticParseResult timeFollowUpParse(
            String semanticDomain,
            String contractId,
            String queryObject,
            String operation,
            String metric,
            String sourceFacet,
            String wire,
            String answerPlanType,
            String timeType,
            String mentionedDishName,
            String anchorPolicy) {
        AiQuerySemanticParseResult.AiQuerySemanticParseResultBuilder builder =
                AiQuerySemanticParseResult.builder()
                        .parseMissing(false)
                        .semanticDomain(semanticDomain)
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
                                        .sourceFacet(sourceFacet)
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
