package com.nongxinle.ai.graph.business;

import com.nongxinle.ai.conversation.AiQuerySemanticLexicon;
import com.nongxinle.ai.dto.business.PurchaseAnswerPlan;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class PurchaseGoodsDetailCardSupportTest {

    @Test
    void buildCard_titleReflectsPurchaseSourceType() {
        assertEquals(
                "昨天·原料采购",
                cardTitle(PurchaseAnswerPlan.builder()
                        .planType(PurchaseAnswerPlan.TYPE_PURCHASE_PERIOD_GOODS_DETAIL)
                        .timeLabel("昨天")
                        .purchaseSourceType(AiQuerySemanticLexicon.SOURCE_ALL)
                        .focusRows(List.of(Map.of("goodsName", "白菜")))
                        .build()));
        assertEquals(
                "昨天·自采商品",
                cardTitle(PurchaseAnswerPlan.builder()
                        .planType(PurchaseAnswerPlan.TYPE_PURCHASE_PERIOD_GOODS_DETAIL)
                        .timeLabel("昨天")
                        .purchaseSourceType(AiQuerySemanticLexicon.SOURCE_SELF_PURCHASE)
                        .focusRows(List.of(Map.of("goodsName", "白菜")))
                        .build()));
        assertEquals(
                "昨天·供货商订货",
                cardTitle(PurchaseAnswerPlan.builder()
                        .planType(PurchaseAnswerPlan.TYPE_PURCHASE_PERIOD_GOODS_DETAIL)
                        .timeLabel("昨天")
                        .purchaseSourceType(AiQuerySemanticLexicon.SOURCE_SUPPLIER_PURCHASE)
                        .focusRows(List.of(Map.of("goodsName", "白菜")))
                        .build()));
    }

    private static String cardTitle(PurchaseAnswerPlan plan) {
        Map<String, Object> card = PurchaseGoodsDetailCardSupport.buildCard(plan);
        assertNotNull(card);
        return card.get("title").toString();
    }
}
