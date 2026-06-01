package com.nongxinle.ai.semantic.intake;

import com.nongxinle.ai.semantic.AiQuerySemanticParseResult;
import com.nongxinle.ai.semantic.SemanticParserAllowedOutputContract;
import com.nongxinle.ai.semantic.contract.DomainContractSelectionResult;
import com.nongxinle.ai.semantic.frame.SemanticFrameValidationResult;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WarehouseInventoryShortageSemanticsSupportTest {

    @Test
    void reasonDeclaresShortageSemantics_acceptsPrimaryAndAliasMarkers() {
        assertTrue(
                WarehouseInventoryShortageSemanticsSupport.reasonDeclaresShortageSemantics(
                        "warehouse_inventory_shortage_semantics"));
        assertTrue(
                WarehouseInventoryShortageSemanticsSupport.reasonDeclaresShortageSemantics(
                        "query_contains_inventory_shortage_keywords"));
    }

    @Test
    void reconcileIntake_skipsWhenPrimaryDomainIsDishCost() {
        SemanticIntakeResult mapped =
                SemanticIntakeResult.builder()
                        .status(SemanticIntakeStatus.READY)
                        .primaryDomain("DISH_COST")
                        .needClarification(false)
                        .reason("dish_ingredient_cover_days")
                        .canonicalUserQuery("椒麻鸡配料够用几天？")
                        .build();

        SemanticIntakeResult reconciled =
                WarehouseInventoryShortageSemanticsSupport.reconcileIntake(null, mapped);

        assertEquals("DISH_COST", reconciled.getPrimaryDomain());
        assertEquals("dish_ingredient_cover_days", reconciled.getReason());
    }

    @Test
    void reconcileIntake_doesNotPromoteWhenDishCoverAlreadyReconciledEarlier() {
        SemanticIntakeInput input =
                SemanticIntakeInput.builder()
                        .hasPreviousTurn(true)
                        .previousPathCode(
                                com.nongxinle.ai.context.AiResolvedQueryIntent.PATH_WAREHOUSE_STOCK)
                        .build();
        SemanticIntakeResult dishReady =
                SemanticIntakeResult.builder()
                        .status(SemanticIntakeStatus.READY)
                        .primaryDomain("DISH_COST")
                        .needClarification(false)
                        .reason("dish_ingredient_cover_days")
                        .isFollowUp(true)
                        .usedPreviousContext(true)
                        .build();

        SemanticIntakeResult afterWarehouse =
                WarehouseInventoryShortageSemanticsSupport.reconcileIntake(input, dishReady);

        assertEquals("DISH_COST", afterWarehouse.getPrimaryDomain());
        assertFalse(
                WarehouseInventoryShortageSemanticsSupport.intakeSignalsInventoryShortageSemantics(
                        afterWarehouse));
    }

    @Test
    void reconcileIntake_promotesWarehouseRiskAfterPreviousDishIngredientCover() {
        SemanticIntakeInput input =
                SemanticIntakeInput.builder()
                        .hasPreviousTurn(true)
                        .previousPathCode(
                                com.nongxinle.ai.context.AiResolvedQueryIntent
                                        .PATH_DISH_COST_ANALYSIS)
                        .previousStructuredIntentDetail("dish_ingredient_cover_days")
                        .previousMentionedDishName("椒麻鸡")
                        .build();
        SemanticIntakeResult mapped =
                SemanticIntakeResult.builder()
                        .status(SemanticIntakeStatus.READY)
                        .primaryDomain("DISH_COST")
                        .isFollowUp(true)
                        .usedPreviousContext(true)
                        .needClarification(false)
                        .reason("warehouse_inventory_shortage_semantics")
                        .warehouseInventorySemantics(
                                WarehouseInventoryShortageSemanticsSupport.SEMANTICS_UNDERSTOCK_QUERY)
                        .canonicalUserQuery("有没有快不够用的原料？")
                        .build();

        SemanticIntakeResult afterDish =
                SemanticIntakeDishIngredientCoverDaysSupport.reconcile(input, mapped);
        SemanticIntakeResult afterDishFollow =
                SemanticIntakeDishFollowUpInheritanceSupport.reconcile(input, afterDish);
        SemanticIntakeResult reconciled =
                WarehouseInventoryShortageSemanticsSupport.reconcileIntake(input, afterDishFollow);

        assertEquals("WAREHOUSE", reconciled.getPrimaryDomain());
        assertEquals(
                WarehouseInventoryShortageSemanticsSupport.SEMANTICS_UNDERSTOCK_QUERY,
                reconciled.getWarehouseInventorySemantics());
        assertFalse(
                SemanticIntakeDishIngredientCoverDaysSupport.intakeDeclaresDishIngredientCoverDays(
                        reconciled));
        assertTrue(reconciled.getReason().contains("warehouse_inventory_risk"));
    }

    @Test
    void reconcileIntake_promotesReadyForUnderstockP1() {
        SemanticIntakeResult mapped =
                SemanticIntakeResult.builder()
                        .status(SemanticIntakeStatus.READY)
                        .primaryDomain("WAREHOUSE")
                        .needClarification(false)
                        .reason("query_contains_inventory_shortage_keywords")
                        .warehouseInventorySemantics(
                                WarehouseInventoryShortageSemanticsSupport.SEMANTICS_UNDERSTOCK_QUERY)
                        .canonicalUserQuery("哪些常用原料库存偏少？")
                        .build();

        SemanticIntakeResult reconciled =
                WarehouseInventoryShortageSemanticsSupport.reconcileIntake(null, mapped);

        assertFalse(Boolean.TRUE.equals(reconciled.getNeedClarification()));
        assertTrue(reconciled.getStatus() == SemanticIntakeStatus.READY);
        assertTrue(reconciled.getReason().contains("warehouse_inventory_risk"));
    }

    @Test
    void filterContractSelection_removesGoodsAmountRankingLowFromAllowed() {
        SemanticParserAllowedOutputContract contract =
                SemanticParserAllowedOutputContract.builder()
                        .selectedDomain("WAREHOUSE")
                        .allowedContracts(
                                List.of(
                                        entry("warehouse.overview", "ACTIVE"),
                                        entry(
                                                WarehouseInventoryShortageSemanticsSupport
                                                        .CONTRACT_GOODS_AMOUNT_RANKING_LOW,
                                                "ACTIVE")))
                        .build();
        DomainContractSelectionResult selection =
                DomainContractSelectionResult.builder()
                        .selectedDomain("WAREHOUSE")
                        .parserAllowedOutputContract(contract)
                        .selectedActiveContractCount(2)
                        .build();
        SemanticIntakeResult intake =
                SemanticIntakeResult.builder()
                        .primaryDomain("WAREHOUSE")
                        .reason("warehouse_inventory_shortage_semantics")
                        .build();

        DomainContractSelectionResult filtered =
                WarehouseInventoryShortageSemanticsSupport.filterContractSelection(
                        selection, intake);

        List<SemanticParserAllowedOutputContract.AllowedContractEntry> allowed =
                filtered.getParserAllowedOutputContract().getAllowedContracts();
        assertFalse(
                allowed.stream()
                        .anyMatch(
                                e ->
                                        WarehouseInventoryShortageSemanticsSupport
                                                .CONTRACT_GOODS_AMOUNT_RANKING_LOW
                                                .equals(e.getContractId())));
        assertTrue(
                filtered.getParserAllowedOutputContract()
                        .getContractSelectionBoundaryHints()
                        .stream()
                        .anyMatch(h -> h.contains("warehouse_inventory_shortage_semantics")));
    }

    @Test
    void reconcileIntake_correctsPurchaseMisrouteToWarehouseReady() {
        SemanticIntakeResult mapped =
                SemanticIntakeResult.builder()
                        .status(SemanticIntakeStatus.READY)
                        .primaryDomain("PURCHASE")
                        .needClarification(false)
                        .reason("warehouse_inventory_shortage_semantics")
                        .warehouseInventorySemantics(
                                WarehouseInventoryShortageSemanticsSupport.SEMANTICS_UNDERSTOCK_QUERY)
                        .canonicalUserQuery("哪些常用原料库存偏少？")
                        .build();

        SemanticIntakeResult reconciled =
                WarehouseInventoryShortageSemanticsSupport.reconcileIntake(null, mapped);

        assertTrue(reconciled.getStatus() == SemanticIntakeStatus.READY);
        assertFalse(Boolean.TRUE.equals(reconciled.getNeedClarification()));
        assertTrue("WAREHOUSE".equals(reconciled.getPrimaryDomain()));
    }

    @Test
    void resolveClarificationQuestion_alertUsesBusinessGapWording() {
        SemanticIntakeResult intake =
                SemanticIntakeResult.builder()
                        .primaryDomain("WAREHOUSE")
                        .reason("warehouse_inventory_alert_semantics")
                        .warehouseInventorySemantics(
                                WarehouseInventoryShortageSemanticsSupport.SEMANTICS_UNDERSTOCK_QUERY)
                        .build();

        String q = WarehouseInventoryShortageSemanticsSupport.resolveClarificationQuestion(intake);

        assertTrue(q.contains("保质期"));
        assertTrue(q.contains("warehouse.stock_replenishment_needed"));
        assertTrue(q.contains("报警专链尚未开放"));
        assertTrue(q.contains("账面库存金额较低"));
    }

    @Test
    void resolveClarificationQuestion_replacesIntakeScopeAskingWithKnownGap() {
        SemanticIntakeResult intake =
                SemanticIntakeResult.builder()
                        .primaryDomain("WAREHOUSE")
                        .warehouseInventorySemantics(
                                WarehouseInventoryShortageSemanticsSupport.SEMANTICS_UNDERSTOCK_QUERY)
                        .reason("warehouse_inventory_shortage_semantics")
                        .clarificationQuestion("您是想查具体哪些原料，还是所有原料？")
                        .build();

        String q = WarehouseInventoryShortageSemanticsSupport.resolveClarificationQuestion(intake);

        assertTrue(q.contains("暂不能生成"));
        assertFalse(q.contains("具体哪些"));
    }

    @Test
    void reconcileIntake_promotesReadyWhenExplicitAmountRankingSemantics() {
        SemanticIntakeResult mapped =
                SemanticIntakeResult.builder()
                        .status(SemanticIntakeStatus.NEED_CLARIFICATION)
                        .primaryDomain("WAREHOUSE")
                        .needClarification(true)
                        .clarificationQuestion("库存偏少专链未开放")
                        .warehouseInventorySemantics(
                                WarehouseInventoryShortageSemanticsSupport
                                        .SEMANTICS_EXPLICIT_AMOUNT_RANKING_LOW)
                        .reason("warehouse_inventory_shortage_semantics")
                        .canonicalUserQuery("哪些商品账面库存金额较低？")
                        .build();

        SemanticIntakeResult reconciled =
                WarehouseInventoryShortageSemanticsSupport.reconcileIntake(null, mapped);

        assertTrue(reconciled.getStatus() == SemanticIntakeStatus.READY);
        assertFalse(Boolean.TRUE.equals(reconciled.getNeedClarification()));
        assertFalse(
                WarehouseInventoryShortageSemanticsSupport.intakeSignalsInventoryShortageSemantics(
                        reconciled));
    }

    @Test
    void intakeExplicitAmountRankingLow_acceptsInventoryAmountLowAliasAndAmountReason() {
        assertEquals(
                WarehouseInventoryShortageSemanticsSupport.SEMANTICS_EXPLICIT_AMOUNT_RANKING_LOW,
                WarehouseInventoryShortageSemanticsSupport.normalizeSemantics("INVENTORY_AMOUNT_LOW"));
        assertTrue(
                WarehouseInventoryShortageSemanticsSupport.intakeExplicitAmountRankingLow(
                        SemanticIntakeResult.builder()
                                .reason("warehouse_inventory_amount_ranking_low")
                                .build()));
        assertFalse(
                WarehouseInventoryShortageSemanticsSupport.parsedDeclaresInventoryRisk(
                        com.nongxinle.ai.semantic.intake.llm.LlmSemanticIntakeParsed.builder()
                                .warehouseInventorySemantics("EXPLICIT_AMOUNT_RANKING_LOW")
                                .reason("warehouse_inventory_amount_ranking_low")
                                .build()));
    }

    @Test
    void parsedDeclaresInventoryRisk_acceptsUnderstockAndOutOfStockSemantics() {
        assertTrue(
                WarehouseInventoryShortageSemanticsSupport.parsedDeclaresInventoryRisk(
                        com.nongxinle.ai.semantic.intake.llm.LlmSemanticIntakeParsed.builder()
                                .primaryDomain("PURCHASE")
                                .warehouseInventorySemantics("understock_query")
                                .build()));
        assertTrue(
                WarehouseInventoryShortageSemanticsSupport.parsedDeclaresInventoryRisk(
                        com.nongxinle.ai.semantic.intake.llm.LlmSemanticIntakeParsed.builder()
                                .primaryDomain("WAREHOUSE")
                                .warehouseInventorySemantics("OUT_OF_STOCK")
                                .build()));
    }

    @Test
    void normalizeSemantics_acceptsUnderstockOutOfStockAndShortageAlias() {
        assertEquals(
                WarehouseInventoryShortageSemanticsSupport.SEMANTICS_UNDERSTOCK_QUERY,
                WarehouseInventoryShortageSemanticsSupport.normalizeSemantics("understock_query"));
        assertEquals(
                WarehouseInventoryShortageSemanticsSupport.SEMANTICS_OUT_OF_STOCK,
                WarehouseInventoryShortageSemanticsSupport.normalizeSemantics("OUT_OF_STOCK"));
        assertEquals(
                WarehouseInventoryShortageSemanticsSupport.SEMANTICS_UNDERSTOCK_QUERY,
                WarehouseInventoryShortageSemanticsSupport.normalizeSemantics("SHORTAGE_OR_ALERT"));
        assertEquals(
                WarehouseInventoryShortageSemanticsSupport.SEMANTICS_EXPLICIT_AMOUNT_RANKING_LOW,
                WarehouseInventoryShortageSemanticsSupport.normalizeSemantics("inventory_amount_low"));
    }

    @Test
    void filterContractSelection_keepsAllowedWhenExplicitAmountRankingSemantics() {
        SemanticParserAllowedOutputContract contract =
                SemanticParserAllowedOutputContract.builder()
                        .selectedDomain("WAREHOUSE")
                        .allowedContracts(
                                List.of(
                                        entry(
                                                WarehouseInventoryShortageSemanticsSupport
                                                        .CONTRACT_GOODS_AMOUNT_RANKING_LOW,
                                                "ACTIVE")))
                        .build();
        DomainContractSelectionResult selection =
                DomainContractSelectionResult.builder()
                        .selectedDomain("WAREHOUSE")
                        .parserAllowedOutputContract(contract)
                        .selectedActiveContractCount(1)
                        .build();
        SemanticIntakeResult intake =
                SemanticIntakeResult.builder()
                        .primaryDomain("WAREHOUSE")
                        .warehouseInventorySemantics(
                                WarehouseInventoryShortageSemanticsSupport
                                        .SEMANTICS_EXPLICIT_AMOUNT_RANKING_LOW)
                        .reason("warehouse_inventory_amount_ranking_low")
                        .build();

        DomainContractSelectionResult filtered =
                WarehouseInventoryShortageSemanticsSupport.filterContractSelection(
                        selection, intake);

        assertEquals(1, filtered.getParserAllowedOutputContract().getAllowedContracts().size());
    }

    @Test
    void validateGoodsAmountRankingLowBlocked_clarifiesWhenV2PicksWhC() {
        SemanticIntakeResult intake =
                SemanticIntakeResult.builder()
                        .reason("warehouse_inventory_shortage_semantics")
                        .build();
        AiQuerySemanticParseResult parse =
                AiQuerySemanticParseResult.builder()
                        .semanticSlots(
                                AiQuerySemanticParseResult.SemanticSlotsPart.builder()
                                        .selectedContractId(
                                                WarehouseInventoryShortageSemanticsSupport
                                                        .CONTRACT_GOODS_AMOUNT_RANKING_LOW)
                                        .build())
                        .build();

        SemanticFrameValidationResult result =
                WarehouseInventoryShortageSemanticsSupport.validateGoodsAmountRankingLowBlocked(
                        parse, intake);

        assertTrue(result.needSemanticClarification());
    }

    private static SemanticParserAllowedOutputContract.AllowedContractEntry entry(
            String contractId, String capabilityStatus) {
        return SemanticParserAllowedOutputContract.AllowedContractEntry.builder()
                .contractId(contractId)
                .capabilityStatus(capabilityStatus)
                .build();
    }
}
