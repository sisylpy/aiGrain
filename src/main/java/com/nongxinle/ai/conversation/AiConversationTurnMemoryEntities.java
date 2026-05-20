package com.nongxinle.ai.conversation;

import com.alibaba.fastjson2.JSON;
import com.nongxinle.ai.dto.business.AiResultAnchor;
import com.nongxinle.ai.semantic.AiQuerySemanticParseResult;
import com.nongxinle.entity.GbAiConversationTurnMemoryEntity;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.util.Date;
import java.util.List;

/**
 * {@link AiConversationTurnMemory} 与持久化实体互转。
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class AiConversationTurnMemoryEntities {

    public static GbAiConversationTurnMemoryEntity toEntity(Long userId, AiConversationTurnMemory m) {
        if (m == null || userId == null) {
            return null;
        }
        GbAiConversationTurnMemoryEntity e = new GbAiConversationTurnMemoryEntity();
        e.setGbAiConversationId(m.getConversationId());
        e.setGbAiCtmUserId(userId);
        e.setGbAiCtmRunId(m.getPreviousRunId() != null ? m.getPreviousRunId() : 0L);
        e.setGbAiCtmIntentCode(trimToNull(m.getLastIntentCode()));
        e.setGbAiCtmPathCode(trimToNull(m.getLastPathCode()));
        e.setGbAiCtmStructuredIntentDetail(trimToNull(m.getLastStructuredIntentDetail()));
        e.setGbAiCtmPurchaseSourceType(trimToNull(m.getLastPurchaseSourceType()));
        e.setGbAiCtmStartDate(trimToNull(m.getLastStartDate()));
        e.setGbAiCtmEndDate(trimToNull(m.getLastEndDate()));
        e.setGbAiCtmTimeLabel(trimToNull(m.getLastTimeLabel()));
        e.setGbAiCtmScopeType(trimToNull(m.getLastScopeType()));
        e.setGbAiCtmVisibleStoreIds(visibleStoresJson(m.getLastVisibleStoreIds()));
        Long fs = m.getLastFocusedStoreId();
        e.setGbAiCtmFocusedStoreId(fs);
        e.setGbAiCtmFocusedStoreName(trimToNull(m.getLastFocusedStoreName()));
        e.setGbAiCtmMentionedStore(trimToNull(m.getLastMentionedStore()));
        e.setGbAiCtmMentionedDishName(trimToNull(m.getLastMentionedDishName()));
        e.setGbAiCtmFocusType(trimToNull(m.getLastFocusType()));
        e.setGbAiCtmFocusName(trimToNull(m.getLastFocusName()));
        e.setGbAiCtmEffectiveScopeSource(trimToNull(m.getLastEffectiveScopeSource()));
        e.setGbAiCtmEffectiveQuestion(trimToNull(m.getLastEffectiveQuestion()));
        e.setGbAiCtmAnswerSummary(trimToNull(m.getLastAnswerSummary()));
        String ts = trimToNull(m.getLastToolSummary());
        if (m.getLastHarnessMultiStoreMatchedStores() != null
                && !m.getLastHarnessMultiStoreMatchedStores().isEmpty()) {
            ts = trimToNull(AiConversationTurnMemory.embedHarnessMultiStoreInToolSummary(ts, m.getLastHarnessMultiStoreMatchedStores()));
        }
        if (m.getLastResultAnchors() != null && !m.getLastResultAnchors().isEmpty()) {
            ts = trimToNull(AiConversationTurnMemory.embedResultAnchorsInToolSummary(ts, m.getLastResultAnchors()));
        }
        if (m.getLastSemanticSlots() != null) {
            ts = trimToNull(AiConversationTurnMemory.embedSemanticSlotsInToolSummary(ts, m.getLastSemanticSlots()));
        }
        if (ts != null && ts.length() > 1900) {
            ts = ts.substring(0, 1900) + "…";
        }
        e.setGbAiCtmToolSummary(ts);
        e.setGbAiCtmCreateTime(new Date());
        return e;
    }

    public static AiConversationTurnMemory fromEntity(GbAiConversationTurnMemoryEntity e) {
        if (e == null) {
            return null;
        }
        List<Integer> storeIds = null;
        String json = trimToNull(e.getGbAiCtmVisibleStoreIds());
        if (json != null) {
            try {
                storeIds = JSON.parseArray(json, Integer.class);
            } catch (Exception ignore) {
                storeIds = List.of();
            }
        }
        String rawTs = trimToNull(e.getGbAiCtmToolSummary());
        List<String> harnessMs = AiConversationTurnMemory.readHarnessMultiStoreFromToolSummary(rawTs);
        List<AiResultAnchor> ra =
                AiConversationTurnMemory.readResultAnchorsFromToolSummary(rawTs);
        AiQuerySemanticParseResult.SemanticSlotsPart ss =
                AiConversationTurnMemory.readSemanticSlotsFromToolSummary(rawTs);
        return AiConversationTurnMemory.builder()
                .conversationId(e.getGbAiConversationId())
                .previousRunId(e.getGbAiCtmRunId())
                .lastIntentCode(trimToNull(e.getGbAiCtmIntentCode()))
                .lastPathCode(trimToNull(e.getGbAiCtmPathCode()))
                .lastStructuredIntentDetail(trimToNull(e.getGbAiCtmStructuredIntentDetail()))
                .lastPurchaseSourceType(trimToNull(e.getGbAiCtmPurchaseSourceType()))
                .lastStartDate(trimToNull(e.getGbAiCtmStartDate()))
                .lastEndDate(trimToNull(e.getGbAiCtmEndDate()))
                .lastTimeLabel(trimToNull(e.getGbAiCtmTimeLabel()))
                .lastScopeType(trimToNull(e.getGbAiCtmScopeType()))
                .lastVisibleStoreIds(storeIds)
                .lastFocusedStoreId(e.getGbAiCtmFocusedStoreId())
                .lastFocusedStoreName(trimToNull(e.getGbAiCtmFocusedStoreName()))
                .lastMentionedStore(trimToNull(e.getGbAiCtmMentionedStore()))
                .lastMentionedDishName(trimToNull(e.getGbAiCtmMentionedDishName()))
                .lastFocusType(trimToNull(e.getGbAiCtmFocusType()))
                .lastFocusName(trimToNull(e.getGbAiCtmFocusName()))
                .lastEffectiveScopeSource(trimToNull(e.getGbAiCtmEffectiveScopeSource()))
                .lastEffectiveQuestion(trimToNull(e.getGbAiCtmEffectiveQuestion()))
                .lastAnswerSummary(trimToNull(e.getGbAiCtmAnswerSummary()))
                .lastToolSummary(rawTs)
                .lastHarnessMultiStoreMatchedStores(
                        harnessMs == null || harnessMs.isEmpty() ? null : harnessMs)
                .lastResultAnchors(ra == null || ra.isEmpty() ? null : ra)
                .lastSemanticSlots(ss)
                .build();
    }

    private static String visibleStoresJson(List<Integer> ids) {
        if (ids == null || ids.isEmpty()) {
            return null;
        }
        return JSON.toJSONString(ids);
    }

    private static String trimToNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
