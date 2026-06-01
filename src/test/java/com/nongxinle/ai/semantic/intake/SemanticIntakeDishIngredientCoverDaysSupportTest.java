package com.nongxinle.ai.semantic.intake;

import com.nongxinle.ai.context.AiResolvedQueryIntent;
import com.nongxinle.ai.semantic.AiQuerySemanticParseResult;
import com.nongxinle.ai.semantic.SemanticParserAllowedOutputContract;
import com.nongxinle.ai.semantic.contract.DomainContractSelectionResult;
import com.nongxinle.ai.semantic.intake.llm.LlmSemanticIntakeJsonParser;
import com.nongxinle.ai.semantic.intake.llm.LlmSemanticIntakeParsed;
import com.nongxinle.ai.semantic.matrix.DishCostAnalysisSemanticCapabilityMatrix;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SemanticIntakeDishIngredientCoverDaysSupportTest {

    @Test
    void jsonParser_injectsReasonMarkerWhenWarehouseSemanticsIsStockDaysMislabel() {
        LlmSemanticIntakeParsed parsed =
                LlmSemanticIntakeJsonParser.parseRaw(
                        """
                        {
                          "questionMode": "SINGLE_QUESTION",
                          "normalizationType": "PASS_THROUGH",
                          "canonicalUserQuery": "椒麻鸡配料够用几天？",
                          "isFollowUp": true,
                          "usedPreviousContext": true,
                          "primaryDomain": "WAREHOUSE",
                          "candidateDomains": ["WAREHOUSE"],
                          "routeType": "EXPLICIT",
                          "confidence": 0.9,
                          "needClarification": false,
                          "reason": "follow_up_warehouse",
                          "warehouseInventorySemantics": "STOCK_DAYS"
                        }
                        """);

        assertFalse(parsed.isParseFailed());
        assertTrue(
                SemanticIntakeDishIngredientCoverDaysSupport.reasonDeclaresDishIngredientCoverDays(
                        parsed.getReason()));
        assertEquals("STOCK_DAYS", parsed.getWarehouseInventorySemantics());
    }

    @Test
    void reconcile_promotesWarehouseStockDaysMislabelToDishCost() {
        SemanticIntakeInput input =
                SemanticIntakeInput.builder()
                        .hasPreviousTurn(true)
                        .previousPathCode(AiResolvedQueryIntent.PATH_WAREHOUSE_STOCK)
                        .build();
        SemanticIntakeResult mapped =
                SemanticIntakeResult.builder()
                        .status(SemanticIntakeStatus.READY)
                        .primaryDomain("WAREHOUSE")
                        .isFollowUp(true)
                        .usedPreviousContext(true)
                        .reason(
                                SemanticIntakeDishIngredientCoverDaysSupport.appendDishCoverReasonMarker(
                                        "follow_up_warehouse"))
                        .warehouseInventorySemantics("STOCK_DAYS")
                        .canonicalUserQuery("椒麻鸡配料够用几天？")
                        .build();

        SemanticIntakeResult reconciled =
                SemanticIntakeDishIngredientCoverDaysSupport.reconcile(input, mapped);

        assertEquals(SemanticIntakePrimaryDomain.DISH_COST, reconciled.getPrimaryDomain());
        assertNull(reconciled.getWarehouseInventorySemantics());
        assertFalse(Boolean.TRUE.equals(reconciled.getNeedClarification()));
    }

    @Test
    void reconcile_crossDomainWhenCandidateDomainsIncludeDishCost() {
        SemanticIntakeInput input =
                SemanticIntakeInput.builder()
                        .hasPreviousTurn(true)
                        .previousPathCode(AiResolvedQueryIntent.PATH_WAREHOUSE_STOCK)
                        .build();
        SemanticIntakeResult mapped =
                SemanticIntakeResult.builder()
                        .status(SemanticIntakeStatus.READY)
                        .primaryDomain("WAREHOUSE")
                        .candidateDomains(List.of("WAREHOUSE", "DISH_COST"))
                        .isFollowUp(true)
                        .usedPreviousContext(true)
                        .reason("follow_up_ambiguous")
                        .warehouseInventorySemantics(
                                WarehouseInventoryShortageSemanticsSupport.SEMANTICS_UNDERSTOCK_QUERY)
                        .build();

        SemanticIntakeResult reconciled =
                SemanticIntakeDishIngredientCoverDaysSupport.reconcile(input, mapped);

        assertEquals(SemanticIntakePrimaryDomain.DISH_COST, reconciled.getPrimaryDomain());
        assertNull(reconciled.getWarehouseInventorySemantics());
    }

    @Test
    void intakeSignalsInventoryShortage_falseWhenStockDaysMislabelOnReason() {
        SemanticIntakeResult intake =
                SemanticIntakeResult.builder()
                        .primaryDomain("WAREHOUSE")
                        .reason("dish_ingredient_cover_days")
                        .warehouseInventorySemantics("STOCK_DAYS")
                        .build();

        assertFalse(
                WarehouseInventoryShortageSemanticsSupport.intakeSignalsInventoryShortageSemantics(
                        intake));
    }

    @Test
    void mustNotApplyWhenV2LockedDishCoverContract() {
        SemanticIntakeResult intake =
                SemanticIntakeResult.builder()
                        .primaryDomain("WAREHOUSE")
                        .reason("warehouse_inventory_shortage_semantics")
                        .warehouseInventorySemantics(
                                WarehouseInventoryShortageSemanticsSupport.SEMANTICS_UNDERSTOCK_QUERY)
                        .build();
        AiQuerySemanticParseResult sem =
                AiQuerySemanticParseResult.builder()
                        .semanticSlots(
                                AiQuerySemanticParseResult.SemanticSlotsPart.builder()
                                        .selectedContractId(
                                                DishCostAnalysisSemanticCapabilityMatrix
                                                        .CONTRACT_DISH_INGREDIENT_COVER_DAYS)
                                        .build())
                        .build();

        assertTrue(
                SemanticIntakeDishIngredientCoverDaysSupport
                        .mustNotApplyWarehouseInventoryShortagePipeline(intake, sem));
    }

    @Test
    void reconcile_promotesPurchaseWithReasonMarkerToDishCost() {
        SemanticIntakeResult mapped =
                SemanticIntakeResult.builder()
                        .status(SemanticIntakeStatus.READY)
                        .primaryDomain("PURCHASE")
                        .candidateDomains(List.of("PURCHASE"))
                        .routeType("EXPLICIT")
                        .needClarification(false)
                        .reason("dish_ingredient_cover_days")
                        .build();

        SemanticIntakeResult reconciled =
                SemanticIntakeDishIngredientCoverDaysSupport.reconcile(
                        SemanticIntakeInput.builder().build(), mapped);

        assertEquals(SemanticIntakePrimaryDomain.DISH_COST, reconciled.getPrimaryDomain());
        assertNull(reconciled.getWarehouseInventorySemantics());
    }

    @Test
    void reconcile_promotesPurchaseWhenCandidateDomainsIncludeDishCost() {
        SemanticIntakeResult mapped =
                SemanticIntakeResult.builder()
                        .status(SemanticIntakeStatus.READY)
                        .primaryDomain("PURCHASE")
                        .candidateDomains(List.of("PURCHASE", "DISH_COST"))
                        .routeType("EXPLICIT")
                        .needClarification(false)
                        .reason("ambiguous_ingredient_domain")
                        .build();

        SemanticIntakeResult reconciled =
                SemanticIntakeDishIngredientCoverDaysSupport.reconcile(
                        SemanticIntakeInput.builder().build(), mapped);

        assertEquals(SemanticIntakePrimaryDomain.DISH_COST, reconciled.getPrimaryDomain());
        assertTrue(
                SemanticIntakeDishIngredientCoverDaysSupport.reasonDeclaresDishIngredientCoverDays(
                        reconciled.getReason()));
    }

    @Test
    void reconcile_promotesPurchaseStockDaysMislabelToDishCost() {
        SemanticIntakeResult mapped =
                SemanticIntakeResult.builder()
                        .status(SemanticIntakeStatus.READY)
                        .primaryDomain("PURCHASE")
                        .candidateDomains(List.of("PURCHASE"))
                        .routeType("EXPLICIT")
                        .needClarification(false)
                        .reason("purchase_overview")
                        .warehouseInventorySemantics("STOCK_DAYS")
                        .build();

        SemanticIntakeResult reconciled =
                SemanticIntakeDishIngredientCoverDaysSupport.reconcile(
                        SemanticIntakeInput.builder().build(), mapped);

        assertEquals(SemanticIntakePrimaryDomain.DISH_COST, reconciled.getPrimaryDomain());
        assertNull(reconciled.getWarehouseInventorySemantics());
    }

    @Test
    void collectProtocolErrors_flagsPurchaseWithDishCoverReason() {
        LlmSemanticIntakeParsed parsed =
                LlmSemanticIntakeParsed.builder()
                        .primaryDomain("PURCHASE")
                        .reason("dish_ingredient_cover_days")
                        .needClarification(false)
                        .build();
        List<String> errors = new ArrayList<>();

        SemanticIntakeDishIngredientCoverDaysSupport.collectDishIngredientCoverProtocolErrors(
                parsed, errors);

        assertFalse(errors.isEmpty());
        assertTrue(errors.stream().anyMatch(e -> e.contains("not PURCHASE")));
    }

    @Test
    void filterContractSelection_notNarrowedWhenIntakeReconciledToDishCost() {
        SemanticParserAllowedOutputContract contract =
                SemanticParserAllowedOutputContract.builder()
                        .selectedDomain("DISH_COST")
                        .allowedContracts(
                                List.of(
                                        entry("dish.ingredient_cover_days.v1", "ACTIVE"),
                                        entry(
                                                WarehouseInventoryShortageSemanticsSupport
                                                        .CONTRACT_INVENTORY_RISK_LIST,
                                                "ACTIVE")))
                        .build();
        DomainContractSelectionResult selection =
                DomainContractSelectionResult.builder()
                        .selectedDomain("DISH_COST")
                        .parserAllowedOutputContract(contract)
                        .build();
        SemanticIntakeResult intake =
                SemanticIntakeResult.builder()
                        .primaryDomain("DISH_COST")
                        .reason("dish_ingredient_cover_days")
                        .build();

        DomainContractSelectionResult filtered =
                WarehouseInventoryShortageSemanticsSupport.filterContractSelection(
                        selection, intake);

        assertEquals(2, filtered.getParserAllowedOutputContract().getAllowedContracts().size());
    }

    private static SemanticParserAllowedOutputContract.AllowedContractEntry entry(
            String id, String status) {
        return SemanticParserAllowedOutputContract.AllowedContractEntry.builder()
                .contractId(id)
                .capabilityStatus(status)
                .build();
    }
}
