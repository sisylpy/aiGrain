package com.nongxinle.ai.workrecord.business;

import com.nongxinle.ai.conversation.AiConversationCoreService;
import com.nongxinle.ai.workrecord.WorkRecordBusinessCardErrors;
import com.nongxinle.ai.workrecord.WorkRecordBusinessCardException;
import com.nongxinle.ai.workrecord.WorkRecordScopeGuard;
import com.nongxinle.ai.workrecord.dto.WorkRecordFromBusinessCardRequest;
import com.nongxinle.entity.GbAiConversationEntity;
import com.nongxinle.entity.GbAiMessageEntity;
import com.nongxinle.mapper.GbAiMessageMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.Objects;

@Component
@RequiredArgsConstructor
public class WorkRecordBusinessCardSourceLoader {

    private static final String ROLE_ASSISTANT = "assistant";

    private final AiConversationCoreService conversationCoreService;
    private final GbAiMessageMapper messageMapper;
    private final WorkRecordScopeGuard scopeGuard;

    public record LoadedBusinessSource(
            WorkRecordScopeGuard.ResolvedScope scope,
            GbAiConversationEntity bizConversation,
            GbAiMessageEntity assistantMessage,
            List<Map<String, Object>> cards) {
    }

    public LoadedBusinessSource load(WorkRecordFromBusinessCardRequest request) {
        validateRequest(request);

        WorkRecordScopeGuard.ResolvedScope scope =
                scopeGuard.resolveAndValidate(
                        request.getUserId(), request.getDepartmentId(), request.getDistributerId());

        GbAiConversationEntity bizConv;
        try {
            bizConv =
                    conversationCoreService.requireConversationOwnedByUser(
                            request.getSourceConversationId(), request.getUserId());
        } catch (IllegalArgumentException ex) {
            throw new WorkRecordBusinessCardException(
                    WorkRecordBusinessCardErrors.CONVERSATION_NOT_OWNED, ex.getMessage());
        }

        GbAiMessageEntity message = messageMapper.selectById(request.getSourceMessageId());
        if (message == null) {
            throw new WorkRecordBusinessCardException(
                    WorkRecordBusinessCardErrors.MESSAGE_NOT_FOUND,
                    "source message not found: " + request.getSourceMessageId());
        }
        if (!Objects.equals(message.getGbAiMessageConversationId(), request.getSourceConversationId())) {
            throw new WorkRecordBusinessCardException(
                    WorkRecordBusinessCardErrors.MESSAGE_NOT_FOUND,
                    "source message does not belong to source conversation");
        }
        if (!ROLE_ASSISTANT.equalsIgnoreCase(message.getGbAiMessageRole())) {
            throw new WorkRecordBusinessCardException(
                    WorkRecordBusinessCardErrors.MESSAGE_NOT_FOUND, "source message must be assistant");
        }
        if (message.getGbAiMessageRunId() == null
                || !Objects.equals(message.getGbAiMessageRunId(), request.getSourceRunId())) {
            throw new WorkRecordBusinessCardException(
                    WorkRecordBusinessCardErrors.RUN_MISMATCH,
                    "sourceRunId does not match assistant message runId");
        }

        List<Map<String, Object>> cards =
                WorkRecordBusinessCardCardsJsonSupport.parseCardsArray(message.getGbAiMessageCardsJson());

        return new LoadedBusinessSource(scope, bizConv, message, cards);
    }

    private static void validateRequest(WorkRecordFromBusinessCardRequest request) {
        if (request == null || request.getUserId() == null) {
            throw new WorkRecordBusinessCardException(
                    WorkRecordBusinessCardErrors.MESSAGE_NOT_FOUND, "userId required");
        }
        if (request.getDepartmentId() == null) {
            throw new IllegalArgumentException("departmentId required");
        }
        if (request.getSourceConversationId() == null
                || request.getSourceMessageId() == null
                || request.getSourceRunId() == null) {
            throw new WorkRecordBusinessCardException(
                    WorkRecordBusinessCardErrors.MESSAGE_NOT_FOUND, "source conversation/message/run required");
        }
        if (!StringUtils.hasText(request.getSourceCardType())) {
            throw new WorkRecordBusinessCardException(
                    WorkRecordBusinessCardErrors.CARD_NOT_FOUND, "sourceCardType required");
        }
        if (!StringUtils.hasText(request.getSourceItemKey())) {
            throw new WorkRecordBusinessCardException(
                    WorkRecordBusinessCardErrors.ITEM_NOT_FOUND, "sourceItemKey required");
        }
    }
}
