package com.nongxinle.ai.graph.business.execution;

import com.nongxinle.ai.conversation.AiQuerySemanticLexicon;
import com.nongxinle.ai.context.AiResolvedQueryContext;
import com.nongxinle.ai.dto.business.PurchaseAnswerPlan;
import com.nongxinle.ai.semantic.AiQuerySemanticParseResult;
import com.nongxinle.ai.semantic.contract.SemanticContractCompletionEngine;
import com.nongxinle.ai.semantic.contract.SemanticContractValidationDebug;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PurchaseSemanticExecutionIntentResolverTest {

    @Test
    void periodGoodsListContractsMapToSameExecutionWithSourceFacet() {
        assertPeriodGoodsIntent(
                "purchase.period_goods_list",
                null,
                AiQuerySemanticLexicon.SOURCE_ALL);
        assertPeriodGoodsIntent(
                "purchase.period_goods_list.self",
                null,
                AiQuerySemanticLexicon.SOURCE_SELF_PURCHASE);
        assertPeriodGoodsIntent(
                "purchase.period_goods_list.supplier",
                null,
                AiQuerySemanticLexicon.SOURCE_SUPPLIER_PURCHASE);
    }

    @Test
    void periodGoodsListContractIdsRecognized() {
        assertTrue(PurchaseSemanticExecutionIntentResolver.isPeriodGoodsListContractId(
                "purchase.period_goods_list"));
        assertTrue(PurchaseSemanticExecutionIntentResolver.isPeriodGoodsListContractId(
                "purchase.period_goods_list.self"));
        assertTrue(PurchaseSemanticExecutionIntentResolver.isPeriodGoodsListContractId(
                "purchase.period_goods_list.supplier"));
    }

    private static void assertPeriodGoodsIntent(
            String contractId, String frameFacet, String expectedSourceFacet) {
        java.util.LinkedHashMap<String, Object> trace = new java.util.LinkedHashMap<>();
        trace.put(SemanticContractCompletionEngine.TRACE_CONTRACT_ENTRY_VALIDATED, true);
        AiQuerySemanticParseResult parse =
                AiQuerySemanticParseResult.builder()
                        .semanticSlots(
                                AiQuerySemanticParseResult.SemanticSlotsPart.builder()
                                        .selectedContractId(contractId)
                                        .structuredIntentDetailWire(
                                                AiQuerySemanticLexicon
                                                        .STRUCTURED_PURCHASE_PERIOD_GOODS_LIST)
                                        .sourceFacet(frameFacet)
                                        .queryObject("GOODS")
                                        .operation("DETAIL")
                                        .build())
                        .contractCompletionTrace(trace)
                        .build();
        AiResolvedQueryContext rq =
                AiResolvedQueryContext.builder()
                        .querySemanticParse(parse)
                        .semanticContractValidation(
                                SemanticContractValidationDebug.builder()
                                        .matchedContractId(contractId)
                                        .build())
                        .build();

        PurchaseSemanticExecutionIntent intent = PurchaseSemanticExecutionIntentResolver.resolve(rq);
        assertTrue(intent.isActive());
        assertEquals(contractId, intent.getMatchedContractId());
        assertEquals(PurchaseSemanticExecutionIntent.EXEC_PERIOD_GOODS_LIST, intent.getExecutionIntentType());
        assertEquals("PERIOD_GOODS_LIST", intent.getToolDetailWantedKey());
        assertEquals(PurchaseAnswerPlan.TYPE_PURCHASE_PERIOD_GOODS_DETAIL, intent.getAnswerPlanType());
        assertEquals(expectedSourceFacet, intent.getSourceFacet());
    }
}
