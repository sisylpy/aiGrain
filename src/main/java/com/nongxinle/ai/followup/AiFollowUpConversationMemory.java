package com.nongxinle.ai.followup;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;

/**
 * 进程内上一轮追问快照。**conversationId 非空时**仅按 {@code userId:conversationId} 读写，不混入 userId 兜底。
 */
@Slf4j
@Service
public class AiFollowUpConversationMemory {

    private final ConcurrentHashMap<String, AiFollowUpIntentSnapshot> lastByConv = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, AiFollowUpIntentSnapshot> lastByUserFallback = new ConcurrentHashMap<>();

    private static String key(Long userId, Long conversationId) {
        return String.valueOf(userId) + ":" + conversationId;
    }

    public void remember(Long userId, Long conversationId, AiFollowUpIntentSnapshot snap) {
        if (userId == null || snap == null) {
            return;
        }
        if (conversationId != null) {
            lastByConv.put(key(userId, conversationId), snap);
        } else {
            lastByUserFallback.put(userId, snap);
        }
    }

    public AiFollowUpIntentSnapshot load(Long userId, Long conversationId) {
        if (userId == null) {
            return null;
        }
        if (conversationId != null) {
            return lastByConv.get(key(userId, conversationId));
        }
        return lastByUserFallback.get(userId);
    }
}
