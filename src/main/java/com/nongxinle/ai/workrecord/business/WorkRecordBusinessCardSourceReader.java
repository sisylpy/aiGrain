package com.nongxinle.ai.workrecord.business;

import com.nongxinle.ai.conversation.AiConversationCoreService;
import com.nongxinle.ai.workrecord.WorkRecordConstants;
import com.nongxinle.ai.workrecord.WorkRecordOwnership;
import com.nongxinle.ai.workrecord.WorkRecordScopeGuard;
import com.nongxinle.ai.workrecord.WorkRecordSourceCardErrors;
import com.nongxinle.ai.workrecord.WorkRecordSourceCardException;
import com.nongxinle.ai.workrecord.dto.WorkRecordSourceCardResponse;
import com.nongxinle.entity.GbAiMessageEntity;
import com.nongxinle.entity.GbWorkRecordEntity;
import com.nongxinle.mapper.GbAiMessageMapper;
import com.nongxinle.mapper.GbWorkRecordMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.Objects;

@Component
@RequiredArgsConstructor
public class WorkRecordBusinessCardSourceReader {

    private static final String ROLE_ASSISTANT = "assistant";

    private final GbWorkRecordMapper workRecordMapper;
    private final GbAiMessageMapper messageMapper;
    private final WorkRecordScopeGuard scopeGuard;
    private final AiConversationCoreService conversationCoreService;
    private final WorkRecordBusinessCardPayloadLocator payloadLocator;

    public WorkRecordSourceCardResponse read(Long recordId, Long userId) {
        if (recordId == null || userId == null) {
            throw new WorkRecordSourceCardException(
                    WorkRecordSourceCardErrors.WORK_RECORD_NOT_FOUND, "recordId and userId required");
        }

        GbWorkRecordEntity record = workRecordMapper.selectById(recordId);
        if (record == null || (record.getGbWrStatus() != null && record.getGbWrStatus() == 1)) {
            throw new WorkRecordSourceCardException(
                    WorkRecordSourceCardErrors.WORK_RECORD_NOT_FOUND, "work record not found: " + recordId);
        }

        WorkRecordScopeGuard.ResolvedScope scope =
                scopeGuard.resolveAndValidate(
                        userId, record.getGbWrDepartmentId(), record.getGbWrDistributerId());
        try {
            WorkRecordOwnership.assertOwnedRecord(record, userId, scope);
        } catch (IllegalArgumentException ex) {
            throw new WorkRecordSourceCardException(
                    WorkRecordSourceCardErrors.WORK_RECORD_NOT_FOUND, ex.getMessage());
        }

        if (!WorkRecordConstants.ORIGIN_BUSINESS_CARD.equals(record.getGbWrOriginType())) {
            throw new WorkRecordSourceCardException(
                    WorkRecordSourceCardErrors.NOT_BUSINESS_CARD,
                    "work record is not from business card");
        }
        requireBizFields(record);

        try {
            conversationCoreService.requireConversationOwnedByUser(
                    record.getGbWrBizConversationId(), userId);
        } catch (IllegalArgumentException ex) {
            throw new WorkRecordSourceCardException(
                    WorkRecordSourceCardErrors.BUSINESS_CARD_NOT_FOUND, ex.getMessage());
        }

        GbAiMessageEntity message = messageMapper.selectById(record.getGbWrBizMessageId());
        if (message == null
                || !Objects.equals(message.getGbAiMessageConversationId(), record.getGbWrBizConversationId())
                || !ROLE_ASSISTANT.equalsIgnoreCase(message.getGbAiMessageRole())
                || message.getGbAiMessageRunId() == null
                || !Objects.equals(message.getGbAiMessageRunId(), record.getGbWrBizRunId())) {
            throw new WorkRecordSourceCardException(
                    WorkRecordSourceCardErrors.BUSINESS_CARD_NOT_FOUND,
                    "business source message not found or mismatched");
        }

        WorkRecordItemKey itemKey = parseItemKey(record.getGbWrBizItemKey());
        List<Map<String, Object>> cards =
                parseCards(message.getGbAiMessageCardsJson());
        Map<String, Object> card = requireCard(cards, record.getGbWrBizCardType());
        Map<String, Object> cardPayload = WorkRecordBusinessCardCardsJsonSupport.payload(card);
        Map<String, Object> payload =
                payloadLocator.resolvePayload(record.getGbWrBizCardType(), card, itemKey);

        String scopeLabel =
                WorkRecordBusinessCardFactTextSupport.firstNonBlank(cardPayload, "scopeLabel");
        String answerPlanType =
                WorkRecordBusinessCardCardsJsonSupport.resolveAnswerPlanType(card, cardPayload);
        if (!StringUtils.hasText(answerPlanType)) {
            answerPlanType = record.getGbWrBizAnswerPlanType();
        }

        return WorkRecordSourceCardResponse.builder()
                .recordId(record.getGbWrId())
                .conversationId(record.getGbWrBizConversationId())
                .messageId(record.getGbWrBizMessageId())
                .runId(record.getGbWrBizRunId())
                .cardType(record.getGbWrBizCardType())
                .itemKey(record.getGbWrBizItemKey())
                .payload(payload)
                .rawFactText(record.getGbWrRawContent())
                .timestamp(message.getGbAiMessageCreateTime())
                .scopeLabel(scopeLabel)
                .sourceAnswerPlanType(answerPlanType)
                .cardTitle(stringField(card, "title"))
                .cardSubtitle(stringField(card, "subtitle"))
                .chartType(stringField(card, "chartType"))
                .build();
    }

    private static void requireBizFields(GbWorkRecordEntity record) {
        if (record.getGbWrBizConversationId() == null
                || record.getGbWrBizMessageId() == null
                || record.getGbWrBizRunId() == null
                || !StringUtils.hasText(record.getGbWrBizCardType())
                || !StringUtils.hasText(record.getGbWrBizItemKey())) {
            throw new WorkRecordSourceCardException(
                    WorkRecordSourceCardErrors.BUSINESS_CARD_NOT_FOUND,
                    "work record missing business source fields");
        }
    }

    private static WorkRecordItemKey parseItemKey(String raw) {
        try {
            return WorkRecordItemKey.parse(raw);
        } catch (com.nongxinle.ai.workrecord.WorkRecordBusinessCardException ex) {
            throw new WorkRecordSourceCardException(
                    WorkRecordSourceCardErrors.BUSINESS_CARD_NOT_FOUND, ex.getMessage());
        }
    }

    private static List<Map<String, Object>> parseCards(String cardsJson) {
        try {
            return WorkRecordBusinessCardCardsJsonSupport.parseCardsArray(cardsJson);
        } catch (com.nongxinle.ai.workrecord.WorkRecordBusinessCardException ex) {
            throw new WorkRecordSourceCardException(
                    WorkRecordSourceCardErrors.BUSINESS_CARD_NOT_FOUND, ex.getMessage());
        }
    }

    private static Map<String, Object> requireCard(List<Map<String, Object>> cards, String cardType) {
        try {
            return WorkRecordBusinessCardCardsJsonSupport.requireCard(cards, cardType);
        } catch (com.nongxinle.ai.workrecord.WorkRecordBusinessCardException ex) {
            throw new WorkRecordSourceCardException(
                    WorkRecordSourceCardErrors.BUSINESS_CARD_NOT_FOUND, ex.getMessage());
        }
    }

    private static String stringField(Map<String, Object> card, String key) {
        Object v = card.get(key);
        return v == null ? null : v.toString();
    }
}
