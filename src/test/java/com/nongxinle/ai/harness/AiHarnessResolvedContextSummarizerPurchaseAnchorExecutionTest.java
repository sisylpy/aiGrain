package com.nongxinle.ai.harness;

import com.nongxinle.ai.context.AiResolvedQueryContext;
import com.nongxinle.ai.context.AiResolvedQueryIntent;
import com.nongxinle.ai.conversation.AiConversationTurnMemory;
import com.nongxinle.ai.conversation.AiQuerySemanticLexicon;
import com.nongxinle.ai.core.AiRunState;
import com.nongxinle.ai.dto.business.AiResultAnchor;
import com.nongxinle.ai.dto.business.PurchaseAnswerPlan;
import com.nongxinle.ai.semantic.AiQuerySemanticParseResult;
import com.nongxinle.ai.semantic.contract.SemanticContractValidationDebug;
import com.nongxinle.ai.tool.business.AiBusinessToolIds;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiHarnessResolvedContextSummarizerPurchaseAnchorExecutionTest {

    @Test
    void summarize_mirrorsPurchaseSupplierGoodsDetail_toSummaryTop() {
        LinkedHashMap<String, Object> dbg = new LinkedHashMap<>();
        dbg.put("purchaseSupplierGoodsDetailRowsCount", 2);
        dbg.put("purchaseSupplierGoodsDetailNoDataReason", null);
        dbg.put("purchaseSupplierGoodsDetailAlternativeHasData", Boolean.FALSE);
        dbg.put(AiBusinessToolIds.PAYLOAD_PURCHASE_GOODS_ANCHOR_EXECUTION_TARGET_GOODS_NAME, "海天5度白醋");
        dbg.put(AiBusinessToolIds.PAYLOAD_PURCHASE_GOODS_ANCHOR_EXECUTION_TARGET_GOODS_ID, 9001);

        PurchaseAnswerPlan pap = PurchaseAnswerPlan.builder()
                .planType(PurchaseAnswerPlan.TYPE_PURCHASE_SUPPLIER_GOODS_DETAIL)
                .debug(dbg)
                .build();

        AiResolvedQueryIntent qi =
                AiResolvedQueryIntent.builder()
                        .structuredIntentDetail(AiQuerySemanticLexicon.STRUCTURED_PURCHASE_SOURCE_GOODS_QUERY)
                        .purchaseSourceType(AiQuerySemanticLexicon.SOURCE_SUPPLIER_PURCHASE)
                        .build();
        AiResolvedQueryContext ctx = AiResolvedQueryContext.builder().queryIntent(qi).build();
        AiRunState state = AiRunState.builder().resolvedQueryContext(ctx).purchaseAnswerPlan(pap).build();

        Map<String, Object> summary = AiHarnessResolvedContextSummarizer.summarize(ctx, 901L, state);
        assertEquals(2, summary.get("purchaseSupplierGoodsDetailRowsCount"));
        assertEquals(false, summary.get("purchaseSupplierGoodsDetailAlternativeHasData"));
        assertEquals("海天5度白醋", summary.get("purchaseGoodsAnchorExecutionTargetGoodsName"));
        assertEquals(9001, summary.get("purchaseGoodsAnchorExecutionTargetGoodsId"));

        String preview = (String) summary.get("answerPreview");
        assertNotNull(preview);
        assertTrue(preview.contains("海天5度白醋"));
        assertTrue(preview.contains("rows=2"));
        assertFalse(preview.contains("reason="));
    }

    @Test
    void summarize_backfillsFocusEntityId_fromPurchaseGoodsAnchorExecutionTargetGoodsId() {
        LinkedHashMap<String, Object> dbg = new LinkedHashMap<>();
        dbg.put(AiBusinessToolIds.PAYLOAD_PURCHASE_GOODS_ANCHOR_EXECUTION_TARGET_GOODS_ID, 54);
        dbg.put(AiBusinessToolIds.PAYLOAD_PURCHASE_GOODS_ANCHOR_EXECUTION_TARGET_GOODS_NAME, "海天5度白醋");

        PurchaseAnswerPlan pap = PurchaseAnswerPlan.builder()
                .planType(PurchaseAnswerPlan.TYPE_PURCHASE_SUPPLIER_GOODS_DETAIL)
                .debug(dbg)
                .build();

        AiResolvedQueryIntent qi =
                AiResolvedQueryIntent.builder()
                        .structuredIntentDetail(AiQuerySemanticLexicon.STRUCTURED_PURCHASE_SOURCE_GOODS_QUERY)
                        .purchaseSourceType(AiQuerySemanticLexicon.SOURCE_SUPPLIER_PURCHASE)
                        .build();
        AiQuerySemanticParseResult parse =
                AiQuerySemanticParseResult.builder()
                        .semanticSlots(
                                AiQuerySemanticParseResult.SemanticSlotsPart.builder()
                                        .queryObject("GOODS")
                                        .operation("DETAIL")
                                        .detailWanted("SUPPLIER_BREAKDOWN")
                                        .anchorPolicy("USE_PREVIOUS_ANCHOR")
                                        .structuredIntentDetailWire(
                                                AiQuerySemanticLexicon.STRUCTURED_PURCHASE_SOURCE_GOODS_QUERY)
                                        .build())
                        .build();
        AiResolvedQueryContext ctx =
                AiResolvedQueryContext.builder()
                        .queryIntent(qi)
                        .querySemanticParse(parse)
                        .semanticContractValidation(
                                SemanticContractValidationDebug.builder()
                                        .matchedContractId("purchase.goods_anchor.supplier_breakdown")
                                        .build())
                        .previousTurn(
                                AiConversationTurnMemory.builder()
                                        .lastResultAnchors(
                                                List.of(
                                                        AiResultAnchor.builder()
                                                                .entityType(AiResultAnchor.ENTITY_TYPE_GOODS)
                                                                .entityName("海天5度白醋")
                                                                .build()))
                                        .build())
                        .build();
        AiRunState state = AiRunState.builder().resolvedQueryContext(ctx).purchaseAnswerPlan(pap).build();

        Map<String, Object> summary = AiHarnessResolvedContextSummarizer.summarize(ctx, 902L, state);
        assertEquals("54", summary.get("focusEntityId"));
    }
}
