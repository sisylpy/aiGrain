package com.nongxinle.ai.conversation;

import com.nongxinle.entity.GbAiConversationTurnMemoryEntity;
import com.nongxinle.mapper.GbAiConversationTurnMemoryMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;

/**
 * 会话级上一轮查询语义：生产环境以 MySQL 为准（同 conversation 内短期记忆），同 JVM 内存作热路径缓存。
 * **多轮追问必须以 userId + conversationId 为键**。
 * <p>
 * 仅当 {@code conversationId == null} 时使用按 userId 的弱兜底（单机联调），
 * **多窗口并行会话时须每轮回传 conversationId**。
 */
@Slf4j
@Service
public class AiConversationMemoryService {

    private final ConcurrentHashMap<String, AiConversationTurnMemory> lastTurnByUserConversation = new ConcurrentHashMap<>();
    /** 无 conversationId 时的弱兜底：同 JVM 内该用户最近一次完成轮次 */
    private final ConcurrentHashMap<Long, AiConversationTurnMemory> lastTurnByUserFallback = new ConcurrentHashMap<>();

    private final GbAiConversationTurnMemoryMapper turnMemoryMapper;

    public AiConversationMemoryService(
            @Autowired(required = false) GbAiConversationTurnMemoryMapper turnMemoryMapper) {
        this.turnMemoryMapper = turnMemoryMapper;
    }

    /**
     * @param conversationId 非空时先读库最新一条，若无表或无行则回退进程内缓存；**不**按 userId 串其它会话。
     */
    public AiConversationTurnMemory load(Long userId, Long conversationId) {
        if (userId == null) {
            return null;
        }
        if (conversationId != null) {
            AiConversationTurnMemory fromDb = loadFromPersistence(userId, conversationId);
            if (fromDb != null) {
                log.info("[AiConversationMemory] load userId={} conversationId={} source=PERSISTENCE path={}",
                        userId, conversationId, fromDb.getLastPathCode());
                lastTurnByUserConversation.put(key(userId, conversationId), fromDb);
                return fromDb;
            }
            AiConversationTurnMemory cached = lastTurnByUserConversation.get(key(userId, conversationId));
            if (cached != null) {
                log.info("[AiConversationMemory] load userId={} conversationId={} source=MEMORY path={}",
                        userId, conversationId, cached.getLastPathCode());
                return cached;
            }
            log.info("[AiConversationMemory] load userId={} conversationId={} source=NONE",
                    userId, conversationId);
            return null;
        }
        AiConversationTurnMemory fb = lastTurnByUserFallback.get(userId);
        log.warn("[AiConversationMemory] load userId={} conversationId=null source=FALLBACK hit={} path={} — "
                        + "production must send conversationId on every POST /api/ai/runs.",
                userId, fb != null, fb != null ? fb.getLastPathCode() : null);
        return fb;
    }

    private AiConversationTurnMemory loadFromPersistence(Long userId, Long conversationId) {
        if (turnMemoryMapper == null) {
            return null;
        }
        try {
            GbAiConversationTurnMemoryEntity row = turnMemoryMapper.selectLatestByConversationAndUser(
                    conversationId, userId);
            if (row != null) {
                return AiConversationTurnMemoryEntities.fromEntity(row);
            }
        } catch (Exception ex) {
            log.warn("[AiConversationMemory] load persistence failed userId={} conversationId={}: {}",
                    userId, conversationId, ex.toString());
        }
        return null;
    }

    public void rememberCompletedTurn(Long userId, Long conversationId, AiConversationTurnMemory turn) {
        if (userId == null || turn == null) {
            if (log.isDebugEnabled()) {
                log.debug("[AiConversationMemory] remember skip: userId={} conversationId={} turnNull={}",
                        userId, conversationId, turn == null);
            }
            return;
        }
        turn.setConversationId(conversationId);
        if (conversationId != null) {
            lastTurnByUserConversation.put(key(userId, conversationId), turn);
            persistTurn(userId, turn);
        } else {
            log.warn("[AiConversationMemory] remember userId={} conversationId=null path={} — stored only in weak "
                            + "userId fallback + no DB row; production clients should send conversationId.",
                    userId, turn.getLastPathCode());
            lastTurnByUserFallback.put(userId, turn);
        }
        log.info("[AiConversationMemory] remember userId={} conversationId={} path={} intent={} toolSummarySnippet={}",
                userId,
                conversationId,
                turn.getLastPathCode(),
                turn.getLastIntentCode(),
                turn.getLastToolSummary() == null ? null
                        : (turn.getLastToolSummary().length() > 120
                        ? turn.getLastToolSummary().substring(0, 120) + "…"
                        : turn.getLastToolSummary()));
    }

    private void persistTurn(Long userId, AiConversationTurnMemory turn) {
        if (turnMemoryMapper == null) {
            return;
        }
        try {
            GbAiConversationTurnMemoryEntity entity = AiConversationTurnMemoryEntities.toEntity(userId, turn);
            if (entity == null || entity.getGbAiConversationId() == null) {
                return;
            }
            turnMemoryMapper.insert(entity);
        } catch (Exception ex) {
            log.warn("[AiConversationMemory] persist failed userId={} conversationId={} runId={}: {}",
                    userId, turn.getConversationId(), turn.getPreviousRunId(), ex.toString());
        }
    }

    /**
     * @see #rememberCompletedTurn
     */
    public void saveTurn(Long userId, Long conversationId, AiConversationTurnMemory turn) {
        rememberCompletedTurn(userId, conversationId, turn);
    }

    /**
     * @see #load
     */
    public AiConversationTurnMemory loadLastTurn(Long userId, Long conversationId) {
        return load(userId, conversationId);
    }

    private static String key(Long userId, Long conversationId) {
        return userId + ":" + conversationId;
    }
}
