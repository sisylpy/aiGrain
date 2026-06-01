package com.nongxinle.ai.semantic.contract;

import com.nongxinle.ai.conversation.AiQuerySemanticLexicon;
import com.nongxinle.ai.dto.business.PurchaseAnswerPlan;
import com.nongxinle.ai.semantic.AiQuerySemanticParseResult;
import com.nongxinle.ai.semantic.SemanticParserAllowedOutputContract;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ContractBusinessSlotRequirementSupportTest {

    @Test
    void isMetricSemanticallyRequired_periodGoodsListContract_false() {
        SemanticCapabilityContract contract =
                SemanticContractCatalog.findActiveCapabilityContractById(
                        "purchase.period_goods_list", "PURCHASE");
        assertThat(contract).isNotNull();
        assertThat(
                        ContractBusinessSlotRequirementSupport.isMetricSemanticallyRequired(
                                contract, "LIST"))
                .isFalse();
        assertThat(
                        ContractBusinessSlotRequirementSupport.isMetricSemanticallyRequired(
                                contract, null))
                .isFalse();
    }

    @Test
    void isMetricSemanticallyRequired_goodsAmountRanking_true() {
        SemanticCapabilityContract contract =
                SemanticContractCatalog.findActiveCapabilityContractById(
                        "purchase.goods_amount_ranking", "PURCHASE");
        assertThat(contract).isNotNull();
        assertThat(
                        ContractBusinessSlotRequirementSupport.isMetricSemanticallyRequired(
                                contract, "RANKING"))
                .isTrue();
    }

    @Test
    void coalesceMetricFromContract_optionalList_dropsInvalidAndDoesNotFabricate() {
        SemanticCapabilityContract contract =
                SemanticContractCatalog.findActiveCapabilityContractById(
                        "purchase.period_goods_list", "PURCHASE");
        assertThat(
                        ContractBusinessSlotRequirementSupport.coalesceMetricFromContract(
                                null, contract, "LIST"))
                .isNull();
        assertThat(
                        ContractBusinessSlotRequirementSupport.coalesceMetricFromContract(
                                "PURCHASE_COUNT", contract, "LIST"))
                .isNull();
        assertThat(
                        ContractBusinessSlotRequirementSupport.coalesceMetricFromContract(
                                "PURCHASE_AMOUNT", contract, "LIST"))
                .isEqualTo("PURCHASE_AMOUNT");
    }

    @Test
    void complete_periodGoodsList_nullMetric_noSlotMismatchViolation() {
        AiQuerySemanticParseResult raw =
                AiQuerySemanticParseResult.builder()
                        .parseMissing(false)
                        .semanticSlots(
                                AiQuerySemanticParseResult.SemanticSlotsPart.builder()
                                        .selectedContractId("purchase.period_goods_list")
                                        .queryObject("GOODS")
                                        .operation("LIST")
                                        .metric(null)
                                        .sourceFacet(AiQuerySemanticLexicon.SOURCE_ALL)
                                        .structuredIntentDetailWire(
                                                AiQuerySemanticLexicon
                                                        .STRUCTURED_PURCHASE_PERIOD_GOODS_LIST)
                                        .answerPlanType(
                                                PurchaseAnswerPlan.TYPE_PURCHASE_PERIOD_GOODS_DETAIL)
                                        .build())
                        .build();

        DomainContractSelectionResult selection = purchaseSelection("purchase.period_goods_list");

        SemanticContractCompletionEngine.Result result =
                SemanticContractCompletionEngine.complete(
                        SemanticContractCompletionEngine.Request.builder()
                                .rawParse(raw)
                                .selectedDomain("PURCHASE")
                                .contractSelection(selection)
                                .build());

        assertThat(result.isViolation()).isFalse();
        assertThat(result.getCompletedParse().getSemanticSlots().getMetric()).isNull();
        assertThat(
                        SemanticContractCompletionEngine.isContractLockedParse(
                                result.getCompletedParse()))
                .isTrue();
    }

    @Test
    void complete_periodGoodsList_invalidMetric_sanitizedToNull_notViolation() {
        AiQuerySemanticParseResult raw =
                AiQuerySemanticParseResult.builder()
                        .parseMissing(false)
                        .semanticSlots(
                                AiQuerySemanticParseResult.SemanticSlotsPart.builder()
                                        .selectedContractId("purchase.period_goods_list")
                                        .queryObject("GOODS")
                                        .operation("LIST")
                                        .metric("PURCHASE_COUNT")
                                        .sourceFacet(AiQuerySemanticLexicon.SOURCE_ALL)
                                        .build())
                        .build();

        SemanticContractCompletionEngine.Result result =
                SemanticContractCompletionEngine.complete(
                        SemanticContractCompletionEngine.Request.builder()
                                .rawParse(raw)
                                .selectedDomain("PURCHASE")
                                .contractSelection(purchaseSelection("purchase.period_goods_list"))
                                .build());

        assertThat(result.isViolation()).isFalse();
        assertThat(result.getCompletedParse().getSemanticSlots().getMetric()).isNull();
    }

    private static DomainContractSelectionResult purchaseSelection(String contractId) {
        return DomainContractSelectionResult.builder()
                .selectedDomain("PURCHASE")
                .parserAllowedOutputContract(
                        SemanticParserAllowedOutputContract.builder()
                                .selectedDomain("PURCHASE")
                                .allowedContracts(
                                        List.of(
                                                SemanticParserAllowedOutputContract
                                                        .AllowedContractEntry.builder()
                                                        .contractId(contractId)
                                                        .build()))
                                .build())
                .build();
    }
}
