package com.nongxinle.ai.harness;

import com.nongxinle.ai.context.AiResolvedQueryContext;
import com.nongxinle.ai.context.AiResolvedQueryIntent;
import com.nongxinle.ai.conversation.AiQuerySemanticLexicon;
import com.nongxinle.ai.core.AiRunState;
import com.nongxinle.ai.dto.business.AiResultAnchor;
import com.nongxinle.ai.dto.business.PurchaseAnswerPlan;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiHarnessResolvedContextSummarizerPurchaseDrilldownTest {

    @Test
    void summarize_mirrorsPurchaseSupplierGoodsDetail_toSummaryTop() {
        LinkedHashMap<String, Object> dbg = new LinkedHashMap<>();
        dbg.put("purchaseSupplierGoodsDetailRowsCount", 2);
        dbg.put("purchaseSupplierGoodsDetailNoDataReason", null);
        dbg.put("purchaseSupplierGoodsDetailAlternativeHasData", Boolean.FALSE);
        dbg.put("purchaseGoodsDrilldownTargetGoodsName", "海天5度白醋");
        dbg.put("purchaseGoodsDrilldownTargetGoodsId", 9001);

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
        assertEquals("海天5度白醋", summary.get("purchaseGoodsDrilldownTargetGoodsName"));
        assertEquals(9001, summary.get("purchaseGoodsDrilldownTargetGoodsId"));

        String preview = (String) summary.get("answerPreview");
        assertNotNull(preview);
        assertTrue(preview.contains("海天5度白醋"));
        assertTrue(preview.contains("rows=2"));
        assertFalse(preview.contains("reason="));
    }

    @Test
    void summarize_backfillsFollowUpTargetEntityId_fromPurchaseGoodsDrilldownTargetGoodsId() {
        LinkedHashMap<String, Object> dbg = new LinkedHashMap<>();
        dbg.put("purchaseGoodsDrilldownTargetGoodsId", 54);
        dbg.put("purchaseGoodsDrilldownTargetGoodsName", "海天5度白醋");

        PurchaseAnswerPlan pap = PurchaseAnswerPlan.builder()
                .planType(PurchaseAnswerPlan.TYPE_PURCHASE_SUPPLIER_GOODS_DETAIL)
                .debug(dbg)
                .build();

        AiResolvedQueryIntent qi =
                AiResolvedQueryIntent.builder()
                        .structuredIntentDetail(AiQuerySemanticLexicon.STRUCTURED_PURCHASE_SOURCE_GOODS_QUERY)
                        .purchaseSourceType(AiQuerySemanticLexicon.SOURCE_SUPPLIER_PURCHASE)
                        .build();
        AiResolvedQueryContext ctx =
                AiResolvedQueryContext.builder()
                        .queryIntent(qi)
                        .followUpTargetEntityType(AiResultAnchor.ENTITY_TYPE_GOODS)
                        .followUpTargetEntityName("海天5度白醋")
                        .followUpDetailWanted("SUPPLIER_BREAKDOWN")
                        .build();
        AiRunState state = AiRunState.builder().resolvedQueryContext(ctx).purchaseAnswerPlan(pap).build();

        Map<String, Object> summary = AiHarnessResolvedContextSummarizer.summarize(ctx, 902L, state);
        assertEquals("54", summary.get("followUpTargetEntityId"));
    }
}
