package com.nongxinle.ai.storeannouncement;

import com.alibaba.fastjson2.JSON;
import com.nongxinle.ai.workspace.AiWorkspaceTextSupport;
import com.nongxinle.ai.workrecord.WorkRecordConstants;
import com.nongxinle.ai.workrecord.business.WorkRecordBusinessCardCardsJsonSupport;
import com.nongxinle.ai.workrecord.business.WorkRecordBusinessCardPayloadLocator;
import com.nongxinle.ai.workrecord.business.WorkRecordItemKey;
import com.nongxinle.entity.GbAiMessageEntity;
import com.nongxinle.entity.GbAiWorkNoteEntity;
import com.nongxinle.entity.GbAiWorkPinEntity;
import com.nongxinle.entity.GbWorkRecordEntity;
import com.nongxinle.mapper.GbAiMessageMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 从 WorkRecord / Pin 读取可信快照；不接受前端上传的 card JSON 或正文。
 */
@Component
@RequiredArgsConstructor
public class StoreAnnouncementSnapshotBuilder {

    private static final String ROLE_ASSISTANT = "assistant";

    private final GbAiMessageMapper messageMapper;
    private final WorkRecordBusinessCardPayloadLocator payloadLocator;

    public record WorkRecordSnapshot(
            String announcementType,
            String title,
            String textContent,
            String cardType,
            String cardSnapshotJson,
            String cardsSnapshotJson,
            String sourceItemKey,
            Long sourceConversationId,
            Long sourceMessageId,
            Long sourceRunId) {
    }

    public record PinSnapshot(
            String announcementType,
            String title,
            String textContent,
            String cardsSnapshotJson,
            Long sourceConversationId,
            Long sourceMessageId,
            Long sourceRunId) {
    }

    public record NoteSnapshot(
            String announcementType,
            String title,
            String textContent,
            String cardsSnapshotJson,
            Long sourceConversationId,
            Long sourceMessageId,
            Long sourceRunId) {
    }

    public WorkRecordSnapshot buildFromWorkRecord(GbWorkRecordEntity record, String titleOverride) {
        if (record == null) {
            throw new StoreAnnouncementException(
                    StoreAnnouncementErrors.SOURCE_NOT_FOUND, "work record not found");
        }
        if (WorkRecordConstants.ORIGIN_BUSINESS_CARD.equals(record.getGbWrOriginType())) {
            return buildBusinessCardFromWorkRecord(record, titleOverride);
        }
        return buildTextFromWorkRecord(record, titleOverride);
    }

    public PinSnapshot buildFromPin(GbAiWorkPinEntity pin, String titleOverride) {
        if (pin == null) {
            throw new StoreAnnouncementException(
                    StoreAnnouncementErrors.SOURCE_NOT_FOUND, "pin not found");
        }

        GbAiMessageEntity message = loadPinAssistantMessage(pin);
        String text = resolvePinText(message, pin);
        if (!StringUtils.hasText(text)) {
            throw new StoreAnnouncementException(
                    StoreAnnouncementErrors.CONTENT_EMPTY, "pin has no publishable text");
        }

        String cardsJson = null;
        if (message != null && StringUtils.hasText(message.getGbAiMessageCardsJson())) {
            WorkRecordBusinessCardCardsJsonSupport.parseCardsArray(message.getGbAiMessageCardsJson());
            cardsJson = message.getGbAiMessageCardsJson().trim();
        }

        String title = resolveTitle(titleOverride, pin.getGbAiWpTitle(), text);
        Long runId = pin.getGbAiWpRunId();
        if (message != null && message.getGbAiMessageRunId() != null) {
            runId = message.getGbAiMessageRunId();
        }

        return new PinSnapshot(
                StoreAnnouncementConstants.TYPE_AI_MESSAGE,
                title,
                text.trim(),
                cardsJson,
                pin.getGbAiWpConversationId(),
                pin.getGbAiWpMessageId(),
                runId);
    }

    public NoteSnapshot buildFromNote(GbAiWorkNoteEntity note, String titleOverride) {
        if (note == null) {
            throw new StoreAnnouncementException(
                    StoreAnnouncementErrors.SOURCE_NOT_FOUND, "note not found");
        }

        GbAiMessageEntity message = null;
        if (note.getGbAiWnPrimaryMessageId() != null) {
            message = loadNoteAssistantMessage(note);
        }

        String text = resolveNoteText(note, message);
        if (!StringUtils.hasText(text)) {
            throw new StoreAnnouncementException(
                    StoreAnnouncementErrors.CONTENT_EMPTY, "note has no publishable text");
        }

        String cardsJson = null;
        if (message != null && StringUtils.hasText(message.getGbAiMessageCardsJson())) {
            WorkRecordBusinessCardCardsJsonSupport.parseCardsArray(message.getGbAiMessageCardsJson());
            cardsJson = message.getGbAiMessageCardsJson().trim();
        }

        String title = resolveTitle(titleOverride, note.getGbAiWnTitle(), text);
        Long conversationId = resolveNoteConversationId(note);
        Long runId = note.getGbAiWnPrimaryRunId();
        if (message != null && message.getGbAiMessageRunId() != null) {
            runId = message.getGbAiMessageRunId();
        }

        String announcementType =
                note.getGbAiWnPrimaryMessageId() != null
                        ? StoreAnnouncementConstants.TYPE_AI_MESSAGE
                        : StoreAnnouncementConstants.TYPE_TEXT;

        return new NoteSnapshot(
                announcementType,
                title,
                text.trim(),
                cardsJson,
                conversationId,
                note.getGbAiWnPrimaryMessageId(),
                runId);
    }

    /**
     * 正文优先笔记 contentMd（用户可编辑）；其次 sourceTextSnapshot；message.content 仅作后备。
     */
    private static String resolveNoteText(GbAiWorkNoteEntity note, GbAiMessageEntity message) {
        if (StringUtils.hasText(note.getGbAiWnContentMd())) {
            String fromContent =
                    AiWorkspaceTextSupport.normalizeWhitespaceSnapshot(note.getGbAiWnContentMd());
            if (StringUtils.hasText(fromContent)) {
                return fromContent;
            }
        }
        if (StringUtils.hasText(note.getGbAiWnSourceTextSnapshot())) {
            String fromSnapshot =
                    AiWorkspaceTextSupport.normalizeWhitespaceSnapshot(note.getGbAiWnSourceTextSnapshot());
            if (StringUtils.hasText(fromSnapshot)) {
                return fromSnapshot;
            }
        }
        if (message != null && StringUtils.hasText(message.getGbAiMessageContent())) {
            return AiWorkspaceTextSupport.normalizeWhitespaceSnapshot(message.getGbAiMessageContent());
        }
        return null;
    }

    private GbAiMessageEntity loadNoteAssistantMessage(GbAiWorkNoteEntity note) {
        Long messageId = note.getGbAiWnPrimaryMessageId();
        if (messageId == null) {
            return null;
        }
        Long expectedConversationId = resolveNoteConversationId(note);
        GbAiMessageEntity message = messageMapper.selectById(messageId);
        if (message == null) {
            return null;
        }
        if (expectedConversationId != null
                && !Objects.equals(message.getGbAiMessageConversationId(), expectedConversationId)) {
            throw new StoreAnnouncementException(
                    StoreAnnouncementErrors.SOURCE_NOT_FOUND, "note message conversation mismatch");
        }
        if (!ROLE_ASSISTANT.equalsIgnoreCase(message.getGbAiMessageRole())) {
            throw new StoreAnnouncementException(
                    StoreAnnouncementErrors.SOURCE_NOT_FOUND, "note message must be assistant");
        }
        if (note.getGbAiWnPrimaryRunId() != null
                && message.getGbAiMessageRunId() != null
                && !Objects.equals(message.getGbAiMessageRunId(), note.getGbAiWnPrimaryRunId())) {
            throw new StoreAnnouncementException(
                    StoreAnnouncementErrors.SOURCE_NOT_FOUND, "note message runId mismatch");
        }
        return message;
    }

    private static Long resolveNoteConversationId(GbAiWorkNoteEntity note) {
        if (note.getGbAiWnPrimaryConversationId() != null) {
            return note.getGbAiWnPrimaryConversationId();
        }
        return note.getGbAiWnConversationId();
    }

    /**
     * 正文优先 assistant message.content；pin 快照仅在后端已落库且 message 缺失时使用。
     */
    private static String resolvePinText(GbAiMessageEntity message, GbAiWorkPinEntity pin) {
        if (message != null && StringUtils.hasText(message.getGbAiMessageContent())) {
            String fromMessage =
                    AiWorkspaceTextSupport.normalizeWhitespaceSnapshot(message.getGbAiMessageContent());
            if (StringUtils.hasText(fromMessage)) {
                return fromMessage;
            }
        }
        return firstNonBlank(
                pin.getGbAiWpSourceTextSnapshot(), pin.getGbAiWpSourceAnswerPreview());
    }

    private GbAiMessageEntity loadPinAssistantMessage(GbAiWorkPinEntity pin) {
        if (pin.getGbAiWpMessageId() == null) {
            return null;
        }
        GbAiMessageEntity message = messageMapper.selectById(pin.getGbAiWpMessageId());
        if (message == null) {
            return null;
        }
        if (!Objects.equals(message.getGbAiMessageConversationId(), pin.getGbAiWpConversationId())) {
            throw new StoreAnnouncementException(
                    StoreAnnouncementErrors.SOURCE_NOT_FOUND, "pin message conversation mismatch");
        }
        if (!ROLE_ASSISTANT.equalsIgnoreCase(message.getGbAiMessageRole())) {
            throw new StoreAnnouncementException(
                    StoreAnnouncementErrors.SOURCE_NOT_FOUND, "pin message must be assistant");
        }
        if (pin.getGbAiWpRunId() != null
                && message.getGbAiMessageRunId() != null
                && !Objects.equals(message.getGbAiMessageRunId(), pin.getGbAiWpRunId())) {
            throw new StoreAnnouncementException(
                    StoreAnnouncementErrors.SOURCE_NOT_FOUND, "pin message runId mismatch");
        }
        return message;
    }

    private WorkRecordSnapshot buildTextFromWorkRecord(GbWorkRecordEntity record, String titleOverride) {
        String text = firstNonBlank(record.getGbWrPolishedContent(), record.getGbWrRawContent());
        if (!StringUtils.hasText(text)) {
            throw new StoreAnnouncementException(
                    StoreAnnouncementErrors.CONTENT_EMPTY, "work record has no publishable text");
        }
        String title = resolveTitle(titleOverride, record.getGbWrCategoryNameSnapshot(), text);
        return new WorkRecordSnapshot(
                StoreAnnouncementConstants.TYPE_TEXT,
                title,
                text.trim(),
                null,
                null,
                null,
                null,
                record.getGbWrConversationId(),
                record.getGbWrSourceMessageId(),
                record.getGbWrSourceRunId());
    }

    private WorkRecordSnapshot buildBusinessCardFromWorkRecord(
            GbWorkRecordEntity record, String titleOverride) {
        requireBizFields(record);
        WorkRecordItemKey itemKey = parseItemKey(record.getGbWrBizItemKey());

        GbAiMessageEntity message = messageMapper.selectById(record.getGbWrBizMessageId());
        if (message == null) {
            throw new StoreAnnouncementException(
                    StoreAnnouncementErrors.SOURCE_CARD_NOT_FOUND, "business source message not found");
        }
        if (!Objects.equals(message.getGbAiMessageConversationId(), record.getGbWrBizConversationId())) {
            throw new StoreAnnouncementException(
                    StoreAnnouncementErrors.SOURCE_CARD_NOT_FOUND,
                    "business source message conversation mismatch");
        }
        if (!ROLE_ASSISTANT.equalsIgnoreCase(message.getGbAiMessageRole())) {
            throw new StoreAnnouncementException(
                    StoreAnnouncementErrors.SOURCE_CARD_NOT_FOUND, "business source message must be assistant");
        }
        if (message.getGbAiMessageRunId() == null
                || !Objects.equals(message.getGbAiMessageRunId(), record.getGbWrBizRunId())) {
            throw new StoreAnnouncementException(
                    StoreAnnouncementErrors.SOURCE_CARD_NOT_FOUND, "business source runId mismatch");
        }

        List<Map<String, Object>> cards = parseCards(message.getGbAiMessageCardsJson());
        Map<String, Object> card = requireCard(cards, record.getGbWrBizCardType());
        validateItemKeyOnCard(record.getGbWrBizCardType(), card, itemKey);

        String cardJson = JSON.toJSONString(card);
        String cardTitle = stringField(card, "title");
        String title = resolveTitle(titleOverride, cardTitle, record.getGbWrPolishedContent());

        return new WorkRecordSnapshot(
                StoreAnnouncementConstants.TYPE_BUSINESS_CARD,
                title,
                null,
                record.getGbWrBizCardType(),
                cardJson,
                null,
                itemKey.raw(),
                record.getGbWrBizConversationId(),
                record.getGbWrBizMessageId(),
                record.getGbWrBizRunId());
    }

    private void validateItemKeyOnCard(
            String cardType, Map<String, Object> card, WorkRecordItemKey itemKey) {
        try {
            payloadLocator.resolvePayload(cardType, card, itemKey);
        } catch (com.nongxinle.ai.workrecord.WorkRecordSourceCardException ex) {
            throw new StoreAnnouncementException(
                    StoreAnnouncementErrors.SOURCE_CARD_NOT_FOUND, ex.getMessage());
        }
    }

    private static WorkRecordItemKey parseItemKey(String raw) {
        try {
            return WorkRecordItemKey.parse(raw);
        } catch (com.nongxinle.ai.workrecord.WorkRecordBusinessCardException ex) {
            throw new StoreAnnouncementException(
                    StoreAnnouncementErrors.SOURCE_CARD_NOT_FOUND, ex.getMessage());
        }
    }

    private static void requireBizFields(GbWorkRecordEntity record) {
        if (record.getGbWrBizConversationId() == null
                || record.getGbWrBizMessageId() == null
                || record.getGbWrBizRunId() == null
                || !StringUtils.hasText(record.getGbWrBizCardType())
                || !StringUtils.hasText(record.getGbWrBizItemKey())) {
            throw new StoreAnnouncementException(
                    StoreAnnouncementErrors.SOURCE_CARD_NOT_FOUND,
                    "work record missing business source fields (message/run/cardType/itemKey)");
        }
    }

    private static List<Map<String, Object>> parseCards(String cardsJson) {
        try {
            return WorkRecordBusinessCardCardsJsonSupport.parseCardsArray(cardsJson);
        } catch (com.nongxinle.ai.workrecord.WorkRecordBusinessCardException ex) {
            throw new StoreAnnouncementException(
                    StoreAnnouncementErrors.SOURCE_CARD_NOT_FOUND, ex.getMessage());
        }
    }

    private static Map<String, Object> requireCard(List<Map<String, Object>> cards, String cardType) {
        try {
            return WorkRecordBusinessCardCardsJsonSupport.requireCard(cards, cardType);
        } catch (com.nongxinle.ai.workrecord.WorkRecordBusinessCardException ex) {
            throw new StoreAnnouncementException(
                    StoreAnnouncementErrors.SOURCE_CARD_NOT_FOUND, ex.getMessage());
        }
    }

    private static String resolveTitle(String override, String primary, String fallbackText) {
        if (StringUtils.hasText(override)) {
            return override.trim();
        }
        if (StringUtils.hasText(primary)) {
            return primary.trim();
        }
        return truncateTitle(fallbackText);
    }

    private static String truncateTitle(String text) {
        if (!StringUtils.hasText(text)) {
            return "店内公告";
        }
        String normalized = text.trim().replaceAll("\\s+", " ");
        if (normalized.length() <= 80) {
            return normalized;
        }
        return normalized.substring(0, 77) + "...";
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String v : values) {
            if (StringUtils.hasText(v)) {
                return v;
            }
        }
        return null;
    }

    private static String stringField(Map<String, Object> card, String key) {
        Object v = card.get(key);
        return v == null ? null : v.toString();
    }
}
