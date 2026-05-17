package com.nongxinle.ai.platform;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.nongxinle.entity.GbAiConversationEntity;
import com.nongxinle.mapper.GbAiConversationMapper;
import com.nongxinle.mapper.GbAiMessageMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Date;

/**
 * POST /api/ai/runs 真实会话写入 {@code gb_ai_message} 并刷新会话指针；不落 Harness Replay。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiRunMessagePersistenceService {

    static final String ASSISTANT_FAILURE_FALLBACK = "本次回答生成失败，请稍后重试。";
    static final String ASSISTANT_CANCELLED_FALLBACK = "本次回答已取消。";

    private static final String ROLE_USER = "user";
    private static final String ROLE_ASSISTANT = "assistant";
    private static final String STATUS_COMPLETED = "COMPLETED";
    private static final String STATUS_FAILED = "FAILED";
    private static final String STATUS_CANCELLED = "CANCELLED";

    private final GbAiMessageMapper messageMapper;
    private final GbAiConversationMapper conversationMapper;

    public void persistUserMessageForRun(
            Long conversationId,
            Long userId,
            Integer messageType,
            long runId,
            String content) {
        if (conversationId == null || userId == null || content == null) {
            return;
        }
        Date now = new Date();
        int mt = messageType != null ? messageType : 0;
        try {
            messageMapper.upsertRunScopedMessage(
                    conversationId,
                    userId,
                    mt,
                    ROLE_USER,
                    content,
                    runId,
                    STATUS_COMPLETED,
                    now,
                    now);
        } catch (Exception e) {
            log.warn(
                    "[AiRunMessagePersistence] persistUserMessage failed conversationId={} runId={}: {}",
                    conversationId,
                    runId,
                    e.toString());
            throw e;
        }
    }

    /**
     * Assistant 行幂等写入后，始终通过 {@link GbAiMessageMapper#selectMessageIdByConversationRunAndRole}
     * 再查一次主键（insert / ON DUPLICATE KEY UPDATE 两种路径均不依赖 {@code useGeneratedKeys}）。
     */
    public Long persistAssistantMessageForRun(
            Long conversationId,
            Long userId,
            Integer messageType,
            long runId,
            String content,
            String status) {
        if (conversationId == null || userId == null) {
            return null;
        }
        Date now = new Date();
        int mt = messageType != null ? messageType : 0;
        String effectiveContent = content != null ? content : "";
        String effectiveStatus = normalizeAssistantStatus(status);
        try {
            messageMapper.upsertRunScopedMessage(
                    conversationId,
                    userId,
                    mt,
                    ROLE_ASSISTANT,
                    effectiveContent,
                    runId,
                    effectiveStatus,
                    now,
                    now);
        } catch (Exception e) {
            log.warn(
                    "[AiRunMessagePersistence] persistAssistantMessage failed conversationId={} runId={}: {}",
                    conversationId,
                    runId,
                    e.toString());
            throw e;
        }
        Long id = messageMapper.selectMessageIdByConversationRunAndRole(conversationId, runId, ROLE_ASSISTANT);
        if (id == null) {
            log.warn(
                    "[AiRunMessagePersistence] assistant row missing after upsert conversationId={} runId={}",
                    conversationId,
                    runId);
        }
        return id;
    }

    /** 仅在 assistant 写入成功后调用；{@code last_message_id} 始终指向本轮 assistant 行，不因 user 落库而改写。 */
    public void updateConversationAfterRunMessage(
            Long conversationId,
            long runId,
            Long assistantMessageId,
            String lastIntent,
            String lastPath) {
        if (conversationId == null || assistantMessageId == null) {
            return;
        }
        Date now = new Date();
        try {
            conversationMapper.update(
                    null,
                    new LambdaUpdateWrapper<GbAiConversationEntity>()
                            .eq(GbAiConversationEntity::getGbAiConversationId, conversationId)
                            .set(GbAiConversationEntity::getGbAiConversationLastRunId, runId)
                            .set(GbAiConversationEntity::getGbAiConversationLastMessageId, assistantMessageId)
                            .set(GbAiConversationEntity::getGbAiConversationLastIntent, lastIntent)
                            .set(GbAiConversationEntity::getGbAiConversationLastPath, lastPath)
                            .set(GbAiConversationEntity::getGbAiConversationUpdateTime, now));
        } catch (Exception e) {
            log.warn(
                    "[AiRunMessagePersistence] updateConversationPointers failed conversationId={} runId={}: {}",
                    conversationId,
                    runId,
                    e.toString());
            throw e;
        }
    }

    /** 供 Run 路径解析消息类型（含会话不存在时的兜底）。 */
    public Integer resolveConversationMessageType(Long conversationId) {
        if (conversationId == null) {
            return 0;
        }
        GbAiConversationEntity c = conversationMapper.selectById(conversationId);
        if (c == null || c.getGbAiConversationType() == null) {
            return 0;
        }
        return c.getGbAiConversationType();
    }

    private static String normalizeAssistantStatus(String raw) {
        if (raw == null || raw.isEmpty()) {
            return STATUS_COMPLETED;
        }
        switch (raw.trim().toUpperCase()) {
            case STATUS_FAILED:
                return STATUS_FAILED;
            case STATUS_CANCELLED:
                return STATUS_CANCELLED;
            case STATUS_COMPLETED:
            default:
                return STATUS_COMPLETED;
        }
    }
}
