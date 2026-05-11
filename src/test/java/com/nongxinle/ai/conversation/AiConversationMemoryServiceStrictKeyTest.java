package com.nongxinle.ai.conversation;

import com.nongxinle.ai.context.AiResolvedQueryIntent;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class AiConversationMemoryServiceStrictKeyTest {

    @Test
    void loadWithConversationId_doesNotFallBackToOtherConversation() {
        AiConversationMemoryService svc = new AiConversationMemoryService(null);
        long uid = 90001L;
        AiConversationTurnMemory purchase = AiConversationTurnMemory.builder()
                .lastPathCode(AiResolvedQueryIntent.PATH_PURCHASE_OVERVIEW)
                .lastIntentCode(AiResolvedQueryIntent.PURCHASE_OVERVIEW)
                .build();
        AiConversationTurnMemory stock = AiConversationTurnMemory.builder()
                .lastPathCode(AiResolvedQueryIntent.PATH_WAREHOUSE_STOCK)
                .lastIntentCode(AiResolvedQueryIntent.WAREHOUSE_STOCK_OVERVIEW)
                .build();

        svc.rememberCompletedTurn(uid, 501L, purchase);
        svc.rememberCompletedTurn(uid, 502L, stock);

        AiConversationTurnMemory a = svc.load(uid, 501L);
        AiConversationTurnMemory b = svc.load(uid, 502L);
        assertNotNull(a);
        assertNotNull(b);
        assertEquals(AiResolvedQueryIntent.PATH_PURCHASE_OVERVIEW, a.getLastPathCode());
        assertEquals(AiResolvedQueryIntent.PATH_WAREHOUSE_STOCK, b.getLastPathCode());

        assertNull(svc.load(uid, 999L), "unknown conversation must not read sibling session");
    }
}
